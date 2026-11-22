package com.game.gametong.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.game.gametong.game.AlkkaegiMatch
import com.game.gametong.game.Board
import com.game.gametong.game.DeskSurface
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
import com.game.gametong.ui.components.BlockPose
import com.game.gametong.ui.components.FunButton
import com.game.gametong.ui.components.PaperCard
import com.game.gametong.ui.components.SelectChip
import com.game.gametong.ui.components.drawBlock
import com.game.gametong.ui.components.drawBlockShadow
import com.game.gametong.ui.components.drawTopFaceBand
import com.game.gametong.ui.components.topFacePoint
import com.game.gametong.ui.theme.Inks
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.roundToInt

private val PlayerBodyColor = Color(0xFF3D6FC0)
private val AiBodyColor = Color(0xFFCB4B40)

@Composable
fun EraserBattleScreen(
    mode: EraserMode,
    playerType: EraserType,
    aiType: EraserType,
    twoPlayer: Boolean,
    surface: DeskSurface,
    aimAssist: Boolean,
    haptics: Boolean,
    onExit: () -> Unit,
) {
    var session by remember { mutableIntStateOf(0) }
    val match: DuelMatch = remember(session, mode, playerType, aiType, surface) {
        when (mode) {
            EraserMode.WRESTLING -> WrestlingMatch(playerType, aiType, surface = surface)
            EraserMode.ALKKAEGI -> AlkkaegiMatch(playerType, aiType, surface = surface)
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
    var lastPinInfo by remember(match) { mutableStateOf<String?>(null) }

    var dragStart by remember(match) { mutableStateOf<Vec2?>(null) }
    var dragNow by remember(match) { mutableStateOf(Vec2.ZERO) }
    /** 내 지우개의 어느 지점을 때릴지(−1 왼끝 ~ 0 가운데 ~ 1 오른끝). */
    var strikeOffset by remember(match) { mutableStateOf(0f) }
    var touchOffset by remember(match) { mutableStateOf<Float?>(null) }

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
                        // 멈추면 놓인 면이 반듯해진다
                        match.world.player.snapTumble()
                        match.world.opponent.snapTumble()
                        when (match) {
                            is WrestlingMatch -> {
                                lastOutcome = match.settleFlick()
                                lastPinInfo = when (lastOutcome) {
                                    TurnOutcome.PIN -> {
                                        val side = match.countSide ?: Side.PLAYER
                                        val body = match.world.bodyOf(side)
                                        val opp = match.world.bodyOf(side.other())
                                        val overlap = match.world.overlapRatio(body, opp) * 100f
                                        "겹침 ${overlap.toInt()}% · 기울기 ${body.lean.roundToInt()}도로 걸쳐 앉았다"
                                    }
                                    TurnOutcome.SLIP ->
                                        "비스듬히 걸쳐만 있었다 — 상대가 빠져나왔다 (기울기 ${match.lastLean.roundToInt()}도)"
                                    TurnOutcome.FELL -> "링아웃! 지우개가 책상 밖으로 떨어졌다"
                                    else -> null
                                }
                                if (lastOutcome == TurnOutcome.SLIP) {
                                    banner = "빠져나왔다!"
                                }
                            }
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
                    // 알까기는 한 방에 판이 끝날 수 있어 AI 실력을 조금 낮춰 첫 판이 일방적이지 않게 한다.
                    val accuracy = if (match is AlkkaegiMatch) 0.62f else 0.82f
                    val velocity = match.aiVelocity(accuracy)
                    match.flick(velocity)
                    frame++
                    syncHud()
                }

                else -> break
            }
        }
    }

    LaunchedEffect(banner) {
        if (banner != null) {
            delay(1200)
            if (match.phase == MatchPhase.AIMING) banner = null
        }
    }

    val canAim = phaseNow == MatchPhase.AIMING && (turnNow == Side.PLAYER || twoPlayer)
    val attacker: EraserBody = match.world.bodyOf(turnNow)

    val dragVector: Vec2? = dragStart?.let { it - dragNow }
    val power = dragVector?.let { Flick.powerOf(it) } ?: 0f

    val dragKey: Vec2? = dragVector?.let {
        Vec2((it.x / 60f).roundToInt().toFloat(), (it.y / 60f).roundToInt().toFloat())
    }
    val prediction: Vec2? = remember(dragKey, canAim, aimAssist, match, strikeOffset) {
        val vec = dragVector
        if (aimAssist && canAim && vec != null && vec.length() > 40f) {
            val v = Flick.velocityFromDrag(vec, attacker.type)
            if (v.length() > 60f) EraserAi.predictRest(match.world, match.turn, v, strikeOffset) else null
        } else {
            null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Inks.Kraft, Inks.KraftDeep))),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            BattleHud(
                mode = mode,
                match = match,
                turnNow = turnNow,
                twoPlayer = twoPlayer,
                surface = surface,
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
                        .background(deskBase(surface))
                        .border(3.dp, Inks.Wood, RoundedCornerShape(14.dp))
                        .pointerInput(canAim, match, boardWidth, turnNow) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    if (canAim) {
                                        val scale = size.width / Board.WIDTH
                                        val start = Vec2(offset.x / scale, offset.y / scale)
                                        dragStart = start
                                        dragNow = start
                                        // 손가락이 지우개에 닿은 지점이 곧 때리는 지점이 된다.
                                        val body = match.world.bodyOf(match.turn)
                                        val dx = start.x - body.pos.x
                                        val dy = start.y - body.pos.y
                                        val reach = body.radius * 1.5f
                                        if (dx * dx + dy * dy <= reach * reach) {
                                            val off = (dx / body.radius).coerceIn(-1f, 1f)
                                            strikeOffset = (off * 10f).roundToInt() / 10f
                                            touchOffset = off
                                        } else {
                                            touchOffset = null
                                        }
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
                                            match.flick(velocity, strikeOffset)
                                            if (haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            moveCount++
                                            syncHud()
                                        }
                                    }
                                    dragStart = null
                                    dragNow = Vec2.ZERO
                                    touchOffset = null
                                },
                                onDragCancel = {
                                    dragStart = null
                                    dragNow = Vec2.ZERO
                                    touchOffset = null
                                },
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        frame
                        drawDesk(surface)
                        drawErasers(match, turnNow, strikeOffset, touchOffset)
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
                            CountBadge(match, lastPinInfo)
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
                strikeOffset = strikeOffset,
                onStrikeOffset = { strikeOffset = it },
                lastOutcome = lastOutcome,
                lastPinInfo = lastPinInfo,
                surface = surface,
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
                    lastPinInfo = null
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
    surface: DeskSurface,
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
                    is WrestlingMatch -> "턴제 3카운트 · ${match.turnNumber}턴 · ${surface.label}"
                    is AlkkaegiMatch -> "${match.round}라운드 · 3판 2선승 · ${surface.label}"
                    else -> ""
                },
                fontSize = 10.sp,
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
    strikeOffset: Float,
    onStrikeOffset: (Float) -> Unit,
    lastOutcome: TurnOutcome?,
    lastPinInfo: String?,
    surface: DeskSurface,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
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
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "때리는 지점",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Inks.SoftInk,
                modifier = Modifier.width(62.dp),
            )
            SelectChip("왼끝", strikeOffset < -0.25f, Inks.Purple, Modifier.weight(1f)) { onStrikeOffset(-0.8f) }
            Spacer(Modifier.width(4.dp))
            SelectChip("가운데", abs(strikeOffset) <= 0.25f, Inks.Purple, Modifier.weight(1f)) { onStrikeOffset(0f) }
            Spacer(Modifier.width(4.dp))
            SelectChip("오른끝", strikeOffset > 0.25f, Inks.Purple, Modifier.weight(1f)) { onStrikeOffset(0.8f) }
        }
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
        Spacer(Modifier.height(5.dp))
        Text(
            text = when {
                lastOutcome == TurnOutcome.PIN -> lastPinInfo ?: "걸쳐 앉았다! 카운트!"
                lastOutcome == TurnOutcome.SLIP -> lastPinInfo ?: "비스듬히 걸쳐만 있었다 — 빠져나왔다"
                lastOutcome == TurnOutcome.FELL -> "링아웃 — 지우개가 책상 밖으로 떨어졌다"
                mode == EraserMode.WRESTLING ->
                    "${surface.hint} · 끝을 때리면 회전이 붙는다. 걸쳐 앉으면 1카운트"
                else ->
                    "${surface.hint} · 끝을 때리면 휘어 들어간다. 상대를 책상 밖으로!"
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Inks.SoftInk,
        )
    }
}

