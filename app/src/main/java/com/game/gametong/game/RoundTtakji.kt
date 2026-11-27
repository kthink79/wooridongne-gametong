package com.game.gametong.game

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** 동그란 딱지 세 가지 놀이. */
enum class RoundMode(
    val title: String,
    val subtitle: String,
    val emoji: String,
    val howTitle: String,
) {
    STICK(
        "붙여먹기",
        "벽에 가깝게 붙이거나 상대 딱지를 덮어 따먹기",
        "🟡",
        "벽/기준선에 더 가까이 붙인 사람이 1점. 상대 딱지를 덮으면 2점!",
    ),
    DISTANCE(
        "멀리 날리기",
        "각도·힘·바람을 읽고 가장 멀리",
        "🌬️",
        "바람은 판마다 바뀝니다. 각도와 힘을 맞춰 더 멀리 날린 쪽이 1점.",
    ),
    BLOW(
        "입바람 넘기기",
        "'파!' 불어 딱지를 결승선 너머로",
        "💨",
        "입바람(또는 스와이프·빠른 탭)으로 딱지를 밀어 결승선을 넘기면 1점. 테이블 밖은 실격.",
    ),
}

/** 동그란 딱지의 물리 상수. */
object RoundTtakjiPhysics {
    const val FIELD_W = 1000f
    const val FIELD_H = 1400f
    const val RADIUS = 54f
    const val WALL_LINE = 180f
    const val START_LINE = 1250f
    const val MAX_SPEED = 1750f
    const val DECEL = 760f
    const val MIN_DRAG = 20f

    fun velocity(drag: Vec2, powerCap: Float = 1f): Vec2 {
        if (drag.length() < MIN_DRAG) return Vec2.ZERO
        val power = (drag.length() / 420f).coerceIn(0f, 1f).coerceAtMost(powerCap)
        return drag.normalized() * (MAX_SPEED * power)
    }

    /**
     * 동그란 딱지가 미끄러져 멈추는 자리. 좌우 벽은 살짝 튕기고,
     * 위쪽 벽(붙이는 선)에 닿으면 그 자리에 붙는다.
     */
    fun slide(start: Vec2, dir: Vec2, speed: Float): Vec2 {
        var pos = start
        var vel = dir.normalized() * speed
        val dt = 1f / 120f
        var elapsed = 0f
        val minX = RADIUS
        val maxX = FIELD_W - RADIUS
        while (elapsed < 5f) {
            val v = vel.length()
            if (v <= 8f) break
            val next = (v - DECEL * dt).coerceAtLeast(0f)
            vel = vel * (next / v)
            pos += vel * dt
            if (pos.x < minX) {
                pos = Vec2(minX, pos.y)
                vel = Vec2(-vel.x * 0.55f, vel.y)
            } else if (pos.x > maxX) {
                pos = Vec2(maxX, pos.y)
                vel = Vec2(-vel.x * 0.55f, vel.y)
            }
            if (pos.y <= WALL_LINE) {
                pos = Vec2(pos.x, WALL_LINE)
                vel = Vec2.ZERO
                break
            }
            if (pos.y >= START_LINE + 80f) {
                pos = Vec2(pos.x, START_LINE + 80f)
                vel = Vec2.ZERO
                break
            }
            elapsed += dt
        }
        return pos
    }

    /** 드래그 방향 → 조준각(0 = 위쪽 벽). */
    fun aimDegOf(drag: Vec2): Float {
        val dir = drag.normalized()
        val deg = Math.toDegrees(kotlin.math.atan2(dir.x.toDouble(), -dir.y.toDouble())).toFloat()
        return deg
    }
}

/**
 * 동그란 딱지 화면 좌표 도우미.
 *
 * 놀이판 수치를 캔버스 픽셀로 바꿀 때 세 화면이 같은 여백·눈금 규칙을 쓰게 한다.
 * 예전에는 입바람 판을 1000 폭으로 나누어 그렸는데 실제 트랙은 1080([BlowMatch.TABLE_END])이라
 * 결승선을 넘긴 딱지가 화면 오른쪽 밖으로 사라졌다. 이제 트랙 전체가 화면 안에 들어온다.
 */
object RoundTtakjiHud {
    /** 캔버스 좌우 여백(폭 대비 비율). */
    const val MARGIN = 0.07f

