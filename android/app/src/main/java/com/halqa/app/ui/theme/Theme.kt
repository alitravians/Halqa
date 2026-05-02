package com.halqa.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val HalqaColorScheme = darkColorScheme(
    primary = HalqaColors.Brand,
    onPrimary = HalqaColors.Text,
    primaryContainer = HalqaColors.BrandDark,
    onPrimaryContainer = HalqaColors.Text,
    secondary = HalqaColors.Pink,
    onSecondary = HalqaColors.Text,
    secondaryContainer = HalqaColors.Pink,
    onSecondaryContainer = HalqaColors.Text,
    tertiary = HalqaColors.Gold,
    onTertiary = HalqaColors.Bg,
    tertiaryContainer = HalqaColors.GoldDark,
    onTertiaryContainer = HalqaColors.Text,
    background = HalqaColors.Bg,
    onBackground = HalqaColors.Text,
    surface = HalqaColors.BgElevated,
    onSurface = HalqaColors.Text,
    surfaceVariant = HalqaColors.BgSurface,
    onSurfaceVariant = HalqaColors.TextMuted,
    error = HalqaColors.Danger,
    onError = HalqaColors.Text,
    outline = HalqaColors.BorderStrong,
    outlineVariant = HalqaColors.Border,
)

@Composable
fun HalqaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HalqaColorScheme,
        typography = HalqaTypography,
        content = content,
    )
}
