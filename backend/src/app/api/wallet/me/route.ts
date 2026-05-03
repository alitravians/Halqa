import type { NextRequest } from "next/server";
import { adminFirestore } from "@/lib/firebase-admin";
import { asError, asJson, requireUser } from "@/lib/auth";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/**
 * GET /api/wallet/me — returns the authenticated user's wallet snapshot.
 *
 * The Android client primarily reads the wallet via the realtime
 * Firestore listener (WalletRepository); this REST mirror exists for:
 *   - First-app-open cold start before the listener attaches, so the
 *     header chip can render without a Firestore round-trip blocking.
 *   - Backend-side debugging / the future admin panel surface.
 *
 * Side-effect-free: GET MUST NOT write. The previous implementation
 * upserted a zero-balance wallet doc on the first read, which raced
 * with /api/wallet/topup and /api/gifts/send. Specifically, if a
 * brand-new user opened the wallet screen and tapped "top up" within
 * the same Vercel invocation window, the wallet/me handler could
 * finish AFTER the topup's transactional create, and the trailing
 * `set({ coins: 0, ... }, { merge: true })` would clobber the
 * topup's `coins: 1000` back to 0 — silent fund loss. Returning
 * defaults from a non-existent doc gives the Android listener the
 * exact same UX (it already emits a zero `WalletSnapshot()` when
 * `snap.exists == false`) without ever writing on a GET.
 *
 * The wallet doc is created lazily by /api/wallet/topup and
 * /api/gifts/send via FieldValue.increment + set+merge inside their
 * own transactions, so first-actual-coin-event is what materialises
 * the row.
 */
export async function GET(req: NextRequest) {
  try {
    const user = await requireUser(req);
    const db = adminFirestore();
    const ref = db.collection("wallets").doc(user.uid);
    const snap = await ref.get();
    if (!snap.exists) {
      return asJson(200, {
        uid: user.uid,
        coins: 0,
        diamonds: 0,
        coinsSpent: 0,
        diamondsEarned: 0,
      });
    }
    const d = snap.data() ?? {};
    return asJson(200, {
      uid: user.uid,
      coins: Number(d.coins ?? 0),
      diamonds: Number(d.diamonds ?? 0),
      coinsSpent: Number(d.coinsSpent ?? 0),
      diamondsEarned: Number(d.diamondsEarned ?? 0),
    });
  } catch (e) {
    return asError(e);
  }
}
