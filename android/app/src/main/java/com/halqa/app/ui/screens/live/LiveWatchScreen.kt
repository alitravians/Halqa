package com.halqa.app.ui.screens.live

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.halqa.app.data.ChatMsg
import com.halqa.app.data.ChatRepository
import com.halqa.app.data.GiftRepository
import com.halqa.app.data.MockData
import com.halqa.app.data.StreamsRepository
import com.halqa.app.data.remote.GiftDto
import com.halqa.app.livekit.HalqaVideoRenderer
import kotlinx.coroutines.launch
import com.halqa.app.livekit.WatchSession
import com.halqa.app.livekit.WatchState
import com.halqa.app.ui.components.GlassCard
import com.halqa.app.ui.components.avatarInitial
import com.halqa.app.ui.navigation.Routes
import com.halqa.app.ui.theme.HalqaColors

@Composable
private fun StreamUnavailable(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HalqaColors.Bg)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("📡", fontSize = 56.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            "البث غير متاح",
            color = HalqaColors.Text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "ربما انتهى البث أو لم يبدأ بعد. عُد للقائمة وجرّب لاحقاً.",
            color = HalqaColors.TextMuted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(24.dp))
        androidx.compose.material3.Button(onClick = onBack) {
            Text("رجوع")
        }
    }
}

@Composable
fun LiveWatchScreen(streamId: String, navController: NavController) {
    val context = LocalContext.current
    val state by WatchSession.state.collectAsState()
    val messages by ChatRepository.observe(streamId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var showGifts by remember { mutableStateOf(false) }
    val giftCatalog by GiftRepository.catalog.collectAsState()
    val streamSnapshot by StreamsRepository.observe(streamId)
        .collectAsState(initial = null)
    var giftError by remember { mutableStateOf<String?>(null) }
    val giftScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        // Catalogue is server-authoritative; refresh on entry so a price
        // change rolls out without an app release. The repository keeps
        // a process-wide cache so this is cheap on a re-open.
        runCatching { GiftRepository.ensureCatalog() }
    }

    DisposableEffect(streamId) {
        WatchSession.start(context.applicationContext, streamId, ownerUid = null)
        onDispose { WatchSession.stop() }
    }

    when (val s = state) {
        is WatchState.Failed -> {
            StreamUnavailable(onBack = { navController.popBackStack() })
            return
        }
        is WatchState.Ended -> {
            StreamUnavailable(onBack = { navController.popBackStack() })
            return
        }
        else -> Unit
    }

    val watching = state as? WatchState.Watching
    // Avatar initial is rendered from this string; defend against the
    // worst case (deep link with an empty streamId, owner uid blanked
    // server-side) so we never feed an empty string into the renderer.
    val hostHandle = watching?.ownerUid?.take(8)?.takeIf { it.isNotBlank() }
        ?: streamId.take(8).takeIf { it.isNotBlank() }
        ?: "مضيف"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF1F1144), Color(0xFF0A0A1A)))),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            StreamHeader(
                hostName = hostHandle,
                hostBadges = emptyList(),
                viewers = watching?.viewerCount ?: 0,
                category = "🔴 مباشر",
                onClose = { navController.popBackStack() },
                isPk = false,
                onPk = { navController.navigate(Routes.avatarBattle("demo")) },
            )

            Box(modifier = Modifier.weight(1f)) {
                if (watching != null) {
                    HalqaVideoRenderer(
                        track = watching.remoteVideo,
                        room = WatchSession.activeRoom,
                    )
                } else {
                    StreamConnecting()
                }
            }

            ChatOverlay(messages = messages)
            BottomBar(
                onGift = { showGifts = !showGifts },
                onLike = { /* heart animation */ },
            )
        }

        AnimatedVisibility(
            visible = showGifts,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            GiftPanel(
                gifts = giftCatalog,
                error = giftError,
                onDismiss = {
                    showGifts = false
                    giftError = null
                },
                onSend = { gift ->
                    giftError = null
                    giftScope.launch {
                        val result = runCatching {
                            GiftRepository.send(
                                streamId = streamId,
                                giftId = gift.id,
                                receiverUid = streamSnapshot?.ownerUid,
                            )
                        }
                        result.onSuccess {
                            // Authoritative balance update lands via
                            // WalletRepository's Firestore listener.
                            // Stream giftTotal lands via
                            // StreamsRepository.observe (M1 SSoT).
                            showGifts = false
                        }.onFailure { t ->
                            giftError = t.message ?: "تعذّر إرسال الهدية"
                        }
                    }
                },
            )
        }

        // Diamonds-raised badge mirrors what the broadcaster sees —
        // both surfaces read the same StreamSnapshot.giftTotal field,
        // so the count never drifts between viewer and host.
        val diamondsRaised = streamSnapshot?.giftTotal ?: 0L
        if (diamondsRaised > 0L) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    "💎 $diamondsRaised",
                    color = HalqaColors.Gold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun StreamConnecting() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⏳", fontSize = 44.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "جارٍ الاتصال بالبث…",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun StreamHeader(
    hostName: String,
    hostBadges: List<com.halqa.app.domain.BadgeType>,
    viewers: Int,
    category: String,
    onClose: () -> Unit,
    isPk: Boolean,
    onPk: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(HalqaColors.Brand, HalqaColors.Pink))),
            contentAlignment = Alignment.Center,
        ) {
            Text(avatarInitial(hostName), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(hostName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (hostBadges.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    com.halqa.app.ui.components.BadgeRow(
                        badges = hostBadges,
                        size = 14.dp,
                        limit = 3,
                        spacing = 3.dp,
                    )
                }
            }
            Text("$category • ${viewers} مشاهد", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
        }

        if (isPk) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(HalqaColors.Gold, HalqaColors.GoldLight)))
                    .clickable { onPk() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text("⚔️ PK", color = Color(0xFF111111), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
        }

        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
        }
    }
}

