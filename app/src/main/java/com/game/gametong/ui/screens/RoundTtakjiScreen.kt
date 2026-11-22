package com.game.gametong.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.game.gametong.audio.MicBlowMeter
import com.game.gametong.game.BlowMatch
import com.game.gametong.game.BlowWind
import com.game.gametong.game.DistanceMatch
import com.game.gametong.game.DistanceShot
import com.game.gametong.game.RoundMode
import com.game.gametong.game.RoundTtakjiPhysics
import com.game.gametong.game.Side
import com.game.gametong.game.StickMatch
import com.game.gametong.ui.components.FunButton
import com.game.gametong.ui.components.PaperCard
import com.game.gametong.ui.components.ScreenHeader
import com.game.gametong.ui.components.ScorePill
import com.game.gametong.ui.components.StatBar
import com.game.gametong.ui.components.TurnBanner
import com.game.gametong.ui.theme.Inks
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** 동그란 딱지 3종 화면. */
@Composable
fun RoundTtakjiScreen(
    mode: RoundMode,
    twoPlayer: Boolean,
    onExit: () -> Unit,
) {
    when (mode) {
        RoundMode.STICK -> StickGameScreen(twoPlayer, onExit)
        RoundMode.DISTANCE -> DistanceGameScreen(twoPlayer, onExit)
        RoundMode.BLOW -> BlowGameScreen(twoPlayer, onExit)
    }
}

private fun aimFromDrag(dx: Float, dy: Float, len: Float): Float {
    val deg = Math.toDegrees(atan2(dx / len.toDouble(), -dy / len.toDouble())).toFloat()
    return deg
}

/* ───────────────────────── 붙여먹기 ───────────────────────── */

