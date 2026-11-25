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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.game.gametong.game.MarbleAim
import com.game.gametong.game.MarbleAimPrediction
import com.game.gametong.game.MarbleCircleGame
import com.game.gametong.game.MarbleHoleGame
import com.game.gametong.game.MarbleWorld
import com.game.gametong.game.Side
import com.game.gametong.game.Vec2
import com.game.gametong.game.physics3d.ParticleSystem
import com.game.gametong.render.Game3DView
import com.game.gametong.render.GroundAim
import com.game.gametong.render.MarbleImpactFx
import com.game.gametong.render.MarbleScene3D
import com.game.gametong.render.MarbleTrailDot
import com.game.gametong.render.ParticleKindView
import com.game.gametong.render.ParticleView
import com.game.gametong.render.SceneChannel
import com.game.gametong.ui.components.FunButton
import com.game.gametong.ui.components.PaperCard
import com.game.gametong.ui.components.PowerMeter
import com.game.gametong.ui.components.ScorePill
import com.game.gametong.ui.components.ScreenHeader
import com.game.gametong.ui.components.TurnBanner
import com.game.gametong.ui.components.WalnutBar
import com.game.gametong.ui.theme.Inks
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

/** 무대 가로/세로 (1000 × 1060 무렵). */
private const val MARBLE_ASPECT = 0.95f

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

/**
 * 구슬이 굴러가는 잔상·충격 연출을 모아 두는 그릇.
 * 매 프레임 장면을 새로 만들기 때문에 상태를 Compose 로 올리지 않고 여기에만 쌓는다(재구성 없음).
 */
private class MarbleFx {
    val impacts = ArrayList<MarbleImpactFx>(32)
    val trails = ArrayList<MarbleTrailDot>(96)

    /** 새로 생긴 충격만 돌려준다(먼지는 그것들에만 뿌린다). */
    fun addImpacts(world: MarbleWorld): List<MarbleImpactFx> {
        val fresh = world.drainImpacts()
        if (fresh.isEmpty()) return emptyList()
        val added = ArrayList<MarbleImpactFx>(fresh.size)
        for (imp in fresh) {
            if (impacts.size >= 30) impacts.removeAt(0)
            val fx = MarbleImpactFx(
                pos = Vec2(imp.x, imp.y),
                strength = imp.strength,
                age = 0f,
                life = 0.55f,
                ringRadius = if (imp.wall) 18f else 22f + imp.strength * 0.02f,
                seed = (imp.a * 1.7f) % 6.28f,
            )
            impacts += fx
            added += fx
        }
        return added
    }

    fun addTrails(world: MarbleWorld, dt: Float) {
        for (m in world.marbles) {
            if (m.sunk) continue
            val speed = m.vel.length()
            if (speed < 90f) continue
            if (trails.size >= 90) trails.removeAt(0)
            trails += MarbleTrailDot(
                pos = m.pos,
                life = 1f,
                radius = m.radius * (0.55f + 0.45f * (speed / 1200f).coerceIn(0f, 1f)),
            )
        }
    }

    fun age(dt: Float) {
        var i = impacts.size - 1
        while (i >= 0) {
            val fx = impacts[i]
            val next = fx.copy(age = fx.age + dt)
            if (next.age >= next.life) impacts.removeAt(i) else impacts[i] = next
            i--
        }
        i = trails.size - 1
        while (i >= 0) {
            val dot = trails[i]
            val next = dot.life - dt * 2.6f
            if (next <= 0f) trails.removeAt(i) else trails[i] = dot.copy(life = next)
            i--
        }
    }
}

private fun marbleAimDeg(dir: Vec2, side: Side): Float {
    if (dir.length() < 1e-3f) return 0f
    val d = dir.normalized()
    return Math.toDegrees(
        atan2(d.x.toDouble(), (if (side == Side.PLAYER) -d.y else d.y).toDouble())
    ).toFloat()
}

/* ───────────────── 원 안 구슬 쳐내기 ───────────────── */

