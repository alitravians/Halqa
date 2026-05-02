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
import io.livekit.android.room.track.VideoTrack

/**
 * Compose wrapper around LiveKit's [TextureViewRenderer] that attaches a
 * [VideoTrack] for the lifetime of the composition. Handles re-attaching
 * automatically when [track] changes (e.g. publisher switches camera, or
 * a new RemoteVideoTrack is subscribed).
 *
 * @param mirror set to true for the local front-camera preview so the user
 *               sees a mirrored selfie view (matches platform conventions).
 */
@Composable
fun HalqaVideoRenderer(
    track: VideoTrack?,
    modifier: Modifier = Modifier.fillMaxSize(),
    mirror: Boolean = false,
) {
    val context = LocalContext.current
    val renderer = remember {
        TextureViewRenderer(context).apply {
            // init() is called lazily by the track when first added.
        }
    }

    SideEffect {
        renderer.setMirror(mirror)
    }

    DisposableEffect(track) {
        val current = track
        current?.addRenderer(renderer)
        onDispose {
            current?.removeRenderer(renderer)
        }
    }

    AndroidView(
        factory = { renderer },
        modifier = modifier,
    )
}
