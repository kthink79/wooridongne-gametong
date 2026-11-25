package com.game.gametong.game

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.sin

/**
 * 지우개 두 개가 올라간 책상. 물리 한 스텝과 충돌·낙하·걸침 판정을 담당한다.
 *
 * - 알까기: 두 지우개 모두 움직인다(탄성 충돌, 책상 밖으로 나가면 떨어짐).
 * - 레슬링: 수비 지우개는 `immovableSide` 로 고정되고, 공격 지우개가 적당한 세기로
 *   부딪히면 그 위로 **걸쳐 올라간다**(`climbing`+`perchedOn`). 겹침 비율과 기운 각으로
 *   핀 난이도가 갈린다.
 * - 진행하면서 90도씩 넘어가며 구르고([EraserBody.tumble]), 중심에서 벗어난 타격은
 *   회전을 붙여 궤적을 휘게 한다.
 * - 표면([surface])의 마찰이 미끄러짐·회전 감쇠·튕김을 바꾼다.
 */
class EraserWorld(
    val player: EraserBody,
    val opponent: EraserBody,
    var immovableSide: Side? = null,
    var surface: DeskSurface = DeskSurface.WOOD,
    /** 중심에서 벗어나 때린 정도(-1~1). 회전이 붙어 궤적이 휜다. */
    var strikeOffset: Float = 0f,
) {
    var elapsed: Float = 0f
        private set
    var lastImpact: Float = 0f
        private set

    /** 이번 스텝에서 부딪힌 세기. 가루 파티클을 이 값으로 터뜨린다. */
    var lastStepImpact: Float = 0f
        private set

    val bodies: List<EraserBody> get() = listOf(player, opponent)

    fun bodyOf(side: Side): EraserBody = if (side == Side.PLAYER) player else opponent
    fun defenderOf(attacker: Side): EraserBody = bodyOf(attacker.other())

    fun copy(): EraserWorld = EraserWorld(player.copy(), opponent.copy(), immovableSide, surface, strikeOffset)

    val settled: Boolean get() = bodies.all { it.isResting() }

    val anyFallen: Boolean get() = player.fallen || opponent.fallen

    /**
     * 튕긴다. [offset] 은 내 지우개의 어느 지점을 때렸는지(-1 왼끝 ~ 0 가운데 ~ 1 오른끝)로,
     * 중심에서 벗어날수록 회전이 크게 붙고 궤적이 옆으로 휜다.
     */
    /**
     * 지우개를 민다.
     *
     * @param offset 누른 자리(−1 왼끝 ~ 0 가운데 ~ 1 오른끝). 회전이 붙어 궤적이 휜다.
     * @param tip 가장자리를 눌러 **앞으로 젖히는** 정도(0~1). 뒤 가장자리를 누르고 밀면 앞으로
     *   넘어가듯 들려 상대 위로 올라간다(따먹기).
     */
    fun flick(side: Side, velocity: Vec2, offset: Float = 0f, tip: Float = 0f) {
        val body = bodyOf(side)
        if (body.fallen) return
        val off = offset.coerceIn(-1f, 1f)
        val t = tip.coerceIn(-1f, 1f)
        strikeOffset = off
        body.vel = velocity
        body.spinSpeed = velocity.length() * (off * 0.075f)
        body.lean = (off * 16f).coerceIn(-24f, 24f)
        // 3D 강체: 중심에서 벗어난 타격은 각운동량을 붙여 옆으로/앞으로 기울인다.
        body.dyn.strike(off, velocity.length(), strikeHeight = 0.28f)
        if (t != 0f) {
            // 가장자리를 디뎠으므로 앞뒤로 젖혀진다(뒤 가장자리를 누르면 앞으로).
            body.dyn.press(t)
            if (t > 0f) body.z = maxOf(body.z, body.blockHeight * 0.35f * t)
        }
        body.climbing = false
        body.perchedOn = null
        if (velocity.length() > 60f) {
            val axis = Vec2(-velocity.y, velocity.x).normalized()
            if (axis.length() > 0f) body.tumbleAxis = axis
        }
    }

    /** [EraserPress] 가 만든 한 번의 누르기를 그대로 받아 친다. */
    fun press(side: Side, push: EraserPush) {
        if (!push.canShoot) return
        flick(side, push.velocity, push.strikeOffset, push.tip)
    }

    fun step(dt: Float = Board.DT) {
        elapsed += dt
        lastStepImpact = 0f
        for (body in bodies) {
            if (body.fallen) continue
            if (immovableSide == body.side) continue
            advance(body, dt)
        }
        handleContact()
        for (body in bodies) checkFall(body)
        updateDynamics(dt)
    }

    /**
     * 3D 기울기·넘어짐을 한 스텝 적분한다.
     * 책상 가장자리에 걸친 만큼 받침이 줄어 넘어지기 쉽고, 넘어지면 90도 텀블로 이어진다.
     */
    private fun updateDynamics(dt: Float) {
        for (body in bodies) {
            if (body.fallen) continue
            val supported = !body.perched && !body.climbing && body.z < 1f
            val edge = edgeFraction(body)
            if (body.dyn.step(dt, surface.material, supported, edge)) {
                // 무게중심이 받침 모서리를 넘었다 → 한 면이 바닥에 닿는 90도 텀블로 넘긴다.
                body.tumble += 90f
            }
        }
    }

    /** 책상 가장자리 밖으로 지우개 중심이 나간 정도(0 ~ 1). */
    private fun edgeFraction(body: EraserBody): Float {
        val p = body.pos
        val dx = maxOf(0f, -p.x, p.x - Board.WIDTH)
        val dy = maxOf(0f, -p.y, p.y - Board.HEIGHT)
        val d = kotlin.math.sqrt(dx * dx + dy * dy)
        return (d / (body.radius * 2.4f)).coerceIn(0f, 1f)
    }

    /** 멈출 때까지(또는 시간이 다 될 때까지) 계산한다. AI 예측과 테스트에 쓴다. */
    fun settle(maxSeconds: Float = Board.MAX_SIM_SECONDS): Float {
        var t = 0f
        while (!settled && t < maxSeconds) {
            step()
            t += Board.DT
        }
        // 멈춘 뒤에는 놓인 면이 반듯해지고, 걸친 자세가 지금 자리에 맞게 확정된다.
        for (body in bodies) if (!body.fallen) body.snapTumble()
        snapPerchPose()
        return t
    }

    fun overlapRatio(attacker: EraserBody, defender: EraserBody): Float =
        Geometry.overlapRatio(attacker.radius, defender.radius, attacker.pos.distanceTo(defender.pos))

    fun pinRatioFor(side: Side): Float {
        val attacker = bodyOf(side)
        val defender = defenderOf(side)
        if (attacker.fallen || defender.fallen) return 0f
        return overlapRatio(attacker, defender)
    }

    /**
     * 지금 그 쪽이 상대 위에 얼마나 잘 **걸쳐 앉았는지**(0~1).
     * 겹침이 [Board.PIN_OVERLAP] 미만이거나 기운 각이 [Board.PIN_MAX_LEAN] 를 넘으면 0(핀 불가).
     */
    fun pinQualityFor(side: Side): Float {
        val attacker = bodyOf(side)
        val defender = defenderOf(side)
        if (attacker.fallen || defender.fallen) return 0f
        if (attacker.perchedOn == null || attacker.perchedOn != defender.side) return 0f
        val overlap = overlapRatio(attacker, defender)
        if (overlap < Board.PIN_OVERLAP) return 0f
        if (attacker.lean > Board.PIN_MAX_LEAN) return 0f
        val overlapScore = (overlap / Board.PIN_FULL_OVERLAP).coerceIn(0f, 1f)
        val leanScore = 1f - (attacker.lean / Board.PIN_MAX_LEAN).coerceIn(0f, 1f)
        return overlapScore * 0.65f + leanScore * 0.35f
    }

    /**
     * 걸쳐 앉은 지우개가 핀 대신 **자연스럽게 걸터져 내려오게** 만든다(빠져나옴).
     * 예전에는 그 자리에서 380 의 속도로 튕겨 나가 "순간이동"처럼 보였다. 이제는
     * 가장자리에서 살짝 밀려 내려오는 정도만 준다.
     */
    fun slipOff(side: Side) {
        val body = bodyOf(side)
        val other = defenderOf(side)
        body.perchedOn = null
        body.climbing = false
        body.z = 0f
        body.lean = 0f
        val away = (body.pos - other.pos).normalized()
        body.vel = if (away.length() > 0f) away * 300f else Vec2.ZERO
        body.spinSpeed *= 0.25f
    }

    private fun advance(body: EraserBody, dt: Float) {
        var decel = body.type.decel * surface.frictionMul
        val other = defenderOther(body)
        val perched = body.perchedOn != null
        if (perched && !other.fallen) {
            // 고무 위의 고무: 훨씬 빨리 붙잡힌다.
            decel += Board.CLIMB_EXTRA_DECEL * overlapRatio(body, other)
        }
        val speed = body.vel.length()
        if (speed > 0f) {
            var next = (speed - decel * dt).coerceAtLeast(0f)
            if (perched) {
                // 상대 위에서는 공기 저항처럼 한 번 더 붙잡아 미끄러져 떨어지는 일을 막는다.
                next *= decay(5.5f, dt)
            }
            body.vel = if (next <= Board.REST_SPEED) Vec2.ZERO else body.vel * (next / speed)
        }
        if (body.contactCooldown > 0f) body.contactCooldown -= dt

        val move = body.vel * dt
        val distance = move.length()
        body.pos = body.pos + move

        // 넘기면서 전진: 이동 거리만큼 90도씩 넘어간다(지름의 절반 = 한 번 넘어감).
        // 상대 위에 올라간 뒤에는 구르지 않고 놓인 면을 유지한다(위에서 데굴데굴 도는 것 방지).
        if (perched) {
            val target = body.settledTumble()
            body.tumble += (target - body.tumble) * minOf(1f, 10f * dt)
            updatePerch(body, other, dt)
        } else if (distance > 0.01f) {
            body.tumble += distance / (body.type.radius * 2f) * 90f
            updatePerch(body, other, dt)
        } else {
            // 멈추면 바닥에 놓인 면이 반듯해진다
            val target = body.settledTumble()
            body.tumble += (target - body.tumble) * minOf(1f, 14f * dt)
        }

        // 회전이 궤적을 휜다(매끈한 표면에서 크게 휜다). 속도는 유지하고 방향만 돌린다.
        if (abs(body.spinSpeed) > 1f && body.vel.length() > 1f) {
            val turn = body.spinSpeed * Board.SPIN_TURN * surface.curveMul * dt
            if (abs(turn) > 1e-4f) body.vel = rotate(body.vel, turn)
        }

        body.spin += (body.spinSpeed * dt).toFloat()
        body.spinSpeed *= exp((-Board.SPIN_DECAY * surface.spinDecayMul * dt).toDouble()).toFloat()

        // 기운 각은 서서히 돌아온다(걸쳐 있을 때는 걸침 각이 우선)
        if (!perched) {
            body.lean += (0f - body.lean) * minOf(1f, 5f * dt)
            body.z += (0f - body.z) * minOf(1f, 9f * dt)
        }
    }

    /**
     * 상대 위에 올라선 동안의 자세.
     *
     * 예전에는 겹침만 보고 기운 각을 공식으로 흥내 냈다. 그래서 **분명히 위에 올라갔는데도**
     * 겹침이 조금 줄면 기운 각이 40도를 넘어 "빠져나왔다"로 판정되고, 그 자리에서 튕겨 나가듯
     * 떨어졌다. 이제는 **무게중심이 상대 윗면 안에 있는지**로 본다.
     *
     * - 중심이 윗면 한가운데(d ≤ 0.35R)면 거의 눕는다(4~9도).
     * - 가장자리로 갈수록 비스듬히 걸친다(0.95R 에서 34도).
     * - 중심이 가장자리(1.15R)를 넘으면 진짜로 걸터져 내려간다(그때만 SLIP).
     */
    private fun updatePerch(body: EraserBody, other: EraserBody, dt: Float) {
        if (body.perchedOn == null || other.fallen) return
        val offset = body.pos - other.pos
        val d = offset.length()
        val radius = other.radius.coerceAtLeast(1f)
        val ratio = d / radius
        val overlap = overlapRatio(body, other)
        body.overlap = overlap

        // 윗면 밖으로 밀려나지 않게 부드럽게 붙잡는다(순간이동 없이 조금씩).
        val limit = radius * 1.06f
        if (d > limit && d > 1e-3f) {
            val back = (d - limit) * minOf(1f, 12f * dt)
            body.pos = body.pos - offset * (back / d)
            body.vel = body.vel * 0.55f
        }

        body.lean += (perchLeanFor(ratio) - body.lean) * minOf(1f, 14f * dt)
        val zTarget = other.blockHeight * (0.60f + 0.40f * (1f - ratio.coerceIn(0f, 1f)))
        body.z += (zTarget - body.z) * minOf(1f, 12f * dt)

        // 무게중심이 가장자리를 완전히 벗어났다 → 걸치지 못하고 내려간다.
        if (ratio > 1.24f) slipOff(body.side)
    }

    /**
     * 상대 윗면에서의 자리(중심 거리 / 상대 반지름)에 따른 **기운 각**.
     * 중심 위(0.35R 이하)면 거의 눕고, 가장자리로 갈수록 비스듬해진다(0.90R에서 38도).
     * 더 나가면 걸치지 못한 각(40도 초과)이 된다.
     */
    private fun perchLeanFor(ratio: Float): Float = when {
        ratio <= 0.35f -> 4f + 5f * (ratio / 0.35f)
        ratio <= 0.90f -> 9f + 29f * ((ratio - 0.35f) / 0.55f)
        else -> 38f + 18f * ((ratio - 0.90f) / 0.20f).coerceIn(0f, 1f)
    }

    /**
     * 멈춘 순간의 걸침 자세를 지금 자리에 맞게 확정한다.
     * 물리 스텝이 몇 번밖에 안 돌아 자세가 덜 자란 채로 판정되는 것을 막는다(화면과 판정이 같은 값).
     */
    fun snapPerchPose() {
        for (body in bodies) {
            if (body.fallen || body.perchedOn == null) continue
            val other = defenderOther(body)
            if (other.fallen) continue
            val ratio = body.pos.distanceTo(other.pos) / other.radius.coerceAtLeast(1f)
            body.overlap = overlapRatio(body, other)
            body.lean = perchLeanFor(ratio)
            body.z = other.blockHeight * (0.60f + 0.40f * (1f - ratio.coerceIn(0f, 1f)))
        }
    }

    private fun defenderOther(body: EraserBody): EraserBody =
        if (body.side == Side.PLAYER) opponent else player

    private fun rotate(v: Vec2, degrees: Float): Vec2 {
        val rad = Math.toRadians(degrees.toDouble())
        val c = kotlin.math.cos(rad).toFloat()
        val s = kotlin.math.sin(rad).toFloat()
        return Vec2(v.x * c - v.y * s, v.x * s + v.y * c)
    }

    private fun handleContact() {
        val a = player
        val b = opponent
        if (a.fallen || b.fallen) return
        if (a.contactCooldown > 0f || b.contactCooldown > 0f) return

        val delta = b.pos - a.pos
        val dist = maxOf(delta.length(), 0.001f)
        if (dist >= a.radius + b.radius) return
        val normalAB = delta * (1f / dist)

        val imm = immovableSide
        if (imm != null) {
            val attacker = if (imm == Side.PLAYER) b else a
            val defender = if (imm == Side.PLAYER) a else b
            handleClimb(attacker, defender)
        } else {
            resolveElastic(a, b, normalAB, dist)
        }
    }

    private fun handleClimb(attacker: EraserBody, defender: EraserBody) {
        if (attacker.climbing || attacker.perchedOn != null) return
        val outward = (attacker.pos - defender.pos).normalized()
        if (outward.length() < 1e-3f) return
        val approach = -attacker.vel.dot(outward)
        lastImpact = maxOf(lastImpact, approach)
        lastStepImpact = maxOf(lastStepImpact, approach)

        // 파고들지 않게 맞닿는 자리까지만 떼어 놓는다.
        val contact = attacker.radius + defender.radius
        val dist = attacker.pos.distanceTo(defender.pos)
        if (dist < contact) attacker.pos = defender.pos + outward * contact

        attacker.dyn.hit(approach, offset = (attacker.pos.x - defender.pos.x) / defender.radius, fromAbove = false)
        defender.dyn.hit(approach * 0.5f, offset = 0f, fromAbove = true)

        when {
            approach > Board.CLIMB_MAX_SPEED -> {
                // 지나치게 세면 고무끼리 튕겨 수비 지우개를 넘어가 버린다(핀 실패).
                attacker.contactCooldown = 0.26f
                val vn = attacker.vel.dot(outward)
                if (vn < 0f) attacker.vel = attacker.vel - outward * (vn * 1.35f)
                attacker.vel = attacker.vel * 0.62f
            }

            approach >= Board.CLIMB_MIN_SPEED -> climbUp(attacker, defender, approach, outward)

            else -> {
                // 살짝 스친 정도: 옆으로 비켜 미끄러진다
                val vn = attacker.vel.dot(outward)
                if (vn < 0f) attacker.vel = attacker.vel - outward * (1.4f * vn)
                attacker.vel = attacker.vel * 0.85f
            }
        }
    }

    /**
     * 옆면을 타고 올라가 상대 윗면에 선다.
     * 세게 칠수록 더 안쪽(무게중심 위)에 서고, 남은 속도도 조금 더 남는다.
     */
    private fun climbUp(attacker: EraserBody, defender: EraserBody, approach: Float, outward: Vec2) {
        val range = (Board.CLIMB_MAX_SPEED - Board.CLIMB_MIN_SPEED).coerceAtLeast(1f)
        val energy = ((approach - Board.CLIMB_MIN_SPEED) / range).coerceIn(0f, 1f)
        attacker.climbing = true
        attacker.perchedOn = defender.side
        attacker.contactCooldown = 0.22f
        // 1.02R(아슬아슬하게 걸터짐) ~ 0.52R(중심 쪽)에 선다.
        val landing = attacker.radius * (1.02f - 0.50f * energy)
        attacker.pos = defender.pos + outward * landing
        attacker.z = maxOf(attacker.z, defender.blockHeight * (0.55f + 0.45f * energy))
        attacker.tumble = attacker.settledTumble()
        val toward = (defender.pos - attacker.pos).normalized()
        val ride = (approach * (0.10f + 0.16f * energy)).coerceIn(0f, 300f)
        val sideways = Vec2(-outward.y, outward.x) * (approach * 0.04f)
        attacker.vel = toward * ride + sideways
        attacker.dyn.hit(approach * 0.4f, offset = 0f, fromAbove = true)
        lastImpact = maxOf(lastImpact, approach)
    }

    private fun resolveElastic(a: EraserBody, b: EraserBody, normalAB: Vec2, dist: Float) {
        val invA = 1f / a.mass
        val invB = 1f / b.mass
        val invSum = invA + invB
        val overlap = (a.radius + b.radius) - dist

        a.pos = a.pos - normalAB * (overlap * invA / invSum)
        b.pos = b.pos + normalAB * (overlap * invB / invSum)

        val rel = b.vel - a.vel
        val vn = rel.dot(normalAB)
        if (vn >= 0f) return
        lastImpact = maxOf(lastImpact, -vn)
        lastStepImpact = maxOf(lastStepImpact, -vn)

        val restitution = (a.type.restitution + b.type.restitution) * 0.5f * surface.restitutionMul
        val impulse = -(1f + restitution) * vn / invSum
        a.vel = a.vel - normalAB * (impulse * invA)
        b.vel = b.vel + normalAB * (impulse * invB)

        // 3D 강체: 맞은 지우개는 무게중심에서 벗어난 충격만큼 각운동량이 붙는다.
        val hitPoint = (a.pos + normalAB * a.radius)
        a.dyn.hit(-vn, offset = strikeOffset * 0.6f)
        b.dyn.hit(-vn, offset = ((hitPoint.x - b.pos.x) / b.radius).coerceIn(-1f, 1f))

        // 스치는 회전은 상대에게도 옮겨 붙는다(맞은 쪽이 돌아간다).
        val tangent = Vec2(-normalAB.y, normalAB.x)
        val tangential = rel.dot(tangent)
        a.spinSpeed -= tangential * 0.03f
        b.spinSpeed += tangential * 0.03f
        b.spinSpeed += strikeOffset * abs(vn) * 0.022f
        b.lean = (b.lean + strikeOffset * 10f).coerceIn(-26f, 26f)
    }

    private fun checkFall(body: EraserBody) {
        if (body.fallen) return
        val p = body.pos
        if (p.x < 0f || p.x > Board.WIDTH || p.y < 0f || p.y > Board.HEIGHT) {
            body.fallen = true
            body.vel = Vec2.ZERO
            body.spinSpeed = 0f
            body.z = 0f
            body.perchedOn = null
            body.climbing = false
        }
    }
}
