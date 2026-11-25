package com.game.gametong.game

import kotlin.math.exp

/**
 * 지우개 **가장자리 누르기** 조작.
 *
 * 진짜 지우개 따먹기는 뒤로 당겼다 놓는(알까기식) 것이 아니라, 지우개의 가장자리를 손가락으로
 * 눌러 **그 방향으로 밀어** 넘어뜨리는 놀이다. 그래서
 *
 * - **누른 자리**가 회전(모멘트)을 정한다. 왼끝을 누르면 왼쪽으로 휘고, 뒤쪽 가장자리를 누르면
 *   앞으로 젖혀져 상대 위로 넘어간다.
 * - **미는 속도**가 힘을 정한다. 손가락이 빠를수록 세게 나간다.
 *
 * 화면은 손가락 위치·시각만 넘기고, 힘·회전·젖힘은 여기서 계산한다(단위 테스트로 검증한다).
 */
data class EraserPush(
    /** 지우개에 줄 속도(무대 단위/초). 0이면 칠 수 없다. */
    val velocity: Vec2,
    /** 누른 지점(−1 왼끝 ~ 0 가운데 ~ 1 오른끝). */
    val strikeOffset: Float,
    /** 앞으로 젖혀지는 정도(−1 뒤로 ~ 0 그대로 ~ 1 앞으로). 뒤 가장자리를 누르면 +. */
    val tip: Float,
    /** 0~1 로 보여 줄 힘. */
    val power: Float,
) {
    val canShoot: Boolean get() = velocity.length() > 1f
}

object EraserPress {
    /** 이보다 느린 손가락은 "스친 것"으로 본다(무대 단위/초). */
    const val MIN_SPEED = 120f

    /** 손가락 속도(무대 단위/초)를 지우개 속도로 옮기는 배율. */
    const val SPEED_GAIN = 0.62f

    /** 손가락이 이만큼은 움직여야 민 것으로 본다(무대 단위). */
    const val MIN_TRAVEL = 12f

    /** 누를 수 있는 범위(지우개 반지름 대비). */
    const val PRESS_REACH = 1.3f

    /**
     * 손가락이 지우개 위를 눌렀는가. 눌렀으면 지우개 중심 기준 좌표(반지름 1)를 돌려준다.
     * 반지름의 [PRESS_REACH] 배 밖이면 null(허공을 누른 것).
     */
    fun localPress(body: EraserBody, point: Vec2): Vec2? {
        val dx = point.x - body.pos.x
        val dy = point.y - body.pos.y
        val reach = body.radius * PRESS_REACH
        if (dx * dx + dy * dy > reach * reach) return null
        return Vec2(
            (dx / body.radius).coerceIn(-1f, 1f),
            (dy / body.radius).coerceIn(-1f, 1f),
        )
    }

    /** 손가락 속도 → 지우개 힘(0~1). */
    fun powerFor(fingerSpeed: Float, type: EraserType): Float {
        val speed = (fingerSpeed.coerceAtLeast(0f) * SPEED_GAIN * type.speedMul)
        return (speed / Board.MAX_FLICK_SPEED).coerceIn(0f, 1f)
    }

    /**
     * 누른 자리와 민 방향·속도로 한 번의 타격을 만든다.
     *
     * @param press 누른 지점(지우개 중심 기준, 반지름 1)
     * @param dir 손가락이 움직인 방향(무대 좌표, 크기는 상관없다)
     * @param fingerSpeed 손가락 속도(무대 단위/초)
     */
    fun push(
        press: Vec2,
        dir: Vec2,
        fingerSpeed: Float,
        type: EraserType = EraserType.STANDARD,
    ): EraserPush {
        val len = dir.length()
        if (len < 1e-4f) return EraserPush(Vec2.ZERO, 0f, 0f, 0f)
        val d = Vec2(dir.x / len, dir.y / len)
        val power = powerFor(fingerSpeed, type)
        val speed = power * Board.MAX_FLICK_SPEED
        if (speed < MIN_SPEED) return EraserPush(Vec2.ZERO, 0f, 0f, power)

        // 민 방향의 오른쪽/앞뒤 성분으로 누른 자리를 읽는다.
        val right = Vec2(-d.y, d.x)
        val lateral = (press.x * right.x + press.y * right.y).coerceIn(-1f, 1f)
        val along = (press.x * d.x + press.y * d.y).coerceIn(-1f, 1f)
        return EraserPush(
            velocity = d * speed,
            strikeOffset = lateral,
            tip = (-along).coerceIn(-1f, 1f),
            power = power,
        )
    }
}

/**
 * 누르고 미는 동안의 손가락 움직임을 모아 속도·방향을 낸다.
 *
 * 마지막 [windowMs] 구간만 보기 때문에, 천천히 끌다가 마지막에 확 미는 동작이 제대로 세게 잡힌다.
 * 시각은 화면이 넘겨주는 밀리초(단조 증가)면 무엇이든 된다.
 */
class PressTracker(private val windowMs: Long = 90L) {
    private val times = ArrayList<Long>(16)
    private val points = ArrayList<Vec2>(16)
    private var travelTotal = 0f

    /** 누르기 시작. */
    fun begin(point: Vec2, timeMs: Long) {
        times.clear()
        points.clear()
        travelTotal = 0f
        sample(point, timeMs)
    }

    /** 손가락이 움직인 자리. */
    fun sample(point: Vec2, timeMs: Long) {
        if (points.isNotEmpty()) {
            travelTotal += (point - points[points.size - 1]).length()
        }
        times += timeMs
        points += point
        while (times.size > 2 && timeMs - times[0] > windowMs * 3) {
            times.removeAt(0)
            points.removeAt(0)
        }
    }

    /** 누른 뒤 손가락이 지나온 총 거리. */
    val travel: Float get() = travelTotal

    /** 지금까지의 이동 방향(단위 벡터). */
    fun direction(): Vec2 {
        if (points.size < 2) return Vec2.ZERO
        val d = points[points.size - 1] - points[0]
        return if (d.length() < 1e-3f) Vec2.ZERO else d.normalized()
    }

    /** 최근 [windowMs] 구간의 손가락 속도(무대 단위/초). */
    fun speed(nowMs: Long = times.lastOrNull() ?: 0L): Float {
        if (points.size < 2) return 0f
        var idx = times.size - 1
        while (idx > 0 && nowMs - times[idx - 1] <= windowMs) idx--
        val dt = (nowMs - times[idx]).coerceAtLeast(1L)
        val from = points[idx]
        val to = points[points.size - 1]
        val dist = (to - from).length()
        // 아주 짧은 구간은 떨림이 크다 — 최소 시간으로 나눈다.
        return dist / (dt.coerceAtLeast(24L) / 1000f)
    }

    /** 최근 구간의 평균 속도(빠른 순간을 놓치지 않도록 [speed] 와 함께 큰 값을 쓴다). */
    fun effectiveSpeed(nowMs: Long = times.lastOrNull() ?: 0L): Float =
        maxOf(speed(nowMs), travelTotal / ((nowMs - (times.firstOrNull() ?: nowMs)).coerceAtLeast(60L) / 1000f) * 0.75f)
}

/** 지수 감쇠 도우미(테스트에서도 같은 식을 쓴다). */
internal fun decay(rate: Float, dt: Float): Float = exp((-rate * dt).toDouble()).toFloat()