@Composable
private fun StickGameScreen(twoPlayer: Boolean, onExit: () -> Unit) {
    val match = remember { StickMatch(twoPlayer) }
    var tick by remember { mutableIntStateOf(0) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragNow by remember { mutableStateOf<Offset?>(null) }
    var power by remember { mutableStateOf(0f) }
    var aimDeg by remember { mutableStateOf(0f) }

    LaunchedEffect(tick) {
        if (!twoPlayer && match.waitingForAi) {
            delay(680)
            match.aiFlick()
            tick++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Inks.Kraft, Inks.KraftDeep, Inks.Kraft)))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "붙여먹기", onBack = onExit)
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
                            else -> if (twoPlayer) "2P 승리!" else "AI에게 졌다…"
                        }
                        twoPlayer -> if (match.waitingForPlayer) "1P 차례" else "2P 차례"
                        match.waitingForPlayer -> "내 차례 — 끌어서 붙이기"
                        else -> "AI가 붙이는 중…"
                    },
                    color = if (match.finished) Inks.Green else if (match.waitingForPlayer) Inks.Red else Inks.Blue,
                    trailing = "${match.round}판 · 먼저 5점",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScorePill(if (twoPlayer) "1P" else "나", "${match.playerScore}", Inks.Red, Modifier.weight(1f))
                    ScorePill("판", "${match.round}", Inks.Gold, Modifier.weight(1f))
                    ScorePill(if (twoPlayer) "2P" else "AI", "${match.aiScore}", Inks.Blue, Modifier.weight(1f))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1000f / 1400f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFFE2C79B))
                        .border(3.dp, Inks.Wood, RoundedCornerShape(18.dp))
                        .pointerInput(match, tick) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    if (!match.finished && (twoPlayer || match.waitingForPlayer)) {
                                        dragStart = offset
                                        dragNow = offset
                                        power = 0f
                                    }
                                },
                                onDrag = { change, _ ->
                                    val start = dragStart
                                    if (start != null) {
                                        dragNow = change.position
                                        change.consume()
                                        val dx = start.x - change.position.x
                                        val dy = start.y - change.position.y
                                        val len = sqrt(dx * dx + dy * dy)
                                        power = (len / (size.width * 0.45f)).coerceIn(0f, 1f)
                                        if (len > 6f) aimDeg = aimFromDrag(dx, dy, len)
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
                                            val side = if (twoPlayer && !match.waitingForPlayer) Side.AI else Side.PLAYER
                                            match.flick(side, aimFromDrag(dx, dy, len), power.coerceAtLeast(0.08f))
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
                        val sy = size.height / 1400f
                        drawBrickWall(sx = sx, sy = sy)
                        // 시작선
                        drawLine(
                            color = Inks.Wood.copy(alpha = 0.35f),
                            start = Offset(0f, 1230f * sy),
                            end = Offset(size.width, 1230f * sy),
                            strokeWidth = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
                        )
                        drawRoundTtakji(match.playerPos.x * sx, match.playerPos.y * sy, sx, Inks.Red, true)
                        drawRoundTtakji(match.aiPos.x * sx, match.aiPos.y * sy, sx, Inks.Blue, false)
                        val start = dragStart
                        val now = dragNow
                        if (start != null && now != null) {
                            val dx = start.x - now.x
                            val dy = start.y - now.y
                            val len = sqrt(dx * dx + dy * dy)
                            if (len > 12f) {
                                val from = if (twoPlayer && !match.waitingForPlayer) {
                                    Offset(match.aiPos.x * sx, match.aiPos.y * sy)
                                } else {
                                    Offset(match.playerPos.x * sx, match.playerPos.y * sy)
                                }
                                val reach = min(len * 2.4f, size.width * 0.9f)
                                val to = Offset(from.x + dx / len * reach, from.y + dy / len * reach)
                                drawLine(
                                    color = Inks.Red,
                                    start = from,
                                    end = to,
                                    strokeWidth = 7f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 12f)),
                                )
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
                    Text(match.outcome, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Inks.Ink)
                }
                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 10.dp) {
                    Column {
                        Text("1P 벽까지 거리 · ${(match.playerPos.y - RoundTtakjiPhysics.WALL_LINE).toInt()}",
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Inks.SoftInk)
                        Spacer(Modifier.height(3.dp))
                        StatBar(
                            value = 1f - ((match.playerPos.y - RoundTtakjiPhysics.WALL_LINE) /
                                (RoundTtakjiPhysics.START_LINE - RoundTtakjiPhysics.WALL_LINE)).coerceIn(0f, 1f),
                            color = Inks.Red,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("상대 벽까지 거리 · ${(match.aiPos.y - RoundTtakjiPhysics.WALL_LINE).toInt()}",
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Inks.SoftInk)
                        Spacer(Modifier.height(3.dp))
                        StatBar(
                            value = 1f - ((match.aiPos.y - RoundTtakjiPhysics.WALL_LINE) /
                                (RoundTtakjiPhysics.START_LINE - RoundTtakjiPhysics.WALL_LINE)).coerceIn(0f, 1f),
                            color = Inks.Blue,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "벽에 더 가까운 쪽이 1점, 상대 딱지를 덮으면 2점. 먼저 5점!",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Inks.SoftInk,
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

/* ───────────────────────── 멀리 날리기 ───────────────────────── */

@Composable
private fun DistanceGameScreen(twoPlayer: Boolean, onExit: () -> Unit) {
    val match = remember { DistanceMatch(twoPlayer) }
    var tick by remember { mutableIntStateOf(0) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragNow by remember { mutableStateOf<Offset?>(null) }
    var power by remember { mutableStateOf(0f) }
    var angle by remember { mutableStateOf(45f) }
    val shooter: Side? = when {
        match.finished -> null
        match.playerShot == null -> Side.PLAYER
        twoPlayer && match.aiShot == null -> Side.AI
        else -> null
    }

    LaunchedEffect(tick) {
        if (!twoPlayer && match.playerShot != null && match.aiShot == null && !match.finished) {
            delay(700)
            match.aiShoot()
            tick++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Inks.Kraft, Inks.KraftDeep, Inks.Kraft)))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "멀리 날리기", onBack = onExit)
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
                            else -> if (twoPlayer) "2P 승리!" else "AI에게 졌다…"
                        }
                        match.playerShot == null -> if (twoPlayer) "1P 차례" else "내 차례 — 각도와 힘을 맞춰 끌기"
                        else -> if (twoPlayer) "2P 차례" else "AI가 날리는 중…"
                    },
                    color = if (match.finished) Inks.Green else Inks.Red,
                    trailing = "${match.round}판 · 먼저 3점",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScorePill(if (twoPlayer) "1P" else "나", "${match.playerScore}", Inks.Red, Modifier.weight(1f))
                    ScorePill(
                        "바람",
                        if (match.wind >= 0f) "→ ${abs(match.wind).toInt()}" else "← ${abs(match.wind).toInt()}",
                        Inks.Teal,
                        Modifier.weight(1f),
                    )
                    ScorePill(if (twoPlayer) "2P" else "AI", "${match.aiScore}", Inks.Blue, Modifier.weight(1f))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1000f / 620f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFFB6DDF0), Color(0xFFEAF4E3))))
                        .border(3.dp, Inks.Wood, RoundedCornerShape(18.dp))
                        .pointerInput(match, tick) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    if (shooter != null) {
                                        dragStart = offset
                                        dragNow = offset
                                        power = 0f
                                        angle = 45f
                                    }
                                },
                                onDrag = { change, _ ->
                                    val start = dragStart
                                    if (start != null) {
                                        dragNow = change.position
                                        change.consume()
                                        val dx = start.x - change.position.x
                                        val dy = start.y - change.position.y
                                        val len = sqrt(dx * dx + dy * dy)
                                        power = (len / (size.width * 0.42f)).coerceIn(0f, 1f)
                                        if (len > 6f) {
                                            val deg = Math.toDegrees(
                                                atan2(-dy / len.toDouble(), dx / len.toDouble())
                                            ).toFloat()
                                            angle = deg.coerceIn(5f, 80f)
                                        }
                                    }
                                },
                                onDragEnd = {
                                    val start = dragStart
                                    val now = dragNow
                                    if (start != null && now != null && shooter != null) {
                                        val dx = start.x - now.x
                                        val dy = start.y - now.y
                                        val len = sqrt(dx * dx + dy * dy)
                                        if (len > 22f) {
                                            val deg = Math.toDegrees(
                                                atan2(-dy / len.toDouble(), dx / len.toDouble())
                                            ).toFloat().coerceIn(5f, 80f)
                                            match.shoot(shooter, deg, power.coerceAtLeast(0.1f))
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
                        val groundY = size.height * 0.9f
                        val s = size.width / 1000f
                        // 땅
                        drawRect(
                            color = Color(0xFFB8CE8E),
                            topLeft = Offset(0f, groundY),
                            size = Size(size.width, size.height - groundY),
                        )
                        drawLine(Inks.Wood.copy(alpha = 0.5f), Offset(0f, groundY), Offset(size.width, groundY), 3f)
                        // 런치패드
                        drawRect(
                            color = Inks.Red,
                            topLeft = Offset(20f * s, groundY - 6f),
                            size = Size(38f * s, 10f),
                        )
                        fun xOf(unit: Float) = (40f + unit * 0.46f) * s
                        fun yOf(height: Float) = groundY - height * 0.46f * s
                        match.playerShot?.let { shot ->
                            drawShotArc(shot, match.wind, ::xOf, ::yOf, groundY, Inks.Red)
                        }
                        match.aiShot?.let { shot ->
                            drawShotArc(shot, match.wind, ::xOf, ::yOf, groundY, Inks.Blue)
                        }
                        // 바람 화살표
                        val windLen = (abs(match.wind) / DistanceMatch.MAX_WIND) * 90f * s
                        if (windLen > 3f) {
                            val cx = size.width * 0.5f
                            val cy = 46f * s
                            val dir = if (match.wind >= 0f) 1f else -1f
                            drawLine(
                                color = Inks.Teal,
                                start = Offset(cx - windLen * dir, cy),
                                end = Offset(cx + windLen * dir, cy),
                                strokeWidth = 7f,
                            )
                            drawCircle(
                                color = Inks.Teal,
                                radius = 8f,
                                center = Offset(cx + windLen * dir, cy),
                            )
                        }
                        // 조준
                        val st = dragStart
                        val nw = dragNow
                        if (st != null && nw != null) {
                            val from = Offset(30f * s, groundY)
                            val rad = Math.toRadians(angle.toDouble())
                            val reach = (120f + power * 260f) * s
                            val to = Offset(
                                from.x + (cos(rad) * reach).toFloat(),
                                from.y - (sin(rad) * reach).toFloat(),
                            )
                            drawLine(
                                color = Inks.Red,
                                start = from,
                                end = to,
                                strokeWidth = 7f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 10f)),
                            )
                            drawCircle(Inks.Red, radius = 8f, center = to)
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
                            text = "각도 ${angle.toInt()}° · 힘 ${(power * 100).toInt()}% · 끌어서 놓기",
                            color = Inks.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShotCard(
                        title = if (twoPlayer) "1P" else "나",
                        shot = match.playerShot,
                        color = Inks.Red,
                        modifier = Modifier.weight(1f),
                    )
                    ShotCard(
                        title = if (twoPlayer) "2P" else "AI",
                        shot = match.aiShot,
                        color = Inks.Blue,
                        modifier = Modifier.weight(1f),
                    )
                }

                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 11.dp, color = Color(0xFFFFF3D6)) {
                    Text(match.outcome, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Inks.Ink)
                }
                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 10.dp) {
                    Text(
                        "바람은 판마다 바뀝니다. 바람이 뒤에서 밀면 조금 낮게, 앞에서 막으면 높고 세게!",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Inks.SoftInk,
                    )
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun ShotCard(title: String, shot: DistanceShot?, color: Color, modifier: Modifier = Modifier) {
    PaperCard(modifier = modifier, padding = 10.dp) {
        Column {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Black, color = color)
            Spacer(Modifier.height(3.dp))
            Text(
                if (shot == null) "아직 안 던짐" else "%.1fm".format(shot.meters),
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = Inks.Ink,
            )
            if (shot != null) {
                Text(
                    "각도 ${shot.angleDeg.toInt()}° · 힘 ${(shot.power * 100).toInt()}%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Inks.SoftInk,
                )
            }
        }
    }
}