@Composable
private fun CountBadge(match: DuelMatch, pinInfo: String?) {
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
                text = (if (side == Side.PLAYER) "내 지우개가 걸쳐 올라갔다" else "상대가 걸쳐 올라갔다") +
                    (pinInfo?.let { " · $it" } ?: ""),
                color = Inks.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
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

private fun deskBase(surface: DeskSurface): Color = when (surface) {
    DeskSurface.COATED -> Color(0xFFEFE6D2)
    DeskSurface.WOOD -> Color(0xFFE9D3A8)
    DeskSurface.PLYWOOD -> Color(0xFFD9C093)
    DeskSurface.DUSTY -> Color(0xFFD6C8AC)
}

private fun DrawScope.drawDesk(surface: DeskSurface) {
    val scale = size.width / Board.WIDTH
    val frameWidth = 12f * scale
    drawRect(Inks.Wood)
    drawRect(
        color = deskBase(surface),
        topLeft = Offset(frameWidth, frameWidth),
        size = Size(size.width - frameWidth * 2, size.height - frameWidth * 2),
    )
    // 연필로 그린 격자 느낌
    val grid = 100f * scale
    val gridAlpha = if (surface == DeskSurface.DUSTY) 0.05f else 0.08f
    var x = grid
    while (x < size.width) {
        drawLine(
            color = Inks.Wood.copy(alpha = gridAlpha),
            start = Offset(x, frameWidth),
            end = Offset(x, size.height - frameWidth),
            strokeWidth = 1.2f,
        )
        x += grid
    }
    var y = grid
    while (y < size.height) {
        drawLine(
            color = Inks.Wood.copy(alpha = gridAlpha),
            start = Offset(frameWidth, y),
            end = Offset(size.width - frameWidth, y),
            strokeWidth = 1.2f,
        )
        y += grid
    }

    when (surface) {
        DeskSurface.COATED -> {
            // 반짝이는 코팅: 비스듬한 빛무리 + 옅은 반사
            drawRect(
                brush = Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.35f), Color.Transparent, Color.White.copy(alpha = 0.18f)),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height),
                ),
                topLeft = Offset(frameWidth, frameWidth),
                size = Size(size.width - frameWidth * 2, size.height - frameWidth * 2),
            )
        }
        DeskSurface.WOOD -> {
            // 나뭇결
            var line = 0
            while (line < 22) {
                val ly = (line * 47f + 18f) * scale
                drawLine(
                    color = Color(0xFF8A6234).copy(alpha = 0.20f),
                    start = Offset(frameWidth, ly),
                    end = Offset(size.width - frameWidth, ly + 6f * scale),
                    strokeWidth = (1f + (line % 3)).toFloat(),
                )
                line++
            }
        }
        DeskSurface.PLYWOOD -> {
            // 거친 결 + 긁힌 자국
            var i = 0
            while (i < 120) {
                val px = ((i * 79) % 1000) * scale
                val py = ((i * 41) % 1500) * scale
                drawLine(
                    color = Color(0xFF7A5A30).copy(alpha = 0.18f),
                    start = Offset(px, py),
                    end = Offset(px + (18f + i % 30) * scale, py + (6f - i % 12) * scale),
                    strokeWidth = 1.4f,
                )
                i++
            }
        }
        DeskSurface.DUSTY -> {
            var i = 0
            while (i < 260) {
                val px = ((i * 61) % 1000) * scale
                val py = ((i * 97) % 1500) * scale
                drawCircle(
                    color = Color(0xFFB9A88A).copy(alpha = 0.30f),
                    radius = (1f + (i % 3)) * scale,
                    center = Offset(px, py),
                )
                i++
            }
        }
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

