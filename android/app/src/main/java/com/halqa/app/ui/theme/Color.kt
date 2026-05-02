package com.halqa.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object HalqaColors {
    val Bg = Color(0xFF0A0A1A)
    val BgElevated = Color(0xFF13132B)
    val BgSurface = Color(0xFF1C1C3A)
    val BgOverlay = Color(0xCC0A0A1A)

    val Text = Color(0xFFF5F5F7)
    val TextMuted = Color(0xFF9CA3AF)
    val TextDim = Color(0xFF6B7280)

    val Brand = Color(0xFF7C3AED)
    val BrandLight = Color(0xFFA855F7)
    val BrandDark = Color(0xFF6B21A8)

    val Pink = Color(0xFFEC4899)
    val PinkLight = Color(0xFFF472B6)

    val Gold = Color(0xFFF59E0B)
    val GoldLight = Color(0xFFFBBF24)
    val GoldDark = Color(0xFFD97706)

    val Success = Color(0xFF10B981)
    val Danger = Color(0xFFEF4444)
    val Warning = Color(0xFFF59E0B)
    val Info = Color(0xFF3B82F6)

    val Border = Color(0x14FFFFFF)
    val BorderStrong = Color(0x29FFFFFF)
    val Divider = Color(0x0AFFFFFF)
}

object HalqaGradients {
    val Brand = Brush.linearGradient(
        colors = listOf(HalqaColors.Brand, HalqaColors.Pink),
    )
    val BrandRadial = Brush.radialGradient(
        colors = listOf(HalqaColors.Brand, HalqaColors.Pink),
    )
    val Gold = Brush.linearGradient(
        colors = listOf(HalqaColors.Gold, HalqaColors.GoldLight),
    )
    val Surface = Brush.verticalGradient(
        colors = listOf(HalqaColors.BgElevated, HalqaColors.Bg),
    )
    val LiveOverlay = Brush.verticalGradient(
        colors = listOf(Color.Transparent, Color(0xCC000000)),
    )
}
