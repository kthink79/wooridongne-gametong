package com.game.gametong.game

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 책상 위 좌표계는 항상 1000 × 1500 단위이고, 화면 크기에 맞춰 배율만 바꾼다. */
data class Vec2(val x: Float, val y: Float) {
    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
    operator fun times(scale: Float) = Vec2(x * scale, y * scale)
    operator fun unaryMinus() = Vec2(-x, -y)

    fun dot(other: Vec2) = x * other.x + y * other.y
    fun length() = sqrt(x * x + y * y)
    fun distanceTo(other: Vec2) = (this - other).length()
    fun normalized(): Vec2 {
        val len = length()
        return if (len < 1e-5f) Vec2(0f, 0f) else Vec2(x / len, y / len)
    }

    companion object {
        val ZERO = Vec2(0f, 0f)

        fun polar(angleDeg: Float, scale: Float): Vec2 {
            val rad = Math.toRadians(angleDeg.toDouble())
            return Vec2(cos(rad).toFloat() * scale, sin(rad).toFloat() * scale)
        }
    }
}
