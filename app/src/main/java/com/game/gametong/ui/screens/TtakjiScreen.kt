package com.game.gametong.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.game.gametong.ui.components.DemoNotice
import com.game.gametong.ui.components.FunButton
import com.game.gametong.ui.components.PaperCard
import com.game.gametong.ui.components.ScreenHeader
import com.game.gametong.ui.theme.Inks
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos

@Composable
fun TtakjiScreen(onBack: () -> Unit) {
    var tries by remember { mutableIntStateOf(0) }
    var hits by remember { mutableIntStateOf(0) }
    var flipped by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("힘 게이지가 초록 구간에 올 때 쳐 보세요") }
    val scope = rememberCoroutineScope()
    val transition = rememberInfiniteTransition(label = "needle")
    val needle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(980, easing = LinearEasing), RepeatMode.Reverse),
        label = "needleValue",
    )
    val flipAngle by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(420),
        label = "flip",
    )

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Inks.Kraft, Inks.KraftDeep)))) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "딱지치기", onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DemoNotice("체험판 — 네모 딱지 뒤집기 손맛만 맛보는 미니 데모입니다. 정식 종목은 다음 업데이트에서 열립니다.")

                Spacer(Modifier.height(18.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.62f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFFF7E7C6))
                        .border(3.dp, Inks.Wood, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val side = size.minDimension * 0.62f
                        val squash = cos(Math.toRadians(flipAngle.toDouble())).toFloat()
                        rotate(degrees = 0f, pivot = Offset(cx, cy)) {
                            // 그림자
                            drawRoundRect(
                                color = Color(0x33000000),
                                topLeft = Offset(cx - side / 2f * squash + 6f, cy - side / 2f + 10f),
                                size = Size(side * squash, side),
                                cornerRadius = CornerRadius(10f),
                            )
                            val topLeft = Offset(cx - side / 2f * squash, cy - side / 2f)
                            val s = Size(side * squash, side)
                            // 빨강/파랑 두 조각
                            drawRect(color = Inks.Red, topLeft = topLeft, size = s)
                            val path = Path().apply {
                                moveTo(topLeft.x, topLeft.y)
                                lineTo(topLeft.x + s.width, topLeft.y + s.height)
                                lineTo(topLeft.x, topLeft.y + s.height)
                                close()
                            }
                            drawPath(path, Inks.Blue)
                            drawRect(
                                color = Inks.Wood,
                                topLeft = topLeft,
                                size = s,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f),
                            )
                            drawLine(
                                color = Inks.Wood,
                                start = Offset(topLeft.x, topLeft.y),
                                end = Offset(topLeft.x + s.width, topLeft.y + s.height),
                                strokeWidth = 3f,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp) {
                    Column {
                        Text("힘 게이지", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Inks.Wood)
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Inks.Wood.copy(alpha = 0.15f))
                                .border(2.dp, Inks.Wood, RoundedCornerShape(10.dp))
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawRect(
                                    color = Inks.Green.copy(alpha = 0.55f),
                                    topLeft = Offset(size.width * 0.62f, 0f),
                                    size = Size(size.width * 0.26f, size.height),
                                )
                                drawLine(
                                    color = Inks.Red,
                                    start = Offset(size.width * needle, 0f),
                                    end = Offset(size.width * needle, size.height),
                                    strokeWidth = 8f,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = message,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Inks.SoftInk,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                FunButton(
                    text = if (tries >= 5) "다시 하기" else "지금 쳐보기",
                    modifier = Modifier.fillMaxWidth(),
                    color = Inks.Red,
                ) {
                    if (tries >= 5) {
                        tries = 0
                        hits = 0
                        message = "힘 게이지가 초록 구간에 올 때 쳐 보세요"
                        return@FunButton
                    }
                    tries++
                    val hit = needle in 0.62f..0.88f
                    if (hit) {
                        hits++
                        flipped = true
                        message = "뒤집혔다! 딱지 한 장 획득 (${hits}/${tries})"
                        scope.launch {
                            delay(520)
                            flipped = false
                        }
                    } else {
                        message = if (needle < 0.62f) "너무 약했다 — 딱지가 반만 들렸다" else "너무 세게 쳤다 — 딱지가 날아갔다"
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "5번 중 ${hits}번 성공",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = Inks.Wood,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun GonggiScreen(onBack: () -> Unit) {
    var catches by remember { mutableIntStateOf(0) }
    var tries by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("구슬이 초록 구간에 들어올 때 잡으세요") }
    val transition = rememberInfiniteTransition(label = "gonggi")
    val marker by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(860, easing = LinearEasing), RepeatMode.Reverse),
        label = "marker",
    )

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Inks.Kraft, Inks.KraftDeep)))) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "공기놀이", onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DemoNotice("체험판 — 공기놀이 '잡기' 타이밍만 맛보는 미니 데모입니다.")

                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(5) { index ->
                        val caught = index < catches
                        Box(
                            modifier = Modifier
                                .width(46.dp)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(50))
                                .background(if (caught) Inks.Gold else Inks.Wood.copy(alpha = 0.15f))
                                .border(3.dp, Inks.Wood, RoundedCornerShape(50)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (caught) "🫘" else "·", fontSize = 20.sp)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp) {
                    Column {
                        Text("타이밍", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Inks.Wood)
                        Spacer(Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(30.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Inks.Wood.copy(alpha = 0.15f))
                                .border(2.dp, Inks.Wood, RoundedCornerShape(10.dp))
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawRect(
                                    color = Inks.Green.copy(alpha = 0.55f),
                                    topLeft = Offset(size.width * 0.38f, 0f),
                                    size = Size(size.width * 0.24f, size.height),
                                )
                                drawCircle(
                                    color = Inks.Red,
                                    radius = size.height * 0.32f,
                                    center = Offset(size.width * marker, size.height / 2f),
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(message, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Inks.SoftInk)
                    }
                }

                Spacer(Modifier.height(16.dp))
                FunButton(
                    text = if (catches >= 5) "한 판 더" else "잡기",
                    modifier = Modifier.fillMaxWidth(),
                    color = Inks.Gold,
                    textColor = Inks.Ink,
                ) {
                    if (catches >= 5) {
                        catches = 0
                        tries = 0
                        message = "구슬이 초록 구간에 들어올 때 잡으세요"
                        return@FunButton
                    }
                    tries++
                    if (marker in 0.38f..0.62f) {
                        catches++
                        message = "잡았다! (${catches}/5)"
                    } else {
                        message = "놓쳤다… (${tries}번 시도)"
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
