package com.game.gametong.game.physics3d

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 3D 벡터. 게임 좌표는 **X 오른쪽 · Y 위 · Z 화면 아래쪽(책상 앞)** 이다.
 * 무대(책상·운동장)의 단위는 기존 2D 좌표(1000 × 1500)와 같은 값을 쓰고,
 * 3D에서는 1/100 배 한 값을 쓴다([WORLD_SCALE]).
 */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    operator fun unaryMinus() = Vec3(-x, -y, -z)

    fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3): Vec3 = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)

    val lengthSq: Float get() = x * x + y * y + z * z
    val length: Float get() = sqrt(lengthSq)

    fun normalized(): Vec3 {
        val l = length
        return if (l < 1e-6f) ZERO else this * (1f / l)
    }

    fun lerp(o: Vec3, t: Float): Vec3 =
        Vec3(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t)

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
        val UP = Vec3(0f, 1f, 0f)
        val RIGHT = Vec3(1f, 0f, 0f)
        val FORWARD = Vec3(0f, 0f, 1f)

        /** 2D 무대 좌표(1000 × 1500)를 3D 세계 좌표로. */
        fun ofField(x: Float, y: Float, height: Float = 0f): Vec3 =
            Vec3(x * WORLD_SCALE, height * WORLD_SCALE, y * WORLD_SCALE)

        /** 3D 세계 좌표를 2D 무대 좌표로. */
        fun toFieldX(v: Vec3): Float = v.x / WORLD_SCALE
        fun toFieldZ(v: Vec3): Float = v.z / WORLD_SCALE
    }
}

/** 무대 좌표 → 세계 좌표 축척. 1000 단위 책상이 10 m 로 읽히는 크기. */
const val WORLD_SCALE: Float = 0.01f

/**
 * 회전을 나타내는 단위 사원수.
 * `a * b` 는 "b 를 먼저, 그다음 a" 회전이다.
 */
data class Quat(val w: Float, val x: Float, val y: Float, val z: Float) {

    operator fun times(o: Quat): Quat = Quat(
        w * o.w - x * o.x - y * o.y - z * o.z,
        w * o.x + x * o.w + y * o.z - z * o.y,
        w * o.y - x * o.z + y * o.w + z * o.x,
        w * o.z + x * o.y - y * o.x + z * o.w,
    )

    operator fun plus(o: Quat): Quat = Quat(w + o.w, x + o.x, y + o.y, z + o.z)
    operator fun times(s: Float): Quat = Quat(w * s, x * s, y * s, z * s)

    val length: Float get() = sqrt(w * w + x * x + y * y + z * z)

    fun normalized(): Quat {
        val l = length
        return if (l < 1e-8f) IDENTITY else Quat(w / l, x / l, y / l, z / l)
    }

    fun conjugate(): Quat = Quat(w, -x, -y, -z)

    /** 이 회전으로 벡터를 돌린다. */
    fun rotate(v: Vec3): Vec3 {
        val qv = Vec3(x, y, z)
        val t = qv.cross(v) * 2f
        return v + t * w + qv.cross(t)
    }

    /** 열 우선(column-major) 4×4 회전 행렬. OpenGL 에 그대로 넘긴다. */
    fun toMatrix(out: FloatArray = FloatArray(16)): FloatArray {
        val xx = x * x
        val yy = y * y
        val zz = z * z
        val xy = x * y
        val xz = x * z
        val yz = y * z
        val wx = w * x
        val wy = w * y
        val wz = w * z
        out[0] = 1f - 2f * (yy + zz)
        out[1] = 2f * (xy + wz)
        out[2] = 2f * (xz - wy)
        out[3] = 0f
        out[4] = 2f * (xy - wz)
        out[5] = 1f - 2f * (xx + zz)
        out[6] = 2f * (yz + wx)
        out[7] = 0f
        out[8] = 2f * (xz + wy)
        out[9] = 2f * (yz - wx)
        out[10] = 1f - 2f * (xx + yy)
        out[11] = 0f
        out[12] = 0f
        out[13] = 0f
        out[14] = 0f
        out[15] = 1f
        return out
    }

    companion object {
        val IDENTITY = Quat(1f, 0f, 0f, 0f)

        /** [angleRad] 만큼 [axis] 를 축으로 도는 회전. */
        fun axisAngle(axis: Vec3, angleRad: Float): Quat {
            val a = axis.normalized()
            val half = angleRad * 0.5f
            val s = sin(half)
            return Quat(cos(half), a.x * s, a.y * s, a.z * s).normalized()
        }

        /** Y축(수직) 기준 yaw, X축 기준 pitch, Z축 기준 roll. 도 단위. */
        fun euler(pitchDeg: Float = 0f, yawDeg: Float = 0f, rollDeg: Float = 0f): Quat {
            val qy = axisAngle(Vec3.UP, Math.toRadians(yawDeg.toDouble()).toFloat())
            val qx = axisAngle(Vec3.RIGHT, Math.toRadians(pitchDeg.toDouble()).toFloat())
            val qz = axisAngle(Vec3.FORWARD, Math.toRadians(rollDeg.toDouble()).toFloat())
            return (qy * qx * qz).normalized()
        }
    }
}
