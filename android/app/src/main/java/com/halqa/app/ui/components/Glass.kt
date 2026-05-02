package com.halqa.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.halqa.app.ui.theme.HalqaColors

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    contentPadding: Dp = 16.dp,
    contentAlpha: Float = 0.7f,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(HalqaColors.BgElevated.copy(alpha = contentAlpha))
            .border(1.dp, HalqaColors.Border, RoundedCornerShape(cornerRadius))
            .padding(contentPadding),
    ) {
        content()
    }
}

@Composable
fun BlurredOverlay(
    modifier: Modifier = Modifier,
    color: Color = Color.Black.copy(alpha = 0.4f),
) {
    Box(modifier = modifier.background(color))
}
