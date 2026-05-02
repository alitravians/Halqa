package com.halqa.app.livekit

import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.VideoTrack

/**
 * Single high-level state for the publisher (broadcast) session.
 *
 * Kept intentionally small; the live `Room` instance lives inside
 * [BroadcastSession] and is not exposed as part of the state class so it
 * cannot accidentally leak across screens or be mutated outside the session.
 */
sealed class BroadcastState {
    object Idle : BroadcastState()
    data class Connecting(val streamId: String) : BroadcastState()
    data class Live(
        val streamId: String,
        val localVideo: LocalVideoTrack?,
        val cameraEnabled: Boolean,
        val micEnabled: Boolean,
        val viewerCount: Int,
        val startedAtMillis: Long,
    ) : BroadcastState()
    data class Failed(val streamId: String?, val message: String) : BroadcastState()
    object Stopping : BroadcastState()
}

/** Subscriber-side state for the watcher. */
sealed class WatchState {
    object Idle : WatchState()
    data class Connecting(val streamId: String) : WatchState()
    data class Watching(
        val streamId: String,
        val remoteVideo: VideoTrack?,
        val ownerUid: String?,
        val viewerCount: Int,
    ) : WatchState()
    data class Ended(val reason: String) : WatchState()
    data class Failed(val streamId: String?, val message: String) : WatchState()
}
