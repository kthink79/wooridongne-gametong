package com.game.gametong.game

import com.game.gametong.game.physics3d.MarbleSpin
import com.game.gametong.game.physics3d.Quat
import com.game.gametong.game.physics3d.SphereCcd
import com.game.gametong.game.physics3d.Vec3
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
    /**
     * 굴러간 자세(월드 사원수). 이동 거리/반지름만큼 **접지점을 축으로** 굴린다.
     * 유리구슬 속 회오리 무늬가 진행 방향으로 돌아가야 "구르는" 것으로 보인다.
     */
    var rollQuat: Quat = Quat.IDENTITY,
) {
    val mass: Float get() = radius * radius * (if (king) 1.6f else 1f)
    val moving: Boolean get() = !sunk && vel.length() > 1f

    /** 이 구슬이 지금 어느 쪽으로 굴러가는지(단위 벡터). 멈췄으면 null. */
    val rollAxisWorld: Vec3?
        get() {
            val v = vel.length()
            if (v < 1f) return null
            // 무대 (x, y) → 세계 (x, z). 구르는 축 = 위 × 진행 방향.
            return Vec3(vel.y / v, 0f, -vel.x / v).normalized()
        }
}

/**
 * 한 스텝에 일어난 충돌 하나. 화면에서 불티·먼지를 터뜨리고 충격 링을 그리는 데 쓴다.
 *
 * @param strength 맞부딪힌 세기(무대 단위/초)
 * @param a 첫 구슬 id ([wall] 이면 같은 값)
 * @param b 두 번째 구슬 id
 * @param wall 바닥 경계에 부딪힌 것인지
 */
