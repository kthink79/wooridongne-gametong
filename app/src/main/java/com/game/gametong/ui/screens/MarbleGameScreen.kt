package com.game.gametong.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.game.gametong.game.MarbleCircleGame
import com.game.gametong.game.MarbleHoleGame
import com.game.gametong.game.Side
import com.game.gametong.ui.components.FunButton
import com.game.gametong.ui.components.PaperCard
import com.game.gametong.ui.components.ScreenHeader
import com.game.gametong.ui.components.ScorePill
import com.game.gametong.ui.components.StatBar
import com.game.gametong.ui.components.TurnBanner
import com.game.gametong.ui.theme.Inks
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun MarbleGameScreen(
    mode: MarbleMode,
    twoPlayer: Boolean,
    onExit: () -> Unit,
) {
    when (mode) {
        MarbleMode.CIRCLE -> CircleGameScreen(twoPlayer, onExit)
        MarbleMode.HOLE -> HoleGameScreen(twoPlayer, onExit)
    }
}

private fun marbleAimDeg(ux: Float, uy: Float, side: Side): Float {
    val deg = Math.toDegrees(
        atan2(ux.toDouble(), (if (side == Side.PLAYER) -uy else uy).toDouble())
    ).toFloat()
    return deg
}

/* ───────────────── 원 안 구슬 쳐내기 ───────────────── */

