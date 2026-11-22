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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.game.gametong.game.Side
import com.game.gametong.game.SquareStrikeResult
import com.game.gametong.game.SquareTtakjiFlight
import com.game.gametong.game.SquareTtakjiMatch
import com.game.gametong.game.StrikeTarget
import com.game.gametong.game.TtakjiMaterial
import com.game.gametong.game.TtakjiPose
import com.game.gametong.game.TtakjiSpec
import com.game.gametong.game.TtakjiSurface
import com.game.gametong.ui.components.BlockPose
import com.game.gametong.ui.components.FunButton
import com.game.gametong.ui.components.PaperCard
import com.game.gametong.ui.components.ScreenHeader
import com.game.gametong.ui.components.ScorePill
import com.game.gametong.ui.components.SelectChip
import com.game.gametong.ui.components.StatBar
import com.game.gametong.ui.components.TurnBanner
import com.game.gametong.ui.components.drawBlock
import com.game.gametong.ui.components.drawOnTopFace
import com.game.gametong.ui.theme.Inks
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val FIELD_W = 1000f
private const val FIELD_H = 1500f

/** 한 수의 2.5D 연출 상태. */
private data class FlightView(
    val attacker: Side,
    val flight: SquareTtakjiFlight,
)

/** 화면에 그릴 딱지 한 장. */
private data class Drawn(
    val side: Side,
    val pose: TtakjiPose,
    val spec: TtakjiSpec,
    val flipped: Boolean,
)

/** 네모 딱지치기 경기 화면. 끌어서 조준하거나, 타격 위치·조준 지점·파워를 골라 친다. */
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
    var lastResult by remember { mutableStateOf<SquareStrikeResult?>(null) }
    var lastActor by remember { mutableStateOf<Side?>(null) }
    var thinking by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf<FlightView?>(null) }

    val playerTurn = match.turn == Side.PLAYER
    val canStrike = !match.finished && !playing && !thinking && (playerTurn || twoPlayer)

    /** 한 수를 계산해 2.5D 연출을 끝까지 돌린다. */
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
        while (!f.isFinished) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
            last = now
            f.advance(dt)
            tick++
        }
        tick++
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Inks.Kraft, Inks.KraftDeep, Inks.Kraft))),
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

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScorePill(if (twoPlayer) "1P" else "나", "${match.playerScore}", Inks.Red, Modifier.weight(1f))
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
                        .pointerInput(match, match.turn, match.finished, playing, thinking) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    if (canStrike) {
                                        dragStart = offset
                                        dragNow = offset
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
                                        if (len > 6f) aimDeg = aimOf(dx / len, dy / len, match.turn)
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
                                            fire(actor, aimOf(dx / len, dy / len, actor), power.coerceIn(0.12f, 1f))
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
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        tick
                        val sx = size.width / FIELD_W
                        val sy = size.height / FIELD_H
                        drawSurface(surface, sx, sy)
                        drawAimGuide(match, sx, sy, dragStart, dragNow)

                        val flightView = view
                        val playerPose = flightView?.let {
                            if (it.attacker == Side.PLAYER) it.flight.attackerPose else it.flight.defenderPose
                        } ?: TtakjiPose(match.playerHome)
                        val aiPose = flightView?.let {
                            if (it.attacker == Side.AI) it.flight.attackerPose else it.flight.defenderPose
                        } ?: TtakjiPose(match.aiHome)
                        val flippedSide = flightView?.let {
                            if (it.flight.result.flipped) it.attacker.other() else null
                        }

                        val drawn = listOf(
                            Drawn(Side.PLAYER, playerPose, match.playerSpec, flippedSide == Side.PLAYER),
                            Drawn(Side.AI, aiPose, match.aiSpec, flippedSide == Side.AI),
                        )
                        // 공격 딱지를 나중에 그려 위로 올라오게 한다
                        val ordered = if (flightView == null) drawn else drawn.sortedBy { it.side == flightView.attacker }
                        for (d in ordered) {
                            drawTtakji(
                                pose = d.pose,
                                spec = d.spec,
                                isMine = d.side == Side.PLAYER,
                                flipped = d.flipped,
                                sx = sx,
                                sy = sy,
                            )
                        }
                    }

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
                            SelectChip("왼쪽 날", strikePoint < -0.4f, Inks.Red, Modifier.weight(1f)) { strikePoint = -0.9f }
                            SelectChip("가운데", strikePoint in -0.4f..0.4f, Inks.Red, Modifier.weight(1f)) { strikePoint = 0f }
                            SelectChip("오른쪽 날", strikePoint > 0.4f, Inks.Red, Modifier.weight(1f)) { strikePoint = 0.9f }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "날을 때리면 토크가 커지고, 가운데를 때리면 곧게 밀립니다.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Inks.SoftInk,
                        )

                        Spacer(Modifier.height(9.dp))
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

                        Spacer(Modifier.height(9.dp))
                        Text(
                            "③ 대각 방향과 파워",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = Inks.Wood,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SelectChip("↙ 왼대각", aimDeg < -8f, Inks.Purple, Modifier.weight(1f)) { aimDeg = -22f }
                            SelectChip("정면", abs(aimDeg) <= 8f, Inks.Purple, Modifier.weight(1f)) { aimDeg = 0f }
                            SelectChip("↘ 오른대각", aimDeg > 8f, Inks.Purple, Modifier.weight(1f)) { aimDeg = 22f }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (p in listOf(0.4f, 0.65f, 0.85f, 1f)) {
                                SelectChip(
                                    label = "${(p * 100).toInt()}%",
                                    selected = abs(power - p) < 0.06f,
                                    color = Inks.Orange,
                                    modifier = Modifier.weight(1f),
                                ) { power = p }
                            }
                        }
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
                Spacer(Modifier.height(26.dp))
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

