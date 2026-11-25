package com.game.gametong.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.game.gametong.game.Side
import com.game.gametong.game.SquareStrikeResult
import com.game.gametong.game.SquareTtakjiEngine
import com.game.gametong.game.SquareTtakjiFlight
import com.game.gametong.game.SquareTtakjiMatch
import com.game.gametong.game.StrikeTarget
import com.game.gametong.game.TtakjiMaterial
import com.game.gametong.game.TtakjiSpec
import com.game.gametong.game.TtakjiSurface
import com.game.gametong.game.Vec2
import com.game.gametong.game.physics3d.ParticleSystem
import com.game.gametong.render.Game3DView
import com.game.gametong.render.GroundAim
import com.game.gametong.render.ParticleKindView
import com.game.gametong.render.ParticleView
import com.game.gametong.render.SceneChannel
import com.game.gametong.render.TtakjiScene3D
import com.game.gametong.ui.components.ArcMeter
import com.game.gametong.ui.components.BarChip
import com.game.gametong.ui.components.FunButton
import com.game.gametong.ui.components.PaperCard
import com.game.gametong.ui.components.PowerMeter
import com.game.gametong.ui.components.ScorePill
import com.game.gametong.ui.components.ScreenHeader
import com.game.gametong.ui.components.SelectChip
import com.game.gametong.ui.components.StatBar
import com.game.gametong.ui.components.TurnBanner
import com.game.gametong.ui.components.WalnutBar
import com.game.gametong.ui.components.WoodKnob
import com.game.gametong.ui.theme.Inks
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/** 화면 비율을 모를 때 쓰는 기본값(세로 2:3). 실제로는 놀이판 상자 비율을 따라간다. */
private const val ASPECT = 2f / 3f

/** 오른쪽 세로 아이콘 메뉴가 여는 조작 패널. */
private enum class TtakjiPanel { STRIKE, TARGET, POWER, INFO }

/** 한 수의 3D 연출 상태. */
private data class FlightView(
    val attacker: Side,
    val flight: SquareTtakjiFlight,
)