/** 지우개 블록의 화면 자세. 이동 거리만큼 넘어가며 구르고, 걸쳐 올라가면 그 높이만큼 뜬다. */
private fun eraserBlockPose(body: EraserBody, scale: Float): BlockPose {
    val width = body.blockWidth * scale
    val height = body.blockHeight * scale
    val axisDeg = Math.toDegrees(
        atan2(body.tumbleAxis.y.toDouble(), body.tumbleAxis.x.toDouble())
    ).toFloat()
    return BlockPose(
        cx = body.pos.x * scale,
        cy = body.pos.y * scale,
        width = width,
        depth = body.blockDepth * scale,
        height = height,
        yawDeg = body.spin,
        tiltDeg = body.tumble,
        tiltAxisDeg = axisDeg,
        rollDeg = body.lean,
        lift = body.z * scale,
    )
}

private fun DrawScope.drawErasers(
    match: DuelMatch,
    turnNow: Side,
    strikeOffset: Float,
    touchOffset: Float?,
) {
    val attacker = match.world.bodyOf(turnNow)
    val defender = match.world.bodyOf(turnNow.other())
    // 걸쳐 올라간 지우개를 위에 그린다
    val order = if (attacker.perched || attacker.z >= defender.z) {
        listOf(defender, attacker)
    } else {
        listOf(attacker, defender)
    }
    for (body in order) {
        if (body.fallen) continue
        drawOneEraser(
            body = body,
            color = if (body.side == Side.PLAYER) PlayerBodyColor else AiBodyColor,
            active = body === attacker && (match.phase == MatchPhase.AIMING || match.phase == MatchPhase.MOVING),
            strikeOffset = if (body === attacker && match.phase == MatchPhase.AIMING) strikeOffset else null,
            touchOffset = if (body === attacker) touchOffset else null,
        )
    }
}

