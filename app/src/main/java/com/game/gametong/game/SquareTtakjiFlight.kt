package com.game.gametong.game

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 딱지 한 장의 2.5D 자세. 그리기 코드는 이 값만 보고 블록을 그린다.
 *
 * - [tilt] 는 경첩축([tiltAxis])을 중심으로 들린 각(도)이다. 90도를 넘으면 넘어가는 중, 180이면 완전히 뒤집힌 상태.
 * - [z] 는 바닥에서 뜬 높이, [yaw] 는 수직축 회전(도), [roll] 은 옆으로 기운 각(도)이다.
 */
data class TtakjiPose(
    val pos: Vec2,
    val z: Float = 0f,
    val tilt: Float = 0f,
    val tiltAxis: Vec2 = Vec2(1f, 0f),
    val yaw: Float = 0f,
    val roll: Float = 0f,
)

/**
 * 한 수가 나간 뒤 두 딱지의 움직임을 프레임 단위로 계산한다.
 *
 * 1. **접근**: 공격 딱지가 마찰로 감속하며 상대에게 미끄러져 들어간다.
 * 2. **충격**: 닿는 순간 밀림·튕김(Z축 바운스)·토크가 한꺼번에 걸린다.
 * 3. **들림**: 맞은 딱지가 경첩축을 축으로 들린다. 뒤집힘 비율이 1 미만이면
 *    최고 70~85도까지 치솟았다가 감쇠 진동으로 도로 떨어지는 **아슬아슬 웨이블**이 나온다.
 * 4. **정착**: 미끄러짐이 멈추고 Z축 바운스가 잦아들면 끝난다.
 *
 * 값은 [advance] 에 넘긴 dt 로 적분하므로, 화면 프레임(약 1/60초)마다 그대로 돌려도 되고
 * 테스트에서 잘게 돌려도 같은 결론이 나온다.
 */
