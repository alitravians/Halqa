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
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.Track
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
 * App-wide singleton that owns the LiveKit publisher session.
 *
 * One broadcaster per device. We expose only the minimum surface area
 * necessary for the broadcasting UI:
 *   - state: StateFlow<BroadcastState> for Compose to render against
 *   - start(...) / stop() lifecycle methods
 *   - simple toggles (camera, mic, switch camera)
 *
 * The underlying [Room] is kept private; the screen receives the local
 * [LocalVideoTrack] via the state object and renders it through
 * [HalqaVideoRenderer].
 */
object BroadcastSession {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<BroadcastState>(BroadcastState.Idle)
    val state: StateFlow<BroadcastState> = _state.asStateFlow()

    @Volatile private var room: Room? = null
    private var eventsJob: Job? = null
    private var startJob: Job? = null

    val isActive: Boolean
        get() = _state.value is BroadcastState.Connecting || _state.value is BroadcastState.Live

    /**
     * Request a publisher token from the backend, connect to LiveKit,
     * publish camera + mic. Streams created server-side; this only joins.
     *
     * @param appContext use the application context — the room outlives any single Activity.
     * @param streamId   roomName as known by the backend; MUST be in the
     *                   `u_<uid>_*` shape (server enforces).
     * @param title      optional human-readable title for `streams/{id}.title`.
     */
    fun start(appContext: Context, streamId: String, title: String?) {
        if (isActive) return
        _state.value = BroadcastState.Connecting(streamId)

        startJob?.cancel()
        startJob = scope.launch {
            try {
                val tokenResp = withContext(Dispatchers.IO) {
                    ApiClient.api.livekitToken(
                        LiveKitTokenRequest(
                            roomName = streamId,
                            role = "publisher",
                            streamTitle = title,
                        )
                    )
                }

                val newRoom = LiveKit.create(
                    appContext = appContext.applicationContext,
                    options = RoomOptions(adaptiveStream = true, dynacast = true),
                )
                room = newRoom

                eventsJob?.cancel()
                eventsJob = scope.launch { newRoom.events.collect(::onRoomEvent) }

                newRoom.connect(
                    url = tokenResp.url,
                    token = tokenResp.token,
                    options = ConnectOptions(autoSubscribe = false),
                )

                val local = newRoom.localParticipant
                local.setCameraEnabled(true)
                local.setMicrophoneEnabled(true)

                val cameraTrack = local.getTrackPublication(Track.Source.CAMERA)?.track as? LocalVideoTrack
                _state.value = BroadcastState.Live(
                    streamId = streamId,
                    localVideo = cameraTrack,
                    cameraEnabled = true,
                    micEnabled = true,
                    viewerCount = 0,
                    startedAtMillis = System.currentTimeMillis(),
                )
            } catch (_: kotlinx.coroutines.CancellationException) {
                // stop() was called while connecting — let stop() own teardown + state.
            } catch (t: Throwable) {
                cleanupRoom()
                _state.value = BroadcastState.Failed(streamId, t.message ?: "تعذّر الاتصال بالبث")
            }
        }
    }

    fun toggleCamera() {
        val current = _state.value as? BroadcastState.Live ?: return
        val r = room ?: return
        scope.launch {
            val next = !current.cameraEnabled
            r.localParticipant.setCameraEnabled(next)
            val cameraTrack = r.localParticipant
                .getTrackPublication(Track.Source.CAMERA)?.track as? LocalVideoTrack
            _state.value = current.copy(cameraEnabled = next, localVideo = cameraTrack)
        }
    }

    fun toggleMic() {
        val current = _state.value as? BroadcastState.Live ?: return
        val r = room ?: return
        scope.launch {
            val next = !current.micEnabled
            r.localParticipant.setMicrophoneEnabled(next)
            _state.value = current.copy(micEnabled = next)
        }
    }

    fun stop() {
        if (_state.value is BroadcastState.Idle) return
        _state.value = BroadcastState.Stopping

        // Tell the backend to flip status -> ended (creates audit log entry).
        val streamId = when (val s = state.value) {
            is BroadcastState.Live -> s.streamId
            is BroadcastState.Connecting -> s.streamId
            is BroadcastState.Failed -> s.streamId
            else -> null
        }
        scope.launch {
            try {
                if (streamId != null) {
                    withContext(Dispatchers.IO) {
                        ApiClient.api.endStream(
                            com.halqa.app.data.remote.EndStreamRequest(streamId)
                        )
                    }
                }
            } catch (_: Throwable) {
                // Network failure shouldn't block local teardown.
            } finally {
                cleanupRoom()
                _state.value = BroadcastState.Idle
            }
        }
    }

    private fun onRoomEvent(event: RoomEvent) {
        when (event) {
            is RoomEvent.ParticipantConnected,
            is RoomEvent.ParticipantDisconnected -> {
                val current = _state.value as? BroadcastState.Live ?: return
                val r = room ?: return
                _state.value = current.copy(viewerCount = r.remoteParticipants.size)
            }
            is RoomEvent.Disconnected -> {
                cleanupRoom()
                if (_state.value !is BroadcastState.Idle) {
                    _state.value = BroadcastState.Idle
                }
            }
            else -> Unit
        }
    }

    private fun cleanupRoom() {
        startJob?.cancel()
        startJob = null
        eventsJob?.cancel()
        eventsJob = null
        room?.disconnect()
        room?.release()
        room = null
    }
}