@Composable
private fun CircleGameScreen(twoPlayer: Boolean, onExit: () -> Unit) {
    var gameKey by remember { mutableIntStateOf(0) }
    val game = remember(gameKey) { MarbleCircleGame(twoPlayer) }
    var tick by remember { mutableIntStateOf(0) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragNow by remember { mutableStateOf<Offset?>(null) }
    var power by remember { mutableStateOf(0f) }
    var aimDeg by remember { mutableStateOf(0f) }
    var thinking by remember { mutableStateOf(false) }

    LaunchedEffect(tick, gameKey) {
        if (!twoPlayer && !game.finished && game.turn == Side.AI) {
            thinking = true
            delay(680)
            game.aiShoot()
            thinking = false
            tick++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Inks.Kraft, Inks.KraftDeep, Inks.Kraft)))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "원 안 구슬 쳐내기", onBack = onExit)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TurnBanner(
                    text = when {
                        game.finished -> when (game.winner) {
                            Side.PLAYER -> if (twoPlayer) "1P 승리!" else "내가 이겼다!"
                            Side.AI -> if (twoPlayer) "2P 승리!" else "AI에게 졌다…"
                            else -> "무승부"
                        }
                        twoPlayer -> if (game.turn == Side.PLAYER) "1P 차례 — 끌어서 왕구슬 치기" else "2P 차례 — 끌어서 왕구슬 치기"
                        game.turn == Side.PLAYER -> "내 차례 — 끌어서 왕구슬 치기"
                        else -> if (thinking) "AI가 각도를 재는 중…" else "AI 차례"
                    },
                    color = if (game.finished) Inks.Green else if (game.turn == Side.PLAYER) Inks.Red else Inks.Blue,
                    trailing = "남은 구슬 ${game.remainingGlasses}",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScorePill(if (twoPlayer) "1P" else "나", "${game.playerScore}", Inks.Red, Modifier.weight(1f))
                    ScorePill("남은 구슬", "${game.remainingGlasses}", Inks.Gold, Modifier.weight(1f))
                    ScorePill(if (twoPlayer) "2P" else "AI", "${game.aiScore}", Inks.Blue, Modifier.weight(1f))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1000f / 1060f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFFD9C39A))
                        .border(3.dp, Inks.Wood, RoundedCornerShape(18.dp))
                        .pointerInput(game, game.turn, game.finished) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    if (!game.finished) {
                                        dragStart = offset
                                        dragNow = offset
                                        power = 0f
                                    }
                                },
                                onDrag = { change, _ ->
                                    val start = dragStart
                                    if (start != null && !game.finished) {
                                        dragNow = change.position
                                        change.consume()
                                        val dx = start.x - change.position.x
                                        val dy = start.y - change.position.y
                                        val len = sqrt(dx * dx + dy * dy)
                                        power = (len / (size.width * 0.42f)).coerceIn(0f, 1f)
                                        if (len > 6f) aimDeg = marbleAimDeg(dx / len, dy / len, game.turn)
                                    }
                                },
                                onDragEnd = {
                                    val start = dragStart
                                    val now = dragNow
                                    if (start != null && now != null && !game.finished) {
                                        val dx = start.x - now.x
                                        val dy = start.y - now.y
                                        val len = sqrt(dx * dx + dy * dy)
                                        if (len > 22f) {
                                            val side = game.turn
                                            game.shoot(
                                                side,
                                                marbleAimDeg(dx / len, dy / len, side),
                                                power.coerceAtLeast(0.08f),
                                            )
                                            tick++
                                        }
                                    }
                                    dragStart = null
                                    dragNow = null
                                    power = 0f
                                },
                                onDragCancel = {
                                    dragStart = null
                                    dragNow = null
                                    power = 0f
                                },
                            )
                        },
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val sx = size.width / 1000f
                        val sy = size.height / 1060f
                        drawCircleGround(sx, sy)
                        val center = Offset(MarbleCircleGame.CIRCLE_X * sx, MarbleCircleGame.CIRCLE_Y * sy)
                        val circleR = MarbleCircleGame.CIRCLE_R * sx
                        drawCircle(
                            color = Color(0xFF7A4A2B),
                            radius = circleR,
                            center = center,
                            style = Stroke(width = 8f),
                        )
                        drawCircle(
                            color = Color(0xFF7A4A2B).copy(alpha = 0.25f),
                            radius = circleR - 6f,
                            center = center,
                            style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f))),
                        )
                        for (marble in game.glasses) {
                            if (marble.sunk) continue
                            drawGlassMarble(marble.pos.x * sx, marble.pos.y * sy, marble.radius * sx, Color(0xFF6FC3E8))
                        }
                        val king = game.king
                        drawGlassMarble(
                            king.pos.x * sx,
                            king.pos.y * sy,
                            king.radius * sx,
                            if (game.turn == Side.PLAYER) Inks.Red else Inks.Blue,
                        )
                        val start = dragStart
                        val now = dragNow
                        if (start != null && now != null) {
                            val dx = start.x - now.x
                            val dy = start.y - now.y
                            val len = sqrt(dx * dx + dy * dy)
                            if (len > 12f) {
                                val from = Offset(king.pos.x * sx, king.pos.y * sy)
                                val reach = min(520f * sx + len, size.width * 0.95f)
                                val to = Offset(from.x + dx / len * reach, from.y + dy / len * reach)
                                drawLine(
                                    color = Inks.Red,
                                    start = from,
                                    end = to,
                                    strokeWidth = 7f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f)),
                                )
                                drawCircle(Inks.Red, radius = 7f, center = to)
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Inks.Wood.copy(alpha = 0.78f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "힘 ${(power * 100).toInt()}% · 각도 ${aimDeg.toInt()}° · 끌어서 놓기",
                            color = Inks.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 11.dp, color = Color(0xFFFFF3D6)) {
                    Text(game.outcome, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Inks.Ink)
                }
                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 10.dp) {
                    Column {
                        Text(
                            "원 밖으로 나간 유리구슬은 그대로 내 몫. 왕구슬은 매 턴 출발점에서 다시 칩니다.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Inks.SoftInk,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "내가 친 횟수 ${game.playerShots} · 상대 ${game.aiShots} (각 8번)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Inks.SoftInk,
                        )
                    }
                }

                if (game.finished) {
                    FunButton(
                        text = "한 판 더",
                        modifier = Modifier.fillMaxWidth(),
                        color = Inks.Green,
                        fontSize = 18,
                    ) { gameKey++ }
                }
                Spacer(Modifier.height(26.dp))
            }
        }
    }
}

/* ───────────────── 구멍치기 ───────────────── */