/**
 * 네모 딱지치기 경기 화면.
 *
 * 운동장 시멘트 바닥을 3D로 그린다. **놀이판이 화면을 최대한 채우도록** 상단 요약을 한 줄로 줄이고,
 * 하단 조작판을 없애 **오른쪽 세로 아이콘 메뉴**로 옮겼다. 아이콘을 누르면 놀이판 위에 작은 패널이 열린다.
 * 끌어서 조준하거나, 패널에서 타격 위치·조준 지점·파워를 골라 친다.
 */
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
    val scope = rememberCoroutineScope()
    // 빠른 연타로 한 수가 두 번 나가지 않게 하는 잠금
    val gate = remember(matchKey) { java.util.concurrent.atomic.AtomicBoolean(false) }
    var tick by remember { mutableIntStateOf(0) }
    var strikePoint by remember { mutableStateOf(0.9f) }
    var target by remember { mutableStateOf(StrikeTarget.AUTO) }
    var aimDeg by remember { mutableStateOf(0f) }
    var power by remember { mutableStateOf(0.8f) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragNow by remember { mutableStateOf<Offset?>(null) }
    /** 끌어온 방향을 3D 바닥에서 계산한 조준선. */
    var aimFrom by remember { mutableStateOf<Vec2?>(null) }
    var aimDir by remember { mutableStateOf(Vec2.ZERO) }
    var lastResult by remember { mutableStateOf<SquareStrikeResult?>(null) }
    var lastActor by remember { mutableStateOf<Side?>(null) }
    var thinking by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf<FlightView?>(null) }
    /** 오른쪽 아이콘 메뉴에서 연 패널(없으면 null). */
    var panel by remember { mutableStateOf<TtakjiPanel?>(null) }
    /** 지금 놀이판 상자의 가로/세로 비율 — 카메라와 터치 좌표가 같은 값을 쓴다. */
    var boardAspect by remember { mutableStateOf(ASPECT) }

    val channel = remember(match) { SceneChannel() }
    val particles = remember(match) { ParticleSystem(capacity = 200) }
    // 딱지 연출도 고정 타임스텝으로 돌린다(화면 주사율과 무관하게 같은 결론).
    val fixed = remember(match) { com.game.gametong.game.physics3d.FixedTimestep(1f / 120f, maxSteps = 6) }

    val playerTurn = match.turn == Side.PLAYER
    val canStrike = !match.finished && !playing && !thinking && (playerTurn || twoPlayer)

    /** 한 수를 계산해 3D 연출을 끝까지 돌린다. */
    suspend fun playFlight(result: SquareStrikeResult, actor: Side) {
        playing = true
        val f = SquareTtakjiFlight(
            result = result,
            attackerHome = match.homeOf(actor),
            defenderHome = match.homeOf(actor.other()),
            attackerSpec = match.specOf(actor),
            defenderSpec = match.specOf(actor.other()),
            surface = match.surface,
            forwardSign = if (actor == Side.PLAYER) -1f else 1f,
        )
        view = FlightView(actor, f)
        var last = withFrameNanos { it }
        var dustClock = 0f
        fixed.reset()
        while (!f.isFinished) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
            last = now
            fixed.advance(dt) { step -> f.advance(step) }
            dustClock += dt
            if (dustClock > 0.05f) {
                dustClock = 0f
                if (f.impactHappened && f.defenderZ < 2f) {
                    // 맞은 자리 흙먼지
                    if (kotlin.random.Random.nextFloat() < 0.5f) {
                        particles.spawnDust(
                            f.defenderPos.x,
                            f.defenderPos.y,
                            z = 2f,
                            power = 0.9f,
                            count = 3,
                            kind = if (surface == TtakjiSurface.MARU) {
                                com.game.gametong.game.physics3d.ParticleKind.DUST
                            } else {
                                com.game.gametong.game.physics3d.ParticleKind.CHALK
                            },
                        )
                    }
                }
                particles.update(dt.coerceAtLeast(0.016f))
            }
            tick++
        }
        // 마지막 먼지 정리
        particles.update(0.016f)
        playing = false
    }

    /** 내(또는 2P) 한 수 → 연출 → (AI 대전이면) AI 한 수 → 연출. */
    suspend fun runStrike(actor: Side, aim: Float, p: Float) {
        val result = match.attack(actor, aim, p, strikePoint, target)
        lastResult = result
        lastActor = actor
        tick++
        panel = null
        playFlight(result, actor)
        if (!twoPlayer && !match.finished && match.turn == Side.AI) {
            thinking = true
            delay(560)
            val aiResult = match.aiAttack()
            thinking = false
            lastResult = aiResult
            lastActor = Side.AI
            tick++
            playFlight(aiResult, Side.AI)
        }
        tick++
    }

    fun fire(actor: Side, aim: Float, p: Float) {
        if (!canStrike) return
        if (!gate.compareAndSet(false, true)) return
        scope.launch {
            try {
                runStrike(actor, aim, p)
            } finally {
                gate.set(false)
            }
        }
    }

    // 그리기: 매 프레임 장면을 새로 만든다.
    androidx.compose.runtime.LaunchedEffect(match) {
        var startNanos = 0L
        while (true) {
            val now = withFrameNanos { it }
            if (startNanos == 0L) startNanos = now
            val seconds = (now - startNanos) / 1_000_000_000f
            val flight = view
            val from = aimFrom
            channel.scene = TtakjiScene3D.build(
                match = match,
                flight = flight?.flight,
                attacker = flight?.attacker,
                aim = if (from != null && !match.finished && !playing && !thinking &&
                    (match.turn == Side.PLAYER || twoPlayer)
                ) {
                    GroundAim(
                        from = from,
                        dir = aimDir,
                        power = power,
                        target = if (target == StrikeTarget.AUTO) null else aimTargetPoint(match, target),
                    )
                } else {
                    null
                },
                particles = particles.particles.map {
                    ParticleView(it.x, it.y, it.z, it.size, it.alpha, ParticleKindView.DUST)
                },
                aspect = boardAspect,
                timeSeconds = seconds,
            )
        }
    }

    val statusLine = when {
        match.finished -> "한 판 더 눌러 다시 시작"
        playing -> "딱지가 튀어 오르고, 넘어갈 듯 말 듯…"
        thinking -> "AI가 각도를 재는 중…"
        else -> describe(match, lastResult, lastActor, twoPlayer)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Inks.Kraft, Inks.KraftDeep, Inks.Kraft))),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "네모 딱지치기",
                onBack = onExit,
                trailing = {
                    PaperCard(padding = 6.dp, corner = 10.dp) {
                        Text(
                            "${match.playerScore} : ${match.aiScore}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = Inks.Ink,
                        )
                    }
                },
            )

            // 상단 요약 — 놀이판을 넓게 쓰려고 한 줄 띠 + 한 줄 안내만 남겼다.
            Column(modifier = Modifier.padding(horizontal = 10.dp)) {
                TurnBanner(
                    text = when {
                        match.finished -> when (match.winner) {
                            Side.PLAYER -> if (twoPlayer) "1P 승리!" else "내가 이겼다!"
                            Side.AI -> if (twoPlayer) "2P 승리!" else "AI에게 졌다…"
                            else -> "무승부"
                        }
                        playing -> "딱지가 날아간다…"
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
                Spacer(Modifier.height(3.dp))
                Text(
                    text = statusLine,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Inks.SoftInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // ── 놀이판 + 오른쪽 세로 아이콘 메뉴 ─────────────────────────
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 2.dp, top = 4.dp, bottom = 6.dp),
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // 놀이판: 남는 공간을 전부 쓴다(비율은 상자에 맞춰 카메라가 따라간다).
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        val aspect = (maxWidth.value / maxHeight.value.coerceAtLeast(1f))
                            .coerceIn(0.42f, 1.15f)
                        if (boardAspect != aspect) boardAspect = aspect
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF3A2A1A))
                                .border(3.dp, Inks.Wood, RoundedCornerShape(16.dp))
                                .pointerInput(match, match.turn, match.finished, playing, thinking) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            if (canStrike) {
                                                dragStart = offset
                                                dragNow = offset
                                                val w = size.width.toFloat().coerceAtLeast(1f)
                                                val h = size.height.toFloat().coerceAtLeast(1f)
                                                aimFrom = TtakjiScene3D.fieldPoint(
                                                    offset.x / w, offset.y / h, boardAspect,
                                                ) ?: match.homeOf(match.turn)
                                                aimDir = Vec2.ZERO
                                            }
                                        },
                                        onDrag = { change, _ ->
                                            val start = dragStart
                                            if (start != null && canStrike) {
                                                dragNow = change.position
                                                change.consume()
                                                val dx = start.x - change.position.x
                                                val dy = start.y - change.position.y
                                                val len = sqrt(dx * dx + dy * dy)
                                                power = (len / (size.width * 0.42f)).coerceIn(0.08f, 1f)
                                                if (len > 6f) {
                                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                                    val h = size.height.toFloat().coerceAtLeast(1f)
                                                    val nowPoint = TtakjiScene3D.fieldPoint(
                                                        change.position.x / w,
                                                        change.position.y / h,
                                                        boardAspect,
                                                    )
                                                    val from = aimFrom
                                                    if (nowPoint != null && from != null) {
                                                        aimDir = nowPoint - from
                                                        val aim = aimOfField(aimDir, match.turn)
                                                        if (aim.isFinite()) aimDeg = aim
                                                    } else {
                                                        aimDeg = aimOf(dx / len, dy / len, match.turn)
                                                    }
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            val start = dragStart
                                            val now = dragNow
                                            dragStart = null
                                            dragNow = null
                                            if (start != null && now != null) {
                                                val dx = start.x - now.x
                                                val dy = start.y - now.y
                                                val len = sqrt(dx * dx + dy * dy)
                                                if (len > 22f) {
                                                    val actor = match.turn
                                                    fire(actor, aimDeg, power.coerceIn(0.12f, 1f))
                                                }
                                            }
                                        },
                                        onDragCancel = {
                                            dragStart = null
                                            dragNow = null
                                        },
                                    )
                                },
                        ) {
                            Game3DView(channel = channel, modifier = Modifier.fillMaxSize())

                            // 판 위 안내(연출 상태)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(Inks.Wood.copy(alpha = 0.72f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text = when {
                                        dragStart != null -> "힘 ${(power * 100).toInt()}% · 각도 ${aimDeg.toInt()}°"
                                        match.finished -> "한 판 더 — 우측 🔁"
                                        playing -> "날아가는 중…"
                                        else -> "${surface.label} · 끌어서 놓기"
                                    },
                                    color = Inks.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                )
                            }
                        }
                    }

                    // 오른쪽 세로 아이콘 메뉴
                    Column(
                        modifier = Modifier
                            .width(54.dp)
                            .fillMaxHeight()
                            .padding(start = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        RailButton("🥏", "타격", active = panel == TtakjiPanel.STRIKE) {
                            panel = if (panel == TtakjiPanel.STRIKE) null else TtakjiPanel.STRIKE
                        }
                        RailButton("🎯", "조준", active = panel == TtakjiPanel.TARGET) {
                            panel = if (panel == TtakjiPanel.TARGET) null else TtakjiPanel.TARGET
                        }
                        RailButton("⚡", "파워", active = panel == TtakjiPanel.POWER) {
                            panel = if (panel == TtakjiPanel.POWER) null else TtakjiPanel.POWER
                        }
                        RailButton("ⓘ", "정보", active = panel == TtakjiPanel.INFO) {
                            panel = if (panel == TtakjiPanel.INFO) null else TtakjiPanel.INFO
                        }
                        Spacer(Modifier.weight(1f))
                        // 주 버튼: 이대로 친다
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (canStrike) Inks.Red else Inks.Gray)
                                .border(3.dp, Inks.Wood, RoundedCornerShape(14.dp))
                                .clickable(enabled = canStrike) { fire(match.turn, aimDeg, power) }
                                .padding(vertical = 9.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("🤚", fontSize = 20.sp)
                            Text(
                                "친다",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Inks.White,
                                maxLines = 1,
                            )
                        }
                        if (match.finished) {
                            Spacer(Modifier.height(4.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Inks.Green)
                                    .border(3.dp, Inks.Wood, RoundedCornerShape(14.dp))
                                    .clickable {
                                        matchKey++
                                        lastResult = null
                                        lastActor = null
                                        view = null
                                        panel = null
                                        tick++
                                    }
                                    .padding(vertical = 9.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("🔁", fontSize = 20.sp)
                                Text(
                                    "한 판",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Inks.White,
                                    maxLines = 1,
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                    }
                }

                // 아이콘을 누르면 놀이판 위에 열리는 조작 패널
                val open = panel
                if (open != null) {
                    // 바깥을 누르면 닫힌다(오른쪽 아이콘 메뉴는 그대로 쓸 수 있도록 비워 둔다).
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 58.dp)
                            .clickable { panel = null },
                    )
                    TtakjiPanelCard(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 58.dp)
                            .width(232.dp),
                        title = when (open) {
                            TtakjiPanel.STRIKE -> "① 타격 위치"
                            TtakjiPanel.TARGET -> "② 조준 지점"
                            TtakjiPanel.POWER -> "③ 파워 · 각도"
                            TtakjiPanel.INFO -> "경기 정보"
                        },
                        onClose = { panel = null },
                    ) {
                        when (open) {
                            TtakjiPanel.STRIKE -> StrikePanel(strikePoint) { strikePoint = it }
                            TtakjiPanel.TARGET -> TargetPanel(target) { target = it }
                            TtakjiPanel.POWER -> PowerPanel(
                                power = power,
                                aimDeg = aimDeg,
                                onPower = { power = it },
                                onAim = { aimDeg = it },
                            )
                            TtakjiPanel.INFO -> InfoPanel(
                                match = match,
                                surface = surface,
                                twoPlayer = twoPlayer,
                                lastResult = lastResult,
                                lastActor = lastActor,
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ───────────────── 오른쪽 아이콘 메뉴 · 패널 ───────────────── */

@Composable
private fun RailButton(
    icon: String,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (active) Inks.Gold.copy(alpha = 0.30f) else Inks.Paper.copy(alpha = 0.90f))
            .border(
                if (active) 3.dp else 2.dp,
                if (active) Inks.Wood else Inks.Wood.copy(alpha = 0.40f),
                RoundedCornerShape(13.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, fontSize = 19.sp, lineHeight = 22.sp)
        Text(
            label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            color = Inks.SoftInk,
            maxLines = 1,
        )
    }
}

@Composable
private fun TtakjiPanelCard(
    modifier: Modifier = Modifier,
    title: String,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    PaperCard(modifier = modifier, padding = 10.dp, color = Color(0xFFFFF6E2)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = Inks.Wood,
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Inks.Wood.copy(alpha = 0.12f))
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✕", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Inks.Wood)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
        }
    }
}

@Composable
private fun StrikePanel(strikePoint: Float, onPick: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            "내 딱지의 어느 날을 때릴까?",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Inks.SoftInk,
        )
        BarChip("왼쪽 날", strikePoint < -0.4f, Modifier.fillMaxWidth()) { onPick(-0.9f) }
        BarChip("가운데", strikePoint in -0.4f..0.4f, Modifier.fillMaxWidth()) { onPick(0f) }
        BarChip("오른쪽 날", strikePoint > 0.4f, Modifier.fillMaxWidth()) { onPick(0.9f) }
    }
}