private fun DrawScope.drawShotArc(
    shot: DistanceShot,
    wind: Float,
    xOf: (Float) -> Float,
    yOf: (Float) -> Float,
    groundY: Float,
    color: Color,
) {
    val rad = Math.toRadians(shot.angleDeg.toDouble())
    val v = DistanceMatch.LAUNCH_SPEED * shot.power
    val vx = (v * cos(rad)).toFloat()
    val vy = (v * sin(rad)).toFloat()
    val tEnd = 2f * vy / DistanceMatch.GRAVITY
    var prev: Offset? = null
    var i = 0
    while (i <= 32) {
        val t = tEnd * i / 32f
        val x = vx * t + 0.5f * wind * t * t
        val y = (vy * t - 0.5f * DistanceMatch.GRAVITY * t * t).coerceAtLeast(0f)
        val point = Offset(xOf(x), yOf(y))
        prev?.let { drawLine(color = color, start = it, end = point, strokeWidth = 5f) }
        prev = point
        i++
    }
    drawLine(
        color = color,
        start = Offset(xOf(shot.distance), groundY - 30f),
        end = Offset(xOf(shot.distance), groundY),
        strokeWidth = 6f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
    )
    drawCircle(color = color, radius = 9f, center = Offset(xOf(shot.distance), groundY - 4f))
}

