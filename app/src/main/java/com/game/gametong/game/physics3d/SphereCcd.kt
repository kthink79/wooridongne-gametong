package com.game.gametong.game.physics3d

import com.game.gametong.game.Vec2
import kotlin.math.sqrt

/**
 * 구(원)끼리의 **연속 충돌 검사(CCD)**.
 *
 * 한 스텝에서 위치를 한 번에 옮기면, 아주 빠른 구슬은 상대 구슬을 그냥 **관통**해 버린다.
 * (60fps 에서 6000 단위/초면 한 프레임에 100 단위 — 구슬 지름보다 크다.)
 * 그래서 두 구가 이번 스텝 안에 **처음 닿는 시각 t(0~1)** 를 풀어 그 지점에서 충돌을 처리한다.
 *
 * |p + v t|² = R² 의 근을 푼다:
 * a = v·v, b = 2 d·v, c = d·d − R²  →  t = (−b − √(b² − 4ac)) / 2a
 */
object SphereCcd {

    /**
     * 두 구가 이번 스텝(0~1) 안에 부딪히는 시각. 부딪히지 않으면 null.
     * [radiusSum] 은 두 반지름의 합. 이미 겹쳐 있으면 0 을 돌려준다.
     */
    fun timeOfImpact(
        aPos: Vec2, aVel: Vec2,
        bPos: Vec2, bVel: Vec2,
        radiusSum: Float,
    ): Float? {
        val dx = bPos.x - aPos.x
        val dy = bPos.y - aPos.y
        val dvx = bVel.x - aVel.x
        val dvy = bVel.y - aVel.y
        val c = dx * dx + dy * dy - radiusSum * radiusSum
        if (c <= 0f) return 0f
        val a = dvx * dvx + dvy * dvy
        if (a < 1e-9f) return null
        val b = 2f * (dx * dvx + dy * dvy)
        if (b >= 0f) return null // 멀어지는 중
        val disc = b * b - 4f * a * c
        if (disc < 0f) return null
        val t = (-b - sqrt(disc)) / (2f * a)
        return if (t in 0f..1f) t else null
    }

    /**
     * 원형 경계(벽)와 부딪히는 시각. [pos]·[vel] 은 이번 스텝 시작 기준,
     * [minX]·[maxX]·[minY]·[maxY] 는 구의 반지름을 뺀 안쪽 경계다.
     * 부딪히지 않으면 null.
     */
    fun wallTimeOfImpact(
        pos: Vec2, vel: Vec2,
        minX: Float, maxX: Float, minY: Float, maxY: Float,
    ): Float? {
        var best: Float? = null
        fun consider(t: Float) {
            if (t in 0f..1f && (best == null || t < best!!)) best = t
        }
        if (vel.x < -1e-6f && pos.x > minX) consider((minX - pos.x) / vel.x)
        if (vel.x > 1e-6f && pos.x < maxX) consider((maxX - pos.x) / vel.x)
        if (vel.y < -1e-6f && pos.y > minY) consider((minY - pos.y) / vel.y)
        if (vel.y > 1e-6f && pos.y < maxY) consider((maxY - pos.y) / vel.y)
        return best
    }
}

/**
 * 구슬의 **회전(스핀)**.
 *
 * 굴러가는 회전은 무늬가 도는 연출에 쓰고, 가로 스핀(탑스핀·백스핀)은 바닥과의 마찰을 통해
 * 진행 방향을 살짝 휘게 한다. 구슬끼리 스치면 스핀이 옮겨 붙는다.
 */
object MarbleSpin {
    /** 굴러가는 각속도(도/초). 반지름이 작을수록 빨리 돈다. */
    fun rollRate(speed: Float, radius: Float): Float =
        if (radius <= 0.01f) 0f else speed / radius * 57.29578f

    /** 가로 스핀이 궤적을 휘게 하는 세기(1/초). 아주 약하게 둔다. */
    const val CURVE = 0.00055f

    /** 스핀 감쇠(1/초). */
    const val DECAY = 2.4f
}