    /** 멀리 날리기에서 화면에 담는 최대 비거리(무대 단위). */
    const val DISTANCE_SPAN = 2400f

    /** 0..[length] 를 캔버스 [width] 안(좌우 여백 제외)에 매핑한다. */
    fun trackX(value: Float, width: Float, length: Float): Float {
        val m = width * MARGIN
        val usable = (width - 2f * m).coerceAtLeast(1f)
        val t = (value / length.coerceAtLeast(1f)).coerceIn(0f, 1f)
        return m + t * usable
    }

    /** 입바람 트랙(0..[BlowMatch.TABLE_END]). */
    fun blowX(pos: Float, width: Float): Float =
        trackX(pos, width, BlowMatch.TABLE_END.toFloat())

    /** 입바람 트랙에서 1 무대 단위가 차지하는 픽셀(지우개 크기 계산용). */
    fun blowScale(width: Float): Float =
        (width - 2f * width * MARGIN).coerceAtLeast(1f) / BlowMatch.TABLE_END

    /** 멀리 날리기 비거리 → 캔버스 x. */
    fun distanceX(distance: Float, width: Float): Float = trackX(distance, width, DISTANCE_SPAN)

    /** 멀리 날리기 트랙에서 1 무대 단위가 차지하는 픽셀. */
    fun distanceScale(width: Float): Float =
        (width - 2f * width * MARGIN).coerceAtLeast(1f) / DISTANCE_SPAN
}

/** 붙여먹기 결과. */
data class StickShot(
    val side: Side,
    val aimDeg: Float,
    val power: Float,
    val endPos: Vec2,
    val wallDistance: Float,
)

/**
 * 붙여먹기: 벽(기준선) 가까이 붙이기 + 상대 딱지 덮어먹기.
 * 두 사람이 한 번씩 붙인 뒤 점수를 계산하고, 먼저 5점을 내는 쪽이 이긴다.
 */
