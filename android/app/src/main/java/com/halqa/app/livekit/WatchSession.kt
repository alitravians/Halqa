package com.halqa.app.livekit

import android.content.Context
import com.halqa.app.data.StreamsRepository
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
import java.util.concurrent.atomic.AtomicInteger

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
    private var connectJob: Job? = null
    private var streamObserveJob: Job? = null

    /**
     * Generation counter — see [BroadcastSession.sessionGen] for the
     * full rationale. Bumped on every [start] / [stop]. Ensures a
     * superseded connect coroutine cannot leak a `Room` into the
     * shared `room` field after the user has already navigated away.
     */
    private val sessionGen = AtomicInteger(0)

    /**
     * The active LiveKit [Room], if any. Exposed read-only so the Compose
     * video renderer can call [Room.initVideoRenderer] before binding the
     * remote video track — required to actually display the stream.
     */
    val activeRoom: Room? get() = room

    fun start(appContext: Context, streamId: String, ownerUid: String?) {
        cleanup()
        _state.value = WatchState.Connecting(streamId)

        val gen = sessionGen.incrementAndGet()
        connectJob = scope.launch {
            try {
                val tokenResp = withContext(Dispatchers.IO) {
                    ApiClient.api.livekitToken(
                        LiveKitTokenRequest(
                            roomName = streamId,
                            role = "viewer",
                        )
                    )
                }
                if (gen != sessionGen.get()) return@launch

                val newRoom = LiveKit.create(
                    appContext = appContext.applicationContext,
                    options = RoomOptions(adaptiveStream = true),
                )
                if (gen != sessionGen.get()) {
                    // Superseded between LiveKit.create() and connect()
                    // — release the unconnected Room so we don't leak.
                    newRoom.release()
                    return@launch
                }
                room = newRoom

                eventsJob?.cancel()
                eventsJob = scope.launch { newRoom.events.collect(::onRoomEvent) }

                newRoom.connect(
                    url = tokenResp.url,
                    token = tokenResp.token,
                    options = ConnectOptions(autoSubscribe = true),
                )
                if (gen != sessionGen.get()) {
                    // Superseded during connect — disconnect & release
                    // and clear `room` only if it still points at us
                    // (a newer start() may have already replaced it).
                    newRoom.disconnect()
                    newRoom.release()
                    if (room === newRoom) room = null
                    return@launch
                }

                _state.value = WatchState.Watching(
                    streamId = streamId,
                    remoteVideo = currentVideoTrack(newRoom),
                    ownerUid = ownerUid,
                    viewerCount = 0,
                )

                // Subscribe to the full server-side stream document
                // (SSoT — see [StreamsRepository.observe]). Two reasons:
                //
                //   1. Authoritative viewer count comes from the LiveKit
                //      webhook → Firestore pipeline, not from
                //      `room.remoteParticipants.size` (which lies on a
                //      viewer-only client because they're excluded from
                //      their own count).
                //
                //   2. We surface server-driven `status == "ended"`
                //      transitions immediately. If the host's app crashes
                //      or loses network, the LiveKit transport may take
                //      tens of seconds to fire `Disconnected`; the
                //      Firestore listener fires the moment the backend
                //      webhook handler runs. Viewers see the "ended"
                //      screen without staring at a frozen last frame.
                streamObserveJob?.cancel()
                streamObserveJob = scope.launch {
                    StreamsRepository.observe(streamId).collect { snap ->
                        snap ?: return@collect
                        val cur = _state.value as? WatchState.Watching ?: return@collect
                        if (cur.streamId != streamId) return@collect
                        if (snap.isEnded) {
                            cleanup()
                            _state.value = WatchState.Ended("انتهى البث")
                            return@collect
                        }
                        if (cur.viewerCount != snap.viewerCount) {
                            _state.value = cur.copy(viewerCount = snap.viewerCount)
                        }
                    }
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                // Superseded by another start() — leave state to the caller.
            } catch (t: Throwable) {
                cleanup()
                _state.value = WatchState.Failed(streamId, t.message ?: "تعذّر الانضمام للبث")
            }
        }
    }

    fun stop() {
        // Bump the generation FIRST so any in-flight connect coroutine
        // (paused at the token RTT or the LiveKit connect handshake)
        // sees a stale snapshot at its next supersede check and bails
        // out without ever flipping state to Watching.
        sessionGen.incrementAndGet()
        cleanup()
        _state.value = WatchState.Idle
    }

    private fun onRoomEvent(event: RoomEvent) {
        val r = room ?: return
        when (event) {
            // Track-level events still drive the remote video binding —
            // we need to re-resolve which RemoteVideoTrack to render
            // when the publisher (un)subscribes camera. The viewer
            // count, on the other hand, comes from Firestore, never
            // from `r.remoteParticipants.size`.
            is RoomEvent.TrackSubscribed,
            is RoomEvent.TrackUnsubscribed,
            is RoomEvent.ParticipantConnected,
            is RoomEvent.ParticipantDisconnected -> {
                val current = _state.value as? WatchState.Watching ?: return
                _state.value = current.copy(
                    remoteVideo = currentVideoTrack(r),
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
        connectJob?.cancel()
        connectJob = null
        eventsJob?.cancel()
        eventsJob = null
        streamObserveJob?.cancel()
        streamObserveJob = null
        room?.disconnect()
        room?.release()
        room = null
    }
}
