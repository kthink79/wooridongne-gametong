package com.game.gametong.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.scale
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.game.gametong.game.AlkkaegiMatch
import com.game.gametong.game.Board
import com.game.gametong.game.DuelMatch
import com.game.gametong.game.EraserAi
import com.game.gametong.game.EraserBody
import com.game.gametong.game.EraserType
import com.game.gametong.game.Flick
import com.game.gametong.game.MatchPhase
import com.game.gametong.game.Side
import com.game.gametong.game.TurnOutcome
import com.game.gametong.game.Vec2
import com.game.gametong.game.WrestlingMatch
import com.game.gametong.ui.EraserMode
import com.game.gametong.ui.components.FunButton
import com.game.gametong.ui.components.PaperCard
import com.game.gametong.ui.theme.Inks
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.roundToInt

private val PlayerBodyColor = Color(0xFF2E5EA8)
private val AiBodyColor = Color(0xFFC9453A)
private val DeskSurface = Color(0xFFE9D3A8)
private val DeskWood = Color(0xFF6B4A2B)

@Composable
fun EraserBattleScreen(
    mode: EraserMode,
    playerType: EraserType,
    aiType: EraserType,
    twoPlayer: Boolean,
    aimAssist: Boolean,
    haptics: Boolean,
    onExit: () -> Unit,
) {
    var session by remember { mutableIntStateOf(0) }
    val match: DuelMatch = remember(session, mode, playerType, aiType) {
        when (mode) {
            EraserMode.WRESTLING -> WrestlingMatch(playerType, aiType)
            EraserMode.ALKKAEGI -> AlkkaegiMatch(playerType, aiType)
        }
    }

    var frame by remember(match) { mutableIntStateOf(0) }
    var moveCount by remember(match) { mutableIntStateOf(0) }
    var phaseNow by remember(match) { mutableStateOf(match.phase) }
    var turnNow by remember(match) { mutableStateOf(match.turn) }
    var winnerNow by remember(match) { mutableStateOf<Side?>(null) }
    var countFlash by remember(match) { mutableStateOf<Side?>(null) }
    var banner by remember(match) { mutableStateOf<String?>(null) }
    var lastOutcome by remember(match) { mutableStateOf<TurnOutcome?>(null) }

    var dragStart by remember(match) { mutableStateOf<Vec2?>(null) }
    var dragNow by remember(match) { mutableStateOf(Vec2.ZERO) }

    val countScale by animateFloatAsState(
        targetValue = if (countFlash != null) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
        label = "countScale",
    )

    val haptic = LocalHapticFeedback.current

    fun syncHud() {
        phaseNow = match.phase
        turnNow = match.turn
        winnerNow = match.winner
    }

    LaunchedEffect(match, moveCount) {
        while (true) {
            when {
                match.phase == MatchPhase.MOVING -> {
                    withFrameNanos { }
                    repeat(2) { match.world.step() }
                    frame++
                    if (match.world.settled) {
                        when (match) {
                            is WrestlingMatch -> lastOutcome = match.settleFlick()
                            is AlkkaegiMatch -> match.settleFlick()
                            else -> Unit
                        }
                        if (haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        syncHud()
                    }
                }

                match.phase == MatchPhase.COUNTING -> {
                    val wrestling = match as? WrestlingMatch
                    countFlash = wrestling?.countSide
                    if (haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    delay(1250)
                    countFlash = null
                    wrestling?.applyCount()
                    syncHud()
                }

                match.phase == MatchPhase.ROUND_END -> {
                    val alk = match as? AlkkaegiMatch
                    banner = when (alk?.lastRoundWinner) {
                        Side.PLAYER -> "라운드 승리!"
                        Side.AI -> "라운드 패배…"
                        null -> null
                    }
                    delay(1650)
                    banner = null
                    alk?.nextRound()
                    syncHud()
                }

                match.phase == MatchPhase.AIMING && match.turn == Side.AI && !twoPlayer -> {
                    delay(620)
                    val velocity = match.aiVelocity(0.82f)
                    match.flick(velocity)
                    frame++
                    syncHud()
                }

                else -> break
            }
        }
    }

    val canAim = phaseNow == MatchPhase.AIMING && (turnNow == Side.PLAYER || twoPlayer)
    val attacker: EraserBody = match.world.bodyOf(turnNow)

    val dragVector: Vec2? = dragStart?.let { it - dragNow }
    val power = dragVector?.let { Flick.powerOf(it) } ?: 0f

    val prediction: Vec2? = remember(
        frame / 6,
        dragStart,
        dragNow,
        canAim,
        aimAssist,
        match,
    ) {
        val vec = dragVector
        if (aimAssist && canAim && vec != null && vec.length() > 40f) {
            val v = Flick.velocityFromDrag(vec, attacker.type)
            if (v.length() > 60f) EraserAi.predictRest(match.world, match.turn, v) else null
        } else {
            null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Inks.Kraft, Inks.KraftDeep)))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            BattleHud(
                mode = mode,
                match = match,
                turnNow = turnNow,
                twoPlayer = twoPlayer,
                onExit = onExit,
            )

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                val boardWidth = min(maxWidth.value, maxHeight.value * (2f / 3f))
                Box(
                    modifier = Modifier
                        .width(boardWidth.dp)
                        .height((boardWidth * 1.5f).dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(DeskWood)
                        .border(3.dp, Inks.Wood, RoundedCornerShape(14.dp))
                        .pointerInput(canAim, match, boardWidth, turnNow) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    if (canAim) {
                                        val scale = size.width / Board.WIDTH
                                        dragStart = Vec2(offset.x / scale, offset.y / scale)
                                        dragNow = dragStart ?: Vec2.ZERO
                                    }
                                },
                                onDrag = { change, _ ->
                                    if (canAim && dragStart != null) {
                                        val scale = size.width / Board.WIDTH
                                        dragNow = Vec2(change.position.x / scale, change.position.y / scale)
                                        change.consume()
                                    }
                                },
                                onDragEnd = {
                                    val start = dragStart
                                    if (canAim && start != null) {
                                        val vec = start - dragNow
                                        val velocity = Flick.velocityFromDrag(vec, attacker.type)
                                        if (velocity.length() > 40f) {
                                            match.flick(velocity)
                                            if (haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            moveCount++
                                            syncHud()
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
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        frame // 다시 그리기 구독
                        drawDesk()
                        drawErasers(match, turnNow)
                        dragStart?.let { start ->
                            drawAim(start, dragNow, attacker, prediction)
                        }
                    }

                    if (countFlash != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .scale(countScale),
                        ) {
                            CountBadge(match)
                        }
                    }

                    val bannerText = banner
                    if (bannerText != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Inks.Wood.copy(alpha = 0.88f))
                                .padding(horizontal = 22.dp, vertical = 14.dp)
                        ) {
                            Text(
                                bannerText,
                                color = Inks.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            BattleFooter(
                mode = mode,
                canAim = canAim,
                turnNow = turnNow,
                twoPlayer = twoPlayer,
                power = power,
                lastOutcome = lastOutcome,
            )
        }

        if (winnerNow != null) {
            ResultOverlay(
                mode = mode,
                match = match,
                winner = winnerNow,
                onRetry = {
                    session++
                    lastOutcome = null
                },
                onExit = onExit,
            )
        }
    }
}

@Composable
private fun BattleHud(
    mode: EraserMode,
    match: DuelMatch,
    turnNow: Side,
    twoPlayer: Boolean,
    onExit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Inks.Paper)
                .border(3.dp, Inks.Wood, CircleShape)
                .clickable(onClick = onExit),
            contentAlignment = Alignment.Center,
        ) {
            Text("←", fontSize = 19.sp, fontWeight = FontWeight.Black, color = Inks.Wood)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = mode.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = Inks.Wood,
            )
            Text(
                text = when (match) {
                    is WrestlingMatch -> "턴제 3카운트 · ${match.turnNumber}턴"
                    is AlkkaegiMatch -> "${match.round}라운드 · 3판 2선승"
                    else -> ""
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Inks.SoftInk,
            )
        }

        PaperCard(padding = 8.dp, corner = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (match) {
                        is WrestlingMatch -> "${match.playerCounts} : ${match.aiCounts}"
                        is AlkkaegiMatch -> "${match.playerRounds} : ${match.aiRounds}"
                        else -> ""
                    },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Inks.Ink,
                )
                Spacer(Modifier.width(8.dp))
                SideDot(if (turnNow == Side.PLAYER) PlayerBodyColor else AiBodyColor)
            }
        }
    }
}