class StickMatch(
    val twoPlayer: Boolean = false,
    val aiSkill: Float = 0.78f,
    private val random: Random = Random(31),
) {
    companion object {
        const val WIN_SCORE = 5
        const val COVER_RATIO = 0.45f
        const val TIE_UNITS = 14f
    }

    val playerStart = Vec2(330f, 1230f)
    val aiStart = Vec2(670f, 1230f)

    var playerPos: Vec2 = playerStart
        private set
    var aiPos: Vec2 = aiStart
        private set

    var playerShot: StickShot? = null
        private set
    var aiShot: StickShot? = null
        private set

    var playerScore: Int = 0
        private set
    var aiScore: Int = 0
        private set

    var round: Int = 1
        private set
    var finished: Boolean = false
        private set
    var winner: Side? = null
        private set

    /** 지난 판 결과 설명. */
    var outcome: String = "딱지를 벽 쪽으로 튕겨 붙여 보세요."
        private set
    var lastRoundClaim: Side? = null
        private set

    val waitingForPlayer: Boolean get() = playerShot == null && !finished
    val waitingForAi: Boolean get() = !twoPlayer && playerShot != null && aiShot == null && !finished

    fun flick(side: Side, aimDeg: Float, power: Float): StickShot {
        if (finished) return StickShot(side, aimDeg, power, playerPos, 0f)
        val start = if (side == Side.PLAYER) playerStart else aiStart
        val dir = SquareTtakjiEngine.aimDirection(aimDeg, -1f)
        val speed = RoundTtakjiPhysics.MAX_SPEED * power.coerceIn(0f, 1f)
        val end = RoundTtakjiPhysics.slide(start, dir, speed)
        val shot = StickShot(side, aimDeg, power.coerceIn(0f, 1f), end, end.y - RoundTtakjiPhysics.WALL_LINE)
        when (side) {
            Side.PLAYER -> {
                playerPos = end
                playerShot = shot
            }
            Side.AI -> {
                aiPos = end
                aiShot = shot
            }
        }
        if (playerShot != null && aiShot != null) resolveRound()
        return shot
    }

    /** AI가 붙인다. 벽까지의 거리로 필요한 힘을 계산하고 실력만큼 흔들린다. */
    fun aiFlick(): StickShot {
        if (twoPlayer) return flick(Side.AI, 0f, 0.6f)
        val dist = (aiStart.y - RoundTtakjiPhysics.WALL_LINE)
        val needed = sqrt(2f * RoundTtakjiPhysics.DECEL * dist) / RoundTtakjiPhysics.MAX_SPEED
        val miss = 1f - aiSkill.coerceIn(0f, 1f)
        val power = (needed + (random.nextFloat() * 2f - 1f) * 0.16f * miss).coerceIn(0.15f, 1f)
        val aim = (random.nextFloat() * 2f - 1f) * 10f * miss
        return flick(Side.AI, aim, power)
    }

    private fun resolveRound() {
        val p = playerPos
        val a = aiPos
        val dist = p.distanceTo(a)
        val playerCovers = Geometry.overlapRatio(RoundTtakjiPhysics.RADIUS, RoundTtakjiPhysics.RADIUS, dist) >= COVER_RATIO
        val aiCovers = Geometry.overlapRatio(RoundTtakjiPhysics.RADIUS, RoundTtakjiPhysics.RADIUS, dist) >= COVER_RATIO

        lastRoundClaim = null
        when {
            playerCovers && aiCovers -> {
                playerScore += 2
                aiScore += 2
                outcome = "서로 딱지를 덮었다! 둘 다 2점."
                lastRoundClaim = null
            }
            playerCovers -> {
                playerScore += 2
                lastRoundClaim = Side.PLAYER
                outcome = "덮어먹기 성공! 상대 딱지를 덮어 2점."
            }
            aiCovers -> {
                aiScore += 2
                lastRoundClaim = Side.AI
                outcome = "상대가 내 딱지를 덮었다… 2점 실점."
            }
            else -> {
                val pDist = p.y - RoundTtakjiPhysics.WALL_LINE
                val aDist = a.y - RoundTtakjiPhysics.WALL_LINE
                when {
                    abs(pDist - aDist) <= TIE_UNITS -> outcome = "거의 같은 자리! 이번 판은 무승부."
                    pDist < aDist -> {
                        playerScore++
                        outcome = "벽에 더 가깝게 붙였다! 1점."
                    }
                    else -> {
                        aiScore++
                        outcome = "상대가 더 가깝다… 1점 실점."
                    }
                }
            }
        }
        checkFinish()
        if (!finished) {
            round++
            playerPos = playerStart
            aiPos = aiStart
            playerShot = null
            aiShot = null
        }
    }

    private fun checkFinish() {
        if (playerScore >= WIN_SCORE || aiScore >= WIN_SCORE) {
            finished = true
            winner = if (playerScore >= aiScore) Side.PLAYER else Side.AI
        }
    }
}

/** 멀리 날리기 한 발. distance 는 날아간 거리(단위), meters 는 화면 표시용(m). */
data class DistanceShot(
    val side: Side,
    val angleDeg: Float,
    val power: Float,
    val distance: Float,
) {
    val meters: Float get() = distance / 32f
}

/**
 * 멀리 날리기: 각도·힘·바람. 한 판마다 바람이 바뀌고, 더 멀리 날린 쪽이 1점.
 * 먼저 3점을 내는 쪽이 이긴다.
 */
