package com.halqa.app.livekit

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.halqa.app.data.StreamsRepository
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
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

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
    private var streamObserveJob: Job? = null

    /**
     * Generation counter incremented on every [start] / [stop] call.
     * The connect coroutine snapshots this at launch and re-checks it
     * at every suspension point — if the snapshot no longer matches
     * the current generation, the request has been superseded
     * (either by another `start` for a different stream, or by a
     * `stop` while we were still connecting) and the coroutine
     * disposes any room it created without ever flipping `_state` or
     * publishing camera / mic. This closes the race where pressing
     * "end" during the publisher token RTT briefly turned the camera
     * on and flickered Live → Idle.
     */
    private val sessionGen = AtomicInteger(0)

    /**
     * The active LiveKit [Room], if any. Exposed read-only so the
     * Compose video renderer can call [Room.initVideoRenderer] on its
     * `TextureViewRenderer` — without that init step the local preview
     * stays blank (root cause of "front camera doesn't work" in
     * v0.1.11). Returns null when the broadcaster is idle.
     */
    val activeRoom: Room? get() = room

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

        // Defence-in-depth #3 (per Khalid's review): bail out before we
        // burn a `/api/livekit/token` round-trip if the Firebase session
        // is missing. This guards against:
        //   1) User reaching this entry point through a stale UI in a way
        //      that bypasses GoLivePrepScreen's gate.
        //   2) Firebase session being revoked between gate check and start
        //      (token refresh failure, admin revoke, password reset).
        // Without this, the backend would 401 and the user would see the
        // generic "تعذّر الاتصال بالبث" instead of an actionable message.
        if (FirebaseAuth.getInstance().currentUser == null) {
            _state.value = BroadcastState.Failed(
                streamId,
                "يجب تسجيل الدخول قبل بدء البث",
            )
            return
        }

        _state.value = BroadcastState.Connecting(streamId)

        // Bump the generation BEFORE launching the connect coroutine
        // so the snapshot we capture is the freshest one.
        val gen = sessionGen.incrementAndGet()
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
                if (gen != sessionGen.get()) return@launch

                val newRoom = LiveKit.create(
                    appContext = appContext.applicationContext,
                    options = RoomOptions(adaptiveStream = true, dynacast = true),
                )
                if (gen != sessionGen.get()) {
                    // Superseded between LiveKit.create() and connect():
                    // dispose the freshly-allocated Room so we don't
                    // leak an unconnected RTC factory.
                    newRoom.release()
                    return@launch
                }
                room = newRoom

                eventsJob?.cancel()
                eventsJob = scope.launch { newRoom.events.collect(::onRoomEvent) }

                newRoom.connect(
                    url = tokenResp.url,
                    token = tokenResp.token,
                    options = ConnectOptions(autoSubscribe = false),
                )
                if (gen != sessionGen.get()) {
                    // Superseded during the connect handshake. Tear
                    // down the half-connected Room without publishing
                    // camera / mic — critical for privacy: pressing
                    // "end" during the connect RTT must NOT turn the
                    // camera on for even a single frame.
                    newRoom.disconnect()
                    newRoom.release()
                    if (room === newRoom) room = null
                    return@launch
                }

                val local = newRoom.localParticipant
                local.setCameraEnabled(true)
                local.setMicrophoneEnabled(true)
                if (gen != sessionGen.get()) {
                    // Superseded after camera+mic were published.
                    // Disable both before tearing down so the LED /
                    // audio indicators flick off immediately rather
                    // than waiting for the underlying capture to be
                    // released by `disconnect()`.
                    local.setCameraEnabled(false)
                    local.setMicrophoneEnabled(false)
                    newRoom.disconnect()
                    newRoom.release()
                    if (room === newRoom) room = null
                    return@launch
                }

                val cameraTrack = local.getTrackPublication(Track.Source.CAMERA)?.track as? LocalVideoTrack
                _state.value = BroadcastState.Live(
                    streamId = streamId,
                    localVideo = cameraTrack,
                    cameraEnabled = true,
                    micEnabled = true,
                    viewerCount = 0,
                    startedAtMillis = System.currentTimeMillis(),
                )

                // Subscribe to the full server-side stream document. The
                // SSoT pipe drives:
                //   - viewer count (LiveKit webhook → Firestore; see
                //     [StreamsRepository] for why we don't use
                //     `room.remoteParticipants.size`),
                //   - remote `status == "ended"` flips (moderator force-end
                //     from the admin panel, or the publisher's room being
                //     reaped server-side after `room_finished`). When that
                //     happens we tear our local Room down even if the
                //     LiveKit transport-level Disconnected event never
                //     arrives — the user gets the correct "ended" UI
                //     immediately and the dangling RTC connection is
                //     released within one Firestore tick.
                streamObserveJob?.cancel()
                streamObserveJob = scope.launch {
                    StreamsRepository.observe(streamId).collect { snap ->
                        snap ?: return@collect
                        val cur = _state.value as? BroadcastState.Live ?: return@collect
                        if (cur.streamId != streamId) return@collect
                        if (snap.isEnded) {
                            cleanupRoom()
                            _state.value = BroadcastState.Idle
                            return@collect
                        }
                        if (cur.viewerCount != snap.viewerCount) {
                            _state.value = cur.copy(viewerCount = snap.viewerCount)
                        }
                    }
                }
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
        // Snapshot the current state BEFORE we transition to `Stopping`.
        // The previous code transitioned first and then read `state.value`,
        // but `Stopping` is a parameter-less object — the `when` always
        // fell into `else -> null`, so `streamId` was always null and the
        // backend `streams/end` call (and its audit_log entry) never ran.
        // Net effect: the stream stayed `status: "live"` in the feed for
        // the full LiveKit room-empty timeout (~5 min) after the
        // broadcaster pressed end, and Trust & Safety lost the
        // `stream_end` audit event entirely.
        val previous = _state.value
        if (previous is BroadcastState.Idle) return
        val streamId = when (previous) {
            is BroadcastState.Live -> previous.streamId
            is BroadcastState.Connecting -> previous.streamId
            is BroadcastState.Failed -> previous.streamId
            BroadcastState.Idle, BroadcastState.Stopping -> null
        }

        // Bump the generation FIRST. The connect coroutine (if still
        // running) checks `sessionGen.get()` at every suspension point
        // and aborts cleanly when it sees a newer generation, which
        // means it will NOT publish camera / mic and will NOT flip
        // state to Live in the window between the user pressing "end"
        // and this stop() coroutine actually awaiting on the API. This
        // is what closes the privacy race that previously caused the
        // camera to flick on for one frame after the user had already
        // pressed end.
        sessionGen.incrementAndGet()

        _state.value = BroadcastState.Stopping
        val pendingStartJob = startJob
        scope.launch {
            try {
                // Wait for the in-flight connect job (if any) to fully
                // unwind before we touch the Room or fire the end-stream
                // API. cancelAndJoin guarantees the coroutine has run
                // its catch / finally, so any partially-allocated
                // Room has either been released by the supersede checks
                // above or is sitting in `room` ready for cleanupRoom.
                pendingStartJob?.cancelAndJoin()
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
            // Viewer-count updates are driven by Firestore (server-side
            // webhook), not by LiveKit room events — see comment above
            // `viewerCountJob`. We intentionally ignore Participant*
            // events here so a phantom signalling-only ghost can never
            // inflate the count the broadcaster sees.
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
        streamObserveJob?.cancel()
        streamObserveJob = null
        room?.disconnect()
        room?.release()
        room = null
    }
}
