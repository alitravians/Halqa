import { adminFirestore } from "@/lib/firebase-admin";
import { HttpError } from "@/lib/auth";

/**
 * Centralised ban-state check.
 *
 * Why this exists
 * ---------------
 * Until now only `/api/livekit/token` checked the bans collection
 * (PR earlier in the audit). Every other authenticated endpoint
 * trusted `requireUser` and proceeded — meaning a banned user could
 * still hit `POST /api/gifts/send` and `POST /api/wallet/topup`
 * directly via curl/Postman. The Android client wouldn't let them
 * (the broadcast/watch screens 403 out before reaching the gift UI),
 * but the server is the only authoritative gate and it was open on
 * those paths. The blast radius:
 *
 *   1. `gifts/send` — primary moderation bypass. The single most
 *      common reason to ban a user is harassment-via-gifting (spam
 *      gifts at a host they're harassing, manipulating per-host
 *      blocklists by churning streamIds). Every per-host blocklist
 *      written by hosts (`users/{hostUid}/giftBlocklist/{senderUid}`)
 *      had to be paired with a global ban to actually stop the
 *      sender; until now the global ban only stopped them from
 *      broadcasting / viewing, not from the very action they were
 *      banned for.
 *
 *   2. `wallet/topup` — banned user can keep loading their wallet
 *      (in beta, behind `BYPASS_TOPUP_FOR_BETA`) and continue
 *      spending it via the now-fixed gifts gate. Closes the topup
 *      side of the same loop.
 *
 * What is NOT gated (deliberately)
 * --------------------------------
 *   - `streams/end` — banned user must be able to clean up their
 *     own running stream so the LiveKit room frees up. Refusing the
 *     end call would leave a stale `live` doc + paid-for room until
 *     LiveKit's `room_finished` webhook fires.
 *   - `kyc/submit` — KYC is the appeal path. Refusing here would
 *     trap any banned user who needs identity verification to lift
 *     a ban.
 *   - `users/me` POST — profile updates (display name, bio, avatar)
 *     are not the action the ban targets. Letting a banned user
 *     correct their handle while suspended is harmless.
 *   - `settings` POST — preferences (language, theme, notifications)
 *     have no abuse vector.
 *   - `audit/[uid]` — reading own audit log is a PDPL-style right.
 *
 * Implementation
 * --------------
 * The bans collection is the source of truth. A doc with
 * `{userId, active: true}` blocks. Multiple bans are allowed
 * (the same user can be re-banned for new reasons); we only need
 * one active row to refuse. `.limit(1)` is the cheap stop.
 *
 * No composite index is needed: two equality filters use the
 * automatic single-field indexes Firestore always maintains.
 */
export async function assertNotBanned(uid: string): Promise<void> {
  const snap = await adminFirestore()
    .collection("bans")
    .where("userId", "==", uid)
    .where("active", "==", true)
    .limit(1)
    .get();
  if (!snap.empty) {
    throw new HttpError(403, "Account is banned.");
  }
}