@Composable
private fun TargetPanel(target: StrikeTarget, onPick: (StrikeTarget) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            "상대 딱지의 어디를 노릴까?",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Inks.SoftInk,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            SelectChip(
                label = StrikeTarget.CENTER.label,
                selected = target == StrikeTarget.CENTER,
                color = Inks.Blue,
                modifier = Modifier.weight(1f),
            ) { onPick(if (target == StrikeTarget.CENTER) StrikeTarget.AUTO else StrikeTarget.CENTER) }
            SelectChip(
                label = StrikeTarget.CORNER.label,
                selected = target == StrikeTarget.CORNER,
                color = Inks.Blue,
                modifier = Modifier.weight(1f),
            ) { onPick(if (target == StrikeTarget.CORNER) StrikeTarget.AUTO else StrikeTarget.CORNER) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            SelectChip(
                label = StrikeTarget.DIAGONAL.label,
                selected = target == StrikeTarget.DIAGONAL,
                color = Inks.Blue,
                modifier = Modifier.weight(1f),
            ) { onPick(if (target == StrikeTarget.DIAGONAL) StrikeTarget.AUTO else StrikeTarget.DIAGONAL) }
            SelectChip(
                label = StrikeTarget.WEAK_GAP.label,
                selected = target == StrikeTarget.WEAK_GAP,
                color = Inks.Blue,
                modifier = Modifier.weight(1f),
            ) { onPick(if (target == StrikeTarget.WEAK_GAP) StrikeTarget.AUTO else StrikeTarget.WEAK_GAP) }
        }
        Text(
            if (target == StrikeTarget.AUTO) "끌어온 방향 그대로 친다 — 노림 지점 없음" else target.hint,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = Inks.SoftInk,
        )
    }
}