/* ───────────────────────── 입바람 넘기기 ───────────────────────── */

@Composable
private fun BlowGameScreen(twoPlayer: Boolean, onExit: () -> Unit) {
    val match = remember { BlowMatch(twoPlayer) }
    var tick by remember { mutableIntStateOf(0) }
    var lastActor by remember { mutableStateOf<Side?>(null) }
    var micOn by remember { mutableStateOf(false) }
    var micBlocked by remember { mutableStateOf(false) }
    var level by remember { mutableStateOf(0f) }
    var swipeStrength by remember { mutableStateOf(0f) }
    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapAt by remember { mutableLongStateOf(0L) }
    val meter = remember { MicBlowMeter() }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            micOn = meter.start()
            micBlocked = !micOn
        } else {
            micBlocked = true
        }
    }

    LaunchedEffect(micOn) {
        while (micOn) {
            level = meter.level
            delay(80)
        }
    }
    DisposableEffect(Unit) {
        onDispose { meter.stop() }
    }
    LaunchedEffect(tick) {
        if (!twoPlayer && !match.finished && lastActor == Side.PLAYER) {
            delay(650)
            match.aiBlow()
            lastActor = Side.AI
            tick++
        }
    }

    val canBlow = !match.finished && (twoPlayer || lastActor != Side.PLAYER)
    val activeSide = if (twoPlayer && lastActor == Side.PLAYER) Side.AI else Side.PLAYER

    fun doBlow(strength: Float) {
        if (!canBlow) return
        match.blow(activeSide, strength)
        lastActor = activeSide
        tick++
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Inks.Kraft, Inks.KraftDeep, Inks.Kraft)))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "입바람 넘기기", onBack = onExit)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TurnBanner(
                    text = when {
                        match.finished -> if (match.winner == Side.PLAYER) {
                            if (twoPlayer) "1P 승리!" else "내가 이겼다!"
                        } else {
                            if (twoPlayer) "2P 승리!" else "AI에게 졌다…"
                        }
                        twoPlayer -> if (activeSide == Side.PLAYER) "1P 차례 — 불어서 밀기" else "2P 차례 — 불어서 밀기"
                        lastActor == Side.PLAYER -> "AI가 부는 중…"
                        else -> "내 차례 — '파!' 불어 밀기"
                    },
                    color = if (match.finished) Inks.Green else if (activeSide == Side.PLAYER) Inks.Red else Inks.Blue,
                    trailing = "${match.round}판 · 먼저 3점",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScorePill(if (twoPlayer) "1P" else "나", "${match.playerScore}", Inks.Red, Modifier.weight(1f))
                    ScorePill("남은 입김", "${match.playerBlows}", Inks.Gold, Modifier.weight(1f))
                    ScorePill(if (twoPlayer) "2P" else "AI", "${match.aiScore}", Inks.Blue, Modifier.weight(1f))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1000f / 640f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFFDCC49B))
                        .border(3.dp, Inks.Wood, RoundedCornerShape(18.dp))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val sx = size.width / 1000f
                        val sy = size.height / 640f
                        val finishX = BlowMatch.FINISH * sx
                        val endX = BlowMatch.TABLE_END * sx
                        // 테이블
                        drawRect(
                            color = Color(0xFFCFAF7C),
                            topLeft = Offset(0f, 60f * sy),
                            size = Size(size.width, size.height - 90f * sy),
                        )
                        drawLine(
                            color = Inks.Green,
                            start = Offset(finishX, 70f * sy),
                            end = Offset(finishX, size.height - 30f * sy),
                            strokeWidth = 8f,
                        )
                        drawLine(
                            color = Inks.Wood.copy(alpha = 0.35f),
                            start = Offset(endX, 70f * sy),
                            end = Offset(endX, size.height - 30f * sy),
                            strokeWidth = 3f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                        )
                        drawRoundTtakjiSmall(match.playerPos * sx, 240f * sy, sx, Inks.Red)
                        drawRoundTtakjiSmall(match.aiPos * sx, 440f * sy, sx, Inks.Blue)
                    }
                }

                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 10.dp) {
                    Column {
                        Text(
                            "1P 진행도 ${(match.playerProgress * 100).toInt()}% · ${match.playerBlows}번 남음",
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Inks.SoftInk,
                        )
                        Spacer(Modifier.height(3.dp))
                        StatBar(value = match.playerProgress, color = Inks.Red)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "상대 진행도 ${(match.aiProgress * 100).toInt()}%",
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Inks.SoftInk,
                        )
                        Spacer(Modifier.height(3.dp))
                        StatBar(value = match.aiProgress, color = Inks.Blue)
                    }
                }

                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 11.dp, color = Color(0xFFFFF3D6)) {
                    Text(match.outcome, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Inks.Ink)
                }

                // 마이크
                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 11.dp) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "🎤 입바람",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = Inks.Wood,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                when {
                                    micOn -> "마이크 켜짐"
                                    micBlocked -> "마이크 사용 불가"
                                    else -> "마이크 꺼짐"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Inks.SoftInk,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        StatBar(value = level, color = Inks.Teal, marker = BlowWind.NOISE_FLOOR)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FunButton(
                                text = if (micOn) "🎤 부는 중" else "마이크 켜기",
                                modifier = Modifier.weight(1f),
                                color = if (micOn) Inks.Teal else Inks.Blue,
                                fontSize = 15,
                            ) {
                                if (!micOn) {
                                    if (meter.hasPermission(context)) {
                                        micOn = meter.start()
                                        micBlocked = !micOn
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (micOn && canBlow) Inks.Red else Inks.Gray)
                                    .border(3.dp, Inks.Wood, RoundedCornerShape(16.dp))
                                    .pointerInput(micOn, canBlow) {
                                        detectTapGestures(
                                            onPress = {
                                                if (micOn && canBlow) {
                                                    meter.resetPeak()
                                                    tryAwaitRelease()
                                                    val s = BlowWind.strengthFromAmplitude(meter.peak)
                                                    doBlow(if (s < 0.06f) 0.06f else s)
                                                }
                                            }
                                        )
                                    }
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "누르고 파!",
                                    color = Inks.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (micBlocked) "마이크를 쓸 수 없어도 아래 스와이프·빠른 탭으로 똑같이 플레이할 수 있어요."
                            else "마이크에 대고 '파!' 하고 부는 동안 세게 불수록 멀리 밀립니다.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Inks.SoftInk,
                        )
                    }
                }

                // 대체 조작
                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 11.dp) {
                    Column {
                        Text("손으로 부는 대체 조작", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Inks.Wood)
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(74.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Inks.Teal.copy(alpha = 0.12f))
                                .border(2.dp, Inks.Teal, RoundedCornerShape(12.dp))
                                .pointerInput(canBlow) {
                                    var start: Offset? = null
                                    detectDragGestures(
                                        onDragStart = { start = it },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val st = start
                                            if (st != null) {
                                                val dx = change.position.x - st.x
                                                val dy = change.position.y - st.y
                                                swipeStrength = (sqrt(dx * dx + dy * dy) / (size.width * 0.7f))
                                                    .coerceIn(0f, 1f)
                                            }
                                        },
                                        onDragEnd = {
                                            if (canBlow) doBlow(swipeStrength.coerceAtLeast(0.05f))
                                            start = null
                                            swipeStrength = 0f
                                        },
                                        onDragCancel = {
                                            start = null
                                            swipeStrength = 0f
                                        },
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "위로 쓱 — 스와이프 (${(swipeStrength * 100).toInt()}%)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = Inks.Teal,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        FunButton(
                            text = "👏 빠른 탭으로 불기 (${tapCount}연타)",
                            modifier = Modifier.fillMaxWidth(),
                            color = Inks.Gold,
                            textColor = Inks.Ink,
                            fontSize = 14,
                        ) {
                            val now = System.currentTimeMillis()
                            tapCount = if (now - lastTapAt < 700L) min(tapCount + 1, 8) else 1
                            lastTapAt = now
                            doBlow(BlowWind.strengthFromTaps(tapCount).coerceAtLeast(0.08f))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "결승선까지 조금씩, 너무 세게 밀면 테이블 밖으로 날아가 처음 자리로 돌아갑니다.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Inks.SoftInk,
                        )
                    }
                }
                Spacer(Modifier.height(26.dp))
            }
        }
    }
}

