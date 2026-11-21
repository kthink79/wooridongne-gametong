package com.game.gametong.game

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.min
import kotlin.math.sqrt

object Geometry {
    /**
     * 두 원이 겹치는 넓이. 지우개 레슬링의 "얼마나 올라탔는가"(핀 비율) 판정에 쓴다.
     */
    fun circleOverlapArea(r1: Float, r2: Float, distance: Float): Float {
        if (distance >= r1 + r2) return 0f
        val d = distance.coerceAtLeast(0f)
        if (d <= abs(r1 - r2)) {
            val small = min(r1, r2)
            return (Math.PI * small.toDouble() * small.toDouble()).toFloat()
        }
        val a = r1.toDouble()
        val b = r2.toDouble()
        val dd = d.toDouble()
        val partA = a * a * acos((dd * dd + a * a - b * b) / (2 * dd * a))
        val partB = b * b * acos((dd * dd + b * b - a * a) / (2 * dd * b))
        val partC = 0.5 * sqrt(
            (-dd + a + b) * (dd + a - b) * (dd - a + b) * (dd + a + b)
        )
        return (partA + partB - partC).toFloat().coerceAtLeast(0f)
    }

    /** 서로 다른 두 원의 겹침 넓이를 a 원 넓이로 나눈 비율(0~1). */
    fun overlapRatio(r1: Float, r2: Float, distance: Float): Float {
        if (r1 <= 0f) return 0f
        val full = Math.PI.toFloat() * r1 * r1
        return (circleOverlapArea(r1, r2, distance) / full).coerceIn(0f, 1f)
    }
}