class SquareTtakjiFlight(
    val result: SquareStrikeResult,
    private val attackerHome: Vec2,
    private val defenderHome: Vec2,
    private val attackerSpec: TtakjiSpec,
    private val defenderSpec: TtakjiSpec,
    private val surface: TtakjiSurface,
    private val forwardSign: Float,
) {
    companion object {
        /** 무대 단위 중력(단위/초²). */
        const val GRAVITY = 5200f

        /** 아슬아슬 웨이블의 진동수·감쇠. */
        const val WOBBLE_FREQ = 6.4f
        const val WOBBLE_DAMP = 3.0f

        /** 이 시간이 지나고 각도가 이보다 작으면 정착한 것으로 본다. */
        const val SETTLE_TIME = 0.55f
        const val SETTLE_TILT = 0.8f

        /** 한 수 연출의 최대 길이(초). */
        const val MAX_DURATION = 3.2f

        /** 이보다 느린 충돌은 튕김으로 치지 않는다(바닥에서 미세 진동 방지). */
        const val MIN_BOUNCE_VZ = 260f

        /** 남은 높이·속도가 이보다 작으면 바닥에 눕힌다. */
        const val REST_Z = 1.2f
        const val REST_VZ = 2f

        /** 회전(yaw) 감쇠 계수. */
        const val SPIN_DECAY = 3.2f
    }

    private val dir: Vec2 = SquareTtakjiEngine.aimDirection(result.approachDeg, forwardSign)

    /** 접근 중 미끄러짐 */
    private var attackerSpeed: Float =
        SquareTtakjiEngine.MAX_ATTACK_SPEED * result.appliedPower * attackerSpec.material.speedMul
    private val attackerDecel: Float = surface.friction * attackerSpec.material.frictionMul + 1f
    private var attackerTravel: Float = 0f

    private val contactForward: Float = (defenderHome - attackerHome).dot(dir)

    /** 두 딱지의 모서리가 맞닿는 지점까지 가야 하는 거리. */
    private val contactTravel: Float = (
        contactForward - (attackerSpec.halfSize + defenderSpec.halfSize) * SquareTtakjiEngine.CONTACT_SLACK
        ).coerceIn(0f, SquareTtakjiEngine.FIELD_H)

    var time: Float = 0f
        private set

    /** 맞은 뒤 흐른 시간(초). 아직 안 맞았으면 음수. */
    var impactTime: Float = -1f
        private set

    var impactHappened: Boolean = false
        private set

    // ── 맞은 딱지 ──
    var defenderPos: Vec2 = defenderHome
        private set
    var defenderZ: Float = 0f
        private set
    var defenderTilt: Float = 0f
        private set
    var defenderYaw: Float = 0f
        private set
    var defenderRoll: Float = 0f
        private set
    var defenderSpeed: Float = 0f
        private set

    private var defenderVz: Float = 0f
    private val defenderDecel: Float = surface.friction * defenderSpec.material.frictionMul + 1f

    // ── 공격 딱지 ──
    var attackerZ: Float = 0f
        private set
    var attackerTilt: Float = 0f
        private set
    private var attackerVz: Float = 0f

    /** 연출 내내 관찰된 최고 들림 각(도). 웨이블 검증에도 쓴다. */
    var peakTiltSeen: Float = 0f
        private set
    var maxHopSeen: Float = 0f
        private set
    var maxYawSeen: Float = 0f
        private set

    private var finished: Boolean = false

    val tiltAxis: Vec2 get() = Vec2(-dir.y, dir.x)

    val attackerPose: TtakjiPose
        get() = TtakjiPose(
            pos = attackerHome + dir * attackerTravel,
            z = attackerZ,
            tilt = attackerTilt,
            tiltAxis = tiltAxis,
        )

    val defenderPose: TtakjiPose
        get() = TtakjiPose(
            pos = defenderPos,
            z = defenderZ,
            tilt = defenderTilt,
            tiltAxis = tiltAxis,
            yaw = defenderYaw,
            roll = defenderRoll,
        )

    val isFinished: Boolean get() = finished

    val defenderAirborne: Boolean get() = defenderZ > 0.5f

    /** 90도를 넘긴 순간이 있는가(넘어가는 중). */
    val overTheEdge: Boolean get() = peakTiltSeen >= 90f

    /**
     * 한 프레임 진행한다. 연출이 끝났으면 true.
     * dt 는 초 단위이며 0.05초를 넘으면 잘라서(프레임이 끊겨도 튀지 않게) 쓴다.
     */
    fun advance(dt: Float): Boolean {
        if (finished) return true
        val step = dt.coerceIn(0f, 0.05f)
        time += step

        if (!impactHappened) stepApproach(step) else stepImpact(step)

        peakTiltSeen = maxOf(peakTiltSeen, defenderTilt)
        maxHopSeen = maxOf(maxHopSeen, defenderZ)
        maxYawSeen = maxOf(maxYawSeen, abs(defenderYaw))

        if (time >= MAX_DURATION) finished = true
        return finished
    }

    /** 연출을 끝까지 돌린다(테스트·AI 예측용). 걸린 시간을 돌려준다. */
    fun runToEnd(dt: Float = 1f / 120f): Float {
        var t = 0f
        while (!finished && t < MAX_DURATION + 0.5f) {
            advance(dt)
            t += dt
        }
        return t
    }

    // ── 접근 ─────────────────────────────────────────────────────
    private fun stepApproach(dt: Float) {
        if (result.contact) {
            val remain = contactTravel - attackerTravel
            attackerTravel += minOf(attackerSpeed * dt, remain.coerceAtLeast(0f))
            attackerSpeed = (attackerSpeed - attackerDecel * dt).coerceAtLeast(0f)
            // 지면을 스치며 조금씩 들썩인다
            val arc = sin(((attackerTravel / 120f).coerceIn(0f, 3.14159f)).toDouble()).toFloat()
            attackerZ = 9f * result.appliedPower * surface.bounce * arc
            if (remain <= 1f) startImpact()
        } else {
            // 헛스윙: 미끄러져 멈출 때까지
            attackerTravel += attackerSpeed * dt
            attackerSpeed = (attackerSpeed - attackerDecel * dt).coerceAtLeast(0f)
            attackerTilt += ((result.appliedPower * 10f) - attackerTilt) * (6f * dt)
            if (attackerSpeed <= Board.REST_SPEED) {
                attackerSpeed = 0f
                attackerTilt += (0f - attackerTilt) * (10f * dt)
                if (attackerTilt < 0.4f && time > 0.25f) finished = true
            }
        }
    }

    private fun startImpact() {
        impactHappened = true
        impactTime = time
        attackerSpeed = 0f

        // 공격 딱지는 충격을 받고 살짝 들렸다 내려앉는다.
        attackerVz = 40f * result.appliedPower * (0.4f + surface.bounce)
        attackerTilt = minOf(14f, result.appliedPower * 16f)

        // 맞은 딱지는 밀려나며 튀고 돌기 시작한다.
        defenderSpeed = result.pushSpeed
        defenderVz = sqrt(2f * GRAVITY * result.bounceHeight.coerceAtLeast(0f))
        defenderTilt = 0f
        defenderYaw = 0f
        defenderRoll = 0f
    }

    // ── 충격 이후 ─────────────────────────────────────────────────
    private fun stepImpact(dt: Float) {
        val since = (time - impactTime).coerceAtLeast(0f)

        // 1) 밀려나기 — 회전이 붙으면 궤적이 옆으로 휜다
        if (defenderSpeed > 0f) {
            val v = defenderSpeed
            val spin = result.defenderSpin * (v / 500f).coerceIn(0f, 2f)
            val curve = Vec2(-dir.y, dir.x) * (spin * 0.00042f * v * dt)
            defenderPos = defenderPos + dir * (v * dt) + curve
            val next = (v - defenderDecel * dt).coerceAtLeast(0f)
            defenderSpeed = if (next <= Board.REST_SPEED) 0f else next
        }

        // 2) Z축 바운스(공중으로 튀어 올랐다가 몇 번 튄다)
        if (defenderZ > 0f || defenderVz > 0f) {
            defenderZ += defenderVz * dt
            defenderVz -= GRAVITY * dt
            if (defenderZ <= 0f) {
                defenderZ = 0f
                val impactSpeed = abs(defenderVz)
                val restitution = 0.30f + 0.35f * surface.bounce
                // 약한 튕김은 삼킨다(안 그러면 바닥에서 영원히 미세하게 튄다).
                defenderVz = if (impactSpeed > MIN_BOUNCE_VZ) impactSpeed * restitution else 0f
            }
            if (defenderZ <= REST_Z && abs(defenderVz) <= REST_VZ) {
                defenderZ = 0f
                defenderVz = 0f
            }
        }

        // 3) 회전(yaw) — 지수 감쇠하는 각속도의 적분
        defenderYaw = result.defenderSpin / SPIN_DECAY *
            (1f - exp(-SPIN_DECAY * since)) *
            (1f + 0.15f * sin((WOBBLE_FREQ * since).toDouble()).toFloat())

        // 4) 들림(tilt) — 뒤집힘 또는 아슬아슬 웨이블
        updateTilt(since)

        // 들린 각에 비례해 옆으로도 기운다(중심에서 벗어난 타격일수록 크게).
        val rollTarget = (result.defenderSpin / 90f).coerceIn(-1f, 1f) * (defenderTilt * 0.30f)
        defenderRoll += (rollTarget - defenderRoll) * minOf(1f, 9f * dt)

        // 5) 공격 딱지의 잔짓
        if (attackerZ > 0f || attackerVz > 0f) {
            attackerZ += attackerVz * dt
            attackerVz -= GRAVITY * dt
            if (attackerZ <= 0f) {
                attackerZ = 0f
                attackerVz = if (abs(attackerVz) > MIN_BOUNCE_VZ) abs(attackerVz) * 0.25f else 0f
            }
            if (attackerZ <= REST_Z && abs(attackerVz) <= REST_VZ) {
                attackerZ = 0f
                attackerVz = 0f
            }
        }
        attackerTilt += (0f - attackerTilt) * minOf(1f, 8f * dt)

        val upright = if (result.flipped) defenderTilt >= 179.5f else defenderTilt < SETTLE_TILT
        if (since > SETTLE_TIME && defenderZ <= 0.01f && defenderSpeed <= 0f &&
            abs(defenderVz) < 1f && upright
        ) {
            finished = true
        }
    }

    private fun updateTilt(since: Float) {
        if (result.flipped) {
            // 넘어간다: 살짝 지나쳤다(오버슛) 내려앉는 곡선
            val dur = result.flipDuration.coerceAtLeast(0.18f)
            val p = (since / dur).coerceIn(0f, 1f)
            val ease = 1f - (1f - p) * (1f - p) * (1f - p)
            val overshoot = if (p >= 1f) 0f else 6f * sin((3.14159f * p).toDouble()).toFloat()
            defenderTilt = (180f * ease + overshoot).coerceAtMost(181f)
            return
        }
        // 들렸다가 도로 떨어진다. 최고 각(peak)은 뒤집힘 비율에서 나온다.
        val peak = result.tiltPeak
        val rise = 0.09f + 0.09f * result.appliedPower
        defenderTilt = if (since <= rise) {
            val p = since / rise
            peak * sin((3.14159f / 2f * p).toDouble()).toFloat()
        } else {
            // 감쇠 진동: 바닥을 때린 뒤에도 몇 번 더 들썩인다.
            val e = since - rise
            peak * exp(-WOBBLE_DAMP * e) * abs(cos((WOBBLE_FREQ * e).toDouble()).toFloat())
        }
    }
}