private fun DrawScope.drawAimGuide(
    match: SquareTtakjiMatch,
    sx: Float,
    sy: Float,
    start: Offset?,
    now: Offset?,
) {
    if (start == null || now == null) return
    val dx = start.x - now.x
    val dy = start.y - now.y
    val len = sqrt(dx * dx + dy * dy)
    if (len < 12f) return
    val home = match.homeOf(match.turn)
    val from = Offset(home.x * sx, home.y * sy)
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

/** 딱지 블록의 화면 자세. 경첩축으로 넘어가며 들리므로 아랫모서리가 바닥에 붙어 돈다. */
private fun ttakjiBlockPose(
    pose: TtakjiPose,
    spec: TtakjiSpec,
    sx: Float,
    sy: Float,
): BlockPose {
    val scale = (sx + sy) / 2f
    val width = spec.size * sx
    val depth = spec.size * sy
    val height = (4f + spec.thick * 4f) * scale
    val tilt = pose.tilt
    return BlockPose(
        cx = pose.pos.x * sx,
        cy = pose.pos.y * sy,
        width = width,
        depth = depth,
        height = height,
        yawDeg = pose.yaw,
        tiltDeg = tilt,
        tiltAxisDeg = Math.toDegrees(
            atan2(pose.tiltAxis.y.toDouble(), pose.tiltAxis.x.toDouble())
        ).toFloat(),
        rollDeg = pose.roll,
        lift = pose.z * scale,
    )
}

private fun DrawScope.drawTtakji(
    pose: TtakjiPose,
    spec: TtakjiSpec,
    isMine: Boolean,
    flipped: Boolean,
    sx: Float,
    sy: Float,
) {
    val block = ttakjiBlockPose(pose, spec, sx, sy)
    val face = if (isMine) Inks.Blue else Inks.Red
    val back = if (isMine) Inks.Red else Inks.Blue
    val top = if (flipped) back else face
    val side = if (flipped) back.copy(alpha = 0.75f) else face.copy(alpha = 0.75f)
    drawBlock(
        pose = block,
        topFaceColor = top,
        sideColor = side,
        borderColor = Inks.Wood,
        borderWidth = 4f,
    )
    // 접힌 대각선
    drawOnTopFace(block, -0.42f, -0.42f, 0.42f, 0.42f, Inks.White.copy(alpha = 0.35f), 3f)
    if (!flipped) {
        // 들뜬 날개 틈(변 가운데를 비스듬히)
        val line = when (spec.weakEdge) {
            0 -> listOf(-0.55f, -0.5f, 0.55f, -0.5f)
            1 -> listOf(0.5f, -0.55f, 0.5f, 0.55f)
            2 -> listOf(-0.55f, 0.5f, 0.55f, 0.5f)
            else -> listOf(-0.5f, -0.55f, -0.5f, 0.55f)
        }
        drawOnTopFace(block, line[0], line[1], line[2], line[3], Inks.Gold, 7f)
        drawOnTopFace(block, line[0], line[1], line[2], line[3], Color(0xFF8A5A00), 2.5f)
    }
}
