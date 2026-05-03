package com.halqa.app.ui.screens.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.halqa.app.data.StreamPreview
import com.halqa.app.data.StreamsRepository
import com.halqa.app.ui.components.BadgeRow
import com.halqa.app.ui.components.HalqaLogo
import com.halqa.app.ui.components.avatarInitial
import com.halqa.app.ui.navigation.Routes
import com.halqa.app.ui.theme.HalqaColors

private val categories = listOf(
    "الكل",
    "ترفيه",
    "موسيقى",
    "ألعاب",
    "دردشة",
    "تعليم",
    "طبخ",
    "ثقافة",
    "صباح",
    "تقنية",
    "عائلي",
    "PK",
)

@Composable
fun FeedScreen(navController: NavController) {
    var category by remember { mutableStateOf("الكل") }
    val liveStreams by StreamsRepository.liveStreams()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val previews = remember(liveStreams) {
        liveStreams.mapIndexed { idx, s ->
            // The stream title is the closest user-typed string we have for
            // the host's display name on the feed grid. It can legitimately
            // be empty for legacy documents (created before the backend
            // started defaulting `title` to "بث جديد"), or for any stream
            // whose title starts with a space (`substringBefore(' ')` then
            // collapses to ""). Fall back to the start of the owner uid so
            // the row always renders something deterministic instead of
            // throwing inside the avatar.
            val derivedHost = s.title.substringBefore(" ").take(20).trim()
            val safeHost = derivedHost.ifEmpty { s.ownerUid.take(8) }.ifEmpty { "مضيف" }
            StreamPreview(
                id = s.streamId,
                title = s.title.ifBlank { "بث جديد" },
                hostName = safeHost,
                hostHandle = s.ownerUid.take(8),
                avatarSeed = (s.ownerUid.hashCode() and 0x7fffffff) % 360,
                viewers = s.viewerCount,
                category = "الكل",
                tag = "🔴 مباشر",
                coverHue = (idx * 37) % 360,
            )
        }
    }
    val visible = if (category == "الكل") previews
        else if (category == "PK") previews.filter { it.title.contains("PK") }
        else previews.filter { it.category == category || it.title.contains(category) }

    Column(modifier = Modifier.fillMaxSize().background(HalqaColors.Bg)) {
        FeedHeader(navController)
        CategoryRow(selected = category, onSelect = { category = it })
        Spacer(Modifier.height(8.dp))

        if (visible.isEmpty()) {
            EmptyFeedState()
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(visible, key = { it.id }) { s ->
                    StreamCard(stream = s, onClick = {
                        navController.navigate(Routes.liveWatch(s.id))
                    })
                }
            }
        }
    }
}

@Composable
private fun EmptyFeedState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("📡", fontSize = 56.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            "لا توجد حلقات مباشرة الآن",
            color = HalqaColors.Text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "كن أول من يبدأ — اضغط زر البث المباشر في الشريط السفلي.",
            color = HalqaColors.TextMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun FeedHeader(navController: NavController) {
    // Search + Notifications top-bar icons were previously rendered here
    // with empty `onClick = { /* search */ }` and `{ /* notifications */ }`
    // bodies. There is no SearchScreen, no NotificationsScreen, no
    // `Routes.Search`, no `Routes.Notifications`, no FCM consumer wired
    // anywhere in the app. A user tapping either icon got the ripple
    // effect and absolutely nothing else — a textbook UX-lying control,
    // same class as PR #65 (dead category chip) and PR #69 (fake topup
    // button). Removed both. When the search and notifications features
    // ship, restore the icons in this row, add the routes to
    // `ui/navigation/Routes.kt`, and wire the onClicks to
    // `navController.navigate(...)`. Until then the header is a clean
    // brand bar with just the logo.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HalqaLogo(size = 32, textSize = 18)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun CategoryRow(selected: String, onSelect: (String) -> Unit) {
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(categories) { c ->
            val isSelected = c == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isSelected) Brush.linearGradient(listOf(HalqaColors.Brand, HalqaColors.Pink))
                        else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.06f))),
                    )
                    .border(
                        if (isSelected) 0.dp else 1.dp,
                        if (isSelected) Color.Transparent else HalqaColors.Border,
                        RoundedCornerShape(20.dp),
                    )
                    .clickable { onSelect(c) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    c,
                    color = if (isSelected) Color.White else HalqaColors.TextMuted,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
fun StreamCard(stream: StreamPreview, onClick: () -> Unit) {
    val gradient = streamGradient(stream)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
            .clip(RoundedCornerShape(20.dp))
            .background(gradient)
            .clickable { onClick() },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                    ),
                ),
        )

        Box(modifier = Modifier
            .align(Alignment.TopStart)
            .padding(8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFEF4444))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                )
                Text("مباشر", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (stream.isPk) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(listOf(HalqaColors.Gold, HalqaColors.GoldLight)))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text("⚔️ PK", color = Color(0xFF111111), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(10.dp),
        ) {
            Text(
                stream.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(HalqaColors.Brand, HalqaColors.Pink))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        avatarInitial(stream.hostName),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    stream.hostName,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                if (stream.hostBadges.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    BadgeRow(badges = stream.hostBadges, size = 12.dp, limit = 2, spacing = 2.dp)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "👥 ${formatViewers(stream.viewers)}",
                    color = Color.White,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

private fun formatViewers(n: Int): String =
    when {
        n >= 1_000_000 -> String.format(java.util.Locale.US, "%.1f", n / 1_000_000.0).trimEnd('0').trimEnd('.') + "م"
        n >= 1_000 -> String.format(java.util.Locale.US, "%.1f", n / 1_000.0).trimEnd('0').trimEnd('.') + "ك"
        else -> "$n"
    }

private fun streamGradient(stream: StreamPreview): Brush {
    val baseHue = stream.coverHue
    fun col(h: Int, s: Float, l: Float): Color {
        val hue = (h % 360) / 360f
        val (r, g, b) = hslToRgb(hue, s, l)
        return Color(r, g, b)
    }
    return Brush.linearGradient(
        listOf(
            col(baseHue, 0.6f, 0.45f),
            col((baseHue + 35) % 360, 0.7f, 0.35f),
            col((baseHue + 70) % 360, 0.55f, 0.25f),
        ),
    )
}

private fun hslToRgb(h: Float, s: Float, l: Float): Triple<Float, Float, Float> {
    val c = (1 - kotlin.math.abs(2 * l - 1)) * s
    val x = c * (1 - kotlin.math.abs((h * 6) % 2 - 1))
    val m = l - c / 2
    val (r1, g1, b1) = when {
        h < 1f / 6 -> Triple(c, x, 0f)
        h < 2f / 6 -> Triple(x, c, 0f)
        h < 3f / 6 -> Triple(0f, c, x)
        h < 4f / 6 -> Triple(0f, x, c)
        h < 5f / 6 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Triple(r1 + m, g1 + m, b1 + m)
}
