package com.game.gametong.game

import com.game.gametong.game.physics3d.MarbleSpin
import com.game.gametong.game.physics3d.SphereCcd
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** 바닥 위 구슬 하나. 왕구슬은 조금 크고 무겁다. */
data class MarbleBody(
    val id: Int,
    var pos: Vec2,
    var vel: Vec2 = Vec2.ZERO,
    val radius: Float = 24f,
    val king: Boolean = false,
    var sunk: Boolean = false,
    /** 굴러가는 각도(도). 무늬가 도는 연출에 쓴다. */
    var spin: Float = 0f,
    /** 가로 스핀(탑스핀·백스핀, 도/초). 궤적을 살짝 휘게 한다. */
    var sideSpin: Float = 0f,
) {
    val mass: Float get() = radius * radius * (if (king) 1.6f else 1f)
    val moving: Boolean get() = !sunk && vel.length() > 1f
}

/**
 * 구슬 여러 개가 구르는 바닥.
 *
 * - **연속 충돌 검사(CCD)**: 고속으로 나는 구슬도 상대를 관통하지 않도록,
 *   이번 스텝 안에서 처음 닿는 시각을 풀어 그 지점에서 충돌을 처리한다([SphereCcd]).
 * - **탄성 충돌**: 질량·반발계수를 부른 순차 임펄스.
 * - **스핀**: 스치는 충돌은 가로 스핀을 옮겨 궤적을 살짝 휘게 하고, 굴러가는 각도를 적분한다.
 * - **구멍 경사**: 구멍 근처에서는 바닥이 비스듬해져 구멍 쪽으로 미끄러져 내려간다([holeSlope]).
 */
