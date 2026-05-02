package com.halqa.app.livekit

import android.content.Context
import com.halqa.app.data.remote.ApiClient
import com.halqa.app.data.remote.LiveKitTokenRequest
import io.livekit.android.ConnectOptions
import io.livekit.android.LiveKit
import io.livekit.android.RoomOptions
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.track.RemoteVideoTrack
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Singleton viewer session — joins a LiveKit room as subscriber and exposes
 * the broadcaster's video track for rendering.
 *
 * One viewer session at a time on mobile. Calling [start] for a new stream
 * automatically tears the previous one down.
 */
object WatchSession {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<WatchState>(WatchState.Idle)
    val state: StateFlow<WatchState> = _state.asStateFlow()

    @Volatile private var room: Room? = null
    private var eventsJob: Job? = null

    fun start(appContext: Context, streamId: String, ownerUid: String?) {
        cleanup()
        _state.value = WatchState.Connecting(streamId)

        scope.launch {
            try {
                val tokenResp = withContext(Dispatchers.IO) {
                    ApiClient.api.livekitToken(
                        LiveKitTokenRequest(
                            roomName = streamId,
                            role = "viewer",
                        )
                    )
                }

                val newRoom = LiveKit.create(
                    appContext = appContext.applicationContext,
                    options = RoomOptions(adaptiveStream = true),
                )
                room = newRoom

                eventsJob?.cancel()
                eventsJob = scope.launch { newRoom.events.collect(::onRoomEvent) }

                newRoom.connect(
                    url = tokenResp.url,
                    token = tokenResp.token,
                    options = ConnectOptions(autoSubscribe = true),
                )

                _state.value = WatchState.Watching(
                    streamId = streamId,
                    remoteVideo = currentVideoTrack(newRoom),
                    ownerUid = ownerUid,
                    viewerCount = newRoom.remoteParticipants.size,
                )
            } catch (t: Throwable) {
                cleanup()
                _state.value = WatchState.Failed(streamId, t.message ?: "تعذّر الانضمام للبث")
            }
        }
    }

    fun stop() {
        cleanup()
        _state.value = WatchState.Idle
    }

    private fun onRoomEvent(event: RoomEvent) {
        val r = room ?: return
        when (event) {
            is RoomEvent.TrackSubscribed,
            is RoomEvent.TrackUnsubscribed,
            is RoomEvent.ParticipantConnected,
            is RoomEvent.ParticipantDisconnected -> {
                val current = _state.value as? WatchState.Watching ?: return
                _state.value = current.copy(
                    remoteVideo = currentVideoTrack(r),
                    viewerCount = r.remoteParticipants.size,
                )
            }
            is RoomEvent.Disconnected -> {
                cleanup()
                _state.value = WatchState.Ended("انتهى البث")
            }
            else -> Unit
        }
    }

    private fun currentVideoTrack(r: Room): VideoTrack? {
        // First remote participant publishing a CAMERA track wins.
        for (p in r.remoteParticipants.values) {
            val pub = p.getTrackPublication(Track.Source.CAMERA)
            val t = pub?.track
            if (t is RemoteVideoTrack) return t
        }
        return null
    }

    private fun cleanup() {
        eventsJob?.cancel()
        eventsJob = null
        room?.disconnect()
        room?.release()
        room = null
    }
}
