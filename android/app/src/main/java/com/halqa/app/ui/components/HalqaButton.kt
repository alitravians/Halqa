package com.halqa.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.halqa.app.ui.theme.HalqaColors
import com.halqa.app.ui.theme.HalqaGradients

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fillMaxWidth: Boolean = true,
) {
    Box(
        modifier = modifier
            .let { if (fillMaxWidth) it.fillMaxWidth() else it }
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) HalqaGradients.Brand else Brush.linearGradient(listOf(Color.Gray, Color.DarkGray))),
        contentAlignment = Alignment.Center,
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                contentColor = Color.White,
                disabledContentColor = Color.White.copy(alpha = 0.5f),
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .let { if (fillMaxWidth) it.fillMaxWidth() else it }
                .heightIn(min = 56.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(text, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun GoldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fillMaxWidth: Boolean = true,
) {
    Box(
        modifier = modifier
            .let { if (fillMaxWidth) it.fillMaxWidth() else it }
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) HalqaGradients.Gold else Brush.linearGradient(listOf(Color.Gray, Color.DarkGray))),
        contentAlignment = Alignment.Center,
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                contentColor = Color(0xFF111111),
                disabledContentColor = Color(0xFF111111).copy(alpha = 0.5f),
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .let { if (fillMaxWidth) it.fillMaxWidth() else it }
                .heightIn(min = 56.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(text, fontWeight = FontWeight.Bold, color = Color(0xFF111111))
        }
    }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fillMaxWidth: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Color.White,
            containerColor = Color.White.copy(alpha = 0.05f),
        ),
        shape = RoundedCornerShape(16.dp),
        border = ButtonDefaults.outlinedButtonBorder(enabled).copy(width = 1.dp),
        modifier = modifier
            .let { if (fillMaxWidth) it.fillMaxWidth() else it }
            .heightIn(min = 56.dp)
            .border(1.dp, HalqaColors.BorderStrong, RoundedCornerShape(16.dp)),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text(text, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun TextLinkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = HalqaColors.BrandLight),
        modifier = modifier,
    ) {
        Text(text, fontWeight = FontWeight.Medium)
    }
}