class MarbleWorld(
    val width: Float = 1000f,
    val height: Float = 1060f,
    val friction: Float = 480f,
    val restitution: Float = 0.94f,
    val marbles: MutableList<MarbleBody> = mutableListOf(),
    /** 구멍 위치(구멍치기). 비어 있으면 경사가 없다. */
    val holes: List<Vec2> = emptyList(),
    val holeRadius: Float = 0f,
    /** 구멍으로 미끄러지는 가속도(단위/초²). 0이면 경사가 없다. */
    val holeSlope: Float = 0f,
) {
    var lastHitA: Int = -1
    var lastHitB: Int = -1
    var elapsed: Float = 0f
        private set

    val moving: Boolean get() = marbles.any { it.moving }

    fun body(id: Int): MarbleBody? = marbles.firstOrNull { it.id == id }

    fun flick(id: Int, vel: Vec2) {
        val body = body(id) ?: return
        body.vel = vel
    }

    fun step(dt: Float = FIXED_DT) {
        elapsed += dt
        // 구멍 언저리 경사는 마찰보다 먼저 작용한다(비스듬한 바닥에 놓인 구슬이 굴러 내려간다).
        applyHoleSlope(dt)
        var remaining = dt
        var guard = 0
        while (remaining > 1e-5f && guard < 10) {
            guard++
            val t = earliestImpact(remaining)
            when {
                t == null -> {
                    integrate(remaining)
                    resolveWalls()
                    remaining = 0f
                }
                t <= 1e-5f -> {
                    resolveCollisions()
                    resolveWalls()
                    integrate(1e-4f)
                    remaining -= 1e-4f
                }
                else -> {
                    integrate(t)
                    resolveCollisions()
                    resolveWalls()
                    remaining -= t
                }
            }
        }
        applySpin(dt)
    }

    /** 이번 시간창(초) 안에서 가장 빨리 부딪히는 시각(초). 없으면 null. */
    private fun earliestImpact(window: Float): Float? {
        var best: Float? = null
        for (i in marbles.indices) {
            val a = marbles[i]
            if (a.sunk || a.vel.lengthSq() < 1e-6f) continue
            for (j in i + 1 until marbles.size) {
                val b = marbles[j]
                if (b.sunk) continue
                // 시간 단위(초)로 바로 나온다.
                val t = SphereCcd.timeOfImpact(a.pos, a.vel, b.pos, b.vel, a.radius + b.radius)
                if (t != null && t <= window) {
                    if (best == null || t < best!!) best = t
                }
            }
        }
        for (m in marbles) {
            if (m.sunk || m.vel.lengthSq() < 1e-6f) continue
            val t = SphereCcd.wallTimeOfImpact(
                m.pos, m.vel,
                minX = m.radius, maxX = width - m.radius,
                minY = m.radius, maxY = height - m.radius,
            )
            if (t != null && t <= window) {
                if (best == null || t < best!!) best = t
            }
        }
        return best
    }

    private fun Vec2.lengthSq(): Float = x * x + y * y

    /** 마찰하며 위치를 옮긴다. */
    private fun integrate(slice: Float) {
        if (slice <= 0f) return
        for (m in marbles) {
            if (m.sunk) continue
            val v = m.vel.length()
            if (v > 0f) {
                val next = (v - friction * slice).coerceAtLeast(0f)
                m.vel = if (next <= 1f) Vec2.ZERO else m.vel * (next / v)
            }
            if (m.vel.length() <= 0f) continue
            m.pos = m.pos + m.vel * slice
        }
    }

    /** 바닥 경계에서 살짝 튕긴다. */
    private fun resolveWalls() {
        for (m in marbles) {
            if (m.sunk) continue
            if (m.pos.x < m.radius) {
                m.pos = Vec2(m.radius, m.pos.y)
                m.vel = Vec2(-m.vel.x * restitution, m.vel.y)
            } else if (m.pos.x > width - m.radius) {
                m.pos = Vec2(width - m.radius, m.pos.y)
                m.vel = Vec2(-m.vel.x * restitution, m.vel.y)
            }
            if (m.pos.y < m.radius) {
                m.pos = Vec2(m.pos.x, m.radius)
                m.vel = Vec2(m.vel.x, -m.vel.y * restitution)
            } else if (m.pos.y > height - m.radius) {
                m.pos = Vec2(m.pos.x, height - m.radius)
                m.vel = Vec2(m.vel.x, -m.vel.y * restitution)
            }
        }
    }

    fun resolveCollisions() {
        for (i in marbles.indices) {
            for (j in i + 1 until marbles.size) {
                val a = marbles[i]
                val b = marbles[j]
                if (a.sunk || b.sunk) continue
                val delta = b.pos - a.pos
                val dist = delta.length()
                val minDist = a.radius + b.radius
                if (dist <= 0.0001f || dist >= minDist) continue
                val n = delta * (1f / dist)
                val overlap = minDist - dist
                val invA = 1f / a.mass
                val invB = 1f / b.mass
                val invSum = invA + invB
                a.pos = a.pos - n * (overlap * invA / invSum)
                b.pos = b.pos + n * (overlap * invB / invSum)
                lastHitA = a.id
                lastHitB = b.id
                val rel = b.vel - a.vel
                val vn = rel.dot(n)
                if (vn >= 0f) continue
                val impulse = -(1f + restitution) * vn / invSum
                a.vel = a.vel - n * (impulse * invA)
                b.vel = b.vel + n * (impulse * invB)

                // 스치는 충돌: 접선 속도가 가로 스핀으로 옮겨 붙는다(백스핀/탑스핀).
                val tangent = Vec2(-n.y, n.x)
                val tangential = rel.dot(tangent)
                a.sideSpin += tangential * 0.16f
                b.sideSpin -= tangential * 0.16f
            }
        }
    }

    /** 굴러가는 각도와 가로 스핀(궤적을 살짝 휘게 한다). */
    private fun applySpin(dt: Float) {
        for (m in marbles) {
            if (m.sunk) continue
            val speed = m.vel.length()
            if (speed > 0f) {
                m.spin = (m.spin + MarbleSpin.rollRate(speed, m.radius) * dt) % 360f
                if (m.sideSpin != 0f) {
                    val dir = m.vel * (1f / speed)
                    val lateral = Vec2(-dir.y, dir.x)
                    m.vel = m.vel + lateral * (m.sideSpin * 0.30f * dt)
                }
            }
            m.sideSpin *= kotlin.math.exp((-MarbleSpin.DECAY * dt).toDouble()).toFloat()
            if (kotlin.math.abs(m.sideSpin) < 1f) m.sideSpin = 0f
        }
    }

    /** 구멍 근처의 비스듬한 바닥: 구멍 쪽으로 미끄러져 내려간다. */
    private fun applyHoleSlope(dt: Float) {
        if (holes.isEmpty() || holeSlope <= 0f || holeRadius <= 0f) return
        val reach = holeRadius * 1.25f
        for (m in marbles) {
            if (m.sunk) continue
            // 빠르게 지나가는 구슬은 구멍 언저리를 그냥 넘어간다(굴러 넘는 물리).
            if (m.vel.length() > 300f) continue
            for (hole in holes) {
                val d = m.pos.distanceTo(hole)
                if (d > reach || d < 0.5f) continue
                val dir = (hole - m.pos).normalized()
                val strength = holeSlope * (0.5f + 0.5f * (1f - d / reach))
                m.vel = m.vel + dir * (strength * dt)
            }
        }
    }

    fun simulateUntilRest(maxSeconds: Float = 6f): Float {
        var t = 0f
        while (moving && t < maxSeconds) {
            step()
            t += FIXED_DT
        }
        return t
    }

    companion object {
        /** 고정 타임스텝(초). 120Hz 로 계산한다. */
        const val FIXED_DT = 1f / 120f
    }
}