@Composable
private fun CircleGameScreen(twoPlayer: Boolean, onExit: () -> Unit) {
    var gameKey by remember { mutableIntStateOf(0) }
    val game = remember(gameKey) { MarbleCircleGame(twoPlayer) }
    var tick by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var power by remember { mutableStateOf(0f) }
    var aimFrom by remember { mutableStateOf<Vec2?>(null) }
    var aimDir by remember { mutableStateOf(Vec2.ZERO) }
    var aimDeg by remember { mutableStateOf(0f) }
    var thinking by remember { mutableStateOf(false) }
    var prediction by remember { mutableStateOf<MarbleAimPrediction?>(null) }
    var rollingNow by remember { mutableStateOf(false) }

    val channel = remember(game) { SceneChannel() }
    val particles = remember(game) { ParticleSystem(capacity = 180) }
    val fx = remember(game) { MarbleFx() }

    // 상대(AI) 차례: 잠깐 겨냥하는 척하다가 실제로 굴리기 시작한다.
    LaunchedEffect(tick, gameKey) {
        if (!twoPlayer && !game.finished && game.turn == Side.AI && !game.inFlight) {
            thinking = true
            delay(680)
            game.aiBeginShot()
            thinking = false
        }
    }

    LaunchedEffect(game) {
        var startNanos = 0L
        var lastNanos = 0L
        while (true) {
            val now = withFrameNanos { it }
            if (startNanos == 0L) {
                startNanos = now
                lastNanos = now
            }
            val seconds = (now - startNanos) / 1_000_000_000f
            val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
            lastNanos = now
            particles.update(dt.coerceAtLeast(1f / 240f))
            if (game.inFlight) {
                val rolling = game.advanceShot(dt)
                for (imp in fx.addImpacts(game.world)) {
                    particles.spawnDust(
                        imp.pos.x, imp.pos.y,
                        z = 0f,
                        power = (imp.strength / 700f).coerceIn(0.18f, 1.3f),
                        count = 3,
                    )
                }
                rollingNow = rolling
                if (!rolling) {
                    game.finishShot()
                    for (g in game.glasses.filter { it.sunk }) {
                        particles.spawnDust(g.pos.x, g.pos.y, z = 0f, power = 0.6f, count = 4)
                    }
                    prediction = null
                    tick++
                }
            }
            fx.addTrails(game.world, dt)
            fx.age(dt)
            channel.scene = MarbleScene3D.build(
                marbles = listOf(game.king) + game.glasses,
                king = game.king,
                holes = emptyList(),
                playRadius = MarbleCircleGame.CIRCLE_R,
                aim = if (dragging) {
                    GroundAim(
                        from = aimFrom ?: game.king.pos,
                        dir = aimDir,
                        power = power.coerceAtLeast(0.15f),
                        prediction = prediction,
                    )
                } else {
                    null
                },
                particles = particles.particles.map {
                    ParticleView(it.x, it.y, it.z, it.size, it.alpha, ParticleKindView.DUST)
                },
                aspect = MARBLE_ASPECT,
                timeSeconds = seconds,
                fieldHeight = 1060f,
                impacts = fx.impacts,
                trails = fx.trails,
            )
        }
    }

    MarbleScaffold(
        title = "원 안 구슬 쳐내기",
        onExit = onExit,
        top = {
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
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScorePill(if (twoPlayer) "1P" else "나", "${game.playerScore}", Inks.Red, Modifier.weight(1f))
                ScorePill("남은 구슬", "${game.remainingGlasses}", Inks.Gold, Modifier.weight(1f))
                ScorePill(if (twoPlayer) "2P" else "AI", "${game.aiScore}", Inks.Blue, Modifier.weight(1f))
            }
        },
        field = {
            MarbleField(
                channel = channel,
                aspect = MARBLE_ASPECT,
                onDragStart = { ux, uy, _ ->
                    // 내 차례가 아니거나 구슬이 굴러가는 중에는 조준할 수 없다.
                    if (!game.finished && !game.inFlight && (twoPlayer || game.turn == Side.PLAYER)) {
                        dragging = true
                        power = 0f
                        aimDir = Vec2.ZERO
                        aimFrom = MarbleScene3D.fieldPoint(ux, uy, MARBLE_ASPECT, 1060f) ?: game.king.pos
                    }
                },
                onDrag = { ux, uy, _, screenWidth, startX, startY, x, y ->
                    if (dragging && !game.finished) {
                        val dx = startX - x
                        val dy = startY - y
                        val len = sqrt(dx * dx + dy * dy)
                        power = (len / (screenWidth * 0.42f)).coerceIn(0f, 1f)
                        if (len > 6f) {
                            val p = MarbleScene3D.fieldPoint(ux, uy, MARBLE_ASPECT, 1060f)
                            val from = aimFrom
                            if (p != null && from != null) {
                                aimDir = p - from
                                aimDeg = marbleAimDeg(aimDir, game.turn)
                                prediction = MarbleAim.predict(
                                    world = game.world,
                                    from = game.king.pos,
                                    velocity = MarbleAim.velocity(
                                        aimDeg,
                                        power.coerceAtLeast(0.08f),
                                        if (game.turn == Side.PLAYER) -1f else 1f,
                                    ),
                                    radius = game.king.radius,
                                    ignoreId = game.king.id,
                                )
                            }
                        }
                    }
                },
                onDragEnd = { length, _ ->
                    if (dragging && !game.finished && !game.inFlight &&
                        length > 22f && aimDir.length() > 1e-3f
                    ) {
                        // 여기서 바로 결과를 내지 않고 **실제로 굴리기 시작**만 한다.
                        // 화면 루프가 매 프레임 조금씩 굴려 부딪힘을 보여 주고, 멈추면 정산한다.
                        game.beginShot(
                            game.turn,
                            marbleAimDeg(aimDir, game.turn),
                            power.coerceAtLeast(0.08f),
                        )
                    }
                    dragging = false
                    power = 0f
                    aimDir = Vec2.ZERO
                    prediction = null
                },
                onDragCancel = {
                    dragging = false
                    power = 0f
                    aimDir = Vec2.ZERO
                    prediction = null
                },
                hintText = when {
                    rollingNow -> "구슬이 굴러가는 중…"
                    else -> "힘 ${(power * 100).toInt()}% · 각도 ${aimDeg.toInt()}° · 끌어서 놓기"
                },
            )
        },
        controls = {
            WalnutBar(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    PowerMeter(value = power, modifier = Modifier.fillMaxWidth(), caption = "당긴 만큼 세게 날아갑니다")
                }
            }
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 11.dp, color = Color(0xFFFFF3D6)) {
                Text(game.outcome, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Inks.Ink)
            }
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 10.dp) {
                Column {
                    Text(
                        "원 밖으로 나간 유리구슬은 그대로 내 몫. 왕구슬은 매 턴 출발점에서 다시 칩니다.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Inks.SoftInk,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "내가 친 횟수 ${game.playerShots} · 상대 ${game.aiShots} (각 8번)",
                        fontSize = 12.sp,
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
            Spacer(Modifier.height(10.dp))
        },
    )
}