@Composable
private fun SideDot(color: Color) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun BattleFooter(
    mode: EraserMode,
    canAim: Boolean,
    turnNow: Side,
    twoPlayer: Boolean,
    power: Float,
    lastOutcome: TurnOutcome?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = when {
                canAim && turnNow == Side.PLAYER -> "내 차례 — 내 지우개를 뒤로 당겼다 놓으세요"
                canAim && twoPlayer -> "2P 차례 — 기기를 넘겨받고 당겼다 놓으세요"
                else -> "상대가 겨냥하는 중…"
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            color = Inks.Wood,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Inks.Wood.copy(alpha = 0.18f))
                .border(2.dp, Inks.Wood, RoundedCornerShape(8.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(power.coerceIn(0f, 1f))
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Inks.Green, Inks.Gold, Inks.Red)
                        )
                    )
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = when {
                lastOutcome == TurnOutcome.PIN -> "올라탔다! 카운트!"
                lastOutcome == TurnOutcome.FELL -> "지우개가 책상 밖으로… 턴을 넘깁니다"
                mode == EraserMode.WRESTLING ->
                    "너무 약하면 흐르고, 너무 세면 넘어갑니다. 겹쳐 멈추면 1카운트"
                else -> "상대를 책상 밖으로 밀어내세요. 내 지우개가 먼저 나가면 라운드 패배"
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Inks.SoftInk,
        )
    }
}