@Composable
private fun PowerPanel(
    power: Float,
    aimDeg: Float,
    onPower: (Float) -> Unit,
    onAim: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArcMeter(value = power, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(6.dp))
            Text(
                "${(power * 100).toInt()}%",
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                color = Inks.Ink,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            WoodKnob("↙", aimDeg < -8f, size = 34.dp) { onAim(-22f) }
            WoodKnob("정면", abs(aimDeg) <= 8f, size = 34.dp) { onAim(0f) }
            WoodKnob("↘", aimDeg > 8f, size = 34.dp) { onAim(22f) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            BarChip("살살", power < 0.45f, Modifier.weight(1f)) { onPower(0.35f) }
            BarChip("보통", power in 0.45f..0.75f, Modifier.weight(1f)) { onPower(0.62f) }
            BarChip("세게", power > 0.75f, Modifier.weight(1f)) { onPower(0.92f) }
        }
        PowerMeter(
            value = power,
            modifier = Modifier.fillMaxWidth(),
            caption = "판 위의 호와 같은 값입니다",
        )
    }
}

@Composable
private fun InfoPanel(
    match: SquareTtakjiMatch,
    surface: TtakjiSurface,
    twoPlayer: Boolean,
    lastResult: SquareStrikeResult?,
    lastActor: Side?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            ScorePill(if (twoPlayer) "1P" else "나", "${match.playerScore}", Inks.Red, Modifier.weight(1f))
            ScorePill("노면", surface.label, Inks.Blue, Modifier.weight(1f))
            ScorePill(if (twoPlayer) "2P" else "AI", "${match.aiScore}", Inks.Blue, Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (twoPlayer) "1P 피로도" else "내 피로도",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = Inks.Wood,
                modifier = Modifier.width(58.dp),
            )
            StatBar(
                value = match.playerStamina / 100f,
                color = if (match.playerStamina > 40f) Inks.Green else Inks.Orange,
                modifier = Modifier.weight(1f),
                marker = match.playerPowerCap,
            )
            Spacer(Modifier.width(5.dp))
            Text(
                "${match.playerStamina.toInt()}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = Inks.Ink,
            )
        }
        Text(
            "파워 상한 ${(match.playerPowerCap * 100).toInt()}%" +
                if (match.playerInjury > 0) " · 부상 ${match.playerInjury}턴" else "",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Inks.SoftInk,
        )
        Text(
            describe(match, lastResult, lastActor, twoPlayer),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Inks.Ink,
        )
    }
}

