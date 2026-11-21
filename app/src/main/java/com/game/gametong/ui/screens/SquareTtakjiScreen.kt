package com.game.gametong.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.game.gametong.game.Side
import com.game.gametong.game.SquareStrikeResult
import com.game.gametong.game.SquareTtakjiMatch
import com.game.gametong.game.TtakjiMaterial
import com.game.gametong.game.TtakjiSpec
import com.game.gametong.game.TtakjiSurface
import com.game.gametong.ui.components.FunButton
import com.game.gametong.ui.components.PaperCard
import com.game.gametong.ui.components.ScreenHeader
import com.game.gametong.ui.components.ScorePill
import com.game.gametong.ui.components.SelectChip
import com.game.gametong.ui.components.StatBar
import com.game.gametong.ui.components.TurnBanner
import com.game.gametong.ui.theme.Inks
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

private const val FIELD_W = 1000f
private const val FIELD_H = 1500f

/** 네모 딱지치기 경기 화면. 끌어서 조준하고 놓으면 한 수가 나간다. */
@Composable
fun SquareTtakjiScreen(
    surface: TtakjiSurface,
    playerMaterial: TtakjiMaterial,
    playerThickness: Int,
    aiMaterial: TtakjiMaterial,
    aiThickness: Int,
    twoPlayer: Boolean,
    onExit: () -> Unit,
) {
    var matchKey by remember { mutableIntStateOf(0) }
    val match = remember(matchKey) {
        SquareTtakjiMatch(
            surface = surface,
            playerSpec = TtakjiSpec(playerMaterial, playerThickness),
            aiSpec = TtakjiSpec(aiMaterial, aiThickness),
            twoPlayer = twoPlayer,
        )
    }
    var tick by remember { mutableIntStateOf(0) }
    var strikePoint by remember { mutableStateOf(0.9f) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragNow by remember { mutableStateOf<Offset?>(null) }
    var power by remember { mutableStateOf(0f) }
    var aimDeg by remember { mutableStateOf(0f) }
    var lastResult by remember { mutableStateOf<SquareStrikeResult?>(null) }
    var lastActor by remember { mutableStateOf<Side?>(null) }
    var flippedSide by remember { mutableStateOf<Side?>(null) }
    var thinking by remember { mutableStateOf(false) }

    val aiFlip by animateFloatAsState(
        targetValue = if (flippedSide == Side.AI) 180f else 0f,
        animationSpec = tween(420),
        label = "aiFlip",
    )

    LaunchedEffect(tick, matchKey) {
        if (!twoPlayer && !match.finished && match.turn == Side.AI) {
            thinking = true
            delay(620)
            val result = match.aiAttack()
            lastResult = result
            lastActor = Side.AI
            flippedSide = if (result.flipped) Side.PLAYER else null
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
            ScreenHeader(title = "네모 딱지치기", onBack = onExit)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val playerTurn = match.turn == Side.PLAYER
                TurnBanner(
                    text = when {
                        match.finished -> when (match.winner) {
                            Side.PLAYER -> if (twoPlayer) "1P 승리!" else "내가 이겼다!"
                            Side.AI -> if (twoPlayer) "2P 승리!" else "AI에게 졌다…"
                            else -> "무승부"
                        }
                        twoPlayer -> if (playerTurn) "1P 차례 — 끌어서 조준" else "2P 차례 — 끌어서 조준"
                        playerTurn -> "내 차례 — 끌어서 조준"
                        else -> if (thinking) "AI가 각도를 재는 중…" else "AI 차례"
                    },
                    color = when {
                        match.finished -> Inks.Green
                        match.turn == Side.PLAYER -> Inks.Red
                        else -> Inks.Blue
                    },
                    trailing = "먼저 3점",
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScorePill("나", "${match.playerScore}", Inks.Red, Modifier.weight(1f))
                    ScorePill("노면", surface.label, Inks.Blue, Modifier.weight(1f))
                    ScorePill(if (twoPlayer) "2P" else "AI", "${match.aiScore}", Inks.Blue, Modifier.weight(1f))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(surfaceColor(surface))
                        .border(3.dp, Inks.Wood, RoundedCornerShape(18.dp))
                        .pointerInput(match, match.turn, match.finished) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    if (!match.finished) {
                                        dragStart = offset
                                        dragNow = offset
                                        power = 0f
                                        aimDeg = 0f
                                    }
                                },
                                onDrag = { change, _ ->
                                    val start = dragStart
                                    if (start != null && !match.finished) {
                                        dragNow = change.position
                                        change.consume()
                                        val dx = start.x - change.position.x
                                        val dy = start.y - change.position.y
                                        val len = sqrt(dx * dx + dy * dy)
                                        power = (len / (size.width * 0.42f)).coerceIn(0f, 1f)
                                        if (len > 6f) {
                                            aimDeg = aimOf(dx / len, dy / len, match.turn)
                                        }
                                    }
                                },
                                onDragEnd = {
                                    val start = dragStart
                                    val now = dragNow
                                    if (start != null && now != null && !match.finished) {
                                        val dx = start.x - now.x
                                        val dy = start.y - now.y
                                        val len = sqrt(dx * dx + dy * dy)
                                        if (len > 22f) {
                                            val actor = match.turn
                                            val aim = aimOf(dx / len, dy / len, actor)
                                            val p = power.coerceIn(0.05f, 1f)
                                            val result = match.attack(actor, aim, p, strikePoint)
                                            lastResult = result
                                            lastActor = actor
                                            flippedSide = if (result.flipped) actor.other() else null
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
                        val sx = size.width / FIELD_W
                        val sy = size.height / FIELD_H
                        drawSurface(surface, sx, sy)
                        drawLine(
                            color = Inks.Wood.copy(alpha = 0.35f),
                            start = Offset(0f, SquareTtakjiMatch.AI_HOME_Y * sy),
                            end = Offset(size.width, SquareTtakjiMatch.AI_HOME_Y * sy),
                            strokeWidth = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f)),
                        )
                        drawTtakji(
                            cx = match.aiHome.x * sx,
                            cy = match.aiHome.y * sy,
                            spec = match.aiSpec,
                            flipped = flippedSide == Side.AI,
                            flipAngle = if (flippedSide == Side.AI) aiFlip else 0f,
                            isMine = false,
                            scale = sx,
                        )
                        drawTtakji(
                            cx = match.playerHome.x * sx,
                            cy = match.playerHome.y * sy,
                            spec = match.playerSpec,
                            flipped = flippedSide == Side.PLAYER,
                            flipAngle = 0f,
                            isMine = true,
                            scale = sx,
                        )
                        val start = dragStart
                        val now = dragNow
                        if (start != null && now != null) {
                            val dx = start.x - now.x
                            val dy = start.y - now.y
                            val len = sqrt(dx * dx + dy * dy)
                            if (len > 12f) {
                                val from = if (match.turn == Side.PLAYER) {
                                    Offset(match.playerHome.x * sx, match.playerHome.y * sy)
                                } else {
                                    Offset(match.aiHome.x * sx, match.aiHome.y * sy)
                                }
                                val ux = dx / len
                                val uy = dy / len
                                val reach = min(len * 2.2f, size.width * 0.92f)
                                val to = Offset(from.x + ux * reach, from.y + uy * reach)
                                drawLine(
                                    color = Inks.Red,
                                    start = from,
                                    end = to,
                                    strokeWidth = 7f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 12f)),
                                )
                                drawCircle(Inks.Red, radius = 10f, center = to)
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
                            text = when {
                                dragStart != null -> "힘 ${(power * 100).toInt()}% · 각도 ${aimDeg.toInt()}°"
                                match.finished -> "한 판 더 눌러 다시 시작"
                                else -> "${surface.label} · 끌어서 놓기"
                            },
                            color = Inks.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 10.dp) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (twoPlayer) "1P 피로도" else "내 피로도",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = Inks.Wood,
                                modifier = Modifier.width(76.dp),
                            )
                            StatBar(
                                value = match.playerStamina / 100f,
                                color = if (match.playerStamina > 40f) Inks.Green else Inks.Orange,
                                modifier = Modifier.weight(1f),
                                marker = match.playerPowerCap,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "${match.playerStamina.toInt()}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = Inks.Ink,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "파워 상한",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Inks.SoftInk,
                                modifier = Modifier.width(76.dp),
                            )
                            Text(
                                "${(match.playerPowerCap * 100).toInt()}%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = Inks.Ink,
                            )
                            if (match.playerInjury > 0) {
                                Spacer(Modifier.width(8.dp))
                                InjuryTag("부상 · ${match.playerInjury}턴 힘 제한")
                            } else if (match.playerStamina < 25f) {
                                Spacer(Modifier.width(8.dp))
                                InjuryTag("피로 — 살살 치면 회복")
                            }
                        }
                        if (!twoPlayer) {
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "AI 피로도",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Inks.SoftInk,
                                    modifier = Modifier.width(76.dp),
                                )
                                StatBar(
                                    value = match.aiStamina / 100f,
                                    color = Inks.Blue,
                                    modifier = Modifier.weight(1f),
                                    height = 10,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (match.aiInjury > 0) "부상 ${match.aiInjury}턴" else "${match.aiStamina.toInt()}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Inks.Ink,
                                )
                            }
                        }
                    }
                }

                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 10.dp) {
                    Column {
                        Text(
                            "내 딱지의 어느 날을 때릴까?",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = Inks.Wood,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SelectChip("왼쪽 날", strikePoint < -0.4f, Inks.Red, Modifier.weight(1f)) { strikePoint = -0.9f }
                            SelectChip("가운데", strikePoint in -0.4f..0.4f, Inks.Red, Modifier.weight(1f)) { strikePoint = 0f }
                            SelectChip("오른쪽 날", strikePoint > 0.4f, Inks.Red, Modifier.weight(1f)) { strikePoint = 0.9f }
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "날을 때리면 토크가 커집니다. 상대 딱지의 노란 들뜬 틈을 향해 끌어 보세요.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Inks.SoftInk,
                        )
                    }
                }

                PaperCard(
                    modifier = Modifier.fillMaxWidth(),
                    padding = 11.dp,
                    color = Color(0xFFFFF3D6),
                ) {
                    Text(
                        text = describe(match, lastResult, lastActor, twoPlayer),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Inks.Ink,
                    )
                }

                if (match.finished) {
                    FunButton(
                        text = "같은 조건으로 한 판 더",
                        modifier = Modifier.fillMaxWidth(),
                        color = Inks.Green,
                        fontSize = 18,
                    ) {
                        matchKey++
                        lastResult = null
                        flippedSide = null
                        lastActor = null
                        tick++
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

/** 화면에서 끌어온 방향(ux, uy, y 아래가 +)을 그 쪽 조준각으로 바꾼다. */
private fun aimOf(ux: Float, uy: Float, side: Side): Float {
    val deg = Math.toDegrees(
        atan2(ux.toDouble(), (if (side == Side.PLAYER) -uy else uy).toDouble())
    ).toFloat()
    return deg
}

@Composable
private fun InjuryTag(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Inks.Red.copy(alpha = 0.15f))
            .border(2.dp, Inks.Red, RoundedCornerShape(8.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Black, color = Inks.Red)
    }
}

private fun describe(
    match: SquareTtakjiMatch,
    result: SquareStrikeResult?,
    actor: Side?,
    twoPlayer: Boolean,
): String {
    if (match.finished) {
        return when (match.winner) {
            Side.PLAYER -> "경기 끝 — ${match.playerScore} : ${match.aiScore} 로 ${if (twoPlayer) "1P" else "내가"} 이겼다!"
            Side.AI -> "경기 끝 — ${match.playerScore} : ${match.aiScore} 로 ${if (twoPlayer) "2P가" else "AI가"} 이겼다."
            else -> "경기 끝 — ${match.playerScore} : ${match.aiScore} 무승부."
        }
    }
    val r = result ?: return "상대 딱지의 노란 날개 틈을 노려 끌어서 놓으세요."
    val who = when {
        twoPlayer && actor == Side.AI -> "2P가"
        twoPlayer -> "1P가"
        actor == Side.AI -> "AI가"
        else -> "내가"
    }
    return when {
        r.floorHit -> "$who 헛스윙! 바닥을 세게 쳤다 — ${match.surface.injuryTurns}턴 동안 파워가 묶인다."
        !r.contact -> "$who 딱지가 상대에게 닿지 않았다. 더 세게, 또는 노면을 바꿔 보세요."
        r.flipped && r.weakPoint -> "$who 들뜬 날개 틈을 정확히 맞혔다! 뒤집혀서 한 장 따먹었다."
        r.flipped -> "$who 맞혔다 — 딱지가 뒤집혔다! 한 장 따먹었다."
        r.weakPoint -> "$who 날개 틈을 맞혔지만 살짝 모자랐다. 조금 더 세게!"
        else -> "$who 맞혔지만 뒤집히지 않았다 (뒤집힘 ${(r.flipRatio * 100).toInt()}%)."
    }
}

private fun surfaceColor(surface: TtakjiSurface): Color = when (surface) {
    TtakjiSurface.MARU -> Color(0xFFE6C68E)
    TtakjiSurface.DIRT -> Color(0xFFD2B183)
    TtakjiSurface.ASPHALT -> Color(0xFF8F8C86)
}

private fun DrawScope.drawSurface(surface: TtakjiSurface, sx: Float, sy: Float) {
    when (surface) {
        TtakjiSurface.MARU -> {
            var y = 0f
            while (y < size.height) {
                drawLine(
                    color = Color(0xFFB98B4E).copy(alpha = 0.45f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 3f,
                )
                y += 38f * sy
            }
        }
        TtakjiSurface.DIRT -> {
            var i = 0
            while (i < 160) {
                val x = ((i * 97) % 1000) * sx
                val y = ((i * 61) % 1500) * sy
                drawCircle(
                    color = Color(0xFF7A5A33).copy(alpha = 0.22f),
                    radius = (2 + (i % 4)).toFloat(),
                    center = Offset(x, y),
                )
                i++
            }
        }
        TtakjiSurface.ASPHALT -> {
            drawRect(Color(0xFF6E6B66).copy(alpha = 0.5f))
            var i = 0
            while (i < 220) {
                val x = ((i * 89) % 1000) * sx
                val y = ((i * 53) % 1500) * sy
                drawCircle(
                    color = Color(0xFF3F3D3A).copy(alpha = 0.30f),
                    radius = (1 + (i % 3)).toFloat(),
                    center = Offset(x, y),
                )
                i++
            }
        }
    }
}

private fun DrawScope.drawTtakji(
    cx: Float,
    cy: Float,
    spec: TtakjiSpec,
    flipped: Boolean,
    flipAngle: Float,
    isMine: Boolean,
    scale: Float,
) {
    val half = spec.halfSize * scale
    val c = Offset(cx, cy)
    rotate(degrees = flipAngle, pivot = c) {
        drawRoundRect(
            color = Color(0x33000000),
            topLeft = Offset(c.x - half + 5f, c.y - half + 9f),
            size = Size(half * 2f, half * 2f),
            cornerRadius = CornerRadius(10f),
        )
        drawRect(
            color = if (isMine) Inks.Blue else Inks.Red,
            topLeft = Offset(c.x - half, c.y - half),
            size = Size(half * 2f, half * 2f),
        )
        val path = Path().apply {
            moveTo(c.x - half, c.y - half)
            lineTo(c.x + half, c.y + half)
            lineTo(c.x - half, c.y + half)
            close()
        }
        drawPath(path, if (isMine) Inks.Red else Inks.Blue)
        drawRect(
            color = Inks.Wood,
            topLeft = Offset(c.x - half, c.y - half),
            size = Size(half * 2f, half * 2f),
            style = Stroke(width = 6f),
        )
        val gap = half * 0.6f
        val a = when (spec.weakEdge) {
            0 -> Offset(c.x - gap, c.y - half)
            1 -> Offset(c.x + half, c.y - gap)
            2 -> Offset(c.x - gap, c.y + half)
            else -> Offset(c.x - half, c.y - gap)
        }
        val b = when (spec.weakEdge) {
            0 -> Offset(c.x + gap, c.y - half)
            1 -> Offset(c.x + half, c.y + gap)
            2 -> Offset(c.x + gap, c.y + half)
            else -> Offset(c.x - half, c.y + gap)
        }
        drawLine(color = Inks.Gold, start = a, end = b, strokeWidth = 13f)
        drawLine(color = Color(0xFF8A5A00), start = a, end = b, strokeWidth = 4f)
        if (flipped) {
            drawRect(
                color = Inks.White.copy(alpha = 0.35f),
                topLeft = Offset(c.x - half, c.y - half),
                size = Size(half * 2f, half * 2f),
            )
        }
    }
}
