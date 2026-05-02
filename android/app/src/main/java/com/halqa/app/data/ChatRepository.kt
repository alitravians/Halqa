package com.halqa.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Real-time chat for a single live stream.
 *
 * Reads come straight from Firestore at `streams/{streamId}/chat`, ordered
 * by `createdAt` ascending and capped at the most recent 200 messages so
 * older history is paged in deliberately rather than loaded eagerly into
 * memory. Writes go through the backend (`POST /api/streams/{id}/chat`)
 * so server-side moderation, ban-checks and audit logging stay
 * authoritative — the Android client never writes to the chat
 * subcollection directly.
 */
object ChatRepository {

    private fun col(streamId: String) = FirebaseFirestore.getInstance()
        .collection("streams").document(streamId).collection("chat")

    /**
     * Reactive chat history for [streamId]. Emits the empty list when
     * there are no messages yet or on listener errors.
     *
     * The returned [ChatMsg] list is in display order (oldest first); the
     * UI scrolls to the bottom on each new message.
     */
    fun observe(streamId: String): Flow<List<ChatMsg>> = callbackFlow {
        if (streamId.isBlank()) {
            trySend(emptyList())
            awaitClose { /* nothing to detach */ }
            return@callbackFlow
        }
        val reg = col(streamId)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limitToLast(200)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val items = snap.documents.mapNotNull { d ->
                    val text = d.getString("text") ?: return@mapNotNull null
                    val author = d.getString("authorName")
                        ?: d.getString("authorUid")
                        ?: return@mapNotNull null
                    ChatMsg(
                        id = d.id,
                        user = author,
                        message = text,
                        isMod = (d.getBoolean("isMod") ?: false),
                        isVip = (d.getBoolean("isVip") ?: false),
                    )
                }
                trySend(items)
            }
        awaitClose { reg.remove() }
    }
}
