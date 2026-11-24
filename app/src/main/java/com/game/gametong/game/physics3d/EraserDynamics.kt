package com.game.gametong.game.physics3d

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * 지우개 하나의 **3D 기울기 동역학**.
 *
 * 예전에는 "기운 각"을 정해진 공식으로 흉내 냈다. 이제는 직육면체의 관성 모멘트를 두고
 * 각운동량을 적분한다.
 *
 * - 타격 지점이 중심에서 벗어날수록(왼끝·오른끝) 옆으로 도는 각운동량이 붙는다([rollRate]).
 * - 낮게 때리면 앞으로 넘어가는 각운동량이 붙고([pitchRate]), 높게 때리면 반대로 젖혀진다.
 * - 받침면 안에 무게중심이 있으면 중력이 되돌리려 하고(복원 토크),
 *   무게중심이 받침면(모서리)을 벗어나면 중력이 도리어 넘어뜨린다 → [overTip] 가 켜진다.
 * - 책상 가장자리에 걸치면 받침이 줄어 넘어지는 각이 작아진다 → [edgeFraction].
 * - 바닥 마찰이 클수록 각속도가 빨리 죽는다(먼지 쌓인 책상에서 잘 안 흔들린다).
 *
 * 넘어짐이 확정되면 [consumeFlop] 가 true 를 돌려주고, 부르는 쪽(지우개 물리)이
 * 90도 텀블을 더해 "한 면이 바닥에 닿는" 자세로 넘긴다.
 */