private fun DrawScope.drawOneEraser(
    body: EraserBody,
    color: Color,
    active: Boolean,
    strikeOffset: Float?,
    touchOffset: Float?,
) {
    val scale = size.width / Board.WIDTH
    val pose = eraserBlockPose(body, scale)
    val cx = pose.cx
    val cy = pose.cy

    if (active) {
        drawCircle(
            color = Inks.White.copy(alpha = 0.75f),
            radius = body.radius * scale + 9f,
            center = Offset(cx, cy),
            style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 9f))),
        )
    }

    // 바닥 그림자를 먼저 깔고 그 위에 블록을 그린다(걸쳐 있을 때는 상대 위에 떨어진다).
    drawBlockShadow(pose, alpha = if (body.perched) 0.26f else 0.20f)
    drawBlock(
        pose = pose,
        topFaceColor = color.copy(alpha = 0.96f),
        sideColor = color.copy(alpha = 0.72f),
        borderColor = Inks.Wood,
        borderWidth = 3f,
        shadow = false,
    )
    // 종이 라벨(윗면 띠) + 글자 느낌의 얇은 선
    drawTopFaceBand(pose, -0.26f, 0.26f, Color(0xFFFFF7E6))
    drawTopFaceBand(pose, 0.26f, 0.30f, color.copy(alpha = 0.55f))
    val lineColor = Inks.SoftInk.copy(alpha = 0.55f)
    for (v in listOf(-0.14f, -0.02f, 0.10f)) {
        val a = topFacePoint(pose, -0.26f, v)
        val b = topFacePoint(pose, 0.18f, v)
        drawLine(lineColor, a, b, strokeWidth = 2.4f)
    }

    // 때리는 지점 표시(내 지우개에 잠깐 나타나는 표적)
    val marker = touchOffset ?: strikeOffset
    if (marker != null && active) {
        val p = topFacePoint(pose, marker.coerceIn(-0.48f, 0.48f), 0.34f)
        drawCircle(
            color = if (touchOffset != null) Inks.Red else Inks.Gold,
            radius = 8f,
            center = p,
            style = Stroke(width = 3f),
        )
        drawCircle(
            color = Inks.White.copy(alpha = 0.8f),
            radius = 2.5f,
            center = p,
        )
    }

    // 걸쳐 올라간 지우개에 작은 표시
    if (body.perched) {
        val p = topFacePoint(pose, 0f, -0.36f)
        drawCircle(Inks.Gold, radius = 5f, center = p)
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

    val head = 20f
    val left = Offset(
        to.x - dir.x * head - dir.y * head * 0.55f,
        to.y - dir.y * head + dir.x * head * 0.55f,
    )
    val right = Offset(
        to.x - dir.x * head + dir.y * head * 0.55f,
        to.y - dir.y * head - dir.x * head * 0.55f,
    )
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(to.x, to.y)
        lineTo(left.x, left.y)
        lineTo(right.x, right.y)
        close()
    }
    drawPath(path, Inks.Red)

    val barW = 220f
    val barX = from.x - barW / 2f
    val barY = from.y + attacker.radius * scale + 18f
    drawRoundRect(
        color = Inks.Wood.copy(alpha = 0.35f),
        topLeft = Offset(barX, barY),
        size = Size(barW, 14f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(7f),
    )
    drawRoundRect(
        color = when {
            power > 0.85f -> Inks.Red
            power > 0.55f -> Inks.Gold
            else -> Inks.Green
        },
        topLeft = Offset(barX, barY),
        size = Size(barW * power, 14f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(7f),
    )

    if (prediction != null && prediction.x >= 0f) {
        drawCircle(
            color = Inks.Blue.copy(alpha = 0.85f),
            radius = attacker.radius * scale * 0.55f,
            center = Offset(prediction.x * scale, prediction.y * scale),
            style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
        )
    }
}