/* ───────────────── 구멍치기 ───────────────── */

@Composable
private fun HoleGameScreen(twoPlayer: Boolean, onExit: () -> Unit) {
    var gameKey by remember { mutableIntStateOf(0) }
    val game = remember(gameKey) { MarbleHoleGame(twoPlayer) }
    var tick by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var power by remember { mutableStateOf(0f) }
    var aimFrom by remember { mutableStateOf<Vec2?>(null) }
    var aimDir by remember { mutableStateOf(Vec2.ZERO) }
    var aimDeg by remember { mutableStateOf(0f) }
    var thinking by remember { mutableStateOf(false) }
    var prediction by remember { mutableStateOf<MarbleAimPrediction?>(null) }
    var rollingNow by remember { mutableStateOf(false) }

    val channel = remember(game) { SceneChannel() }
    val particles = remember(game) { ParticleSystem(capacity = 180) }
    val fx = remember(game) { MarbleFx() }

    LaunchedEffect(tick, gameKey) {
        if (!twoPlayer && !game.finished && game.turn == Side.AI && !game.inFlight) {
            thinking = true
            delay(680)
            game.aiBeginShot()
            thinking = false
        }
    }

    LaunchedEffect(game) {
        var startNanos = 0L
        var lastNanos = 0L
        while (true) {
            val now = withFrameNanos { it }
            if (startNanos == 0L) {
                startNanos = now
                lastNanos = now
            }
            val seconds = (now - startNanos) / 1_000_000_000f
            val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
            lastNanos = now
            particles.update(dt.coerceAtLeast(1f / 240f))
            if (game.inFlight) {
                val rolling = game.advanceShot(dt)
                for (imp in fx.addImpacts(game.world)) {
                    particles.spawnDust(
                        imp.pos.x, imp.pos.y,
                        z = 0f,
                        power = (imp.strength / 700f).coerceIn(0.18f, 1.3f),
                        count = 3,
                    )
                }
                rollingNow = rolling
                if (!rolling) {
                    game.finishShot()
                    val res = game.lastResult
                    if (res?.progressed == true) {
                        val hole = game.holes.getOrNull(res.holeIndex)
                        if (hole != null) {
                            particles.spawnHoleBurst(hole.x, hole.y)
                        }
                    }
                    prediction = null
                    tick++
                }
            }
            fx.addTrails(game.world, dt)
            fx.age(dt)
            channel.scene = MarbleScene3D.build(
                marbles = listOf(game.playerMarble, game.aiMarble),
                king = null,
                holes = game.holes,
                playRadius = null,
                aim = if (dragging) {
                    GroundAim(
                        from = aimFrom ?: game.marbleOf(game.turn).pos,
                        dir = aimDir,
                        power = power.coerceAtLeast(0.15f),
                        target = game.holes.getOrNull(game.progressOf(game.turn).coerceAtMost(2)),
                        prediction = prediction,
                    )
                } else {
                    null
                },
                particles = particles.particles.map {
                    ParticleView(it.x, it.y, it.z, it.size, it.alpha, ParticleKindView.DUST)
                },
                aspect = MARBLE_ASPECT,
                timeSeconds = seconds,
                fieldHeight = 1080f,
                impacts = fx.impacts,
                trails = fx.trails,
            )
        }
    }

    MarbleScaffold(
        title = "구멍치기",
        onExit = onExit,
        top = {
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
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScorePill(if (twoPlayer) "1P" else "나", "${game.playerScore}", Inks.Red, Modifier.weight(1f))
                ScorePill("내 구멍", "${game.playerProgress}/3", Inks.Gold, Modifier.weight(1f))
                ScorePill(if (twoPlayer) "2P" else "AI", "${game.aiScore}", Inks.Blue, Modifier.weight(1f))
            }
        },
        field = {
            MarbleField(
                channel = channel,
                aspect = MARBLE_ASPECT,
                onDragStart = { ux, uy, _ ->
                    // 내 차례가 아니거나 구슬이 굴러가는 중에는 조준할 수 없다.
                    if (!game.finished && !game.inFlight && (twoPlayer || game.turn == Side.PLAYER)) {
                        dragging = true
                        power = 0f
                        aimDir = Vec2.ZERO
                        aimFrom = MarbleScene3D.fieldPoint(ux, uy, MARBLE_ASPECT, 1080f)
                            ?: game.marbleOf(game.turn).pos
                    }
                },
                onDrag = { ux, uy, _, screenWidth, startX, startY, x, y ->
                    if (dragging && !game.finished) {
                        val dx = startX - x
                        val dy = startY - y
                        val len = sqrt(dx * dx + dy * dy)
                        power = (len / (screenWidth * 0.42f)).coerceIn(0f, 1f)
                        if (len > 6f) {
                            val p = MarbleScene3D.fieldPoint(ux, uy, MARBLE_ASPECT, 1080f)
                            val from = aimFrom
                            if (p != null && from != null) {
                                aimDir = p - from
                                aimDeg = marbleAimDeg(aimDir, game.turn)
                                val mine = game.marbleOf(game.turn)
                                prediction = MarbleAim.predict(
                                    world = game.world,
                                    from = mine.pos,
                                    velocity = MarbleAim.velocity(
                                        aimDeg,
                                        power.coerceAtLeast(0.08f),
                                        if (game.turn == Side.PLAYER) -1f else 1f,
                                    ),
                                    radius = mine.radius,
                                    ignoreId = mine.id,
                                )
                            }
                        }
                    }
                },
                onDragEnd = { length, _ ->
                    if (dragging && !game.finished && !game.inFlight &&
                        length > 22f && aimDir.length() > 1e-3f
                    ) {
                        // 실제로 굴리기 시작한다 — 화면 루프가 굴리고, 구멍에 빠지는 순간을 보여 준다.
                        game.beginShot(
                            game.turn,
                            marbleAimDeg(aimDir, game.turn),
                            power.coerceAtLeast(0.08f),
                        )
                    }
                    dragging = false
                    power = 0f
                    aimDir = Vec2.ZERO
                    prediction = null
                },
                onDragCancel = {
                    dragging = false
                    power = 0f
                    aimDir = Vec2.ZERO
                    prediction = null
                },
                hintText = when {
                    rollingNow -> "구슬이 굴러가는 중…"
                    else -> "힘 ${(power * 100).toInt()}% · 각도 ${aimDeg.toInt()}° · 끌어서 놓기"
                },
            )
        },
        controls = {
            WalnutBar(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    PowerMeter(value = power, modifier = Modifier.fillMaxWidth(), caption = "당긴 만큼 세게 날아갑니다")
                }
            }
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 11.dp, color = Color(0xFFFFF3D6)) {
                Text(game.outcome, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Inks.Ink)
            }
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 10.dp) {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for ((index, _) in game.holes.withIndex()) {
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
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (done) Inks.White else Inks.SoftInk,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "구멍 3개를 넣은 뒤에는 상대 구슬을 맞혀야 따먹습니다. 성공하면 한 번 더!",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Inks.SoftInk,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "치기 ${game.playerShots} / ${game.aiShots} · 남은 구멍 상대 ${(3 - game.aiProgress).coerceAtLeast(0)}",
                        fontSize = 12.sp,
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
            Spacer(Modifier.height(10.dp))
        },
    )
}