/** 화면에서 끌어온 방향(ux, uy, y 아래가 +)을 그 쪽 조준각으로 바꾼다. */
private fun aimOf(ux: Float, uy: Float, side: Side): Float {
    val deg = Math.toDegrees(
        atan2(ux.toDouble(), (if (side == Side.PLAYER) -uy else uy).toDouble())
    ).toFloat()
    return deg
}

/** 3D 바닥에서 끌어온 방향(무대 좌표)을 조준각으로. */
private fun aimOfField(dir: Vec2, side: Side): Float {
    if (dir.length() < 1e-3f) return 0f
    val d = dir.normalized()
    return Math.toDegrees(
        atan2(d.x.toDouble(), (if (side == Side.PLAYER) -d.y else d.y).toDouble())
    ).toFloat()
}

/** 조준 지점이 있을 때 바닥에 링을 그릴 위치. */
private fun aimTargetPoint(match: SquareTtakjiMatch, target: StrikeTarget): Vec2? {
    if (target == StrikeTarget.AUTO) return null
    val defender = match.aiHome
    val side = match.turn
    val half = match.specOf(side.other()).halfSize
    return when (target) {
        StrikeTarget.CENTER -> defender
        StrikeTarget.CORNER -> defender + Vec2(half * 0.8f, -half * 0.8f)
        StrikeTarget.DIAGONAL -> defender + Vec2(-half * 0.5f, -half * 0.7f)
        StrikeTarget.WEAK_GAP -> {
            val edge = match.specOf(side.other()).weakEdge
            val off = half * 0.3f
            when (edge) {
                0 -> defender + Vec2(off, -half)
                1 -> defender + Vec2(half, off)
                2 -> defender + Vec2(off, half)
                else -> defender + Vec2(-half, off)
            }
        }
        else -> null
    }
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
    val r = result ?: return "상대 딱지의 노란 날개 틈이나 모서리를 노려 끌어서 놓으세요."
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
        r.nearFlip -> "$who 아슬아슬! ${r.tiltPeak.toInt()}도까지 들렸다가 도로 떨어졌다. 조금만 더!"
        r.weakPoint -> "$who 날개 틈을 맞혔지만 모자랐다(뒤집힘 ${(r.flipRatio * 100).toInt()}%). 조금 더 세게!"
        else -> "$who 맞혔지만 뒤집히지 않았다 (뒤집힘 ${(r.flipRatio * 100).toInt()}% · 들림 ${r.tiltPeak.toInt()}도)."
    }
}
