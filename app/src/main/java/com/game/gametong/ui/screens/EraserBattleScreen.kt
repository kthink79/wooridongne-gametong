package com.game.gametong.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.game.gametong.game.physics3d.FixedTimestep
import com.game.gametong.game.physics3d.ParticleKind
import com.game.gametong.game.physics3d.ParticleSystem
import com.game.gametong.render.EraserAim
import com.game.gametong.render.EraserScene3D
import com.game.gametong.render.Game3DView
import com.game.gametong.render.ParticleKindView
import com.game.gametong.render.ParticleView
import com.game.gametong.render.SceneChannel
import com.game.gametong.ui.EraserMode
import com.game.gametong.ui.components.BarHint
import com.game.gametong.ui.components.BarLabel
import com.game.gametong.ui.components.PaperCard
import com.game.gametong.ui.components.PowerMeter
import com.game.gametong.ui.components.ScreenHeader
import com.game.gametong.ui.components.WalnutBar
import com.game.gametong.ui.components.WoodKnob
import com.game.gametong.ui.theme.Inks
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private val PlayerBodyColor = Color(0xFFC7322A)
private val AiBodyColor = Color(0xFF1F3F9E)

/** 3D 무대의 화면 비율(세로 2:3). */
private const val BOARD_ASPECT = 2f / 3f

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

    // ── 3D 렌더링·물리 자원 ──
    val channel = remember(match) { SceneChannel() }
    val particles = remember(match) { ParticleSystem(capacity = 220) }
    val fixed = remember(match) { FixedTimestep(step = 1f / 120f, maxSteps = 6) }

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

    /** 충돌한 자리(두 지우개 사이)에 가루를 뿌린다. */
    fun burstAtContact(impact: Float) {
        if (impact <= 0f) return
        val world = match.world
        val a = world.player.pos
        val b = world.opponent.pos
        val mx = (a.x + b.x) * 0.5f
        val my = (a.y + b.y) * 0.5f
        particles.spawnCrumbs(mx, my, z = 18f, impact = impact, count = (4 + impact / 160f).toInt())
        if (impact > 260f) particles.spawnDust(mx, my, z = 4f, power = (impact / 700f), count = 6)
    }

    // 물리: 고정 타임스텝(120Hz)으로 계산한다. 화면 주사율과 무관하게 같은 결과가 나온다.
    LaunchedEffect(match, moveCount) {
        var lastNanos = 0L
        while (true) {
            when {
                match.phase == MatchPhase.MOVING -> {
                    val now = withFrameNanos { it }
                    val dt = if (lastNanos == 0L) {
                        1f / 60f
                    } else {
                        ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
                    }
                    lastNanos = now
                    fixed.advance(dt) { stepDt ->
                        match.world.step(stepDt)
                        particles.update(stepDt)
                        burstAtContact(match.world.lastStepImpact)
                    }
                    frame++
                    if (match.world.settled) {
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
                        lastNanos = 0L
                        fixed.reset()
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

    // 그리기: 매 프레임 장면을 새로 만들어 GL 뷰에 넘긴다.
    LaunchedEffect(match) {
        var startNanos = 0L
        while (true) {
            val now = withFrameNanos { it }
            if (startNanos == 0L) startNanos = now
            val seconds = (now - startNanos) / 1_000_000_000f
            val attackerBody = match.world.bodyOf(match.turn)
            val aim = dragStart?.let { start ->
                val vec = start - dragNow
                if (vec.length() < 8f) {
                    null
                } else {
                    val predicted = if (aimAssist && vec.length() > 40f) {
                        val v = Flick.velocityFromDrag(vec, attackerBody.type)
                        if (v.length() > 60f) {
                            EraserAi.predictRest(match.world, match.turn, v, strikeOffset)
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                    EraserAim(
                        from = attackerBody.pos,
                        to = attackerBody.pos + vec,
                        power = Flick.powerOf(vec),
                        predicted = predicted,
                    )
                }
            }
            channel.scene = EraserScene3D.build(
                bodies = match.world.bodies,
                activeSide = match.turn,
                surface = surface,
                aim = aim,
                particles = particles.particles.map {
                    ParticleView(
                        x = it.x,
                        y = it.y,
                        z = it.z,
                        size = it.size,
                        alpha = it.alpha,
                        kind = when (it.kind) {
                            ParticleKind.CRUMB -> ParticleKindView.CRUMB
                            ParticleKind.DUST -> ParticleKindView.DUST
                            ParticleKind.CHALK -> ParticleKindView.CHALK
                            ParticleKind.SPARK -> ParticleKindView.SPARK
                        },
                    )
                },
                aspect = BOARD_ASPECT,
                timeSeconds = seconds,
                perchedSide = match.world.bodies.firstOrNull { it.perched }?.side,
            )
        }
    }

    val canAim = phaseNow == MatchPhase.AIMING && (turnNow == Side.PLAYER || twoPlayer)
    val attacker: EraserBody = match.world.bodyOf(turnNow)

    val dragVector: Vec2? = dragStart?.let { it - dragNow }
    val power = dragVector?.let { Flick.powerOf(it) } ?: 0f

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
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                val boardWidth = min(maxWidth.value, maxHeight.value * BOARD_ASPECT)
                Box(
                    modifier = Modifier
                        .width(boardWidth.dp)
                        .height((boardWidth / BOARD_ASPECT).dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF2A1C10))
                        .border(3.dp, Inks.Wood, RoundedCornerShape(16.dp))
                        .pointerInput(canAim, match, turnNow) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    if (canAim) {
                                        val w = size.width.toFloat().coerceAtLeast(1f)
                                        val h = size.height.toFloat().coerceAtLeast(1f)
                                        val start = EraserScene3D.fieldPoint(
                                            offset.x / w,
                                            offset.y / h,
                                            BOARD_ASPECT,
                                        )
                                        if (start != null) {
                                            dragStart = start
                                            dragNow = start
                                            // 손가락이 지우개에 닿은 지점이 곧 때리는 지점이 된다.
                                            val body = match.world.bodyOf(match.turn)
                                            val dx = start.x - body.pos.x
                                            val dy = start.y - body.pos.y
                                            val reach = body.radius * 1.8f
                                            if (dx * dx + dy * dy <= reach * reach) {
                                                val off = (dx / body.radius).coerceIn(-1f, 1f)
                                                strikeOffset = (off * 10f).roundToInt() / 10f
                                                touchOffset = off
                                            } else {
                                                touchOffset = null
                                            }
                                        }
                                    }
                                },
                                onDrag = { change, _ ->
                                    if (canAim && dragStart != null) {
                                        val w = size.width.toFloat().coerceAtLeast(1f)
                                        val h = size.height.toFloat().coerceAtLeast(1f)
                                        val point = EraserScene3D.fieldPoint(
                                            change.position.x / w,
                                            change.position.y / h,
                                            BOARD_ASPECT,
                                        )
                                        if (point != null) dragNow = point
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
                        },
                ) {
                    Game3DView(channel = channel, modifier = Modifier.fillMaxSize())

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
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Inks.Paper)
                .border(3.dp, Inks.Wood, CircleShape)
                .clickable(onClick = onExit),
            contentAlignment = Alignment.Center,
        ) {
            Text("←", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Inks.Wood)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = mode.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = Inks.Wood,
            )
            Text(
                text = when (match) {
                    is WrestlingMatch -> "턴제 3카운트 · ${match.turnNumber}턴 · ${surface.label}"
                    is AlkkaegiMatch -> "${match.round}라운드 · 3판 2선승 · ${surface.label}"
                    else -> ""
                },
                fontSize = 12.sp,
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
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Black,
                    color = Inks.Ink,
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(if (turnNow == Side.PLAYER) PlayerBodyColor else AiBodyColor)
                )
            }
        }
    }
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
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = when {
                canAim && turnNow == Side.PLAYER -> "내 차례 — 지우개를 뒤로 당겼다 놓으세요"
                canAim && twoPlayer -> "2P 차례 — 기기를 넘겨받고 당겼다 놓으세요"
                else -> "상대가 겨냥하는 중…"
            },
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            color = Inks.Wood,
        )
        Spacer(Modifier.height(6.dp))
        WalnutBar(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                PowerMeter(
                    value = power,
                    modifier = Modifier.fillMaxWidth(),
                    caption = if (power > 0.01f) "힘 ${(power * 100).toInt()}%" else "당길수록 세게 날아갑니다",
                )
            }
            WoodKnob("왼끝", strikeOffset < -0.25f) { onStrikeOffset(-0.8f) }
            WoodKnob("가운데", abs(strikeOffset) <= 0.25f) { onStrikeOffset(0f) }
            WoodKnob("오른끝", strikeOffset > 0.25f) { onStrikeOffset(0.8f) }
        }
        Spacer(Modifier.height(5.dp))
        BarLabel(
            when {
                lastOutcome == TurnOutcome.PIN -> lastPinInfo ?: "걸쳐 앉았다! 카운트!"
                lastOutcome == TurnOutcome.SLIP -> lastPinInfo ?: "비스듬히 걸쳐만 있었다 — 빠져나왔다"
                lastOutcome == TurnOutcome.FELL -> "링아웃 — 지우개가 책상 밖으로 떨어졌다"
                mode == EraserMode.WRESTLING ->
                    "${surface.hint} · 걸쳐 앉으면 1카운트"
                else ->
                    "${surface.hint} · 상대를 책상 밖으로!"
            },
            modifier = Modifier.fillMaxWidth(),
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
                fontSize = 12.sp,
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
                com.game.gametong.ui.components.FunButton(
                    text = "다시 하기",
                    modifier = Modifier.fillMaxWidth(),
                    color = Inks.Green,
                ) { onRetry() }
                Spacer(Modifier.height(8.dp))
                com.game.gametong.ui.components.FunButton(
                    text = "나가기",
                    modifier = Modifier.fillMaxWidth(),
                    color = Inks.WoodLight,
                ) { onExit() }
            }
        }
    }
}
