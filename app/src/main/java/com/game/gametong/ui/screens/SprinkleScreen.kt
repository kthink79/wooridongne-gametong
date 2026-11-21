package com.game.gametong.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.game.gametong.game.SprinkleMath
import com.game.gametong.ui.components.DemoNotice
import com.game.gametong.ui.components.FunButton
import com.game.gametong.ui.components.PaperCard
import com.game.gametong.ui.components.ScreenHeader
import com.game.gametong.ui.theme.Inks
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SprinkleScreen(
    owned: Int,
    onBack: () -> Unit,
    onSprinkled: (Int) -> Unit,
) {
    val rare = 5
    val legendary = 1
    val common = owned - rare - legendary

    var honor by remember { mutableStateOf(false) }
    var flying by remember { mutableStateOf(false) }
    var gained by remember { mutableIntStateOf(0) }
    var done by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val recipients = if (honor) SprinkleMath.HONOR_RECIPIENTS else 12
    val preview = if (honor) {
        SprinkleMath.honorPrestige(recipients, rare, legendary)
    } else {
        SprinkleMath.bulkPrestige(recipients, rare, legendary)
    }

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Inks.Kraft, Inks.KraftDeep)))) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "이벤트 뿌리기", onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DemoNotice("체험판 — 명성 계산식은 v0.1 기획 공식을 그대로 씁니다. 실제 뿌리기·알림은 서버 연동 후.")

                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp) {
                    Column {
                        Text("내 수집품", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Inks.Wood)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CollectChip("일반", common, Inks.Gold)
                            CollectChip("희귀", rare, Inks.Blue)
                            CollectChip("전설", legendary, Inks.Red)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "전설은 '탕정면 한정' — 뿌리면 동네에 이름이 남습니다.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Inks.SoftInk,
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SprinkleModeCard(
                        title = "물량 뿌리기",
                        desc = "12명에게 골고루\n명성 ×1.0",
                        color = Inks.Green,
                        selected = !honor,
                        modifier = Modifier.weight(1f),
                    ) { honor = false }
                    SprinkleModeCard(
                        title = "명예 뿌리기",
                        desc = "5명에게 크게\n명성 ×1.6",
                        color = Inks.Purple,
                        selected = honor,
                        modifier = Modifier.weight(1f),
                    ) { honor = true }
                }

                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp, color = Color(0xFFFFF3D6)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("예상 명성", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Inks.SoftInk)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "+$preview",
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Black,
                            color = Inks.Red,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "30 + 6×${recipients.coerceAtMost(20)} + 4×(희귀 ${rare} + 2×전설 ${legendary})" +
                                if (honor) " × 1.6" else "",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Inks.SoftInk,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "일일 상한 ${SprinkleMath.DAILY_CAP}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Inks.SoftInk,
                        )
                    }
                }

                AnimatedVisibility(visible = done, enter = fadeIn(), exit = fadeOut()) {
                    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, color = Color(0xFFE8F4E6)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🎉 동네에 뿌렸다!", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Inks.Green)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "명성 +$gained · 탕정면 주민들이 딱지를 주워 갔습니다",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Inks.Ink,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                FunButton(
                    text = when {
                        flying -> "뿌리는 중…"
                        done -> "한 번 더 뿌리기"
                        else -> "뿌리기"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (done) Inks.WoodLight else Inks.Purple,
                    enabled = !flying,
                ) {
                    if (flying) return@FunButton
                    flying = true
                    scope.launch {
                        delay(1200)
                        gained = preview
                        done = true
                        flying = false
                        onSprinkled(preview)
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun CollectChip(label: String, count: Int, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.18f))
            .border(2.dp, color, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(10.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text("$label $count", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Inks.Ink)
    }
}

@Composable
private fun SprinkleModeCard(
    title: String,
    desc: String,
    color: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) color.copy(alpha = 0.16f) else Inks.Paper)
            .border(if (selected) 3.dp else 2.dp, if (selected) color else Inks.Wood.copy(alpha = 0.4f), shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Black, color = Inks.Ink)
        Spacer(Modifier.height(4.dp))
        Text(
            desc,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Inks.SoftInk,
            textAlign = TextAlign.Center,
            lineHeight = 15.sp,
        )
    }
}