@Composable
private fun CountBadge(match: DuelMatch) {
    val wrestling = match as? WrestlingMatch ?: return
    val side = wrestling.countSide
    val count = when (side) {
        Side.PLAYER -> wrestling.playerCounts + 1
        Side.AI -> wrestling.aiCounts + 1
        null -> 1
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (side == Side.PLAYER) PlayerBodyColor else AiBodyColor)
                .border(4.dp, Inks.White, RoundedCornerShape(20.dp))
                .padding(horizontal = 26.dp, vertical = 12.dp)
        ) {
            Text(
                text = "$count COUNT!",
                color = Inks.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Inks.Wood.copy(alpha = 0.8f))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (side == Side.PLAYER) "내 지우개가 올라탔다!" else "상대가 올라탔다…",
                color = Inks.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ResultOverlay(
    mode: EraserMode,
    match: DuelMatch,
    winner: Side?,
    onRetry: () -> Unit,
    onExit: () -> Unit,
) {
    val win = winner == Side.PLAYER
    val scaleAnim = remember { Animatable(0.7f) }
    LaunchedEffect(Unit) { scaleAnim.animateTo(1f, spring(dampingRatio = 0.5f)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Inks.Wood.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        PaperCard(
            modifier = Modifier
                .padding(28.dp)
                .scale(scaleAnim.value),
            padding = 20.dp,
            color = Color(0xFFFFF2D8),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (win) "🏆" else "😵", fontSize = 46.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (win) "승리!" else "패배…",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    color = if (win) Inks.Green else Inks.Red,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = when (match) {
                        is WrestlingMatch -> "${match.playerCounts} : ${match.aiCounts} 카운트"
                        is AlkkaegiMatch -> "${match.playerRounds} : ${match.aiRounds} 라운드"
                        else -> ""
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Inks.Ink,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (win) "동네 명성 +12 (데모: 실제 반영 없음)" else "다음 판에 다시 도전해 보세요",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Inks.SoftInk,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                FunButton(
                    text = "다시 하기",
                    modifier = Modifier.fillMaxWidth(),
                    color = Inks.Green,
                ) { onRetry() }
                Spacer(Modifier.height(8.dp))
                FunButton(
                    text = "나가기",
                    modifier = Modifier.fillMaxWidth(),
                    color = Inks.WoodLight,
                ) { onExit() }
            }
        }
    }
}

// ── 캔버스 그리기 ─────────────────────────────────────────────

private fun DrawScope.drawDesk() {
    val scale = size.width / Board.WIDTH
    val frameWidth = 12f * scale
    drawRect(DeskWood)
    drawRect(
        color = DeskSurface,
        topLeft = Offset(frameWidth, frameWidth),
        size = Size(size.width - frameWidth * 2, size.height - frameWidth * 2),
    )
    // 연필로 그린 격자 느낌
    val grid = 100f * scale
    var x = grid
    while (x < size.width) {
        drawLine(
            color = Inks.Wood.copy(alpha = 0.08f),
            start = Offset(x, frameWidth),
            end = Offset(x, size.height - frameWidth),
            strokeWidth = 1.2f,
        )
        x += grid
    }
    var y = grid
    while (y < size.height) {
        drawLine(
            color = Inks.Wood.copy(alpha = 0.08f),
            start = Offset(frameWidth, y),
            end = Offset(size.width - frameWidth, y),
            strokeWidth = 1.2f,
        )
        y += grid
    }
    // 중앙 표시
    drawCircle(
        color = Inks.Wood.copy(alpha = 0.10f),
        radius = 60f * scale,
        center = Offset(size.width / 2f, size.height / 2f),
        style = Stroke(width = 2f),
    )
    drawLine(
        color = Inks.Red.copy(alpha = 0.25f),
        start = Offset(size.width / 2f - 18f * scale, size.height / 2f),
        end = Offset(size.width / 2f + 18f * scale, size.height / 2f),
        strokeWidth = 2f,
    )
    drawLine(
        color = Inks.Red.copy(alpha = 0.25f),
        start = Offset(size.width / 2f, size.height / 2f - 18f * scale),
        end = Offset(size.width / 2f, size.height / 2f + 18f * scale),
        strokeWidth = 2f,
    )
}

private fun DrawScope.drawErasers(match: DuelMatch, turnNow: Side) {
    val player = match.world.player
    val ai = match.world.opponent
    val attacker = match.world.bodyOf(turnNow)
    val defender = match.world.bodyOf(turnNow.other())
    val order = if (attacker.climbing) listOf(defender, attacker) else listOf(player, ai)
    for (body in order) {
        if (body.fallen) continue
        drawOneEraser(
            body = body,
            color = if (body.side == Side.PLAYER) PlayerBodyColor else AiBodyColor,
            active = body === attacker && match.phase == MatchPhase.AIMING,
        )
    }
}

private fun DrawScope.drawOneEraser(body: EraserBody, color: Color, active: Boolean) {
    val scale = size.width / Board.WIDTH
    val cx = body.pos.x * scale
    val cy = body.pos.y * scale
    val w = body.radius * 2f * scale
    val h = body.radius * 1.56f * scale
    val corner = CornerRadius(h * 0.20f)

    if (active) {
        drawCircle(
            color = Inks.White.copy(alpha = 0.75f),
            radius = body.radius * scale + 9f,
            center = Offset(cx, cy),
            style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 9f))),
        )
    }

    rotate(degrees = body.spin, pivot = Offset(cx, cy)) {
        drawRoundRect(
            color = Color(0x33000000),
            topLeft = Offset(cx - w / 2f + 4f, cy - h / 2f + 7f),
            size = Size(w, h),
            cornerRadius = corner,
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(cx - w / 2f, cy - h / 2f),
            size = Size(w, h),
            cornerRadius = corner,
        )
        drawRoundRect(
            color = Color(0xFFFFF7E6),
            topLeft = Offset(cx - w / 2f + w * 0.07f, cy - h / 2f + h * 0.10f),
            size = Size(w * 0.86f, h * 0.32f),
            cornerRadius = CornerRadius(h * 0.10f),
        )
        drawRoundRect(
            color = Inks.Wood,
            topLeft = Offset(cx - w / 2f, cy - h / 2f),
            size = Size(w, h),
            cornerRadius = corner,
            style = Stroke(width = 3f),
        )
    }
}

