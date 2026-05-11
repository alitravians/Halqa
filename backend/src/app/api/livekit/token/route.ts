import { NextRequest } from "next/server";
import { AccessToken } from "livekit-server-sdk";
import { adminFirestore } from "@/lib/firebase-admin";
import { asError, asJson, HttpError, requireUser } from "@/lib/auth";
import { assertNotBanned } from "@/lib/bans";
import { assertProdSafe, evaluateKycBeta } from "@/lib/kyc-allowlist";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

interface TokenBody {
  roomName: string;
  /** "publisher" lets the user broadcast; "viewer" subscribes only. */
  role: "publisher" | "viewer";
  /** Optional title for stream metadata when starting a publisher session. */
  streamTitle?: string;
}

const ROOM_NAME_RE = /^[A-Za-z0-9_-]{4,64}$/;

/**
 * Hard cap for `streamTitle`. Picked at 120 because:
 *
 *   - The Android FeedScreen renders titles via
 *     `s.title.substringBefore(" ").take(20)`, so anything past the
 *     first word is unread on the grid. 120 chars leaves a healthy
 *     margin for two-line render on the LiveWatchScreen header.
 *   - LiveKit's participant.metadata is capped (effectively low-kB)
 *     and the title is fed in via `metadata` on the AccessToken.
 *   - The single most important reason: without this cap, a sender
 *     can ship a multi-megabyte title which is then:
 *       1. Written to `streams/{streamId}.title` (Firestore doc 1MB
 *          hard limit — a single bloated title can push the doc
 *          near the limit, making subsequent in-place updates
 *          (viewerCount ticks, status flips) fail with the cryptic
 *          "Document exceeds maximum size" 500.
 *       2. Replicated to every viewer's snapshot listener on every
 *          viewerCount tick — bandwidth amplification: 1MB × N
 *          viewers per Firestore tick. A single attacker sets the
 *          burden on every other client (and on the host's data
 *          plan as their stream room fills).
 *       3. Echoed into the audit_log entry under
 *          `metadata.title`, doubling the storage footprint.
 *
 * 120 chars matches the implicit cap users/me POST already enforces
 * for `displayName` / `bio` (280 there, 120 here for grid render),
 * keeping all user-controlled strings on a small denominator.
 */
const STREAM_TITLE_MAX = 120;

/** Default title when the publisher leaves the field blank. */
const DEFAULT_STREAM_TITLE = "بث جديد";

/**
 * Normalise an incoming `streamTitle`:
 *   - undefined / non-string → default ("بث جديد").
 *   - trimmed empty → default.
 *   - longer than [STREAM_TITLE_MAX] graphemes → reject (400).
 *   - else → trimmed string.
 *
 * Returning a `string | { error }` discriminated shape keeps the
 * decision in one place; the caller surfaces 400 with the inner
 * error verbatim. JS string `.length` counts UTF-16 code units, not
 * Unicode codepoints — but the 1MB Firestore doc limit is in
 * BYTES, and one Arabic codepoint in UTF-16 is 1-2 code units, so
 * `.length <= 120` already guarantees a small byte footprint
 * (worst case 4 bytes per codepoint × 120 = 480 bytes). Cheaper
 * than running through Intl.Segmenter for grapheme counting.
 */
function normaliseStreamTitle(
  raw: unknown
): { ok: true; value: string } | { ok: false; error: string } {
  if (raw === undefined || raw === null) {
    return { ok: true, value: DEFAULT_STREAM_TITLE };
  }
  if (typeof raw !== "string") {
    return { ok: false, error: "streamTitle must be a string." };
  }
  if (raw.length > STREAM_TITLE_MAX) {
    // Reject BEFORE trim so a 10MB-of-spaces payload still fails
    // fast — trim() on a multi-megabyte string is cheap but the
    // round-trip waste isn't worth the courtesy.
    return {
      ok: false,
      error: `streamTitle too long (max ${STREAM_TITLE_MAX} chars).`,
    };
  }
  const trimmed = raw.trim();
  if (trimmed.length === 0) {
    return { ok: true, value: DEFAULT_STREAM_TITLE };
  }
  return { ok: true, value: trimmed };
}