@Composable
private fun StreamContent(hostName: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(HalqaColors.Brand, HalqaColors.Pink))),
                contentAlignment = Alignment.Center,
            ) {
                Text(avatarInitial(hostName), fontSize = 44.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Text("🔴 بث مباشر", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("(LiveKit integration preview)", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun ChatOverlay(messages: List<ChatMsg>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp)
            .padding(horizontal = 12.dp),
        reverseLayout = true,
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(messages.reversed()) { msg ->
            ChatBubble(msg)
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMsg) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val nameColor = when {
            msg.isMod -> HalqaColors.Gold
            msg.isVip -> HalqaColors.Pink
            else -> HalqaColors.BrandLight
        }
        val badges = buildString {
            if (msg.isMod) append("🛡️")
            if (msg.isVip) append("💎")
        }
        if (badges.isNotEmpty()) {
            Text(badges, fontSize = 12.sp)
            Spacer(Modifier.width(4.dp))
        }
        Text(msg.user, color = nameColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(6.dp))
        Text(msg.message, color = Color.White, fontSize = 13.sp)
    }
}

@Composable
private fun BottomBar(onGift: () -> Unit, onLike: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White.copy(alpha = 0.1f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(22.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text("اكتب رسالة...", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onGift) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(HalqaColors.Gold, HalqaColors.GoldLight))),
                contentAlignment = Alignment.Center,
            ) {
                Text("🎁", fontSize = 20.sp)
            }
        }
        IconButton(onClick = onLike) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(HalqaColors.Pink.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Favorite, contentDescription = "Like", tint = HalqaColors.Pink)
            }
        }
        IconButton(onClick = { }) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Share, contentDescription = "Share", tint = Color.White)
            }
        }
    }
}

@Composable
private fun GiftPanel(
    gifts: List<GiftDto>,
    error: String?,
    onDismiss: () -> Unit,
    onSend: (GiftDto) -> Unit,
) {
    var selected by remember { mutableStateOf<GiftDto?>(null) }
    var sending by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(Color(0xFF13132B))
            .padding(16.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("إرسال هدية", color = HalqaColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = HalqaColors.TextMuted)
                }
            }

            if (gifts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "جارٍ تحميل الهدايا…",
                        color = HalqaColors.TextMuted,
                        fontSize = 13.sp,
                    )
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(gifts) { gift ->
                        GiftItem(
                            gift = gift,
                            isSelected = selected?.id == gift.id,
                            onClick = { selected = gift },
                        )
                    }
                }
            }

            if (error != null) {
                Text(
                    error,
                    color = HalqaColors.Pink,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    selected?.let { "${it.priceCoins} كوين" } ?: "اختر هدية للإرسال",
                    color = HalqaColors.Gold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                val canSend = selected != null && !sending
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (canSend) Brush.linearGradient(listOf(HalqaColors.Brand, HalqaColors.Pink))
                            else Brush.linearGradient(listOf(Color.Gray, Color.DarkGray)),
                        )
                        .clickable(enabled = canSend) {
                            selected?.let {
                                sending = true
                                onSend(it)
                            }
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text(
                        when {
                            sending -> "جارٍ الإرسال…"
                            selected != null -> "إرسال ${selected!!.emoji}"
                            else -> "اختر هدية"
                        },
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun GiftItem(gift: GiftDto, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) HalqaColors.Brand.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.04f))
            .border(
                if (isSelected) 1.5.dp else 1.dp,
                if (isSelected) HalqaColors.BrandLight else HalqaColors.Border,
                RoundedCornerShape(14.dp),
            )
            .clickable { onClick() }
            .padding(12.dp)
            .width(72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(gift.emoji, fontSize = 30.sp)
        Spacer(Modifier.height(4.dp))
        Text(gift.name, color = HalqaColors.Text, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Text("${gift.priceCoins}", color = HalqaColors.Gold, fontSize = 10.sp)
    }
}
