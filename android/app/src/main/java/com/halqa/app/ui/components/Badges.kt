package com.halqa.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halqa.app.domain.BadgeType
import com.halqa.app.domain.topPriority

/**
 * Renders a single circular badge: filled disc tinted with the badge color
 * and the badge icon in white on top. Used inline next to a user's name.
 *
 * Sizes default to a 16 dp disc which sits comfortably next to a 14-22 sp name.
 */
@Composable
fun Badge(
    type: BadgeType,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(type.tint),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = type.icon,
            contentDescription = type.labelAr,
            tint = Color.White,
            modifier = Modifier.size(size * 0.7f),
        )
    }
}

/**
 * Renders the top [limit] badges (sorted by priority) in a horizontal row.
 * Use this inline next to a user's display name on Feed cards, Live Watch
 * header, etc. Pass an empty list to render nothing.
 */
@Composable
fun BadgeRow(
    badges: List<BadgeType>,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    limit: Int = 3,
    spacing: Dp = 4.dp,
) {
    val visible = badges.topPriority(limit)
    if (visible.isEmpty()) return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        visible.forEach { Badge(type = it, size = size) }
    }
}

/**
 * Pill-shaped badge with icon + label for use on the profile page where
 * we have room to show every badge with text.
 */
@Composable
fun BadgePill(
    type: BadgeType,
    modifier: Modifier = Modifier,
) {
    val bg = type.tint.copy(alpha = 0.18f)
    val stroke = type.tint.copy(alpha = 0.45f)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(1.dp, stroke, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = type.icon,
            contentDescription = null,
            tint = type.tint,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = type.labelAr,
            color = type.tint,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