/** 두 구슬 경기가 같은 뼈대(머리 · 3D 판 · 조작판)를 쓰도록 묶은 틀. */
@Composable
private fun MarbleScaffold(
    title: String,
    onExit: () -> Unit,
    top: @Composable () -> Unit,
    field: @Composable () -> Unit,
    controls: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Inks.Kraft, Inks.KraftDeep, Inks.Kraft))),
    ) {
        ScreenHeader(title = title, onBack = onExit)
        Column(modifier = Modifier.padding(horizontal = 12.dp)) { top() }
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            field()
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            controls()
        }
    }
}

/**
 * 3D 구슬 판. 끌어서 조준하고 놓으면 친다.
 * 콜백에 화면 좌표와 함께 드래그 시작·현재 위치(px)를 넘겨 힘을 계산하게 한다.
 */
@Composable
private fun MarbleField(
    channel: SceneChannel,
    aspect: Float,
    onDragStart: (Float, Float, Offset) -> Unit,
    onDrag: (Float, Float, Offset, Float, Float, Float, Float, Float) -> Unit,
    onDragEnd: (lengthPx: Float, power: Float) -> Unit,
    onDragCancel: () -> Unit,
    hintText: String,
) {
    // 제스처가 오래 살아 있어도 최신 콜백을 쓰도록 감싼다(판을 새로 시작해도 옛 판을 치지 않는다).
    val startCb = androidx.compose.runtime.rememberUpdatedState(onDragStart)
    val dragCb = androidx.compose.runtime.rememberUpdatedState(onDrag)
    val endCb = androidx.compose.runtime.rememberUpdatedState(onDragEnd)
    val cancelCb = androidx.compose.runtime.rememberUpdatedState(onDragCancel)
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val fieldWidth = min(maxWidth.value, maxHeight.value * aspect)
        var startX by remember { mutableStateOf(0f) }
        var startY by remember { mutableStateOf(0f) }
        var lastX by remember { mutableStateOf(0f) }
        var lastY by remember { mutableStateOf(0f) }
        Box(
            modifier = Modifier
                .width(fieldWidth.dp)
                .aspectRatio(aspect)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF33241A))
                .border(3.dp, Inks.Wood, RoundedCornerShape(18.dp))
                .pointerInput(channel) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            startX = offset.x
                            startY = offset.y
                            lastX = offset.x
                            lastY = offset.y
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            startCb.value(offset.x / w, offset.y / h, offset)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            lastX = change.position.x
                            lastY = change.position.y
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            dragCb.value(
                                change.position.x / w,
                                change.position.y / h,
                                change.position,
                                w,
                                startX,
                                startY,
                                change.position.x,
                                change.position.y,
                            )
                        },
                        onDragEnd = {
                            val len = sqrt(
                                (startX - lastX) * (startX - lastX) + (startY - lastY) * (startY - lastY)
                            )
                            endCb.value(len, 0f)
                        },
                        onDragCancel = { cancelCb.value() },
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
                    text = hintText,
                    color = Inks.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
