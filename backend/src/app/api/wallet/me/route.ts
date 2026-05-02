import type { NextRequest } from "next/server";
import { adminFirestore } from "@/lib/firebase-admin";
import { asError, asJson, requireUser } from "@/lib/auth";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/**
 * GET /api/wallet/me — returns the authenticated user's wallet doc.
 *
 * The Android client primarily reads the wallet via the realtime
 * Firestore listener (WalletRepository); this REST mirror exists for:
 *   - First-app-open cold start before the listener attaches, so the
 *     header chip can render without a Firestore round-trip blocking.
 *   - Backend-side debugging / the future admin panel surface.
 *
 * Auto-creates the wallet doc with zero balances on first call so a
 * fresh user has a row to listen on.
 */
export async function GET(req: NextRequest) {
  try {
    const user = await requireUser(req);
    const db = adminFirestore();
    const ref = db.collection("wallets").doc(user.uid);
    const snap = await ref.get();
    if (!snap.exists) {
      const fresh = {
        uid: user.uid,
        coins: 0,
        diamonds: 0,
        coinsSpent: 0,
        diamondsEarned: 0,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };
      await ref.set(fresh, { merge: true });
      return asJson(200, fresh);
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