class DistanceMatch(
    val twoPlayer: Boolean = false,
    val aiSkill: Float = 0.75f,
    private val random: Random = Random(77),
) {
    companion object {
        const val WIN_SCORE = 3
        const val LAUNCH_SPEED = 1400f
        const val GRAVITY = 1000f
        const val MAX_WIND = 90f
        const val MIN_ANGLE = 5f
        const val MAX_ANGLE = 80f
    }

    var wind: Float = newWind()
        private set

    var playerShot: DistanceShot? = null
        private set
    var aiShot: DistanceShot? = null
        private set

    var playerScore: Int = 0
        private set
    var aiScore: Int = 0
        private set

    var round: Int = 1
        private set
    var finished: Boolean = false
        private set
    var winner: Side? = null
        private set
    var outcome: String = "바람을 읽고 각도와 힘을 맞춰 보세요."
        private set

    fun newWind(): Float = (random.nextFloat() * 2f - 1f) * MAX_WIND

    fun shoot(side: Side, angleDeg: Float, power: Float): DistanceShot {
        val angle = angleDeg.coerceIn(MIN_ANGLE, MAX_ANGLE)
        val p = power.coerceIn(0.1f, 1f)
        val v = LAUNCH_SPEED * p
        val rad = Math.toRadians(angle.toDouble())
        val vx = (v * cos(rad)).toFloat()
        val vy = (v * sin(rad)).toFloat()
        val t = 2f * vy / GRAVITY
        val distance = (vx * t + 0.5f * wind * t * t).coerceAtLeast(0f)
        val shot = DistanceShot(side, angle, p, distance)
        when (side) {
            Side.PLAYER -> playerShot = shot
            Side.AI -> aiShot = shot
        }
        if (playerShot != null && aiShot != null) resolveRound()
        return shot
    }

    fun aiShoot(): DistanceShot {
        if (twoPlayer) return shoot(Side.AI, 45f, 0.8f)
        val ideal = idealPower(45f)
        val miss = 1f - aiSkill.coerceIn(0f, 1f)
        val angle = (45f + (random.nextFloat() * 2f - 1f) * 12f * miss).coerceIn(MIN_ANGLE, MAX_ANGLE)
        val power = (ideal + (random.nextFloat() * 2f - 1f) * 0.12f * miss).coerceIn(0.1f, 1f)
        return shoot(Side.AI, angle, power)
    }

    /** 주어진 각도에서 가장 멀리 나가는 힘(바람 반영, 0.1~1). */
    fun idealPower(angleDeg: Float): Float {
        var best = 0.1f
        var bestDistance = -1f
        var p = 0.1f
        while (p <= 1.0001f) {
            val angle = angleDeg.coerceIn(MIN_ANGLE, MAX_ANGLE)
            val v = LAUNCH_SPEED * p
            val rad = Math.toRadians(angle.toDouble())
            val t = 2f * (v * sin(rad)).toFloat() / GRAVITY
            val d = (v * cos(rad)).toFloat() * t + 0.5f * wind * t * t
            if (d > bestDistance) {
                bestDistance = d
                best = p
            }
            p += 0.02f
        }
        return best
    }

    private fun resolveRound() {
        val p = playerShot ?: return
        val a = aiShot ?: return
        val diff = p.distance - a.distance
        when {
            abs(diff) < 6f -> outcome = "거의 같은 거리! 무승부."
            diff > 0f -> {
                playerScore++
                outcome = "내가 %.1fm 더 멀리! 1점.".format(diff / 32f)
            }
            else -> {
                aiScore++
                outcome = "상대가 %.1fm 더 멀리… 1점 실점.".format(-diff / 32f)
            }
        }
        if (playerScore >= WIN_SCORE || aiScore >= WIN_SCORE) {
            finished = true
            winner = if (playerScore >= aiScore) Side.PLAYER else Side.AI
        } else {
            round++
            wind = newWind()
            playerShot = null
            aiShot = null
        }
    }
}

/** 입바람 세기를 만드는 입력들. 마이크·스와이프·빠른 탭이 모두 같은 0~1 세기로 모인다. */
object BlowWind {
    const val NOISE_FLOOR = 0.05f
    const val FULL_LEVEL = 0.42f
    const val MIN_SPEED = 180f
    const val SPEED_RANGE = 520f
    const val FRICTION = 560f

    /** 마이크 진폭(0~1) → 입바람 세기. 조용한 방의 잡음은 0이 된다. */
    fun strengthFromAmplitude(peak: Float): Float {
        if (peak <= NOISE_FLOOR) return 0f
        return ((peak - NOISE_FLOOR) / (FULL_LEVEL - NOISE_FLOOR)).coerceIn(0f, 1f)
    }

    /** 스와이프 길이(0~약 360) → 세기. */
    fun strengthFromSwipe(dragUnits: Float): Float =
        (dragUnits / 360f).coerceIn(0f, 1f)

    /** 1초에 몇 번 탭했는지 → 세기. */
    fun strengthFromTaps(tapsPerSecond: Int): Float =
        (tapsPerSecond / 8f).coerceIn(0f, 1f)

    /** 세기 → 딱지가 밀려나는 거리. */
    fun travel(strength: Float): Float {
        val speed = MIN_SPEED + SPEED_RANGE * strength.coerceIn(0f, 1f)
        return speed * speed / (2f * FRICTION)
    }
}

data class BlowShot(
    val side: Side,
    val strength: Float,
    val travel: Float,
    val endPos: Float,
    val crossed: Boolean,
    val offTable: Boolean,
)

