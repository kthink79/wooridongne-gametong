package com.game.gametong.game.physics3d

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 네모 딱지 한 장의 3D 동역학.
 *
 * - **공기압**: 딱지가 빠르게 돌며 낮게 날면 윗면과 아랫면의 압력 차로 살짝 더 떠오른다
 *   ([airLift]). 종이처럼 가벼운 딱지일수록 크게 작용한다.
 * - **바닥 충격량**: 떨어질 때의 속도로 반발계수를 곱해 튕김을 정하고, 충격량이 작으면 삼킨다.
 * - **무게중심 회전 모멘텀**: 맞은 지점이 중심에서 벗어날수록 각운동량이 커져 더 크게 돈다.
 *   각속도는 지수 감쇠로 죽는다.
 *
 * 값의 단위는 무대 좌표(1000 × 1500) 기준이다. [SquareTtakjiFlight] 가 이 모듈로
 * Z축 바운스와 회전을 적분한다.
 */
class TtakjiDynamics(
    val size: Float,
    val thickness: Float,
    val mass: Float,
) {
    var z: Float = 0f
        private set
    var vz: Float = 0f
        private set

    /** 수직축 회전(도). */
    var yaw: Float = 0f
        private set

    /** 회전 각속도(도/초). */
    var yawRate: Float = 0f
        private set

    /** 바닥에 부딪힌 횟수. */
    var bounces: Int = 0
        private set

    /** 이번 스텝에 바닥을 때린 세기(0이면 없음). 먼지 연출에 쓴다. */
    var lastFloorImpact: Float = 0f
        private set

    /** 공중에 떠 있는가. */
    val airborne: Boolean get() = z > 0.5f

    fun reset() {
        z = 0f
        vz = 0f
        yaw = 0f
        yawRate = 0f
        bounces = 0
        lastFloorImpact = 0f
    }

    /**
     * 충격을 받아 날아오른다.
     *
     * @param altitude 튀어 오르는 높이(무대 단위)
     * @param spin 중심에서 벗어난 타격으로 붙는 회전(도/초)
     * @param lever 중심에서 벗어난 정도(0~1) — 회전 모멘텀 계산에 쓴다
     */
    fun launch(altitude: Float, spin: Float, lever: Float, gravity: Float = GRAVITY) {
        z = 0f
        vz = sqrt(2f * gravity * altitude.coerceAtLeast(0f))
        // 무게중심에서 벗어난 충격: 지렛대가 길수록 각운동량이 크다.
        yawRate = spin * (0.85f + 0.35f * lever.coerceIn(0f, 1f))
        lastFloorImpact = 0f
        bounces = 0
    }

    /** 딱지가 도는 것을 계속 돌린다(회전 관성 + 감쇠). */
    fun spinImpulse(deltaDegPerSec: Float) {
        yawRate += deltaDegPerSec
    }

    /**
     * 한 스텝 적분한다.
     *
     * @param surfaceBounce 노면의 반발(0~1)
     * @return 이번 스텝에 바닥을 때린 세기(0이면 없음)
     */
    fun step(dt: Float, surfaceBounce: Float = 0.3f, gravity: Float = GRAVITY): Float {
        lastFloorImpact = 0f

        // 1) 수직: 중력 + 공기압(낮게 돌 때 잠깐 더 뜬다)
        if (z > 0f || vz > 0f) {
            val spinFactor = (abs(yawRate) / 420f).coerceIn(0f, 1f)
            val nearGround = (1f - (z / (size * 0.55f)).coerceIn(0f, 1f))
            val lift = airLift() * spinFactor * nearGround
            vz -= gravity * dt
            vz += lift * dt
            z += vz * dt
            if (z <= 0f) {
                z = 0f
                val impact = abs(vz)
                lastFloorImpact = impact
                if (impact > MIN_BOUNCE_VZ) {
                    val restitution = 0.30f + 0.35f * surfaceBounce
                    vz = impact * restitution
                    bounces++
                } else {
                    vz = 0f
                }
            }
            if (z <= REST_Z && abs(vz) <= REST_VZ) {
                z = 0f
                vz = 0f
            }
        }

        // 2) 회전: 지수 감쇠 + 종이의 미세한 떨림(웨이블)
        val decay = exp((-SPIN_DECAY * dt).toDouble()).toFloat()
        yaw += yawRate * dt
        yawRate *= decay
        if (abs(yawRate) < 0.05f) yawRate = 0f
        return lastFloorImpact
    }

    /** 공기압 세기. 얇고 가벼운 딱지일수록 크다. */
    fun airLift(): Float {
        val lightness = (1.2f - mass * 0.05f).coerceIn(0.7f, 1.4f)
        return 120f * lightness + 40f * (3f - thickness).coerceIn(0f, 2f)
    }

    /** 이번 스텝에 더해진 떨림(도) — 화면에서 종이가 파닥이는 연출에 쓴다. */
    fun flutter(seconds: Float): Float =
        (sin((seconds * 11.3f).toDouble()) * 1.8).toFloat() * (abs(yawRate) / 400f).coerceIn(0f, 1f)

    companion object {
        const val GRAVITY = 5200f
        const val SPIN_DECAY = 3.2f
        const val MIN_BOUNCE_VZ = 260f
        const val REST_Z = 1.2f
        const val REST_VZ = 2f
    }
}