data class MarbleImpact(
    val x: Float,
    val y: Float,
    val strength: Float,
    val a: Int,
    val b: Int,
    val wall: Boolean = false,
)

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

    /** 이번 스텝들에서 일어난 충돌(화면이 [drainImpacts] 로 가져간다). */
    private val impactQueue = ArrayList<MarbleImpact>(16)

    val moving: Boolean get() = marbles.any { it.moving }

    /** 쌓인 충돌을 모두 가져가고 비운다. */
    fun drainImpacts(): List<MarbleImpact> {
        if (impactQueue.isEmpty()) return emptyList()
        val out = ArrayList<MarbleImpact>(impactQueue)
        impactQueue.clear()
        return out
    }

    /** 가장 최근 충돌 세기(카메라 흔들림·소리 연출용). */
    var lastImpactStrength: Float = 0f
        private set

    private fun recordImpact(impact: MarbleImpact) {
        if (impact.strength < 18f) return
        if (impactQueue.size >= 24) impactQueue.removeAt(0)
        impactQueue += impact
        if (impact.strength > lastImpactStrength) lastImpactStrength = impact.strength
        lastImpactStrength *= 0.9f
    }

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
                val hit = abs(m.vel.x)
                m.vel = Vec2(-m.vel.x * restitution, m.vel.y)
                recordImpact(MarbleImpact(m.pos.x, m.pos.y, hit, m.id, m.id, wall = true))
            } else if (m.pos.x > width - m.radius) {
                m.pos = Vec2(width - m.radius, m.pos.y)
                val hit = abs(m.vel.x)
                m.vel = Vec2(-m.vel.x * restitution, m.vel.y)
                recordImpact(MarbleImpact(m.pos.x, m.pos.y, hit, m.id, m.id, wall = true))
            }
            if (m.pos.y < m.radius) {
                m.pos = Vec2(m.pos.x, m.radius)
                val hit = abs(m.vel.y)
                m.vel = Vec2(m.vel.x, -m.vel.y * restitution)
                recordImpact(MarbleImpact(m.pos.x, m.pos.y, hit, m.id, m.id, wall = true))
            } else if (m.pos.y > height - m.radius) {
                m.pos = Vec2(m.pos.x, height - m.radius)
                val hit = abs(m.vel.y)
                m.vel = Vec2(m.vel.x, -m.vel.y * restitution)
                recordImpact(MarbleImpact(m.pos.x, m.pos.y, hit, m.id, m.id, wall = true))
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
                // 맞부딪힌 지점(두 구슬 중심 사이)에 불티·먼지를 터뜨린다.
                recordImpact(
                    MarbleImpact(
                        x = a.pos.x + n.x * a.radius,
                        y = a.pos.y + n.y * a.radius,
                        strength = -vn,
                        a = a.id,
                        b = b.id,
                    )
                )
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

    /**
     * 굴러가는 자세·각도와 가로 스핀(궤적을 살짝 휘게 한다).
     *
     * 굴림은 **이동 거리 / 반지름** 만큼 접지점을 축으로 돌린다(미끄러짐 없이 구르는 조건).
     * 유리구슬 속 회오리 무늬가 이 회전을 따라 돌아가야 공이 구르는 것으로 보인다.
     */
    private fun applySpin(dt: Float) {
        for (m in marbles) {
            if (m.sunk) continue
            val speed = m.vel.length()
            if (speed > 0f) {
                m.spin = (m.spin + MarbleSpin.rollRate(speed, m.radius) * dt) % 360f
                val axis = m.rollAxisWorld
                if (axis != null) {
                    val angle = (speed * dt) / m.radius.coerceAtLeast(1f)
                    if (angle > 1e-4f) {
                        m.rollQuat = (Quat.axisAngle(axis, angle) * m.rollQuat).normalized()
                    }
                }
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

/**
 * 조준 예측 결과. 당구처럼 "내 구슬이 어디에 맞고, 맞은 구슬이 어느 쪽으로 날아가는지"를 보여 준다.
 *
 * @param contact 처음 닿는 지점(무대 좌표)
 * @param targetId 맞는 구슬 id(벽이면 -1)
 * @param targetDir 맞은 구슬이 밀려나는 방향(단위)
 * @param cueDir 내 구슬이 꺾여 가는 방향(단위, 정면 충돌이면 영벡터)
 * @param distance 내 구슬이 그 지점까지 굴러가는 거리
 * @param wall 바닥 경계에 먼저 닿는지
 */
data class MarbleAimPrediction(
    val contact: Vec2,
    val targetId: Int,
    val targetDir: Vec2,
    val cueDir: Vec2,
    val distance: Float,
    val wall: Boolean,
)

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

    /**
     * 이 방향으로 굴러가면 **처음 무엇에 닿는지** 미리 계산한다(조준선·고스트 링).
     *
     * 구슬은 원(반지름 r1+r2)과 광선의 교점으로, 벽은 경계면까지의 거리로 구해 가까운 쪽을 고른다.
     * 이동 거리는 [maxDistance] 로 잘라 지나치게 긴 선을 그리지 않는다.
     */
    fun predict(
        world: MarbleWorld,
        from: Vec2,
        velocity: Vec2,
        radius: Float,
        ignoreId: Int? = null,
        maxDistance: Float = 1100f,
    ): MarbleAimPrediction? {
        val speed = velocity.length()
        if (speed < 1f) return null
        val d = velocity * (1f / speed)

        var bestT = maxDistance
        var bestBall: MarbleBody? = null
        for (m in world.marbles) {
            if (m.sunk || m.id == ignoreId) continue
            val toCenter = m.pos - from
            val proj = toCenter.dot(d)
            if (proj <= 0f) continue
            val sum = radius + m.radius
            val perpSq = toCenter.x * toCenter.x + toCenter.y * toCenter.y - proj * proj
            val hitSq = sum * sum - perpSq
            if (hitSq < 0f) continue
            val t = proj - sqrt(hitSq)
            if (t < 0f) continue
            if (t < bestT) {
                bestT = t
                bestBall = m
            }
        }

        // 벽까지의 거리(구슬 중심이 닿는 자리)
        val wallT = run {
            var t = maxDistance
            if (d.x > 1e-4f) t = minOf(t, (world.width - radius - from.x) / d.x)
            if (d.x < -1e-4f) t = minOf(t, (radius - from.x) / d.x)
            if (d.y > 1e-4f) t = minOf(t, (world.height - radius - from.y) / d.y)
            if (d.y < -1e-4f) t = minOf(t, (radius - from.y) / d.y)
            t.coerceAtLeast(0f)
        }

        val ball = bestBall
        if (ball == null && wallT >= maxDistance) {
            return MarbleAimPrediction(
                contact = from + d * maxDistance,
                targetId = -1,
                targetDir = Vec2.ZERO,
                cueDir = d,
                distance = maxDistance,
                wall = false,
            )
        }
        if (ball == null || wallT < bestT) {
            return MarbleAimPrediction(
                contact = from + d * wallT,
                targetId = -1,
                targetDir = Vec2.ZERO,
                cueDir = Vec2(-d.x, -d.y),
                distance = wallT,
                wall = true,
            )
        }
        val contact = from + d * bestT
        // 접점의 법선은 목표 중심 → 접점 방향이다. 맞은 구슬은 그 **반대쪽**으로 밀린다.
        val n = (contact - ball.pos).normalized()
        val along = d.dot(n)
        val cueDir = Vec2(d.x - n.x * along, d.y - n.y * along)
        return MarbleAimPrediction(
            contact = contact,
            targetId = ball.id,
            targetDir = if (n.length() > 0f) Vec2(-n.x, -n.y) else d,
            cueDir = if (cueDir.length() > 1e-3f) cueDir.normalized() else Vec2.ZERO,
            distance = bestT,
            wall = false,
        )
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

        /** 한 수가 굴러가는 최대 시간(초). 이보다 오래 걸리면 연출을 끊는다. */
        const val MAX_SHOT_SECONDS = 6.5f
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

    // ── 실제로 굴러가는 한 수(화면 연출용) ──────────────────────────
    // 예전에는 shoot() 안에서 끝까지 계산해 결과만 보여 줬다. 이제는 세 단계로 나눠
    // 화면이 매 프레임 [advanceShot] 을 불러 구슬이 구르고 부딪히는 과정을 그대로 보여 준다.

    /** 지금 구슬이 굴러가는 중인가. */
    var inFlight: Boolean = false
        private set

    /** 이번 수의 시작 자리(이동 거리 계산용). */
    private var shotStart: Vec2 = Vec2.ZERO
    private var shotSide: Side = Side.PLAYER
    private var shotAccum: Float = 0f
    private var shotElapsed: Float = 0f

    /** 조준을 정해 왕구슬을 친다. 결과는 [advanceShot] 이 끝난 뒤 [finishShot] 으로 낸다. */
    fun beginShot(side: Side, aimDeg: Float, power: Float): Boolean {
        if (finished || inFlight) return false
        placeKing(side)
        shotStart = king.pos
        shotSide = side
        king.vel = MarbleAim.velocity(aimDeg, power, if (side == Side.PLAYER) -1f else 1f)
        world.lastHitA = -1
        world.lastHitB = -1
        shotAccum = 0f
        shotElapsed = 0f
        inFlight = true
        return true
    }

    /** 실시간 한 프레임만큼 굴린다. 아직 굴러가는 중이면 true. */
    fun advanceShot(dtSeconds: Float): Boolean {
        if (!inFlight) return false
        shotAccum += dtSeconds.coerceIn(0f, 0.1f)
        while (shotAccum >= MarbleWorld.FIXED_DT && shotElapsed < MAX_SHOT_SECONDS) {
            world.step()
            shotAccum -= MarbleWorld.FIXED_DT
            shotElapsed += MarbleWorld.FIXED_DT
            if (!world.moving) break
        }
        if (!world.moving || shotElapsed >= MAX_SHOT_SECONDS) inFlight = false
        return inFlight
    }

    /** 굴러간 결과를 정산한다(점수·차례·문구). */
    fun finishShot(): CircleShotResult {
        val side = shotSide
        inFlight = false
        val travelled = king.pos.distanceTo(shotStart)
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

    /** 한 수를 끝까지 계산한다(테스트·AI 예측용). */
    fun shoot(side: Side, aimDeg: Float, power: Float): CircleShotResult {
        if (!beginShot(side, aimDeg, power)) return CircleShotResult(side, emptyList(), true, 0f)
        runToRest()
        return finishShot()
    }

    private fun runToRest() {
        var guard = 0
        while (advanceShot(MarbleWorld.FIXED_DT) && guard < 2000) guard++
    }

    /** AI가 노릴 각도·힘. 화면과 테스트가 같은 값을 쓴다. */
    fun aiPlan(): Pair<Float, Float> {
        if (twoPlayer) return 0f to 0.5f
        val target: Vec2 = glasses.filter { !it.sunk }
            .maxByOrNull { it.pos.distanceTo(center) }
            ?.pos ?: center
        val from = aiLaunch
        val aim = MarbleAim.aimDeg(from, target, 1f)
        val base = MarbleAim.powerFor(from.distanceTo(target), speedScale = 1.12f)
        val miss = 1f - aiSkill.coerceIn(0f, 1f)
        val power = (base + (random.nextFloat() * 2f - 1f) * 0.2f * miss).coerceIn(0.1f, 1f)
        val angle = aim + (random.nextFloat() * 2f - 1f) * 8f * miss
        return angle to power
    }

    /** AI 한 수를 실제로 굴리기 시작한다(화면 연출). */
    fun aiBeginShot(): Boolean {
        val (angle, power) = aiPlan()
        return beginShot(Side.AI, angle, power)
    }

    fun aiShoot(): CircleShotResult {
        if (twoPlayer) return shoot(Side.AI, 0f, 0.5f)
        val (angle, power) = aiPlan()
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

        /** 한 수가 굴러가는 최대 시간(초). 화면 연출을 끊는 한계. */
        const val MAX_SHOT_SECONDS = 6.5f
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

    // ── 실제로 굴러가는 한 수(화면 연출용) ────────────────────────

    /** 지금 구슬이 굴러가는 중인가. */
    var inFlight: Boolean = false
        private set

    private var liveSide: Side = Side.PLAYER
    private var liveProgress = 0
    private var liveHoleIndex = -1
    private var liveProgressed = false
    private var liveWrongOrder = false
    private var liveHitOpponent = false
    private var liveSunk = false
    private var liveElapsed = 0f
    private var liveAccum = 0f

    fun beginShot(side: Side, aimDeg: Float, power: Float): Boolean {
        if (finished || inFlight) return false
        resetMarble(side)
        val mine = marbleOf(side)
        mine.vel = MarbleAim.velocity(aimDeg, power, if (side == Side.PLAYER) -1f else 1f)
        world.lastHitA = -1
        world.lastHitB = -1
        liveSide = side
        liveProgress = progressOf(side)
        liveHoleIndex = -1
        liveProgressed = false
        liveWrongOrder = false
        liveHitOpponent = false
        liveSunk = false
        liveElapsed = 0f
        liveAccum = 0f
        inFlight = true
        return true
    }

    /** 실시간 한 프레임만큼 굴린다. 아직 굴러가는 중이면 true. */
    fun advanceShot(dtSeconds: Float): Boolean {
        if (!inFlight) return false
        liveAccum += dtSeconds.coerceIn(0f, 0.1f)
        while (liveAccum >= MarbleWorld.FIXED_DT && liveElapsed < MAX_SHOT_SECONDS) {
            world.step()
            liveAccum -= MarbleWorld.FIXED_DT
            liveElapsed += MarbleWorld.FIXED_DT
            scanShotStep()
            if (!world.moving) break
        }
        if (!world.moving || liveElapsed >= MAX_SHOT_SECONDS) inFlight = false
        return inFlight
    }

    /** 한 스텝마다 구멍에 빠졌는지·상대를 맞혔는지 본다. */
    private fun scanShotStep() {
        val mine = marbleOf(liveSide)
        val other = marbleOf(liveSide.other())
        if ((world.lastHitA == mine.id && world.lastHitB == other.id) ||
            (world.lastHitB == mine.id && world.lastHitA == other.id)
        ) {
            liveHitOpponent = true
        }
        if (liveSunk) return
        for ((index, hole) in holes.withIndex()) {
            val near = mine.pos.distanceTo(hole) <= HOLE_R * 0.88f
            if (near && mine.vel.length() <= CATCH_SPEED) {
                liveHoleIndex = index
                mine.pos = hole
                mine.vel = Vec2.ZERO
                liveSunk = true
                if (index == liveProgress) {
                    liveProgress++
                    liveProgressed = true
                } else {
                    liveWrongOrder = true
                }
                break
            }
        }
    }

    /** 굴러간 결과를 정산한다(점수·차례·문구). */
    fun finishShot(): HoleShotResult {
        val side = liveSide
        inFlight = false
        var progress = liveProgress
        var claimed = false
        if (progress >= 3 && liveHitOpponent) {
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
            liveProgressed -> {
                if (side == Side.PLAYER) "${liveHoleIndex + 1}번 구멍 성공! 한 번 더 칩니다."
                else "상대가 ${liveHoleIndex + 1}번 구멍에 넣었다."
            }
            liveWrongOrder -> {
                if (side == Side.PLAYER) "${liveHoleIndex + 1}번 구멍은 아직 순서가 아니다."
                else "상대가 순서를 틀렸다."
            }
            liveHitOpponent -> "상대 구슬을 맞혔지만 구멍 3개를 아직 다 넣지 않았다."
            else -> if (side == Side.PLAYER) "아깝다, 빗나갔다." else "상대가 빗나갔다."
        }
        val mine = marbleOf(side)
        val result = HoleShotResult(
            side = side,
            holeIndex = liveHoleIndex,
            progressed = liveProgressed,
            wrongOrder = liveWrongOrder,
            claimedOpponent = claimed,
            hitOpponent = liveHitOpponent,
            endPos = mine.pos,
        )
        lastResult = result
        checkFinish()
        if (!finished) {
            val again = liveProgressed && !claimed
            if (!again) turn = side.other()
            resetMarble(turn)
            resetMarble(turn.other())
        }
        return result
    }

    /** 한 수를 끝까지 계산한다(테스트·AI 예측용). */
    fun shoot(side: Side, aimDeg: Float, power: Float): HoleShotResult {
        if (!beginShot(side, aimDeg, power)) {
            return HoleShotResult(side, -1, false, false, false, false, startOf(side))
        }
        var guard = 0
        while (advanceShot(MarbleWorld.FIXED_DT) && guard < 2000) guard++
        return finishShot()
    }

    /** AI가 노릴 각도·힘. */
    fun aiPlan(): Pair<Float, Float> {
        if (twoPlayer) return 0f to 0.5f
        val me = aiMarble
        val target = if (progressOf(Side.AI) >= 3) playerMarble.pos
        else holes[progressOf(Side.AI)]
        val aim = MarbleAim.aimDeg(me.pos, target, 1f)
        val base = MarbleAim.powerFor(me.pos.distanceTo(target), speedScale = 1.02f)
        val miss = 1f - aiSkill.coerceIn(0f, 1f)
        val angle = aim + (random.nextFloat() * 2f - 1f) * 6f * miss
        val power = (base + (random.nextFloat() * 2f - 1f) * 0.12f * miss).coerceIn(0.1f, 1f)
        return angle to power
    }

    /** AI 한 수를 실제로 굴리기 시작한다(화면 연출). */
    fun aiBeginShot(): Boolean {
        val (angle, power) = aiPlan()
        return beginShot(Side.AI, angle, power)
    }

    fun aiShoot(): HoleShotResult {
        if (twoPlayer) return shoot(Side.AI, 0f, 0.5f)
        val (angle, power) = aiPlan()
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