private fun DrawScope.drawBrickWall(sx: Float, sy: Float) {
    val wallH = 180f * sy
    drawRect(
        color = Color(0xFFB06A4E),
        topLeft = Offset(0f, 0f),
        size = Size(size.width, wallH),
    )
    var row = 0
    val brickH = 34f * sy
    while (row * brickH < wallH) {
        val y = row * brickH
        drawLine(
            color = Color(0xFF8A4C36),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 2f,
        )
        var x = if (row % 2 == 0) 0f else 40f * sx
        while (x < size.width) {
            drawLine(
                color = Color(0xFF8A4C36),
                start = Offset(x, y),
                end = Offset(x, y + brickH),
                strokeWidth = 2f,
            )
            x += 80f * sx
        }
        row++
    }
    drawLine(
        color = Inks.Wood,
        start = Offset(0f, wallH),
        end = Offset(size.width, wallH),
        strokeWidth = 6f,
    )
}

private fun DrawScope.drawRoundTtakji(
    cx: Float,
    cy: Float,
    scale: Float,
    ring: Color,
    isMine: Boolean,
) {
    val r = RoundTtakjiPhysics.RADIUS * scale
    drawCircle(Color(0x33000000), radius = r, center = Offset(cx + 4f, cy + 6f))
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFFFFE9A6), Color(0xFFE0A02A)),
            center = Offset(cx, cy),
            radius = r,
        ),
        radius = r,
        center = Offset(cx, cy),
    )
    drawCircle(ring, radius = r, center = Offset(cx, cy), style = Stroke(width = 7f))
    drawCircle(ring.copy(alpha = 0.6f), radius = r * 0.42f, center = Offset(cx, cy), style = Stroke(width = 3f))
    if (isMine) {
        drawCircle(Inks.Red, radius = r * 0.18f, center = Offset(cx, cy))
    }
}

private fun DrawScope.drawRoundTtakjiSmall(cx: Float, cy: Float, scale: Float, ring: Color) {
    val r = 54f * scale * 0.8f
    drawCircle(Color(0x33000000), radius = r, center = Offset(cx + 3f, cy + 4f))
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFFFFE9A6), Color(0xFFE0A02A)),
            center = Offset(cx, cy),
            radius = r,
        ),
        radius = r,
        center = Offset(cx, cy),
    )
    drawCircle(ring, radius = r, center = Offset(cx, cy), style = Stroke(width = 5f))
}
