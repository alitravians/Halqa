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

    /**
     * Real-time observer for a single stream's `viewerCount`.
     *
     * The authoritative viewer count lives in Firestore at
     * `streams/{streamId}.viewerCount`, populated by the LiveKit webhook
     * on the backend (`POST /api/livekit/webhook` ↔ `participant_joined`
     * / `participant_left`). We deliberately do NOT use LiveKit's
     * `Room.remoteParticipants.size` because:
     *
     *   1. On the broadcaster side, that count includes any stale
     *      participants the server hasn't garbage-collected yet (the
     *      "60 bots" Ali observed during testing — phantom subscribers
     *      from previous test sessions whose websockets weren't closed
     *      cleanly).
     *   2. It diverges between broadcaster and viewers (each room party
     *      sees a different `remoteParticipants` set — themselves
     *      excluded), so the same stream would show two different counts.
     *   3. It can't distinguish viewers from other publishers / signalling
     *      ghosts.
     *
     * The Firestore-backed count is a single source of truth and matches
     * what the Feed grid shows.
     *
     * Emits 0 when the document is missing (stream hasn't been created
     * yet) or on listener errors.
     */
    fun viewerCount(streamId: String): Flow<Int> = callbackFlow {
        val reg = col().document(streamId).addSnapshotListener { snap, err ->
            if (err != null || snap == null || !snap.exists()) {
                trySend(0)
                return@addSnapshotListener
            }
            val count = (snap.getLong("viewerCount") ?: 0L).toInt().coerceAtLeast(0)
            trySend(count)
        }
        awaitClose { reg.remove() }
    }

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
