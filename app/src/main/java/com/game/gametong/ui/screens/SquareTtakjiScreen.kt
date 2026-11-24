package com.game.gametong.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import kotlin.math.min
import kotlin.math.sqrt

private const val ASPECT = 2f / 3f

/** 한 수의 3D 연출 상태. */
private data class FlightView(
    val attacker: Side,
    val flight: SquareTtakjiFlight,
)

/**
 * 네모 딱지치기 경기 화면.
 * 운동장 시멘트 바닥을 3D로 그리고, 끌어서 조준하거나 타격 위치·조준 지점·파워를 골라 친다.
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
                aspect = ASPECT,
                timeSeconds = seconds,
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Inks.Kraft, Inks.KraftDeep, Inks.Kraft))),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "네모 딱지치기", onBack = onExit)

            // 상단 요약
            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
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
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScorePill(if (twoPlayer) "1P" else "나", "${match.playerScore}", Inks.Red, Modifier.weight(1f))
                    ScorePill("노면", surface.label, Inks.Blue, Modifier.weight(1f))
                    ScorePill(if (twoPlayer) "2P" else "AI", "${match.aiScore}", Inks.Blue, Modifier.weight(1f))
                }
            }

            // 3D 운동장
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                val boardWidth = min(maxWidth.value, maxHeight.value * ASPECT)
                Box(
                    modifier = Modifier
                        .width(boardWidth.dp)
                        .height((boardWidth / ASPECT).dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF3A2A1A))
                        .border(3.dp, Inks.Wood, RoundedCornerShape(18.dp))
                        .pointerInput(match, match.turn, match.finished, playing, thinking) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    if (canStrike) {
                                        dragStart = offset
                                        dragNow = offset
                                        val w = size.width.toFloat().coerceAtLeast(1f)
                                        val h = size.height.toFloat().coerceAtLeast(1f)
                                        aimFrom = TtakjiScene3D.fieldPoint(
                                            offset.x / w, offset.y / h, ASPECT,
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
                                                ASPECT,
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

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Inks.Wood.copy(alpha = 0.78f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = when {
                                dragStart != null -> "힘 ${(power * 100).toInt()}% · 각도 ${aimDeg.toInt()}°"
                                match.finished -> "한 판 더 눌러 다시 시작"
                                playing -> "튀어 오르고, 넘어갈 듯 말 듯…"
                                else -> "${surface.label} · 끌어서 놓기"
                            },
                            color = Inks.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            // 조작판(크림·월넛)
            Column(
                modifier = Modifier
                    .weight(1.05f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WalnutBar(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        ArcMeter(value = power, modifier = Modifier.fillMaxWidth())
                        Text(
                            "파워 ${(power * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = Inks.SoftInk,
                        )
                    }
                    WoodKnob("↙", aimDeg < -8f, size = 40.dp) { aimDeg = -22f }
                    WoodKnob("정면", abs(aimDeg) <= 8f, size = 40.dp) { aimDeg = 0f }
                    WoodKnob("↘", aimDeg > 8f, size = 40.dp) { aimDeg = 22f }
                }

                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 10.dp) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (twoPlayer) "1P 피로도" else "내 피로도",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = Inks.Wood,
                                modifier = Modifier.width(78.dp),
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
                                fontSize = 14.sp,
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
                                modifier = Modifier.width(78.dp),
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
                    }
                }

                // ── 3요소 조작: 타격 위치 · 조준 지점(대각) · 파워 ──────────
                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 10.dp) {
                    Column {
                        Text(
                            "① 타격 위치 — 내 딱지의 어느 날을 때릴까?",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = Inks.Wood,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BarChip("왼쪽 날", strikePoint < -0.4f, Modifier.weight(1f)) { strikePoint = -0.9f }
                            BarChip("가운데", strikePoint in -0.4f..0.4f, Modifier.weight(1f)) { strikePoint = 0f }
                            BarChip("오른쪽 날", strikePoint > 0.4f, Modifier.weight(1f)) { strikePoint = 0.9f }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "② 조준 지점 — 상대의 어디를 노릴까?",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = Inks.Wood,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            for (t in listOf(StrikeTarget.CENTER, StrikeTarget.CORNER, StrikeTarget.DIAGONAL, StrikeTarget.WEAK_GAP)) {
                                SelectChip(
                                    label = t.label,
                                    selected = target == t,
                                    color = Inks.Blue,
                                    modifier = Modifier.weight(1f),
                                ) { target = if (target == t) StrikeTarget.AUTO else t }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (target == StrikeTarget.AUTO) "끌어온 방향 그대로 친다 — 노림 지점 없음" else target.hint,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Inks.SoftInk,
                        )
                        Spacer(Modifier.height(6.dp))
                        PowerMeter(
                            value = power,
                            modifier = Modifier.fillMaxWidth(),
                            caption = "③ 파워 — 판 위의 호와 같은 값입니다",
                        )
                        Spacer(Modifier.height(8.dp))
                        FunButton(
                            text = when {
                                match.finished -> "경기 끝"
                                !canStrike && !thinking -> "상대 차례…"
                                thinking -> "AI가 겨냥 중…"
                                playing -> "날아가는 중…"
                                else -> "이대로 친다"
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color = if (canStrike) Inks.Red else Inks.Gray,
                            fontSize = 18,
                            enabled = canStrike,
                        ) {
                            fire(match.turn, aimDeg, power)
                        }
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
                        lastActor = null
                        view = null
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