/**
 * Compute the broadcaster's age in completed years from a `dob`
 * stored as a UTC ISO date string ("YYYY-MM-DD" — the format written
 * by the Android `DateOfBirthScreen.persistDob`). Returns `null` if
 * the input is malformed; the caller turns `null` into HTTP 403
 * `DOB_INVALID` (the server-side gate is strict — unlike the
 * Android client gate which falls through to allow on malformed
 * input, we refuse here because there is no slower-paced UI to
 * recover through).
 *
 * Mirrors the Android computeAgeYearsFromIsoDob in
 * `GoLivePrepScreen.kt` deliberately: same UTC calendar, same
 * "completed years" semantics. Keeping the two implementations
 * functionally identical means a date that the client picker
 * accepted will never be rejected by this server-side parser as
 * malformed, and a client-side ≥18 verdict matches the server-side
 * verdict exactly (no edge-case timezone drift letting a 17-year-old
 * pass the client gate but fail the server gate or vice versa).
 */
function computeAgeYearsFromIsoDob(iso: string): number | null {
  const parts = iso.split("-");
  if (parts.length !== 3) return null;
  const y = Number.parseInt(parts[0], 10);
  const m = Number.parseInt(parts[1], 10);
  const d = Number.parseInt(parts[2], 10);
  if (!Number.isInteger(y) || !Number.isInteger(m) || !Number.isInteger(d)) {
    return null;
  }
  if (m < 1 || m > 12 || d < 1 || d > 31) return null;
  // UTC "today" — matches the picker's UTC calendar so a stream
  // started from a UTC-ahead timezone doesn't see a one-day skew.
  const now = new Date();
  const curY = now.getUTCFullYear();
  const curM = now.getUTCMonth() + 1;
  const curD = now.getUTCDate();
  let years = curY - y;
  if (curM < m || (curM === m && curD < d)) {
    years -= 1;
  }
  if (years < 0 || years > 200) return null;
  return years;
}