class EraserDynamics(
    /** 무게중심 기준 반폭(무대 단위). */
    val halfWidth: Float,
    /** 반높이(무대 단위). */
    val halfHeight: Float,
    /** 앞뒤 반길이(무대 단위). */
    val halfDepth: Float,
    val mass: Float,
) {
    /** 진행 방향으로 넘어가는 각(도, +면 앞으로). */
    var pitch: Float = 0f
        private set
    var pitchRate: Float = 0f
        private set

    /** 진행 축을 따라 옆으로 기우는 각(도). */
    var roll: Float = 0f
        private set
    var rollRate: Float = 0f
        private set

    /** 이 각을 넘으면 중력이 되돌리지 못하고 넘어간다(무게중심이 받침 모서리를 지나는 각). */
    val tipAngleDeg: Float =
        Math.toDegrees(atan((halfWidth / (halfHeight * 2f).coerceAtLeast(0.01f)).toDouble())).toFloat()

    private var flopPending = false

    /** 한 번 넘어지면 그 뒤로는 중력이 그대로 끌어내린다(되돌리지 않는다). */
    private var hasFlopped = false

    /** 넘어간 횟수(누적). */
    var flops: Int = 0
        private set

    /** 지금 기울어진 전체 각(도) — 그리기·판정에 쓴다. */
    val tiltDeg: Float get() = sqrt(pitch * pitch + roll * roll)

    /** 자세 사원수(그리기용). pitch 는 X축, roll 은 Z축 회전으로 읽는다. */
    fun orientation(): Quat = Quat.euler(pitchDeg = pitch, yawDeg = 0f, rollDeg = roll)

    fun reset() {
        pitch = 0f
        pitchRate = 0f
        roll = 0f
        rollRate = 0f
        flopPending = false
        hasFlopped = false
    }

    /**
     * 튕길 때의 타격. [offset] 은 어느 지점을 때렸는지(-1 왼끝 ~ 0 가운데 ~ 1 오른끝),
     * [speed] 는 타격 속도(무대 단위/초), [strikeHeight] 는 지우개 높이 대비 때린 높이(0 바닥 ~ 1 윗면).
     */
    fun strike(offset: Float, speed: Float, strikeHeight: Float = 0.25f) {
        val off = offset.coerceIn(-1f, 1f)
        // 충격량 J = m·Δv (무대 단위/s × kg), 지렛대 r(무대 단위) → 각운동량 L = J·r
        // 관성 모멘트도 같은 무대 단위로 계산하므로 단위는 일관된다.
        val impulse = speed * mass
        // 옆으로 기우는 각운동량: 중심에서 벗어난 만큼(타격 충격의 일부만 회전으로 간다)
        rollRate += (impulse * off * halfWidth) / inertiaRoll() * RAD_TO_DEG * ROLL_GAIN
        // 앞뒤로 넘어가는 각운동량: 낮게 때릴수록 앞으로 넘어간다
        val lever = (0.5f - strikeHeight.coerceIn(0f, 1f)) * halfHeight * 2f
        pitchRate += (impulse * lever) / inertiaPitch() * RAD_TO_DEG * PITCH_GAIN
    }

    /**
     * 상대 지우개·책상 모서리와 부딪혔을 때.
     * [approach] 는 부딪힌 세기(무대 단위/초), [offset] 은 중심에서 벗어난 정도(-1~1),
     * [fromAbove] 는 위에서 눌렸는지(걸쳐 올라갈 때)다.
     */
    fun hit(approach: Float, offset: Float, fromAbove: Boolean = false) {
        val off = offset.coerceIn(-1f, 1f)
        val impulse = approach * mass * 0.45f
        rollRate += (impulse * off * halfWidth) / inertiaRoll() * RAD_TO_DEG * ROLL_GAIN
        pitchRate += if (fromAbove) {
            -impulse * halfDepth / inertiaPitch() * RAD_TO_DEG * PITCH_GAIN * 0.7f
        } else {
            impulse * halfHeight / inertiaPitch() * RAD_TO_DEG * PITCH_GAIN * 0.55f
        }
    }

    /**
     * 한 스텝 각도를 적분한다.
     *
     * @param table 바닥 재질(마찰이 클수록 빨리 안정된다)
     * @param supported 바닥(또는 상대 지우개)에 받치고 있는가
     * @param edgeFraction 책상 가장자리 밖으로 나간 정도(0 안 ~ 1 완전히 밖)
     * @return 이번 스텝에 넘어졌으면 true
     */
    fun step(
        dt: Float,
        table: DeskMaterial = DeskMaterial.RAW_WOOD,
        supported: Boolean = true,
        edgeFraction: Float = 0f,
    ): Boolean {
        flopPending = false
        val edge = edgeFraction.coerceIn(0f, 1f)
        // 무게중심이 책상 가장자리 밖으로 나갔는가(받침이 거의 없음)
        val beyondEdge = supported && !hasFlopped && edge > 0.45f

        when {
            hasFlopped -> {
                // 이미 넘어졌다: 중력이 끌어내리는 대로 둔다.
            }
            beyondEdge -> {
                // 중심이 가장자리를 넘었다 → 중력이 되돌리지 못하고 무너진다.
                val dir = if (pitchRate < 0f || pitch < 0f) -1f else 1f
                pitchRate += dir * 260f * dt
                if (abs(roll) > 0.5f) rollRate += sign(roll) * 14f * dt
            }
            supported -> {
                val torque = if (abs(pitch) < tipAngleDeg) -pitch * 7.5f else sign(pitch) * 30f
                pitchRate += torque * dt
                // 옆 기움도 중력이 받침면 안으로 되돌린다.
                rollRate -= roll * 6f * dt
            }
            else -> {
                // 공중: 되돌리는 힘 없이 붙은 각운동량만 남는다.
            }
        }

        val damp = 3.4f * (0.6f + table.friction)
        val decay = exp((-damp * dt).toDouble()).toFloat()
        pitchRate *= decay
        rollRate *= decay

        pitch += pitchRate * dt
        roll += rollRate * dt

        // 넘어가는 문턱(88도)을 넘으면 "한 면이 넘어간" 것으로 확정한다.
        var flopThisStep = false
        if (abs(pitch) >= 88f) {
            flopThisStep = true
            flopPending = true
            hasFlopped = true
            flops++
            // 90도 넘김은 바깥(지우개 물리)이 텀블로 옮겨 담는다.
            pitch -= sign(pitch) * 90f
            pitchRate *= -0.25f
        }
        if (abs(pitch) < 0.05f && abs(pitchRate) < 0.05f) {
            pitch = 0f
            pitchRate = 0f
        }
        if (abs(roll) < 0.05f && abs(rollRate) < 0.05f) {
            roll = 0f
            rollRate = 0f
        }
        return flopThisStep
    }

    /** 넘어짐 이벤트를 한 번만 꺼내 간다. */
    fun consumeFlop(): Boolean {
        val had = flopPending
        flopPending = false
        return had
    }

    /** 진행축에 대한 관성 모멘트(옆으로 넘어갈 때). */
    private fun inertiaRoll(): Float =
        mass / 3f * (halfHeight * halfHeight + halfDepth * halfDepth)

    /** 좌우축(튕긴 방향)에 대한 관성 모멘트(앞뒤로 넘어갈 때). */
    private fun inertiaPitch(): Float =
        mass / 3f * (halfHeight * halfHeight + halfWidth * halfWidth)

    private fun sign(v: Float): Float = if (v < 0f) -1f else 1f

    companion object {
        private const val RAD_TO_DEG = 57.29578f

        /** 타격 충격 중 회전(옆으로 기움)으로 옮겨가는 비율. */
        private const val ROLL_GAIN = 0.05f

        /** 타격 충격 중 회전(앞뒤로 넘어감)으로 옮겨가는 비율. */
        private const val PITCH_GAIN = 0.5f
    }
}
