package com.halqa.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halqa.app.ui.theme.HalqaColors
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HalqaLogo(
    modifier: Modifier = Modifier,
    size: Int = 40,
    showText: Boolean = true,
    textSize: Int = 22,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Canvas(modifier = Modifier.size(size.dp)) {
            drawHalqaLogo(this)
        }
        if (showText) {
            Spacer(Modifier.width(0.dp))
            Text(
                "حلقة",
                color = HalqaColors.Text,
                fontWeight = FontWeight.ExtraBold,
                fontSize = textSize.sp,
            )
        }
    }
}

private fun drawHalqaLogo(scope: DrawScope) {
    val w = scope.size.width
    val h = scope.size.height
    val cx = w / 2f
    val cy = h / 2f
    val outerR = w * 0.42f
    val dotR = w * 0.085f

    val gradient = listOf(
        Color(0xFF7C3AED),
        Color(0xFF9333EA),
        Color(0xFFC026D3),
        Color(0xFFEC4899),
        Color(0xFFA855F7),
        Color(0xFF7C3AED),
    )

    for (i in 0 until 6) {
        val angle = i * (Math.PI / 3.0) - Math.PI / 2.0
        val x = cx + cos(angle).toFloat() * outerR
        val y = cy + sin(angle).toFloat() * outerR
        scope.drawCircle(
            color = gradient[i],
            radius = dotR,
            center = Offset(x, y),
        )
    }

    scope.drawCircle(
        color = Color(0xFFF59E0B),
        radius = w * 0.12f,
        center = Offset(cx, cy),
    )
    scope.drawCircle(
        color = Color(0xFFFBBF24),
        radius = w * 0.08f,
        center = Offset(cx, cy),
    )
}
