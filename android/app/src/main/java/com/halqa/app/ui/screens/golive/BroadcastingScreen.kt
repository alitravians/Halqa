package com.halqa.app.ui.screens.golive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.halqa.app.data.StreamsRepository
import com.halqa.app.livekit.BroadcastSession
import com.halqa.app.livekit.BroadcastState
import com.halqa.app.livekit.HalqaVideoRenderer
import com.halqa.app.livekit.LiveBroadcastService
import com.halqa.app.ui.theme.HalqaColors

/**
 * Live publisher view: local camera preview + viewer count + mute/end controls.
 *
 * The session is owned by [BroadcastSession] (singleton) so this screen can
 * be torn down and reattached without dropping the broadcast — handy for
 * accidental backgrounding, rotation, or user navigating elsewhere briefly.
 */
@Composable
fun BroadcastingScreen(navController: NavController) {
    val context = LocalContext.current
    val state by BroadcastSession.state.collectAsState()
    // Track current streamId so we only listen on the live stream's
    // doc; observeStream("") emits null and is safe.
    val streamId = (state as? BroadcastState.Live)?.streamId
        ?: (state as? BroadcastState.Connecting)?.streamId
        ?: ""
    // Lifecycle-aware: when the broadcasting screen is stopped (e.g. user
    // briefly switches to another app via the recent-tasks switcher) the
    // Firestore snapshot listener is detached. The broadcast itself keeps
    // running in [LiveBroadcastService] / [BroadcastSession] independently;
    // this listener only feeds the diamonds-raised overlay, so detaching
    // it for a backgrounded screen is the correct trade-off (saves
    // Firestore reads + battery, re-attaches on resume).
    val streamSnapshot by StreamsRepository.observe(streamId)
        .collectAsStateWithLifecycle(initialValue = null)

    LaunchedEffect(state) {
        if (state is BroadcastState.Idle) {
            // Session ended (either by user or remote disconnect) — exit.
            navController.popBackStack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF1F1144), Color(0xFF0A0A1A)))),
    ) {
        when (val s = state) {
            is BroadcastState.Live -> {
                HalqaVideoRenderer(
                    track = s.localVideo,
                    room = BroadcastSession.activeRoom,
                    modifier = Modifier.fillMaxSize(),
                    mirror = true,
                )
                LiveOverlay(
                    viewerCount = s.viewerCount,
                    diamondsRaised = streamSnapshot?.giftTotal ?: 0L,
                    cameraEnabled = s.cameraEnabled,
                    micEnabled = s.micEnabled,
                    onToggleCam = { BroadcastSession.toggleCamera() },
                    onToggleMic = { BroadcastSession.toggleMic() },
                    onEnd = {
                        LiveBroadcastService.stop(context.applicationContext)
                        BroadcastSession.stop()
                    },
                )
            }
            is BroadcastState.Connecting -> CenterMessage("جارٍ الاتصال بالبث…")
            is BroadcastState.Stopping -> CenterMessage("جاري إنهاء البث…")
            is BroadcastState.Failed -> CenterMessage("تعذّر بدء البث: ${s.message}")
            BroadcastState.Idle -> CenterMessage("…")
        }
    }
}

@Composable
private fun CenterMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = HalqaColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LiveOverlay(
    viewerCount: Int,
    diamondsRaised: Long,
    cameraEnabled: Boolean,
    micEnabled: Boolean,
    onToggleCam: () -> Unit,
    onToggleMic: () -> Unit,
    onEnd: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveBadge()
            Spacer(Modifier.size(10.dp))
            ViewerChip(viewerCount)
            if (diamondsRaised > 0L) {
                Spacer(Modifier.size(8.dp))
                DiamondsChip(diamondsRaised)
            }
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControlChip(
                icon = if (cameraEnabled) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                label = if (cameraEnabled) "الكاميرا" else "بدون كاميرا",
                active = cameraEnabled,
                onClick = onToggleCam,
            )
            ControlChip(
                icon = if (micEnabled) Icons.Filled.Mic else Icons.Filled.MicOff,
                label = if (micEnabled) "المايك" else "كتم",
                active = micEnabled,
                onClick = onToggleMic,
            )
            Spacer(Modifier.weight(1f))
            EndStreamChip(onEnd)
        }
    }
}

@Composable
private fun LiveBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFE53935))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text("🔴 LIVE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ViewerChip(count: Int) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0x66000000))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Visibility, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        Spacer(Modifier.size(4.dp))
        Text("$count", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DiamondsChip(amount: Long) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xCC000000))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("\uD83D\uDC8E", fontSize = 12.sp)
        Spacer(Modifier.size(4.dp))
        Text(
            "$amount",
            color = HalqaColors.Gold,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ControlChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (active) Color(0x66000000) else Color(0xCC000000))
            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = Color.White, fontSize = 10.sp)
    }
}

@Composable
private fun EndStreamChip(onEnd: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFE53935), Color(0xFFB71C1C))))
            .clickable(onClick = onEnd)
            .padding(horizontal = 22.dp, vertical = 12.dp),
    ) {
        Text("إنهاء البث", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
