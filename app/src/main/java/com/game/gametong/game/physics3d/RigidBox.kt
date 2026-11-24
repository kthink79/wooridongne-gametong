package com.game.gametong.game.physics3d

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * 책상 재질. 지우개(고무)와 만났을 때의 마찰계수·반발계수를 담는다.
 * 사용자가 요청한 "책상 재질별(나무/플라스틱/철제 등) 마찰계수·반발계수"가 여기에 있다.
 */
enum class DeskMaterial(
    val label: String,
    /** 쿨롱 마찰계수(0~1). 클수록 미끄러지지 않고 회전도 빨리 죽는다. */
    val friction: Float,
    /** 반발계수(0~1). 클수록 높이 튄다. */
    val restitution: Float,
) {
    VARNISHED_WOOD("코팅 원목", 0.46f, 0.34f),
    RAW_WOOD("나무 책상", 0.58f, 0.28f),
    PLYWOOD("거친 합판", 0.72f, 0.20f),
    PLASTIC("플라스틱 책상", 0.38f, 0.40f),
    STEEL("철제 책상", 0.30f, 0.36f),
    DUSTY("먼지 쌓인 책상", 0.86f, 0.12f);

    /** 고무 지우개와 이 재질이 만드는 마찰계수(합성). */
    fun frictionWith(other: Float): Float = sqrt(friction * other)

    /** 고무 지우개와 이 재질이 만드는 반발계수. */
    fun restitutionWith(other: Float): Float = (restitution + other) * 0.5f
}

/** 지우개 고무의 물성. */
object Rubber {
    const val FRICTION = 0.80f
    const val RESTITUTION = 0.26f

    /** 이 아래 속도면 멈춘 것으로 본다(무대 단위/초). */
    const val REST_SPEED = 14f
}

/**
 * 직육면체 강체(Rigid Body).
 *
 * 위치·자세(사원수)·병진 속도·각속도를 가지고, 무게중심에 대한 **관성 텐서**로
 * 각운동량을 계산한다. 바닥과의 충돌은 여덟 꼭짓점마다 순차 임펄스(법선 + 쿨롱 마찰)로
 * 풀기 때문에, 중심에서 벗어난 타격은 자연스럽게 **모서리를 축으로 기울고**,
 * 무게중심이 받침면을 벗어나면 **넘어지며 굴러간다**.
 *
 * 단위는 세계 좌표(m). 무대 좌표를 쓰려면 [Vec3.ofField] 로 바꿔 넣는다.
 */