private fun DrawScope.drawAim(start: Vec2, now: Vec2, attacker: EraserBody, prediction: Vec2?) {
    val scale = size.width / Board.WIDTH
    val vec = start - now
    if (vec.length() < 6f) return
    val power = (vec.length() / Board.MAX_DRAG).coerceIn(0f, 1f)
    val dir = vec.normalized()
    val from = Offset(attacker.pos.x * scale, attacker.pos.y * scale)
    val length = (min(vec.length(), Board.MAX_DRAG)) * scale
    val to = Offset(from.x + dir.x * length, from.y + dir.y * length)

    drawLine(
        color = Inks.Red.copy(alpha = 0.85f),
        start = from,
        end = to,
        strokeWidth = 6f,
        cap = androidx.compose.ui.graphics.StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f)),
    )

    // 화살촉
    val head = 20f
    val left = Offset(
        to.x - dir.x * head - dir.y * head * 0.55f,
        to.y - dir.y * head + dir.x * head * 0.55f,
    )
    val right = Offset(
        to.x - dir.x * head + dir.y * head * 0.55f,
        to.y - dir.y * head - dir.x * head * 0.55f,
    )
    val path = Path().apply {
        moveTo(to.x, to.y)
        lineTo(left.x, left.y)
        lineTo(right.x, right.y)
        close()
    }
    drawPath(path, Inks.Red)

    // 파워 게이지(발밑)
    val barW = 220f
    val barX = from.x - barW / 2f
    val barY = from.y + attacker.radius * scale + 18f
    drawRoundRect(
        color = Inks.Wood.copy(alpha = 0.35f),
        topLeft = Offset(barX, barY),
        size = Size(barW, 14f),
        cornerRadius = CornerRadius(7f),
    )
    drawRoundRect(
        color = when {
            power > 0.85f -> Inks.Red
            power > 0.55f -> Inks.Gold
            else -> Inks.Green
        },
        topLeft = Offset(barX, barY),
        size = Size(barW * power, 14f),
        cornerRadius = CornerRadius(7f),
    )

    if (prediction != null && prediction.x >= 0f) {
        val px = prediction.x * scale
        val py = prediction.y * scale
        drawCircle(
            color = Inks.Blue.copy(alpha = 0.85f),
            radius = attacker.radius * scale * 0.55f,
            center = Offset(px, py),
            style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
        )
    }
}
