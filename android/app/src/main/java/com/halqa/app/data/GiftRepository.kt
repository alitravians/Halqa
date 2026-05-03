package com.halqa.app.data

import com.halqa.app.data.remote.ApiClient
import com.halqa.app.data.remote.GiftDto
import com.halqa.app.data.remote.SendGiftRequest
import com.halqa.app.data.remote.SendGiftResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Gifting orchestration on top of the backend's `/api/gifts/...` endpoints.
 *
 * Why this lives outside the screen-level ViewModel:
 *   - The catalogue is shared by the broadcaster overlay (diamonds
 *     raised), the watch screen (gift picker), and the wallet screen
 *     (top-up). We cache it in-process so each screen doesn't re-fetch
 *     on every recomposition.
 *   - Sending a gift is rate-sensitive: a fast double-tap should not
 *     fire two transactions at the wallet's limit. The mutex below is
 *     defence-in-depth alongside the server-side atomic txn.
 *
 * All authoritative state — balances, gift totals — flows back via
 * Firestore listeners (WalletRepository, StreamsRepository.observe)
 * after the txn commits. This module is the *write* path; the reads
 * are SSoT-driven.
 */
object GiftRepository {

    private val _catalog = MutableStateFlow<List<GiftDto>>(emptyList())
    val catalog: StateFlow<List<GiftDto>> = _catalog.asStateFlow()

    private val sendMutex = Mutex()

    @Volatile private var catalogLoaded: Boolean = false

    /**
     * Fetch the gift catalog from the backend. Idempotent — once
     * loaded successfully we don't re-fetch on every screen open.
     * The backend caches the response for 60s anyway.
     */
    suspend fun ensureCatalog(forceRefresh: Boolean = false) {
        if (catalogLoaded && !forceRefresh && _catalog.value.isNotEmpty()) return
        val resp = withContext(Dispatchers.IO) { ApiClient.api.giftCatalog() }
        _catalog.value = resp.gifts
        catalogLoaded = true
    }

    /**
     * Send a gift. Suspends until the server commits the atomic
     * transaction; on success the post-txn balance is returned so the
     * UI can render an optimistic update before the Firestore listener
     * fires (typically within 100ms anyway).
     *
     * The mutex guarantees this client emits at most one outstanding
     * `/api/gifts/send` at a time — a double-tap on the gift button
     * never causes two simultaneous debits even if the rendering
     * thread queues them.
     *
     * Throws on backend failure (insufficient coins, stream ended,
     * self-gift, etc). Caller surfaces the message.
     */
    suspend fun send(streamId: String, giftId: String, count: Int = 1): SendGiftResponse {
        return sendMutex.withLock {
            withContext(Dispatchers.IO) {
                ApiClient.api.sendGift(SendGiftRequest(streamId, giftId, count))
            }
        }
    }
}
