package com.game.gametong.render

import com.game.gametong.game.physics3d.Quat
import com.game.gametong.game.physics3d.Vec3
import kotlin.math.tan
import kotlin.math.cos
import kotlin.math.sin

/**
 * 4×4 행렬(열 우선, OpenGL 규약). 게임의 3D 그리기가 전부 이 행렬로 움직인다.
 * 화면 배치를 단위 테스트로 검증할 수 있도록 렌더러도 이 클래스만 쓴다.
 */
object Mat4 {

    fun identity(out: FloatArray = FloatArray(16)): FloatArray {
        out.fill(0f)
        out[0] = 1f
        out[5] = 1f
        out[10] = 1f
        out[15] = 1f
        return out
    }

    /** a × b (a 를 나중에 적용). */
    fun multiply(a: FloatArray, b: FloatArray, out: FloatArray = FloatArray(16)): FloatArray {
        for (col in 0 until 4) {
            for (row in 0 until 4) {
                var sum = 0f
                for (k in 0 until 4) {
                    sum += a[k * 4 + row] * b[col * 4 + k]
                }
                out[col * 4 + row] = sum
            }
        }
        return out
    }

    fun perspective(
        fovYDeg: Float,
        aspect: Float,
        near: Float,
        far: Float,
        out: FloatArray = FloatArray(16),
    ): FloatArray {
        val f = 1f / tan(Math.toRadians(fovYDeg.toDouble() * 0.5).toFloat())
        out.fill(0f)
        out[0] = f / aspect.coerceAtLeast(1e-4f)
        out[5] = f
        out[10] = (far + near) / (near - far)
        out[11] = -1f
        out[14] = 2f * far * near / (near - far)
        return out
    }

    fun lookAt(eye: Vec3, target: Vec3, up: Vec3 = Vec3.UP, out: FloatArray = FloatArray(16)): FloatArray {
        var f = (target - eye).normalized()
        if (f.length < 1e-5f) f = Vec3(0f, 0f, -1f)
        var s = f.cross(up)
        if (s.length < 1e-5f) s = f.cross(Vec3(0f, 0f, 1f))
        s = s.normalized()
        val u = s.cross(f)
        out[0] = s.x
        out[1] = u.x
        out[2] = -f.x
        out[3] = 0f
        out[4] = s.y
        out[5] = u.y
        out[6] = -f.y
        out[7] = 0f
        out[8] = s.z
        out[9] = u.z
        out[10] = -f.z
        out[11] = 0f
        out[12] = -s.dot(eye)
        out[13] = -u.dot(eye)
        out[14] = f.dot(eye)
        out[15] = 1f
        return out
    }

    /** 이동·회전·크기를 한 행렬로. */
    fun trs(
        pos: Vec3,
        quat: Quat = Quat.IDENTITY,
        scale: Vec3 = Vec3(1f, 1f, 1f),
        out: FloatArray = FloatArray(16),
    ): FloatArray {
        val r = quat.toMatrix(FloatArray(16))
        for (col in 0 until 3) {
            out[col * 4 + 0] = r[col * 4 + 0] * scale.x
            out[col * 4 + 1] = r[col * 4 + 1] * scale.y
            out[col * 4 + 2] = r[col * 4 + 2] * scale.z
            out[col * 4 + 3] = 0f
        }
        out[12] = pos.x
        out[13] = pos.y
        out[14] = pos.z
        out[15] = 1f
        return out
    }

    /** 법선용 3×3(상단 왼쪽) — 크기만 반영하고 이동은 뺀다. */
    fun normalMatrix(model: FloatArray, out: FloatArray = FloatArray(9)): FloatArray {
        for (col in 0 until 3) {
            for (row in 0 until 3) {
                out[col * 3 + row] = model[col * 4 + row]
            }
        }
        return out
    }

    /** 행렬로 점을 옮긴다(투영 나눗셈 포함). 실패하면 null. */
    fun transform(m: FloatArray, p: Vec3): FloatArray {
        val x = m[0] * p.x + m[4] * p.y + m[8] * p.z + m[12]
        val y = m[1] * p.x + m[5] * p.y + m[9] * p.z + m[13]
        val z = m[2] * p.x + m[6] * p.y + m[10] * p.z + m[14]
        val w = m[3] * p.x + m[7] * p.y + m[11] * p.z + m[15]
        if (kotlin.math.abs(w) < 1e-6f) return floatArrayOf(0f, 0f, 0f, 0f)
        return floatArrayOf(x / w, y / w, z / w, w)
    }

    /** 역행렬(일반 4×4). 실패하면 null. */
    fun invert(m: FloatArray, out: FloatArray = FloatArray(16)): FloatArray? {
        val inv = FloatArray(16)
        inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] +
            m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10]
        inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] -
            m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10]
        inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] +
            m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9]
        inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] -
            m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9]
        inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] -
            m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10]
        inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] +
            m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10]
        inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] -
            m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9]
        inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] +
            m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9]
        inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] +
            m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6]
        inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] -
            m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6]
        inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] +
            m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5]
        inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] -
            m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5]
        inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] -
            m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6]
        inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] +
            m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6]
        inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] -
            m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5]
        inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] +
            m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5]

        val det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12]
        if (kotlin.math.abs(det) < 1e-9f) return null
        val invDet = 1f / det
        for (i in 0 until 16) out[i] = inv[i] * invDet
        return out
    }

    /** 화면 좌표(NDC) → 바닥(y=0) 위의 점. 카메라 역투영으로 손가락 위치를 책상 좌표로 옮긴다. */
    fun unprojectToGround(
        viewProj: FloatArray,
        ndcX: Float,
        ndcY: Float,
        groundY: Float = 0f,
    ): Vec3? {
        val inv = invert(viewProj) ?: return null
        val near = transform(inv, Vec3(ndcX, ndcY, -1f))
        val far = transform(inv, Vec3(ndcX, ndcY, 1f))
        val origin = Vec3(near[0], near[1], near[2])
        val dir = Vec3(far[0] - near[0], far[1] - near[1], far[2] - near[2])
        if (kotlin.math.abs(dir.y) < 1e-5f) return null
        val t = (groundY - origin.y) / dir.y
        if (t < 0f) return null
        return origin + dir * t
    }

    /** 카메라 기준 오른쪽·위 방향(파티클 빌보드). */
    fun cameraBasis(eye: Vec3, target: Vec3, up: Vec3 = Vec3.UP): Pair<Vec3, Vec3> {
        val f = (target - eye).normalized()
        val right = f.cross(up).normalized()
        val camUp = right.cross(f).normalized()
        return right to camUp
    }

    fun rotationY(rad: Float): FloatArray = Quat.axisAngle(Vec3.UP, rad).toMatrix()

    fun cosDeg(deg: Float): Float = cos(Math.toRadians(deg.toDouble())).toFloat()
    fun sinDeg(deg: Float): Float = sin(Math.toRadians(deg.toDouble())).toFloat()
}
