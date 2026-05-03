package com.halqa.app.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Wallet snapshot for a single user — the source of truth for the in-app
 * spendable currency (`coins`, paid for in SAR via the coin packages) and the
 * non-spendable earnings currency (`diamonds`, accrued from received gifts).
 *
 * `coinsSpent` and `diamondsEarned` are lifetime counters used by the
 * Gift Loop telemetry; the `EarningsSection` of the wallet UI shows the
 * cash-out side derived from `diamonds`.
 */
data class WalletSnapshot(
    val coins: Long = 0L,
    val diamonds: Long = 0L,
    val coinsSpent: Long = 0L,
    val diamondsEarned: Long = 0L,
)

/**
 * Real-time wallet feed.
 *
 * Reads come straight from Firestore (`wallets/{uid}`) so balance changes
 * (gift sent, top-up succeeded, withdrawal approved) propagate to every
 * device instantly. Writes go through the backend (`POST /api/wallet/...`)
 * so server-side audit logging stays the only place where coin / diamond
 * deltas are issued — the Android client never writes balance fields
 * directly.
 *
 * Architectural note (Khalid M1 audit): this is part of the
 * server-authoritative state migration. Previously the wallet screen
 * showed a hardcoded `12,480` UI placeholder; the only source of truth
 * for balances is now this Firestore document.
 */
object WalletRepository {

    private fun col() = FirebaseFirestore.getInstance().collection("wallets")

    /**
     * Real-time wallet for [uid]. Emits a zeroed [WalletSnapshot] when the
     * doc is missing (brand-new user) or on listener errors so the UI
     * always has *something* to render — never a hardcoded mock value.
     */
    fun observe(uid: String): Flow<WalletSnapshot> = callbackFlow {
        val reg = col().document(uid).addSnapshotListener { snap, err ->
            if (err != null || snap == null || !snap.exists()) {
                trySend(WalletSnapshot())
                return@addSnapshotListener
            }
            val data = snap.data ?: emptyMap()
            trySend(
                WalletSnapshot(
                    coins = (data["coins"] as? Long) ?: 0L,
                    diamonds = (data["diamonds"] as? Long) ?: 0L,
                    coinsSpent = (data["coinsSpent"] as? Long) ?: 0L,
                    diamondsEarned = (data["diamondsEarned"] as? Long) ?: 0L,
                )
            )
        }
        awaitClose { reg.remove() }
    }
}