/**
 * 입바람 넘기기: 테이블 위 가벼운 딱지를 결승선 너머로 밀어낸다.
 * 한 판에 각자 4번씩 불 수 있고, 먼저 결승선을 넘기면 1점. 테이블 밖으로 밀면 처음 자리로 돌아간다.
 */
class BlowMatch(
    val twoPlayer: Boolean = false,
    val aiSkill: Float = 0.72f,
    private val random: Random = Random(97),
) {
    companion object {
        const val WIN_SCORE = 3
        const val START = 130f
        const val FINISH = 760f
        const val TABLE_END = 1080f
        const val BLOWS_PER_ROUND = 4
    }

    var playerPos: Float = START
        private set
    var aiPos: Float = START
        private set

    var playerBlows: Int = BLOWS_PER_ROUND
        private set
    var aiBlows: Int = BLOWS_PER_ROUND
        private set

    var playerScore: Int = 0
        private set
    var aiScore: Int = 0
        private set

    var round: Int = 1
        private set
    var finished: Boolean = false
        private set
    var winner: Side? = null
        private set
    var outcome: String = "마이크에 대고 '파!' 불거나, 화면을 위로 스와이프하세요."
        private set

    val playerProgress: Float get() = ((playerPos - START) / (FINISH - START)).coerceIn(0f, 1f)
    val aiProgress: Float get() = ((aiPos - START) / (FINISH - START)).coerceIn(0f, 1f)

    fun blow(side: Side, strength: Float): BlowShot {
        if (finished) return BlowShot(side, strength, 0f, START, false, false)
        val s = strength.coerceIn(0f, 1f)
        val travel = BlowWind.travel(s)
        var pos = if (side == Side.PLAYER) playerPos else aiPos
        var offTable = false
        pos += travel
        if (pos > TABLE_END) {
            pos = START
            offTable = true
        }
        var crossed = false
        if (pos >= FINISH) crossed = true
        when (side) {
            Side.PLAYER -> {
                playerPos = pos
                playerBlows = (playerBlows - 1).coerceAtLeast(0)
            }
            Side.AI -> {
                aiPos = pos
                aiBlows = (aiBlows - 1).coerceAtLeast(0)
            }
        }
        outcome = when {
            offTable -> "테이블 밖으로 날아갔다! 처음 자리로."
            crossed -> (if (side == Side.PLAYER) "결승선 통과! 1점." else "상대가 결승선을 넘었다… 1점 실점.")
            travel < 2f -> "너무 약했다. 조금 더 세게!"
            else -> "딱지가 %.0f만큼 밀려났다.".format(travel)
        }
        if (crossed) {
            if (side == Side.PLAYER) playerScore++ else aiScore++
            checkRoundEnd(scored = true)
        } else if (playerBlows == 0 && aiBlows == 0) {
            checkRoundEnd(scored = false)
        }
        return BlowShot(side, s, travel, pos, crossed, offTable)
    }

    fun aiBlow(): BlowShot {
        if (twoPlayer) return blow(Side.AI, 0.5f)
        val needed = FINISH - aiPos + 20f
        var strength = 0.1f
        var bestDiff = Float.MAX_VALUE
        var s = 0f
        while (s <= 1.0001f) {
            val d = BlowWind.travel(s)
            val diff = abs(d - needed)
            if (diff < bestDiff) {
                bestDiff = diff
                strength = s
            }
            s += 0.02f
        }
        val miss = 1f - aiSkill.coerceIn(0f, 1f)
        val jittered = (strength + (random.nextFloat() * 2f - 1f) * 0.18f * miss).coerceIn(0.05f, 1f)
        return blow(Side.AI, jittered)
    }

    private fun checkRoundEnd(scored: Boolean) {
        if (playerScore >= WIN_SCORE || aiScore >= WIN_SCORE) {
            finished = true
            winner = if (playerScore >= aiScore) Side.PLAYER else Side.AI
            return
        }
        if (scored || (playerBlows == 0 && aiBlows == 0)) {
            round++
            playerPos = START
            aiPos = START
            playerBlows = BLOWS_PER_ROUND
            aiBlows = BLOWS_PER_ROUND
            if (!scored) outcome = "둘 다 4번을 다 불었지만 넘지 못했다. 무승부로 다음 판."
        }
    }
}