/** 구슬 조준 도우미(AI와 테스트가 같은 식을 쓴다). */
object MarbleAim {
    const val MAX_SPEED = 1500f
    const val FRICTION = 480f

    fun aimDeg(from: Vec2, to: Vec2, forwardSign: Float): Float {
        val d = (to - from).normalized()
        val y = if (forwardSign < 0f) -d.y else d.y
        return Math.toDegrees(atan2(d.x.toDouble(), y.toDouble())).toFloat()
    }

    fun powerFor(distance: Float, speedScale: Float = 1.06f, friction: Float = FRICTION): Float {
        val v = sqrt(2f * friction * distance.coerceAtLeast(0f)) * speedScale
        return (v / MAX_SPEED).coerceIn(0.08f, 1f)
    }

    fun velocity(aimDeg: Float, power: Float, forwardSign: Float): Vec2 {
        val dir = SquareTtakjiEngine.aimDirection(aimDeg, forwardSign)
        return dir * (MAX_SPEED * power.coerceIn(0f, 1f))
    }
}

/** 원 안 구슬 쳐내기 한 수의 결과. */
data class CircleShotResult(
    val side: Side,
    val claimed: List<Int>,
    val kingInField: Boolean,
    val travelled: Float,
)

/**
 * 원 안 구슬 쳐내기.
 *
 * 바닥에 그린 원 안에 유리구슬이 모여 있다. 왕구슬을 튕겨 넣어 구슬을 원 밖으로 쳐내면 그 구슬을 따먹는다.
 * 서로 번갈아 8번씩 치고, 더 많이 따먹은 쪽이 이긴다.
 */