class RigidBox(
    val half: Vec3,
    val mass: Float,
    var pos: Vec3 = Vec3.ZERO,
    var quat: Quat = Quat.IDENTITY,
    var vel: Vec3 = Vec3.ZERO,
    var omega: Vec3 = Vec3.ZERO,
) {
    /** 관성 텐서의 대각 성분(무게중심 기준). 직육면체 I = m/3 · (다른 두 반폭²의 합). */
    private val inertia = Vec3(
        mass / 3f * (half.y * half.y + half.z * half.z),
        mass / 3f * (half.x * half.x + half.z * half.z),
        mass / 3f * (half.x * half.x + half.y * half.y),
    )

    val invMass: Float = if (mass <= 0f) 0f else 1f / mass

    /** 월드 좌표에서의 역관성 텐서를 벡터에 곱한다. */
    fun applyInvInertia(worldVec: Vec3): Vec3 {
        val local = quat.conjugate().rotate(worldVec)
        val l = Vec3(
            if (inertia.x > 1e-6f) local.x / inertia.x else 0f,
            if (inertia.y > 1e-6f) local.y / inertia.y else 0f,
            if (inertia.z > 1e-6f) local.z / inertia.z else 0f,
        )
        return quat.rotate(l)
    }

    /** 무게중심 기준 [atLocal] 지점에 [impulse] 를 준다(각운동량이 붙는다). */
    fun applyImpulse(impulse: Vec3, atLocal: Vec3) {
        vel = vel + impulse * invMass
        omega = omega + applyInvInertia(atLocal.cross(impulse))
    }

    /** 여덟 꼭짓점의 월드 좌표. */
    fun corners(): List<Vec3> {
        val out = ArrayList<Vec3>(8)
        for (sx in intArrayOf(-1, 1)) {
            for (sy in intArrayOf(-1, 1)) {
                for (sz in intArrayOf(-1, 1)) {
                    val local = Vec3(half.x * sx, half.y * sy, half.z * sz)
                    out += pos + quat.rotate(local)
                }
            }
        }
        return out
    }

    /** 몸통의 위쪽 축(월드). 뒤집혔는지 판단할 때 쓴다. */
    val upAxis: Vec3 get() = quat.rotate(Vec3.UP)

    /** 바닥에서 가장 낮은 꼭짓점의 높이. */
    fun lowestPoint(groundY: Float = 0f): Float =
        corners().minOf { it.y } - groundY

    /** 바닥과 닿은 꼭짓점의 로컬 좌표 목록. [bounds] 가 있으면 책상 밖으로 나간 꼭짓점은 받쳐 주지 않는다. */
    private fun contactCorners(groundY: Float, skin: Float, bounds: Rect2?): List<Vec3> {
        val out = ArrayList<Vec3>(8)
        for (sx in intArrayOf(-1, 1)) {
            for (sy in intArrayOf(-1, 1)) {
                for (sz in intArrayOf(-1, 1)) {
                    val local = Vec3(half.x * sx, half.y * sy, half.z * sz)
                    val world = pos + quat.rotate(local)
                    if (world.y > groundY + skin) continue
                    if (bounds != null && !bounds.contains(world.x, world.z)) continue
                    out += local
                }
            }
        }
        return out
    }

    /**
     * 한 스텝 진행한다.
     *
     * @param table 바닥 재질(마찰·반발)
     * @param groundY 바닥 높이(보통 0)
     * @param bounds 책상 범위. 주어지면 그 밖으로 나간 꼭짓점은 받쳐 주지 않아
     *   **가장자리에서 기울며 굴러 떨어진다**.
     * @return 이번 스텝에 바닥과 부딪힌 충격량(0이면 접촉 없음). 파티클 연출에 씁니다.
     */
    fun step(
        dt: Float,
        gravity: Float = 9.8f,
        table: DeskMaterial = DeskMaterial.RAW_WOOD,
        groundY: Float = 0f,
        airDrag: Float = 0.02f,
        bounds: Rect2? = null,
    ): Float {
        // 1) 중력·공기 저항
        vel = Vec3(vel.x, vel.y - gravity * dt, vel.z)
        val drag = exp(-airDrag * dt.toDouble()).toFloat()
        vel = vel * drag
        omega = omega * drag

        // 2) 위치·자세 적분
        pos = pos + vel * dt
        integrateOrientation(dt)

        // 3) 바닥 접촉: 꼭짓점마다 순차 임펄스(법선 → 마찰)
        var impact = 0f
        val skin = 0.0008f
        val mu = table.frictionWith(Rubber.FRICTION)
        val e = table.restitutionWith(Rubber.RESTITUTION)
        val contacts = contactCorners(groundY, skin, bounds)
        if (contacts.isNotEmpty()) {
            val n = Vec3.UP
            var deepest = 0f
            var normalImpulseSum = 0f

            // 3-a) 법선 임펄스: 파고든 꼭짓점을 밀어 올리며 반발을 정한다.
            for (local in contacts) {
                val r = quat.rotate(local)
                val pen = groundY - (pos + r).y
                if (pen > deepest) deepest = pen

                val pointVel = vel + omega.cross(r)
                val vn = pointVel.dot(n)
                val rn = r.cross(n)
                val denom = invMass + rn.dot(applyInvInertia(rn))
                if (denom <= 1e-8f) continue
                if (vn < 0f) {
                    // 이 속도 아래면 튀지 않고 스며들게(정지 상태 미세 진동 방지)
                    val bounce = if (-vn > 0.55f) e else 0f
                    val jn = -(1f + bounce) * vn / denom
                    if (jn > 0f) {
                        applyImpulse(n * jn, local)
                        normalImpulseSum += jn
                        impact = maxOf(impact, jn * invMass)
                    }
                }
            }

            // 3-b) 쿨롱 마찰: 각 꼭짓점이 받은 법선 임펄스에 마찰계수를 곱한 만큼만 접선을 줄인다.
            //      (무조건 한 번에 멈춰 버리면 미끄러짐이 사라져 재질 차이가 안 난다.)
            if (normalImpulseSum > 1e-6f) {
                val maxJt = mu * normalImpulseSum / contacts.size
                for (local in contacts) {
                    val r = quat.rotate(local)
                    val pv = vel + omega.cross(r)
                    val vt = pv - n * pv.dot(n)
                    val vtLen = vt.length
                    if (vtLen <= 1e-5f) continue
                    val t = vt * (-1f / vtLen)
                    val rt = r.cross(t)
                    val denomT = invMass + rt.dot(applyInvInertia(rt))
                    if (denomT <= 1e-8f) continue
                    val jt = (vtLen / denomT).coerceAtMost(maxJt)
                    applyImpulse(t * jt, local)
                }
            }

            // 3-c) 위치 보정(파고든 만큼 들어 올린다)
            if (deepest > 0f) {
                pos = Vec3(pos.x, pos.y + deepest * 0.85f, pos.z)
                if (vel.y < 0f) vel = Vec3(vel.x, vel.y * 0.35f, vel.z)
            }
        }

        // 4) 구르기 저항: 바닥에 닿아 있으면 각속도가 서서히 죽는다.
        if (contacts.isNotEmpty()) {
            omega = omega * exp((-0.9f * mu * dt).toDouble()).toFloat()
            val planar = Vec3(vel.x, 0f, vel.z)
            val planarSpeed = planar.length
            if (planarSpeed > 0f) {
                val decel = 0.55f * mu * gravity * dt
                val next = (planarSpeed - decel).coerceAtLeast(0f)
                vel = Vec3(planar.x / planarSpeed * next, vel.y, planar.z / planarSpeed * next)
            }
        }

        // 5) 아주 느리면 완전히 멈춘다(잔떨림 방지)
        if (contacts.isNotEmpty() && vel.length < 0.02f && omega.length < 0.05f) {
            vel = Vec3.ZERO
            omega = Vec3.ZERO
        }
        return impact
    }

    private fun integrateOrientation(dt: Float) {
        val w = omega
        if (w.lengthSq < 1e-10f) return
        val dq = Quat(0f, w.x, w.y, w.z) * quat
        quat = Quat(
            quat.w + 0.5f * dq.w * dt,
            quat.x + 0.5f * dq.x * dt,
            quat.y + 0.5f * dq.y * dt,
            quat.z + 0.5f * dq.z * dt,
        ).normalized()
    }

    /** 바닥 위에서 지금 멈춰 있는가. */
    fun atRest(): Boolean =
        vel.length < 0.02f && omega.length < 0.05f && abs(lowestPoint()) < 0.001f

    /** 책상 밖으로 떨어져 있는가(중심이 무대를 벗어나 바닥 아래로 내려갔다). */
    fun fallenOut(bounds: Rect2, fallY: Float = -0.4f): Boolean {
        val inside = pos.x >= bounds.minX && pos.x <= bounds.maxX &&
            pos.z >= bounds.minZ && pos.z <= bounds.maxZ
        return !inside && pos.y < fallY
    }
}

/** 3D 바닥의 직사각형 범위. */
data class Rect2(
    val minX: Float,
    val minZ: Float,
    val maxX: Float,
    val maxZ: Float,
) {
    val width: Float get() = maxX - minX
    val depth: Float get() = maxZ - minZ
    val centerX: Float get() = (minX + maxX) * 0.5f
    val centerZ: Float get() = (minZ + maxZ) * 0.5f

    fun contains(x: Float, z: Float, margin: Float = 0f): Boolean =
        x >= minX - margin && x <= maxX + margin && z >= minZ - margin && z <= maxZ + margin
}