@Composable
private fun HoleGameScreen(twoPlayer: Boolean, onExit: () -> Unit) {
    var gameKey by remember { mutableIntStateOf(0) }
    val game = remember(gameKey) { MarbleHoleGame(twoPlayer) }
    var tick by remember { mutableIntStateOf(0) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragNow by remember { mutableStateOf<Offset?>(null) }
    var power by remember { mutableStateOf(0f) }
    var aimDeg by remember { mutableStateOf(0f) }
    var thinking by remember { mutableStateOf(false) }

    LaunchedEffect(tick, gameKey) {
        if (!twoPlayer && !game.finished && game.turn == Side.AI) {
            thinking = true
            delay(680)
            game.aiShoot()
            thinking = false
            tick++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Inks.Kraft, Inks.KraftDeep, Inks.Kraft)))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "구멍치기", onBack = onExit)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TurnBanner(
                    text = when {
                        game.finished -> when (game.winner) {
                            Side.PLAYER -> if (twoPlayer) "1P 승리!" else "내가 이겼다!"
                            Side.AI -> if (twoPlayer) "2P 승리!" else "AI에게 졌다…"
                            else -> "무승부"
                        }
                        twoPlayer -> if (game.turn == Side.PLAYER) "1P 차례 — 끌어서 치기" else "2P 차례 — 끌어서 치기"
                        game.turn == Side.PLAYER -> "내 차례 — 끌어서 치기"
                        else -> if (thinking) "AI가 각도를 재는 중…" else "AI 차례"
                    },
                    color = if (game.finished) Inks.Green else if (game.turn == Side.PLAYER) Inks.Red else Inks.Blue,
                    trailing = if (game.turn == Side.PLAYER) "구멍 ${game.playerProgress}/3" else "구멍 ${game.aiProgress}/3",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScorePill(if (twoPlayer) "1P" else "나", "${game.playerScore}", Inks.Red, Modifier.weight(1f))
                    ScorePill(
                        "내 구멍",
                        "${game.playerProgress}/3",
                        Inks.Gold,
                        Modifier.weight(1f),
                    )
                    ScorePill(if (twoPlayer) "2P" else "AI", "${game.aiScore}", Inks.Blue, Modifier.weight(1f))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1000f / 1080f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFFD9C39A))
                        .border(3.dp, Inks.Wood, RoundedCornerShape(18.dp))
                        .pointerInput(game, game.turn, game.finished) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    if (!game.finished) {
                                        dragStart = offset
                                        dragNow = offset
                                        power = 0f
                                    }
                                },
                                onDrag = { change, _ ->
                                    val start = dragStart
                                    if (start != null && !game.finished) {
                                        dragNow = change.position
                                        change.consume()
                                        val dx = start.x - change.position.x
                                        val dy = start.y - change.position.y
                                        val len = sqrt(dx * dx + dy * dy)
                                        power = (len / (size.width * 0.42f)).coerceIn(0f, 1f)
                                        if (len > 6f) aimDeg = marbleAimDeg(dx / len, dy / len, game.turn)
                                    }
                                },
                                onDragEnd = {
                                    val start = dragStart
                                    val now = dragNow
                                    if (start != null && now != null && !game.finished) {
                                        val dx = start.x - now.x
                                        val dy = start.y - now.y
                                        val len = sqrt(dx * dx + dy * dy)
                                        if (len > 22f) {
                                            val side = game.turn
                                            game.shoot(
                                                side,
                                                marbleAimDeg(dx / len, dy / len, side),
                                                power.coerceAtLeast(0.08f),
                                            )
                                            tick++
                                        }
                                    }
                                    dragStart = null
                                    dragNow = null
                                    power = 0f
                                },
                                onDragCancel = {
                                    dragStart = null
                                    dragNow = null
                                    power = 0f
                                },
                            )
                        },
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val sx = size.width / 1000f
                        val sy = size.height / 1080f
                        drawCircleGround(sx, sy, height = 1080f)
                        for ((index, hole) in game.holes.withIndex()) {
                            val center = Offset(hole.x * sx, hole.y * sy)
                            val r = MarbleHoleGame.HOLE_R * sx
                            drawCircle(Color(0xFF3B2B19), radius = r, center = center)
                            drawCircle(
                                color = Color(0xFF6B4A2B),
                                radius = r,
                                center = center,
                                style = Stroke(width = 6f),
                            )
                            val order = if (index == game.progressOf(game.turn)) Inks.Gold else Inks.Wood.copy(alpha = 0.5f)
                            drawCircle(
                                color = order,
                                radius = r * (if (index == game.progressOf(game.turn)) 0.45f else 0.3f),
                                center = center,
                                style = Stroke(width = 5f),
                            )
                        }
                        drawGlassMarble(
                            game.aiMarble.pos.x * sx,
                            game.aiMarble.pos.y * sy,
                            game.aiMarble.radius * sx,
                            Inks.Blue,
                        )
                        drawGlassMarble(
                            game.playerMarble.pos.x * sx,
                            game.playerMarble.pos.y * sy,
                            game.playerMarble.radius * sx,
                            Inks.Red,
                        )
                        val start = dragStart
                        val now = dragNow
                        if (start != null && now != null) {
                            val dx = start.x - now.x
                            val dy = start.y - now.y
                            val len = sqrt(dx * dx + dy * dy)
                            if (len > 12f) {
                                val me = game.marbleOf(game.turn)
                                val from = Offset(me.pos.x * sx, me.pos.y * sy)
                                val reach = min(len * 2.4f, size.width * 0.95f)
                                val to = Offset(from.x + dx / len * reach, from.y + dy / len * reach)
                                drawLine(
                                    color = Inks.Red,
                                    start = from,
                                    end = to,
                                    strokeWidth = 7f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f)),
                                )
                                drawCircle(Inks.Red, radius = 7f, center = to)
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Inks.Wood.copy(alpha = 0.78f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "힘 ${(power * 100).toInt()}% · 각도 ${aimDeg.toInt()}° · 끌어서 놓기",
                            color = Inks.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 11.dp, color = Color(0xFFFFF3D6)) {
                    Text(game.outcome, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Inks.Ink)
                }

                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 10.dp) {
                    Column {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for ((index, hole) in game.holes.withIndex()) {
                                val done = game.progressOf(game.turn) > index
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (done) Inks.Green else Inks.Wood.copy(alpha = 0.12f))
                                        .border(2.dp, Inks.Wood, RoundedCornerShape(10.dp))
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        if (done) "✔ ${index + 1}번" else "${index + 1}번",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (done) Inks.White else Inks.SoftInk,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "구멍 3개를 넣은 뒤에는 상대 구슬을 맞혀야 따먹습니다. 성공하면 한 번 더!",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Inks.SoftInk,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "치기 ${game.playerShots} / ${game.aiShots} · 남은 구멍 상대 ${(3 - game.aiProgress).coerceAtLeast(0)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Inks.SoftInk,
                        )
                    }
                }

                if (game.finished) {
                    FunButton(
                        text = "한 판 더",
                        modifier = Modifier.fillMaxWidth(),
                        color = Inks.Green,
                        fontSize = 18,
                    ) { gameKey++ }
                }
                Spacer(Modifier.height(26.dp))
            }
        }
    }
}