class MarbleCircleGame(
    val twoPlayer: Boolean = false,
    val aiSkill: Float = 0.74f,
    private val random: Random = Random(2026),
    marbleCount: Int = 7,
) {
    companion object {
        const val CIRCLE_X = 500f
        const val CIRCLE_Y = 520f
        const val CIRCLE_R = 300f
        const val KING_RADIUS = 34f
        const val MARBLE_RADIUS = 24f
        const val MAX_TURNS = 8
        const val WIN_SCORE = 3
    }

    val center = Vec2(CIRCLE_X, CIRCLE_Y)
    val circleRadius = CIRCLE_R
    val playerLaunch = Vec2(CIRCLE_X, 1010f)
    val aiLaunch = Vec2(CIRCLE_X, 50f)

    val king: MarbleBody = MarbleBody(0, playerLaunch, radius = KING_RADIUS, king = true)
    val glasses: MutableList<MarbleBody> = buildList {
        repeat(marbleCount) { index ->
            add(MarbleBody(index + 1, glassStart(index, marbleCount), radius = MARBLE_RADIUS))
        }
    }.toMutableList()

    val world = MarbleWorld(
        width = 1000f,
        height = 1060f,
        friction = 470f,
        restitution = 0.95f,
        marbles = (listOf(king) + glasses).toMutableList(),
    )

    var turn: Side = Side.PLAYER
        private set
    var playerScore: Int = 0
        private set
    var aiScore: Int = 0
        private set
    var playerShots: Int = 0
        private set
    var aiShots: Int = 0
        private set
    var finished: Boolean = false
        private set
    var winner: Side? = null
        private set
    var lastResult: CircleShotResult? = null
        private set
    var outcome: String = "왕구슬을 원 안으로 튕겨 유리구슬을 원 밖으로 쳐내세요."
        private set

    val remainingGlasses: Int get() = glasses.count { !it.sunk }

    fun launchPoint(side: Side): Vec2 = if (side == Side.PLAYER) playerLaunch else aiLaunch

    private fun glassStart(index: Int, count: Int): Vec2 {
        // 원 안에 골고루 퍼지도록 결정적인 배치를 쓴다(테스트도 같은 자리에서 시작).
        val ring = if (index % 2 == 0) 0.55f else 0.28f
        val angle = Math.toRadians((index * 137.5) % 360.0)
        val r = CIRCLE_R * (0.35f + ring)
        return Vec2(
            CIRCLE_X + (cos(angle) * r).toFloat(),
            CIRCLE_Y + (sin(angle) * r).toFloat(),
        )
    }

    /** 치기 전에 왕구슬을 각자의 출발점으로 옮긴다. */
    fun placeKing(side: Side) {
        king.pos = launchPoint(side)
        king.vel = Vec2.ZERO
        king.sunk = false
    }

    fun shoot(side: Side, aimDeg: Float, power: Float): CircleShotResult {
        if (finished) return CircleShotResult(side, emptyList(), true, 0f)
        placeKing(side)
        val start = king.pos
        king.vel = MarbleAim.velocity(aimDeg, power, if (side == Side.PLAYER) -1f else 1f)
        world.simulateUntilRest()
        val travelled = king.pos.distanceTo(start)
        val claimed = mutableListOf<Int>()
        for (glass in glasses) {
            if (glass.sunk) continue
            if (glass.pos.distanceTo(center) > circleRadius + glass.radius * 0.4f) {
                glass.sunk = true
                glass.vel = Vec2.ZERO
                claimed += glass.id
            }
        }
        if (claimed.isNotEmpty()) {
            if (side == Side.PLAYER) playerScore += claimed.size else aiScore += claimed.size
            outcome = (if (side == Side.PLAYER) "구슬 ${claimed.size}개를 쳐냈다! +${claimed.size}점."
            else "상대가 구슬 ${claimed.size}개를 쳐냈다… -${claimed.size}점.")
        } else {
            outcome = if (side == Side.PLAYER) "아깝다, 원 안에 남았다." else "상대가 빗나갔다."
        }
        when (side) {
            Side.PLAYER -> playerShots++
            Side.AI -> aiShots++
        }
        val result = CircleShotResult(side, claimed, true, travelled)
        lastResult = result
        checkFinish()
        if (!finished) {
            turn = side.other()
            placeKing(turn)
        }
        return result
    }

    fun aiShoot(): CircleShotResult {
        if (twoPlayer) return shoot(Side.AI, 0f, 0.5f)
        val target: Vec2 = glasses.filter { !it.sunk }
            .maxByOrNull { it.pos.distanceTo(center) }
            ?.pos ?: center
        val from = aiLaunch
        val aim = MarbleAim.aimDeg(from, target, 1f)
        val base = MarbleAim.powerFor(from.distanceTo(target), speedScale = 1.12f)
        val miss = 1f - aiSkill.coerceIn(0f, 1f)
        val power = (base + (random.nextFloat() * 2f - 1f) * 0.2f * miss).coerceIn(0.1f, 1f)
        val angle = aim + (random.nextFloat() * 2f - 1f) * 8f * miss
        return shoot(Side.AI, angle, power)
    }

    private fun checkFinish() {
        if (remainingGlasses == 0 || (playerShots >= MAX_TURNS && aiShots >= MAX_TURNS)) {
            finished = true
            winner = when {
                playerScore > aiScore -> Side.PLAYER
                aiScore > playerScore -> Side.AI
                else -> null
            }
        }
    }
}

