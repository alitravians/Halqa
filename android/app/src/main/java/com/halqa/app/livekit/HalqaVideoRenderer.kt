package com.halqa.app.livekit

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.track.VideoTrack

/**
 * Compose wrapper around LiveKit's [TextureViewRenderer] that attaches a
 * [VideoTrack] for the lifetime of the composition. Handles re-attaching
 * automatically when [track] changes (e.g. publisher switches camera, or
 * a new RemoteVideoTrack is subscribed).
 *
 * **Critical**: a [TextureViewRenderer] WILL NOT render anything until
 * [Room.initVideoRenderer] (or [TextureViewRenderer.init] with the
 * room's `EglBase.Context`) is called — this is why the local front-camera
 * preview was blank in v0.1.11 ("الكاميرا الأمامية ما تشتغل نهائياً").
 * The renderer's surface stays uninitialised so it has no GL context to
 * draw onto, and the track silently no-ops the [VideoTrack.addRenderer]
 * call. We pass the active [Room] in here and initialise the renderer
 * once, before any track is attached.
 *
 * @param room  the LiveKit [Room] the [track] belongs to. May be null
 *              briefly during teardown / before connect — in that case
 *              we skip rendering until a non-null room arrives. The
 *              renderer is re-initialised whenever [room] identity
 *              changes (e.g. publisher restart on a new room).
 * @param mirror set to true for the local front-camera preview so the user
 *               sees a mirrored selfie view (matches platform conventions).
 */
@Composable
fun HalqaVideoRenderer(
    track: VideoTrack?,
    room: Room?,
    modifier: Modifier = Modifier.fillMaxSize(),
    mirror: Boolean = false,
) {
    val context = LocalContext.current

    // Re-create the renderer whenever the Room identity changes so it
    // always shares EGL state with the room it'll render frames from.
    val renderer = remember(room) {
        TextureViewRenderer(context).also { r ->
            // initVideoRenderer is a no-op if room is null (we just won't
            // render anything until a real Room arrives).
            room?.initVideoRenderer(r)
        }
    }

    SideEffect {
        renderer.setMirror(mirror)
    }

    // Bind the current VideoTrack to the renderer. When the track or the
    // renderer changes, detach the old binding cleanly first.
    DisposableEffect(track, renderer) {
        val current = track
        current?.addRenderer(renderer)
        onDispose {
            current?.removeRenderer(renderer)
        }
    }

    // Release EGL/SurfaceTexture resources when the composable leaves
    // composition. Without this, every recomposition leaks GL resources
    // until GC. This DisposableEffect is keyed on `renderer` so that when
    // the renderer is recreated (because `room` changed), the previous
    // one is released exactly once.
    DisposableEffect(renderer) {
        onDispose {
            renderer.release()
        }
    }

    AndroidView(
        factory = { renderer },
        modifier = modifier,
    )
}
