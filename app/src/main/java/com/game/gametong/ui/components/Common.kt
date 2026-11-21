package com.game.gametong.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.game.gametong.ui.theme.CardShape
import com.game.gametong.ui.theme.Inks

/** 두꺼운 테두리와 살짝 뜬 그림자를 가진 종이 카드. */
@Composable
fun PaperCard(
    modifier: Modifier = Modifier,
    color: Color = Inks.Paper,
    borderColor: Color = Inks.Wood,
    corner: Dp = 18.dp,
    padding: Dp = 14.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .shadow(5.dp, shape, clip = false)
            .clip(shape)
            .background(color)
            .border(3.dp, borderColor, shape)
            .padding(padding),
        content = content,
    )
}

/** 누르면 살짝 들어가는 두툼한 버튼. */
@Composable
fun FunButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Inks.Red,
    textColor: Color = Inks.White,
    fontSize: Int = 19,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .shadow(if (enabled) 4.dp else 0.dp, shape, clip = false)
            .clip(shape)
            .background(if (enabled) color else Inks.Gray)
            .border(3.dp, Inks.Wood, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = textColor,
            fontWeight = FontWeight.Black,
            fontSize = fontSize.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/** 상단 띠: 뒤로가기 + 제목. */
@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Inks.Paper)
                    .border(3.dp, Inks.Wood, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text("←", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Inks.Wood)
            }
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            fontSize = 21.sp,
            fontWeight = FontWeight.Black,
            color = Inks.Wood,
        )
        trailing?.invoke()
    }
}

/** 크라프트지 배경(살짝 결이 보이도록 위에서 아래로 색이 바뀐다). */
@Composable
fun KraftBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Inks.Kraft, Inks.KraftDeep, Inks.Kraft)
                )
            ),
        content = content,
    )
}

/** 작은 꼬리표(태그). */
@Composable
fun Tag(text: String, color: Color = Inks.Gold, textColor: Color = Inks.Ink) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .border(2.dp, Inks.Wood, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Black, color = textColor)
    }
}

/** 데모 화면임을 분명히 알리는 띠. */
@Composable
fun DemoNotice(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Inks.Ink.copy(alpha = 0.08f))
            .border(2.dp, Inks.Ink.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Inks.SoftInk,
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Black, color = Inks.Wood)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Inks.Red)
        )
    }
}

@Composable
fun InfoRow(icon: String, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(icon, fontSize = 17.sp)
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Inks.SoftInk)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Black, color = Inks.Ink)
    }
}

val TileShape = CardShape