private fun DrawScope.drawCircleGround(sx: Float, sy: Float, height: Float = 1060f) {
    var y = 0f
    while (y < size.height) {
        drawLine(
            color = Inks.Wood.copy(alpha = 0.10f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f,
        )
        y += 42f * sy
    }
    var i = 0
    while (i < 120) {
        val x = ((i * 83) % 1000) * sx
        val yy = ((i * 57) % height.toInt()) * sy
        drawCircle(
            color = Color(0xFF7A5A33).copy(alpha = 0.10f),
            radius = (1 + (i % 3)).toFloat(),
            center = Offset(x, yy),
        )
        i++
    }
}

private fun DrawScope.drawGlassMarble(cx: Float, cy: Float, radius: Float, tint: Color) {
    drawCircle(Color(0x33000000), radius = radius, center = Offset(cx + 3f, cy + 4f))
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.95f), tint, tint.copy(alpha = 0.75f)),
            center = Offset(cx - radius * 0.3f, cy - radius * 0.35f),
            radius = radius * 1.6f,
        ),
        radius = radius,
        center = Offset(cx, cy),
    )
    drawCircle(
        color = Inks.Wood.copy(alpha = 0.55f),
        radius = radius,
        center = Offset(cx, cy),
        style = Stroke(width = 2.5f),
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.8f),
        radius = radius * 0.22f,
        center = Offset(cx - radius * 0.32f, cy - radius * 0.34f),
    )
}