/** 구멍치기 한 수의 결과. */
data class HoleShotResult(
    val side: Side,
    val holeIndex: Int,
    val progressed: Boolean,
    val wrongOrder: Boolean,
    val claimedOpponent: Boolean,
    val hitOpponent: Boolean,
    val endPos: Vec2,
)

/**
 * 구멍치기.
 *
 * 바닥에 뚫린 구멍 3개를 순서대로 통과한 뒤, 상대 구슬을 맞히면 그 구슬을 따먹는다.
 * 성공하면 같은 사람이 한 번 더 치고, 실패하면 차례가 넘어간다.
 */
class MarbleHoleGame(
    val twoPlayer: Boolean = false,
    val aiSkill: Float = 0.7f,
    private val random: Random = Random(404),
) {
    companion object {
        const val HOLE_R = 54f
        const val MARBLE_R = 26f
        const val CATCH_SPEED = 470f
        const val MAX_TURNS = 10
        const val WIN_SCORE = 3

        /** 구멍 가장자리의 비스듬한 경사(구멍으로 빨려 들어가는 가속도, 단위/초²). */
        const val HOLE_SLOPE = 1800f
    }

    val holes = listOf(Vec2(500f, 250f), Vec2(500f, 530f), Vec2(500f, 810f))
    val playerStart = Vec2(360f, 960f)
    val aiStart = Vec2(640f, 960f)

    val playerMarble = MarbleBody(1, playerStart, radius = MARBLE_R)
    val aiMarble = MarbleBody(2, aiStart, radius = MARBLE_R)
    val world = MarbleWorld(
        width = 1000f,
        height = 1080f,
        friction = 460f,
        restitution = 0.95f,
        marbles = mutableListOf(playerMarble, aiMarble),
        holes = holes,
        holeRadius = HOLE_R,
        holeSlope = HOLE_SLOPE,
    )

    var turn: Side = Side.PLAYER
        private set
    var playerProgress: Int = 0
        private set
    var aiProgress: Int = 0
        private set
    var playerScore: Int = 0
        private set
    var aiScore: Int = 0
        private set
    var playerShots: Int = 0
        private set
    var aiShots: Int = 0
        private set
    var finished: Boolean = false
        private set
    var winner: Side? = null
        private set
    var outcome: String = "구멍 1 → 2 → 3 순서로 넣고, 그다음 상대 구슬을 맞히세요."
        private set
    var lastResult: HoleShotResult? = null
        private set

    fun marbleOf(side: Side): MarbleBody = if (side == Side.PLAYER) playerMarble else aiMarble
    fun progressOf(side: Side): Int = if (side == Side.PLAYER) playerProgress else aiProgress
    fun startOf(side: Side): Vec2 = if (side == Side.PLAYER) playerStart else aiStart
    val nextHoleIndex: Int get() = progressOf(turn).coerceAtMost(2)

    fun resetMarble(side: Side) {
        val body = marbleOf(side)
        body.pos = startOf(side)
        body.vel = Vec2.ZERO
        body.sunk = false
    }

    fun shoot(side: Side, aimDeg: Float, power: Float): HoleShotResult {
        if (finished) return HoleShotResult(side, -1, false, false, false, false, startOf(side))
        resetMarble(side)
        val mine = marbleOf(side)
        val other = marbleOf(side.other())
        mine.vel = MarbleAim.velocity(aimDeg, power, if (side == Side.PLAYER) -1f else 1f)
        world.lastHitA = -1
        world.lastHitB = -1

        var progress = progressOf(side)
        var holeIndex = -1
        var progressed = false
        var wrongOrder = false
        var hitOpponent = false
        var sunk = false
        var t = 0f
        while (world.moving && t < 6f) {
            world.step()
            t += 1f / 120f
            if (world.lastHitA == mine.id && world.lastHitB == other.id ||
                world.lastHitB == mine.id && world.lastHitA == other.id
            ) {
                hitOpponent = true
            }
            if (sunk) continue
            for ((index, hole) in holes.withIndex()) {
                val near = mine.pos.distanceTo(hole) <= HOLE_R * 0.88f
                if (near && mine.vel.length() <= CATCH_SPEED) {
                    holeIndex = index
                    mine.pos = hole
                    mine.vel = Vec2.ZERO
                    sunk = true
                    if (index == progress) {
                        progress++
                        progressed = true
                    } else {
                        wrongOrder = true
                    }
                    break
                }
            }
        }

        var claimed = false
        if (progress >= 3 && hitOpponent) {
            claimed = true
            if (side == Side.PLAYER) playerScore++ else aiScore++
            progress = 0
        }
        when (side) {
            Side.PLAYER -> {
                playerProgress = progress
                playerShots++
            }
            Side.AI -> {
                aiProgress = progress
                aiShots++
            }
        }
        outcome = when {
            claimed -> {
                if (side == Side.PLAYER) "상대 구슬을 맞혔다! 한 개 따먹고 다시 1번 구멍부터."
                else "상대가 내 구슬을 따먹었다…"
            }
            progressed -> {
                if (side == Side.PLAYER) "${holeIndex + 1}번 구멍 성공! 한 번 더 칩니다."
                else "상대가 ${holeIndex + 1}번 구멍에 넣었다."
            }
            wrongOrder -> {
                if (side == Side.PLAYER) "${holeIndex + 1}번 구멍은 아직 순서가 아니다."
                else "상대가 순서를 틀렸다."
            }
            hitOpponent -> "상대 구슬을 맞혔지만 구멍 3개를 아직 다 넣지 않았다."
            else -> if (side == Side.PLAYER) "아깝다, 빗나갔다." else "상대가 빗나갔다."
        }
        val result = HoleShotResult(
            side = side,
            holeIndex = holeIndex,
            progressed = progressed,
            wrongOrder = wrongOrder,
            claimedOpponent = claimed,
            hitOpponent = hitOpponent,
            endPos = mine.pos,
        )
        lastResult = result
        checkFinish()
        if (!finished) {
            val again = progressed && !claimed
            if (!again) turn = side.other()
            resetMarble(turn)
            resetMarble(turn.other())
        }
        return result
    }

    fun aiShoot(): HoleShotResult {
        if (twoPlayer) return shoot(Side.AI, 0f, 0.5f)
        val me = aiMarble
        val target = if (progressOf(Side.AI) >= 3) playerMarble.pos
        else holes[progressOf(Side.AI)]
        val aim = MarbleAim.aimDeg(me.pos, target, 1f)
        val base = MarbleAim.powerFor(me.pos.distanceTo(target), speedScale = 1.02f)
        val miss = 1f - aiSkill.coerceIn(0f, 1f)
        val angle = aim + (random.nextFloat() * 2f - 1f) * 6f * miss
        val power = (base + (random.nextFloat() * 2f - 1f) * 0.12f * miss).coerceIn(0.1f, 1f)
        return shoot(Side.AI, angle, power)
    }

    private fun checkFinish() {
        if (playerScore >= WIN_SCORE || aiScore >= WIN_SCORE ||
            (playerShots >= MAX_TURNS && aiShots >= MAX_TURNS)
        ) {
            finished = true
            winner = when {
                playerScore > aiScore -> Side.PLAYER
                aiScore > playerScore -> Side.AI
                else -> null
            }
        }
    }
}
