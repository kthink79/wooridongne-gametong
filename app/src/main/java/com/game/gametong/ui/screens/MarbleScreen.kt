package com.game.gametong.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.game.gametong.game.Vec2
import com.game.gametong.ui.components.DemoNotice
import com.game.gametong.ui.components.PaperCard
import com.game.gametong.ui.components.ScreenHeader
import com.game.gametong.ui.theme.Inks
import kotlin.math.min

private const val FIELD = 1000f
private val START = Vec2(500f, 800f)
private val HOLE = Vec2(500f, 280f)
private const val HOLE_RADIUS = 78f

@Composable
fun MarbleScreen(onBack: () -> Unit) {
    var marble by remember { mutableStateOf(START) }
    var dragStart by remember { mutableStateOf<Vec2?>(null) }
    var dragNow by remember { mutableStateOf(Vec2.ZERO) }
    var launch by remember { mutableIntStateOf(0) }
    var velocity by remember { mutableStateOf(Vec2.ZERO) }
    var running by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("구슬을 뒤로 당겼다 놓아 구멍에 넣어 보세요") }
    var success by remember { mutableIntStateOf(0) }
    var tries by remember { mutableIntStateOf(0) }

    LaunchedEffect(launch) {
        if (launch == 0) return@LaunchedEffect
        running = true
        var pos = marble
        var vel = velocity
        val dt = 1f / 60f
        while (true) {
            withFrameNanos { }
            val speed = vel.length()
            if (speed <= 6f) break
            val next = (speed - 900f * dt).coerceAtLeast(0f)
            vel = vel * (next / speed)
            pos = pos + vel * dt
            if (pos.x < 0f || pos.x > FIELD || pos.y < 0f || pos.y > FIELD) break
            marble = pos
        }
        running = false
        val landed = pos.distanceTo(HOLE) <= HOLE_RADIUS
        if (landed) {
            success++
            message = "구멍에 쏙! 성공 (${success}/${tries})"
        } else {
            message = "아깝다, 빗나갔다 (${success}/${tries})"
        }
        kotlinx.coroutines.delay(600)
        marble = START
    }

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Inks.Kraft, Inks.KraftDeep)))) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "구슬치기", onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DemoNotice("체험판 — 구슬 하나를 구멍에 넣는 손맛 데모입니다. 판돈·랭킹 연동은 정식 버전에서.")
                Spacer(Modifier.height(10.dp))
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFFD9C39A))
                        .border(3.dp, Inks.Wood, RoundedCornerShape(18.dp))
                        .pointerInput(running) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    if (!running) {
                                        val scale = size.width / FIELD
                                        dragStart = Vec2(offset.x / scale, offset.y / scale)
                                        dragNow = dragStart ?: Vec2.ZERO
                                    }
                                },
                                onDrag = { change, _ ->
                                    if (!running && dragStart != null) {
                                        val scale = size.width / FIELD
                                        dragNow = Vec2(change.position.x / scale, change.position.y / scale)
                                        change.consume()
                                    }
                                },
                                onDragEnd = {
                                    val start = dragStart
                                    if (!running && start != null) {
                                        val vec = start - dragNow
                                        if (vec.length() > 24f) {
                                            tries++
                                            velocity = vec.normalized() * (min(vec.length(), 380f) * 3.2f)
                                            launch++
                                        }
                                    }
                                    dragStart = null
                                    dragNow = Vec2.ZERO
                                },
                                onDragCancel = {
                                    dragStart = null
                                    dragNow = Vec2.ZERO
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val scale = size.width / FIELD
                        // 흙바닥 결
                        var y = 0f
                        while (y < size.height) {
                            drawLine(
                                color = Inks.Wood.copy(alpha = 0.06f),
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1f,
                            )
                            y += 42f
                        }
                        // 구멍
                        drawCircle(Color(0xFF3B2B19), radius = HOLE_RADIUS * scale, center = Offset(HOLE.x * scale, HOLE.y * scale))
                        drawCircle(
                            Color(0xFF6B4A2B),
                            radius = HOLE_RADIUS * scale,
                            center = Offset(HOLE.x * scale, HOLE.y * scale),
                            style = Stroke(width = 4f),
                        )
                        // 시작 표시
                        drawCircle(
                            color = Inks.Wood.copy(alpha = 0.22f),
                            radius = 30f * scale,
                            center = Offset(START.x * scale, START.y * scale),
                            style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))),
                        )
                        // 구슬
                        val center = Offset(marble.x * scale, marble.y * scale)
                        drawCircle(Color(0x33000000), radius = 20f * scale, center = Offset(center.x + 4f, center.y + 6f))
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(Color(0xFF9FD8FF), Color(0xFF2E5EA8)),
                                center = center,
                                radius = 22f * scale,
                            ),
                            radius = 20f * scale,
                            center = center,
                        )
                        // 조준선
                        dragStart?.let { start ->
                            val vec = start - dragNow
                            if (vec.length() > 12f) {
                                val dir = vec.normalized()
                                val from = Offset(start.x * scale, start.y * scale)
                                val to = Offset(
                                    from.x + dir.x * min(vec.length(), 380f) * scale,
                                    from.y + dir.y * min(vec.length(), 380f) * scale,
                                )
                                drawLine(
                                    color = Inks.Red,
                                    start = from,
                                    end = to,
                                    strokeWidth = 5f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 9f)),
                                )
                                drawCircle(Inks.Red, radius = 6f, center = to)
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Inks.Wood.copy(alpha = 0.75f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "당겨서 놓기 · ${success}성공 / ${tries}회",
                            color = Inks.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
                    Text(
                        text = message,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Inks.Ink,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