export async function POST(req: NextRequest) {
  try {
    // Refuse to issue any tokens if a deploy left the unsafe combo
    // (live monetization + global KYC bypass) on. Better to 500 the
    // whole route than to silently let strangers broadcast in
    // production.
    assertProdSafe();
    const user = await requireUser(req);
    const body = (await req.json()) as Partial<TokenBody>;

    if (!body.roomName || typeof body.roomName !== "string") {
      throw new HttpError(400, "roomName is required.");
    }
    if (!ROOM_NAME_RE.test(body.roomName)) {
      throw new HttpError(
        400,
        "roomName must be 4-64 chars, alphanumeric / underscore / dash only."
      );
    }
    const role = body.role === "publisher" ? "publisher" : "viewer";

    // Validate the title NOW (before any DB work) so a malformed
    // payload fails fast with a 400 instead of getting halfway through
    // a Firestore transaction. The cap also defends against
    // multi-megabyte titles that would push the streams/{id} doc
    // toward Firestore's 1MB hard limit and amplify into every viewer's
    // snapshot listener — see normaliseStreamTitle for the full rationale.
    const titleResult = normaliseStreamTitle(body.streamTitle);
    if (!titleResult.ok) {
      throw new HttpError(400, titleResult.error);
    }
    const safeTitle = titleResult.value;

    const apiKey = process.env.LIVEKIT_API_KEY;
    const apiSecret = process.env.LIVEKIT_API_SECRET;
    const wsUrl = process.env.LIVEKIT_URL;
    if (!apiKey || !apiSecret || !wsUrl) {
      throw new HttpError(
        500,
        "LiveKit env vars are not configured (LIVEKIT_API_KEY/SECRET/URL)."
      );
    }

    const db = adminFirestore();

    // P0 — gate every request, even viewers, against an active ban.
    // Identical query to /lib/bans.ts; routed through the helper so
    // gifts/send + wallet/topup + this endpoint stay aligned on a
    // single source of truth.
    await assertNotBanned(user.uid);

    if (role === "publisher") {
      // P0 — only KYC-approved users may broadcast.
      // Beta gating is now allowlist-based (see lib/kyc-allowlist.ts):
      //   - STATIC_ALLOWLIST (founding team)
      //   - KYC_BETA_ALLOWLIST env CSV (operator-managed testers)
      //   - legacy global BYPASS_KYC_FOR_BETA flag (logs a warning on use)
      // Anyone outside the allowlist still needs an approved KYC submission.
      const beta = evaluateKycBeta(user.uid);
      if (!beta.allowed) {
        const kycSnap = await db
          .collection("kyc_submissions")
          .doc(user.uid)
          .get();
        const kycStatus = kycSnap.exists
          ? (kycSnap.data()?.status as string | undefined)
          : undefined;
        if (kycStatus !== "approved") {
          throw new HttpError(
            403,
            "Publisher access requires approved KYC. Submit KYC and wait for review."
          );
        }
      }

      // Layla GR3 — server-side broadcaster ≥18 gate.
      //
      // The Android client at `GoLivePrepScreen.launchBroadcast()` has
      // its own client-side gate, but that gate is intentionally
      // permissive on the "Firestore read still in flight" path
      // (`dobLoaded == false` → fall through to broadcast so a slow
      // network doesn't block known-adult users). It is also permissive
      // when `computeAgeYearsFromIsoDob()` returns null on a malformed
      // string. Either path lets a 17-year-old who taps "ابدأ البث"
      // fast enough — or any non-Android client (web, direct curl,
      // a tampered APK) — slip past the gate entirely.
      //
      // This server-side gate is the durable enforcement. It reads
      // the same `/users/{uid}.dob` field the client writes from
      // `DateOfBirthScreen.persistDob` and parses it the same way.
      // The order of checks:
      //
      //   1. user doc missing            → 403 DOB_NOT_ATTESTED
      //                                    (closed-beta v0.1.20/v0.1.21
      //                                    grandfathered users — the
      //                                    Android client routes them
      //                                    through DateOfBirthScreen
      //                                    once on v0.1.22 first-launch,
      //                                    but a non-Android caller
      //                                    must be turned away).
      //   2. `dob` absent / blank        → 403 DOB_NOT_ATTESTED.
      //   3. `dob` malformed             → 403 DOB_INVALID.
      //   4. age < 18                    → 403 BROADCASTER_UNDERAGE.
      //   5. age ≥ 18                    → fall through to room-name
      //                                    + atomic-create block.
      //
      // Why a separate read instead of folding into the transaction
      // below: the room-create txn already does its own reads/writes;
      // adding another collection's read to that txn inflates the
      // contention surface and obscures the audit trail. The dob
      // field is immutable from the client (firestore rule on
      // /users/{uid} self-update locks both `dob` and `dob_attested_at`),
      // so we can read it outside the txn without TOCTOU risk.
      const userSnap = await db.collection("users").doc(user.uid).get();
      const userData = userSnap.exists ? userSnap.data() ?? {} : {};
      const rawDob = userData.dob;
      if (typeof rawDob !== "string" || rawDob.trim().length === 0) {
        throw new HttpError(
          403,
          "DOB_NOT_ATTESTED — date of birth must be attested before broadcasting."
        );
      }
      const ageYears = computeAgeYearsFromIsoDob(rawDob.trim());
      if (ageYears === null) {
        throw new HttpError(
          403,
          "DOB_INVALID — stored date of birth is malformed. Contact support."
        );
      }
      if (ageYears < 18) {
        throw new HttpError(
          403,
          "BROADCASTER_UNDERAGE — broadcasting requires age 18 or older."
        );
      }

      // P0 — room name MUST belong to this user. Reject squatting/impersonation.
      // Convention: roomName starts with `u_<uid>_` for user-owned streams.
      if (!body.roomName.startsWith(`u_${user.uid}_`)) {
        throw new HttpError(
          403,
          `roomName must be prefixed with u_${user.uid}_ for publisher tokens.`
        );
      }

      // P0 — atomic create-if-absent so two devices can't open two `live` docs.
      //
      // Three cases for an existing doc with this roomName:
      //
      //   (a) ownerUid != caller.uid → room-name squatting / impersonation.
      //   (b) ownerUid == caller.uid && status == "live" → legitimate resume
      //       of a *still-live* stream. The Android client generates a new
      //       streamId per session (`u_{uid}_{ts}` in BroadcastSession.start),
      //       so this realistically only happens when LiveKit briefly
      //       disconnects and the broadcaster reconnects within the
      //       room-empty timeout (~5min) — the stream doc was never marked
      //       ended, just hand them a fresh AccessToken and move on.
      //   (c) ownerUid == caller.uid && status != "live" → the stream was
      //       deliberately ended (by the owner via /api/streams/end, by a
      //       moderator force-ending it, or by the LiveKit room_finished
      //       webhook). Reusing the same streamId here is dangerous on
      //       three counts:
      //         1. Moderation bypass — a moderator force-ends a banned
      //            stream, the broadcaster immediately POSTs /livekit/token
      //            with the same roomName, status flips back to "live",
      //            the moderator's action is silently undone with no
      //            audit trail.
      //         2. Audit hole — there's no `stream_start` audit_log entry
      //            for the resume, so Trust & Safety can't reconstruct
      //            "the user broadcasted again at time T after being
      //            ended at time T-30s".
      //         3. Analytics drift — the resume overwrites `startTime`,
      //            losing the original session length.
      //       The radical fix is to refuse the resume entirely. Clients
      //       that legitimately want to broadcast again must call
      //       /livekit/token with a fresh streamId (which the Android
      //       client already generates on every BroadcastSession.start
      //       via `System.currentTimeMillis()`).
      await db.runTransaction(async (tx) => {
        const ref = db.collection("streams").doc(body.roomName!);
        const existing = await tx.get(ref);
        if (existing.exists) {
          const data = existing.data()!;
          if (data.ownerUid !== user.uid) {
            throw new HttpError(409, "Room name already taken by another user.");
          }
          if (data.status !== "live") {
            throw new HttpError(
              409,
              "This stream has already ended. Start a new broadcast."
            );
          }
          return;
        }
        const now = new Date().toISOString();
        tx.set(ref, {
          streamId: body.roomName,
          ownerUid: user.uid,
          title: safeTitle,
          status: "live",
          startTime: now,
          endTime: null,
          viewerCount: 0,
          roomName: body.roomName,
          createdAt: now,
        });
        const auditRef = db.collection("audit_log").doc();
        tx.set(auditRef, {
          userId: user.uid,
          action: "stream_start",
          timestamp: now,
          metadata: {
            streamId: body.roomName,
            // Always the normalised value — the audit_log entry mirrors
            // exactly what we wrote to streams/{id}.title, so reviewers
            // never have to reconcile a bloated raw payload against
            // the truncated stored title.
            title: safeTitle,
          },
        });
      });
    } else {
      // Viewer — confirm the room exists and is live before issuing a token.
      const streamSnap = await db.collection("streams").doc(body.roomName).get();
      if (!streamSnap.exists) {
        throw new HttpError(404, "Stream not found.");
      }
      const data = streamSnap.data()!;
      if (data.status !== "live") {
        throw new HttpError(410, "Stream is not currently live.");
      }
    }

    // PDPL-critical: the LiveKit `name` field is broadcast to every
    // other participant in the room as part of the participant
    // metadata. We MUST NOT put `email` or `phoneNumber` here — those
    // are PII and Halqa's privacy policy promises they never leave the
    // backend. The previous fallback chain (`email || phoneNumber || uid`)
    // exposed every host's email/phone to every viewer of their stream
    // (and every viewer's email/phone to every other viewer).
    //
    // Use the public profile fields only:
    //   1. displayName — what the user typed in their profile
    //   2. handle      — public @-name fallback
    //   3. uid         — opaque pseudonymous string, last resort so
    //                    LiveKit always has a non-empty value
    const at = new AccessToken(apiKey, apiSecret, {
      identity: user.uid,
      name: user.displayName || user.handle || user.uid,
      ttl: 60 * 60, // 1 hour
    });
    at.addGrant({
      room: body.roomName,
      roomJoin: true,
      canPublish: role === "publisher",
      canSubscribe: true,
      canPublishData: true,
    });
    const token = await at.toJwt();

    return asJson(200, { token, url: wsUrl });
  } catch (err) {
    return asError(err);
  }
}
