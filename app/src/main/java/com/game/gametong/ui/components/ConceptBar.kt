package com.game.gametong.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.game.gametong.ui.theme.Inks

/**
 * 확정된 3D 시안의 하단 조작판 톤.
 *
 * 크림색 판 위에 월넛 나무 손잡이와 주황 게이지가 놓인다. 글씨는 앱이 직접 그리는
 * 선명한 한글이라 어떤 기기에서도 또렷하다.
 */
object BarTone {
    val creamTop = Color(0xFFFBF1DA)
    val creamBottom = Color(0xFFEAD6B0)
    val creamEdge = Color(0xFF8A5A32)
    val walnut = Color(0xFF8A5A2B)
    val walnutDark = Color(0xFF4E3018)
    val walnutLight = Color(0xFFB07C48)
    val orange = Color(0xFFE86A1E)
    val orangeLight = Color(0xFFFFC24A)
    val ink = Color(0xFF3A2A18)
    val trackDeep = Color(0xFFCBB58C)
}

/** 크림색 조작판. 아래쪽에 나무 테두리를 두른 두툼한 판. */
@Composable
fun WalnutBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .shadow(8.dp, shape, clip = false)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(BarTone.creamTop, Color(0xFFF4E5C6), BarTone.creamBottom)
                )
            )
            .border(2.dp, BarTone.creamEdge, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        // 윗면 하이라이트(광택)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(2.dp)
                .background(Color.White.copy(alpha = 0.65f))
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/** 월넛 나무 손잡이. 눌러서 조작을 고른다. */
@Composable
fun WoodKnob(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .scale(if (selected) 1.0f else 0.92f)
                .shadow(if (selected) 5.dp else 2.dp, CircleShape, clip = false)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(BarTone.walnutLight, BarTone.walnut, BarTone.walnutDark),
                        center = Offset(0.35f, 0.3f),
                        radius = 90f,
                    )
                )
                .border(if (selected) 3.dp else 2.dp, if (selected) BarTone.orangeLight else BarTone.walnutDark, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            // 나무 결
            Canvas(modifier = Modifier.size(size)) {
                val w = this.size.width
                var y = w * 0.18f
                var i = 0
                while (y < w * 0.95f) {
                    drawLine(
                        color = Color(0x22000000),
                        start = Offset(w * 0.12f, y),
                        end = Offset(w * 0.88f, y + (if (i % 2 == 0) 1.2f else -1.2f)),
                        strokeWidth = 1.1f,
                    )
                    y += w * 0.16f
                    i++
                }
            }
            Text(
                text = label,
                color = BarTone.creamTop,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 주황색 게이지(힘·파워). 크림 판에 파인 홈 안을 채운다. */
@Composable
fun PowerMeter(
    value: Float,
    modifier: Modifier = Modifier,
    height: Dp = 22.dp,
    caption: String? = null,
) {
    val shape = RoundedCornerShape(height / 2)
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(shape)
                .background(BarTone.trackDeep)
                .border(2.dp, BarTone.creamEdge, shape),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(value.coerceIn(0f, 1f))
                    .height(height)
                    .clip(shape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(BarTone.orange, Color(0xFFF59A2B), BarTone.orangeLight)
                        )
                    ),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(value.coerceIn(0f, 1f))
                    .height(height * 0.34f)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.55f), Color.Transparent)
                        )
                    ),
            )
        }
        if (caption != null) {
            Spacer(Modifier.height(3.dp))
            Text(
                caption,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = BarTone.ink.copy(alpha = 0.75f),
            )
        }
    }
}

/** 딱지치기의 둥근 게이지(주황 호). 힘을 호의 길이로 보여 준다. */
@Composable
fun ArcMeter(
    value: Float,
    modifier: Modifier = Modifier,
    height: Dp = 54.dp,
) {
    Canvas(modifier = modifier.height(height)) {
        val stroke = size.height * 0.42f
        val inset = stroke * 0.75f
        val arcSize = Size(size.width - inset * 2f, (size.height - inset) * 2f)
        drawArc(
            color = BarTone.trackDeep,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawArc(
            color = BarTone.orange,
            startAngle = 180f,
            sweepAngle = 180f * value.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke * 0.86f, cap = StrokeCap.Round),
        )
        drawArc(
            color = Color.White.copy(alpha = 0.45f),
            startAngle = 188f,
            sweepAngle = 164f * value.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset(inset, inset + stroke * 0.16f),
            size = arcSize,
            style = Stroke(width = stroke * 0.22f, cap = StrokeCap.Round),
        )
    }
}

/** 판 위의 작은 이름표. */
@Composable
fun BarLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 12.sp,
        fontWeight = FontWeight.Black,
        color = BarTone.ink.copy(alpha = 0.8f),
    )
}

/** 딱지치기·구슬치기에서 쓰는 힘 표시 + 안내 문구 줄. */
@Composable
fun BarHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = BarTone.ink.copy(alpha = 0.8f),
        textAlign = TextAlign.Center,
    )
}

/** 힘·각도 칩(누르면 그 값으로 맞춰진다). */
@Composable
fun BarChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) BarTone.walnut else BarTone.creamTop.copy(alpha = 0.9f))
            .border(2.dp, if (selected) BarTone.orangeLight else BarTone.creamEdge, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            color = if (selected) BarTone.creamTop else Inks.SoftInk,
            textAlign = TextAlign.Center,
        )
    }
}
