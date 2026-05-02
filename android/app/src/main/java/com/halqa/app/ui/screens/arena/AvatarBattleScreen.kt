package com.halqa.app.ui.screens.arena

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.halqa.app.data.MockData
import com.halqa.app.ui.theme.HalqaColors
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AvatarBattleScreen(matchId: String, navController: NavController) {
    var leftHp by remember { mutableFloatStateOf(0.75f) }
    var rightHp by remember { mutableFloatStateOf(0.62f) }
    var timeLeft by remember { mutableIntStateOf(284) }

    val infiniteTransition = rememberInfiniteTransition(label = "battle_anim")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "pulse",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0D001A), Color(0xFF1A0533)))),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            BattleHeader(timeLeft = timeLeft, onClose = { navController.popBackStack() })
            Spacer(Modifier.height(12.dp))
            BattleArena(pulse = pulse, leftHp = leftHp, rightHp = rightHp)
            Spacer(Modifier.height(16.dp))
            ScoreBar(leftScore = 3420, rightScore = 2810)
            Spacer(Modifier.height(12.dp))
            AttackButtons(
                onAttack = {
                    leftHp = (leftHp + 0.05f).coerceAtMost(1f)
                    rightHp = (rightHp - 0.03f).coerceAtLeast(0f)
                },
            )
            Spacer(Modifier.weight(1f))
            MiniGameBanner()
            Spacer(Modifier.height(12.dp))
            GiftAttackRow()
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun BattleHeader(timeLeft: Int, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFEF4444))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text("⚔️ PK LIVE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Text("⏱ ${timeLeft / 60}:${"%02d".format(timeLeft % 60)}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(HalqaColors.Gold.copy(alpha = 0.2f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text("👥 4.2K مشاهد", color = HalqaColors.Gold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(6.dp))
        IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
        }
    }
}

@Composable
private fun BattleArena(pulse: Float, leftHp: Float, rightHp: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.radialGradient(listOf(Color(0xFF2A1056), Color(0xFF0D001A))))
            .border(1.dp, HalqaColors.Brand.copy(alpha = 0.3f), RoundedCornerShape(24.dp)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            for (i in 0..5) {
                val angle = Math.toRadians((pulse.toDouble() + i * 60))
                val x = cx + cos(angle).toFloat() * 120f
                val y = cy + sin(angle).toFloat() * 80f
                drawCircle(
                    color = HalqaColors.Brand.copy(alpha = 0.1f),
                    radius = 30f,
                    center = Offset(x, y),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarFighter(
                name = "عبدالله",
                hp = leftHp,
                color1 = HalqaColors.Brand,
                color2 = Color(0xFF4F46E5),
                level = 14,
                emoji = "⚔️",
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("VS", color = Color.White.copy(alpha = 0.4f), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(4.dp))
                Text("R3", color = HalqaColors.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            AvatarFighter(
                name = "نورة",
                hp = rightHp,
                color1 = HalqaColors.Pink,
                color2 = Color(0xFFBE185D),
                level = 11,
                emoji = "🛡️",
            )
        }
    }
}

@Composable
private fun AvatarFighter(name: String, hp: Float, color1: Color, color2: Color, level: Int, emoji: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("LV $level", color = HalqaColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(color1, color2)))
                .border(3.dp, color1, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(name.first().toString(), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Text(name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.1f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(hp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.linearGradient(
                            if (hp > 0.5f) listOf(Color(0xFF10B981), Color(0xFF34D399))
                            else if (hp > 0.25f) listOf(HalqaColors.Gold, Color(0xFFFCD34D))
                            else listOf(Color(0xFFEF4444), Color(0xFFF87171)),
                        ),
                    ),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(emoji, fontSize = 18.sp)
    }
}

@Composable
private fun ScoreBar(leftScore: Int, rightScore: Int) {
    val total = leftScore + rightScore
    val leftRatio = leftScore.toFloat() / total

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$leftScore نقطة", color = HalqaColors.BrandLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("$rightScore نقطة", color = HalqaColors.Pink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color.White.copy(alpha = 0.1f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(leftRatio)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Brush.linearGradient(listOf(HalqaColors.Brand, HalqaColors.BrandLight))),
            )
        }
    }
}

@Composable
private fun AttackButtons(onAttack: () -> Unit) {
    val attacks = listOf("🌹 وردة" to 1, "🔥 نار" to 10, "👑 تاج" to 99, "🦁 أسد" to 499, "💎 ماسة" to 1999)
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(attacks) { (label, price) ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(HalqaColors.BgElevated)
                    .border(1.dp, HalqaColors.Border, RoundedCornerShape(14.dp))
                    .clickable { onAttack() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, fontSize = 14.sp, color = HalqaColors.Text, fontWeight = FontWeight.SemiBold)
                    Text("$price 💰", fontSize = 10.sp, color = HalqaColors.Gold)
                }
            }
        }
    }
}

@Composable
private fun MiniGameBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF059669), Color(0xFF10B981))))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🎮", fontSize = 24.sp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Mini-Game: تحدي النقر!", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("انقر بأسرع ما يمكن خلال 10 ثواني — ساعد مذيعك مجاناً!", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun GiftAttackRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(HalqaColors.BgElevated)
                .border(1.dp, HalqaColors.Border, RoundedCornerShape(14.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("💰 12,480 كوين", color = HalqaColors.Gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(HalqaColors.Brand, HalqaColors.Pink)))
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("⚡ هجوم خاص", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}
