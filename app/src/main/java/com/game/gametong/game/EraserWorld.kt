package com.game.gametong.game

/**
 * 지우개 두 개가 올라간 책상. 물리 한 스텝과 충돌·낙하 판정을 담당한다.
 *
 * - 알까기: 두 지우개 모두 움직인다(탄성 충돌, 책상 밖으로 나가면 떨어짐).
 * - 레슬링: 수비 지우개는 `immovableSide` 로 고정되고, 공격 지우개가 적당한 세기로
 *   부딪히면 그 위로 올라탄다(`climbing`). 올라탄 채로 멈춰 겹침 비율이 충분하면 핀.
 */
class EraserWorld(
    val player: EraserBody,
    val opponent: EraserBody,
    var immovableSide: Side? = null,
) {
    var elapsed: Float = 0f
        private set
    var lastImpact: Float = 0f
        private set

    val bodies: List<EraserBody> get() = listOf(player, opponent)

    fun bodyOf(side: Side): EraserBody = if (side == Side.PLAYER) player else opponent
    fun defenderOf(attacker: Side): EraserBody = bodyOf(attacker.other())

    fun copy(): EraserWorld = EraserWorld(player.copy(), opponent.copy(), immovableSide)

    val settled: Boolean get() = bodies.all { it.isResting() }

    val anyFallen: Boolean get() = player.fallen || opponent.fallen

    fun flick(side: Side, velocity: Vec2) {
        val body = bodyOf(side)
        if (body.fallen) return
        body.vel = velocity
        body.spinSpeed = velocity.length() * 0.02f
    }

    fun step(dt: Float = Board.DT) {
        elapsed += dt
        for (body in bodies) {
            if (body.fallen) continue
            if (immovableSide == body.side) continue
            advance(body, dt)
        }
        handleContact()
        for (body in bodies) checkFall(body)
    }

    /** 멈출 때까지(또는 시간이 다 될 때까지) 계산한다. AI 예측과 테스트에 쓴다. */
    fun settle(maxSeconds: Float = Board.MAX_SIM_SECONDS): Float {
        var t = 0f
        while (!settled && t < maxSeconds) {
            step()
            t += Board.DT
        }
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

    private fun advance(body: EraserBody, dt: Float) {
        var decel = body.type.decel
        val other = defenderOther(body)
        if (body.climbing && !other.fallen) {
            decel += Board.CLIMB_EXTRA_DECEL * overlapRatio(body, other)
        }
        val speed = body.vel.length()
        if (speed > 0f) {
            val next = (speed - decel * dt).coerceAtLeast(0f)
            body.vel = if (next <= Board.REST_SPEED) Vec2.ZERO else body.vel * (next / speed)
        }
        if (body.contactCooldown > 0f) body.contactCooldown -= dt

        body.pos = body.pos + body.vel * dt
        body.spin += body.spinSpeed * dt
        body.spinSpeed *= kotlin.math.exp(-3.5f * dt)
    }

    private fun defenderOther(body: EraserBody): EraserBody =
        if (body.side == Side.PLAYER) opponent else player

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
        if (attacker.climbing) return
        val outward = (attacker.pos - defender.pos).normalized()
        val approach = -attacker.vel.dot(outward)
        lastImpact = maxOf(lastImpact, approach)

        when {
            approach > Board.CLIMB_MAX_SPEED -> {
                // 지나치게 세면 수비 지우개를 넘어가 버린다(핀 실패)
                attacker.contactCooldown = 0.55f
                attacker.vel = attacker.vel * 0.80f
            }

            approach >= Board.CLIMB_MIN_SPEED -> {
                // 적당한 세기: 옆면을 타고 위로 올라탄다
                attacker.climbing = true
                attacker.vel = attacker.vel * 0.70f
                val towardCenter = (defender.pos - attacker.pos).normalized()
                attacker.vel = attacker.vel + towardCenter * 70f
            }

            else -> {
                // 살짝 스친 정도: 옆으로 비켜 미끄러진다
                val vn = attacker.vel.dot(outward)
                if (vn < 0f) attacker.vel = attacker.vel - outward * (1.4f * vn)
                attacker.vel = attacker.vel * 0.85f
            }
        }
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

        val restitution = (a.type.restitution + b.type.restitution) * 0.5f
        val impulse = -(1f + restitution) * vn / invSum
        a.vel = a.vel - normalAB * (impulse * invA)
        b.vel = b.vel + normalAB * (impulse * invB)

        val tangent = Vec2(-normalAB.y, normalAB.x)
        val tangential = rel.dot(tangent)
        a.spinSpeed -= tangential * 0.03f
        b.spinSpeed += tangential * 0.03f
    }

    private fun checkFall(body: EraserBody) {
        if (body.fallen) return
        val p = body.pos
        if (p.x < 0f || p.x > Board.WIDTH || p.y < 0f || p.y > Board.HEIGHT) {
            body.fallen = true
            body.vel = Vec2.ZERO
            body.spinSpeed = 0f
        }
    }
}
