package com.halqa.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.halqa.app.data.remote.ApiClient
import com.halqa.app.data.remote.LiveStreamDto
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Read-only live-streams repository.
 *
 * Reads come straight from Firestore (`streams` where status == "live") so
 * the feed is real-time without a polling loop. Writes (start/end) go via
 * the backend at [ApiClient.api] so server-side audit logging stays the
 * single source of truth.
 */
object StreamsRepository {

    private fun col() = FirebaseFirestore.getInstance().collection("streams")

    /** Real-time list of `live` streams. Empty if there are none. */
    fun liveStreams(): Flow<List<LiveStreamDto>> = callbackFlow {
        val reg = col()
            .whereEqualTo("status", "live")
            .orderBy("startTime", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val items = snap?.documents?.mapNotNull { d ->
                    val streamId = d.getString("streamId") ?: return@mapNotNull null
                    val ownerUid = d.getString("ownerUid") ?: return@mapNotNull null
                    val title = d.getString("title") ?: ""
                    val startTime = d.getString("startTime")
                    val viewers = (d.getLong("viewerCount") ?: 0L).toInt()
                    val roomName = d.getString("roomName") ?: streamId
                    LiveStreamDto(streamId, ownerUid, title, startTime, viewers, roomName)
                } ?: emptyList()
                trySend(items)
            }
        awaitClose { reg.remove() }
    }
}
