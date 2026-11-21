package com.game.gametong.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.game.gametong.ui.theme.Inks

/** 고르는 칩(모드·난이도 등). */
@Composable
fun SelectChip(
    label: String,
    selected: Boolean,
    color: Color = Inks.Blue,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) color.copy(alpha = 0.18f) else Inks.Kraft.copy(alpha = 0.55f))
            .border(if (selected) 3.dp else 2.dp, if (selected) color else Inks.Wood.copy(alpha = 0.35f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            color = Inks.Ink,
            textAlign = TextAlign.Center,
            maxLines = 2,
            lineHeight = 15.sp,
        )
    }
}

/** 큰 선택 카드(놀이 고르기). */
@Composable
fun OptionCard(
    title: String,
    subtitle: String,
    emoji: String,
    selected: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (selected) color.copy(alpha = 0.18f) else Inks.Paper)
            .border(if (selected) 4.dp else 2.dp, if (selected) color else Inks.Wood.copy(alpha = 0.45f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 26.sp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Black, color = Inks.Ink)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Inks.SoftInk)
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(color)
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text("선택", color = Inks.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

/** 점수 표시 알약. */
@Composable
fun ScorePill(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.14f))
            .border(2.dp, color.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(vertical = 5.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Inks.SoftInk, maxLines = 1)
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.Black, color = Inks.Ink, maxLines = 1)
    }
}

/** 가로 게이지(피로도·파워). marker 는 빨간 세로선(상한·목표). */
@Composable
fun StatBar(
    value: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Int = 16,
    marker: Float? = null,
    background: Color = Inks.Wood.copy(alpha = 0.15f),
) {
    Canvas(modifier = modifier.fillMaxWidth().height(height.dp)) {
        val radius = CornerRadius(8.dp.toPx())
        drawRoundRect(color = background, cornerRadius = radius)
        val w = size.width * value.coerceIn(0f, 1f)
        if (w > 0f) {
            drawRoundRect(
                color = color,
                size = Size(w, size.height),
                cornerRadius = radius,
            )
        }
        drawRoundRect(
            color = Inks.Wood,
            cornerRadius = radius,
            style = Stroke(width = 2.dp.toPx()),
        )
        marker?.let {
            val x = size.width * it.coerceIn(0f, 1f)
            drawLine(
                color = Inks.Red,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 4.dp.toPx(),
            )
        }
    }
}

/** 턴 표시 띠. */
@Composable
fun TurnBanner(text: String, color: Color, trailing: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color)
            .border(2.dp, Inks.Wood, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            color = Inks.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
        )
        trailing?.let {
            Text(it, color = Inks.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
    }
}
