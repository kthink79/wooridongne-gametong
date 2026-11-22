package com.game.gametong.game

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** 네모 딱지치기 노면. 마찰·탄성·먼지가 손맛을 바꾼다. */
enum class TtakjiSurface(
    val label: String,
    val hint: String,
    /** 쿨롱 마찰에 가까운 일정 감속(단위/초²). 낮을수록 멀리 미끄러진다. */
    val friction: Float,
    /** 바닥에 부딪혔을 때 튀는 정도(0~1). */
    val bounce: Float,
    /** 먼지 연출 세기(0~1). */
    val dust: Float,
) {
    MARU("교실 마루", "미끄럽고 잘 튕긴다 · 헛스윙 부상 1턴", 380f, 0.58f, 0.15f),
    DIRT("흙바닥", "먼지가 인다 · 무난하게 멈춘다 · 부상 2턴", 850f, 0.30f, 0.65f),
    ASPHALT("아스팔트", "거칠어 빨리 멈춘다 · 부상 2턴", 1150f, 0.18f, 0.35f);

    /** 헛스윙으로 바닥을 쳤을 때 파워가 묶이는 턴 수. 마루는 푹신해 1턴. */
    val injuryTurns: Int get() = if (bounce >= 0.5f) 1 else 2
}

/** 네모 딱지의 재질. 뒤집힘에 대한 저항과 공격력·활주 거리가 갈린다. */
enum class TtakjiMaterial(
    val label: String,
    val blurb: String,
    /** 이 재질로 만든 딱지가 뒤집히는 기준 토크. 높을수록 잘 안 뒤집힌다. */
    val flipResistance: Float,
    /** 때리는 쪽 토크 배율. */
    val attackMul: Float,
    /** 같은 힘으로 밀었을 때 마찰을 이기는 배율. 낮을수록 멀리 미끄러진다. */
    val frictionMul: Float,
    val speedMul: Float,
) {
    NEWSPAPER("신문지 딱지", "가볍고 잘 뒤집히지만 바람에 약하다", 52f, 1.00f, 0.90f, 1.05f),
    COLOR_PAPER("색종이 딱지", "얇고 반듯한 표준형", 58f, 1.05f, 1.00f, 1.00f),
    CARDSTOCK("도화지 딱지", "두껍고 묵직 — 잘 안 뒤집히지만 세게 나간다", 80f, 1.15f, 1.12f, 0.94f),
    CARDBOARD("골판지 딱지", "가장 무겁다 — 뒤집기는 어렵고 밀어내기에 강하다", 104f, 1.22f, 1.28f, 0.86f),
}

/**
 * 네모 딱지 한 장의 사양. 두께(1~3)는 뒤집힘 저항과 크기에 반영되고,
 * 들뜬 날개 틈(weakEdge)은 0=위, 1=오른쪽, 2=아래, 3=왼쪽 변이다.
 */
data class TtakjiSpec(
    val material: TtakjiMaterial = TtakjiMaterial.COLOR_PAPER,
    val thickness: Int = 2,
    val weakEdge: Int = 0,
) {
    val thick: Int get() = thickness.coerceIn(1, 3)
    val flipResistance: Float get() = material.flipResistance + (thick - 1) * 7f
    val size: Float get() = 190f + (thick - 1) * 12f
    val halfSize: Float get() = size / 2f
    val thicknessLabel: String
        get() = when (thick) {
            1 -> "얇게"
            2 -> "보통"
            else -> "두껍게"
        }
    fun withWeakEdge(edge: Int): TtakjiSpec = copy(weakEdge = edge)
}

/**
 * 상대 딱지의 어느 지점을 노릴지. 조준각(대각 방향)·파워와 함께 3요소 조작을 이룬다.
 *
 * [aimBiasDeg] 는 조준각에 더해지는 편향(도)이고, 나머지는 토크·회전·튕김·밀림 배율이다.
 */
enum class StrikeTarget(
    val label: String,
    val hint: String,
    val aimBiasDeg: Float,
    val torqueMul: Float,
    val spinMul: Float,
    val bounceMul: Float,
    val pushMul: Float,
) {
    /** 끌어온 방향 그대로. 예전과 같은 손맛. */
    AUTO("자동", "끌어온 방향 그대로 친다", 0f, 1f, 1f, 1f, 1f),

    /** 정통으로 밀어낸다 — 잘 튀고 잘 밀리지만 뒤집기는 어렵다. */
    CENTER("중앙", "정통으로 밀어낸다 — 잘 튀고 잘 밀리지만 잘 안 뒤집힌다", 0f, 0.86f, 0.40f, 1.30f, 1.22f),

    /** 모서리를 스친다 — 회전이 크게 붙어 옆으로 휘며 돈다. */
    CORNER("모서리", "모서리를 비스듬히 스친다 — 회전이 붙어 옆으로 휜다", 15f, 1.02f, 1.40f, 0.95f, 0.92f),

    /** 대각선을 파고든다 — 토크가 가장 세다. */
    DIAGONAL("대각", "대각선으로 파고든다 — 토크가 가장 세다", 9f, 1.30f, 0.95f, 0.85f, 0.90f),

    /** 들뜬 날개 틈을 직접 노린다 — 조준이 틈 쪽으로 살짝 보정된다. */
    WEAK_GAP("들뜬 틈", "들뜬 날개 틈을 직접 노린다 — 조준이 틈 쪽으로 보정된다", 0f, 1f, 0.75f, 1.05f, 0.90f),
}

/**
 * 네모 딱지치기 2.5D 물리.
 *
 * - 딱지의 진행 거리는 감속에서 닫힌 식으로 구한다(speed² / 2·decel).
 * - 상대 딱지와의 접촉점을 계산해 어느 변을 때렸는지, 들뜬 날개 틈을 맞혔는지 판정한다.
 * - 때리는 위치(딱지 중심에서 얼마나 벗어났는지)가 토크를 만들고, 뒤집힘 비율이 1을 넘으면 뒤집힌다.
 * - 결과에는 프레임 단위 2.5D 연출([com.game.gametong.game.SquareTtakjiFlight])이 쓸
 *   들림 각·회전·튕김 정보가 함께 담긴다.
 */
data class SquareStrikeResult(
    val contact: Boolean,
    val floorHit: Boolean,
    val weakPoint: Boolean,
    val flipped: Boolean,
    val flipRatio: Float,
    val slideDistance: Float,
    val overshoot: Float,
    val appliedPower: Float,
    val contactOffset: Float,
    val hitEdge: Int,
    val defenderEdge: Int,
    // ── 2.5D 연출·조작용 ─────────────────────────────────────────
    /** 노린 지점 */
    val target: StrikeTarget = StrikeTarget.AUTO,
    /** 실제로 적용된 입사 방향(도, 0 = 상대 정면) */
    val approachDeg: Float = 0f,
    /** 내 딱지의 어느 날을 때렸는지(-1~1) */
    val strikePoint: Float = 0f,
    /** 상대 변에서 얼마나 벗어난 지점을 맞혔는지로 늘어난 지렛대 배율 */
    val lever: Float = 1f,
    /** 맞은 딱지가 들리는 최고 각(도). 뒤집히면 180. */
    val tiltPeak: Float = 0f,
    /** 뒤집히는 데 걸리는 시간(초) */
    val flipDuration: Float = 0f,
    /** 맞은 딱지에 붙는 회전(도/초) */
    val defenderSpin: Float = 0f,
    /** 튀어 오르는 높이(무대 단위) */
    val bounceHeight: Float = 0f,
    /** 밀려나는 속도(단위/초) */
    val pushSpeed: Float = 0f,
) {
    val scored: Boolean get() = flipped

    /** 넘어갈 듯 말 듯 아슬아슬하게 들렸다가 도로 떨어진 수. */
    val nearFlip: Boolean get() = !flipped && contact && tiltPeak >= NEAR_FLIP_MIN

    companion object {
        /** 이 각 이상 들렸으면 "아슬아슬" 연출로 본다. */
        const val NEAR_FLIP_MIN = 65f
    }
}

object SquareTtakjiEngine {
    const val FIELD_W = 1000f
    const val FIELD_H = 1500f

    /** 풀파워로 때렸을 때 딱지가 나가는 최대 속도(단위/초). */
    const val MAX_ATTACK_SPEED = 1500f

    /**
     * 이 세기 이상으로 헛스윙하면 바닥을 쳐 부상한다.
     * 부상 중 파워 상한(0.5)보다 높게 잡아, 부상 중의 약한 헛스윙이 부상을 다시 만들지 않게 한다.
     */
    const val FLOOR_HIT_POWER = 0.6f

    /** 부상 중에는 이 세기까지만 낼 수 있다. */
    const val INJURED_POWER_CAP = 0.5f

    /** 파워 1.0 · 정타 기준 토크. 재질·두께의 뒤집힘 저항과 비교된다. */
    const val TORQUE_UNIT = 115f

    /** 들뜬 날개 틈을 정확히 맞혔을 때 토크 배율. */
    const val WEAK_POINT_MUL = 1.85f

    /** 들뜬 틈을 노릴 때 조준이 틈 쪽으로 휘는 최대 각(도). */
    const val GAP_ASSIST_MAX = 22f

    /** 맞은 지점이 변 끝일 때의 지렛대 배율(중심 0.85 ~ 끝 1.15). */
    const val LEVER_BASE = 0.85f
    const val LEVER_RANGE = 0.30f

    /** 날개 틈의 유효 폭(변의 절반을 1로 봤을 때). */
    const val WEAK_POINT_WINDOW = 0.45f

    /** 접촉 판정을 조금 관대하게(딱지 모서리끼리 스치는 것도 포함). */
    const val CONTACT_SLACK = 0.94f

    /** 때리는 자리가 이만큼 벗어나면 토크가 최대가 된다. */
    const val EDGE_TORQUE_FLOOR = 0.55f
    const val EDGE_TORQUE_RANGE = 0.45f

    /** 맞은 딱지가 받는 밀림 속도 비율(공격 속도 대비). */
    const val PUSH_UNIT = 0.34f

    /** 피로도 100에서 1.0, 0에서 0.70까지 파워 상한이 준다. */
    private const val STAMINA_CAP_MIN = 0.70f
    private const val STAMINA_CAP_RANGE = 0.30f

    /** 살살(이 구간) 치면 피로가 회복된다. */
    const val SOFT_MIN = 0.25f
    const val SOFT_MAX = 0.55f
    const val SOFT_RECOVERY = 10f

    fun powerCap(stamina: Float, injuryTurns: Int): Float {
        val staminaCap = STAMINA_CAP_MIN + STAMINA_CAP_RANGE * (stamina.coerceIn(0f, 100f) / 100f)
        return if (injuryTurns > 0) minOf(staminaCap, INJURED_POWER_CAP) else staminaCap
    }

    fun appliedPower(rawPower: Float, stamina: Float, injuryTurns: Int): Float =
        rawPower.coerceIn(0f, 1f).coerceAtMost(powerCap(stamina, injuryTurns))

    /** 조준각 0 = 상대 쪽 정면. 플레이어는 위(-y), AI는 아래(+y)를 본다. */
    fun aimDirection(aimDeg: Float, forwardSign: Float = -1f): Vec2 {
        val rad = Math.toRadians(aimDeg.toDouble())
        return Vec2(sin(rad).toFloat(), (cos(rad).toFloat() * forwardSign))
    }

    fun slideDistance(speed: Float, surface: TtakjiSurface, attacker: TtakjiSpec): Float {
        val decel = surface.friction * attacker.material.frictionMul
        return if (decel <= 0f) 0f else speed * speed / (2f * decel)
    }

    /** 이번 한 수 뒤의 피로도·부상 상태. */
    data class StaminaResult(
        val stamina: Float,
        val injuryTurns: Int,
        val soft: Boolean,
    )

    /**
     * 피로도는 세게 칠수록 많이 줄고, 살살(0.30~0.62) 치면 오히려 회복된다.
     * 바닥을 세게 치면(헛스윙) 노면에 따라 1~2턴 파워가 묶인다.
     */
    fun settleStamina(
        stamina: Float,
        appliedPower: Float,
        floorHit: Boolean,
        surface: TtakjiSurface,
        injuryTurns: Int,
    ): StaminaResult {
        val soft = !floorHit && appliedPower in SOFT_MIN..SOFT_MAX
        var next = stamina - (3f + 9f * appliedPower)
        if (soft) next += SOFT_RECOVERY
        var injury = (injuryTurns - 1).coerceAtLeast(0)
        if (floorHit) injury = surface.injuryTurns
        return StaminaResult(next.coerceIn(0f, 100f), injury, soft)
    }

    fun rotate(v: Vec2, deg: Float): Vec2 {
        val rad = Math.toRadians(deg.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        return Vec2(v.x * c - v.y * s, v.x * s + v.y * c)
    }

    /**
     * 때리는 한 수를 계산한다. 상태는 바꾸지 않는다(예측·AI 탐색에 그대로 쓴다).
     *
     * @param strikePoint 내 딱지의 어느 쪽을 때렸는지 -1(왼쪽 끝)~0(가운데)~1(오른쪽 끝)
     */
    fun strike(
        attackerPos: Vec2,
        attacker: TtakjiSpec,
        defenderPos: Vec2,
        defender: TtakjiSpec,
        defenderAngleDeg: Float,
        surface: TtakjiSurface,
        aimDeg: Float,
        power: Float,
        strikePoint: Float,
        forwardSign: Float = -1f,
        target: StrikeTarget = StrikeTarget.AUTO,
    ): SquareStrikeResult {
        val applied = power.coerceIn(0f, 1f)
        val aim = aimDeg + targetBias(attackerPos, defenderPos, defender, forwardSign, aimDeg, target)
        val dir = aimDirection(aim, forwardSign)
        val speed = MAX_ATTACK_SPEED * applied * attacker.material.speedMul
        val slide = slideDistance(speed, surface, attacker)

        val toDef = defenderPos - attackerPos
        val forward = toDef.dot(dir)
        val lateralVec = toDef - dir * forward
        val lateral = lateralVec.length()
        val reach = (attacker.halfSize + defender.halfSize) * CONTACT_SLACK
        // 살포시는 상대 모서리에 닿을 거리까지 가야 한다(중심 거리에서 두 반폭을 뺀 만큼).
        val contact = forward >= 0f && lateral <= reach && forward - reach <= slide

        if (!contact) {
            val floorHit = applied >= FLOOR_HIT_POWER
            val overshoot = slide - forward
            return SquareStrikeResult(
                contact = false,
                floorHit = floorHit,
                weakPoint = false,
                flipped = false,
                flipRatio = 0f,
                slideDistance = slide,
                overshoot = overshoot,
                appliedPower = applied,
                contactOffset = 0f,
                hitEdge = -1,
                defenderEdge = defender.weakEdge,
                target = target,
                approachDeg = aim,
                strikePoint = strikePoint,
                pushSpeed = applied * MAX_ATTACK_SPEED * PUSH_UNIT * target.pushMul,
            )
        }

        val contactPoint = attackerPos + dir * (forward - reach).coerceIn(0f, slide)
        val local = rotate(contactPoint - defenderPos, -defenderAngleDeg)
        // 때려 들어온 방향의 반대쪽 변이 맞은 변이다(아래에서 올라오면 상대의 아래 변).
        val approach = rotate(-dir, -defenderAngleDeg)
        val half = defender.halfSize
        val hitEdge = when {
            abs(approach.x) > abs(approach.y) -> if (approach.x > 0f) 1 else 3
            else -> if (approach.y > 0f) 2 else 0
        }
        val along = if (hitEdge == 1 || hitEdge == 3) local.y else local.x
        val contactOffset = (along / half).coerceIn(-1f, 1f)
        val weak = hitEdge == defender.weakEdge && abs(contactOffset) <= WEAK_POINT_WINDOW

        val pointFactor = EDGE_TORQUE_FLOOR + EDGE_TORQUE_RANGE * abs(strikePoint.coerceIn(-1f, 1f))
        // 변 끝을 맞히면 지렛대가 길어져 토크가 커진다(중심을 맞히면 덜 들린다).
        val lever = LEVER_BASE + LEVER_RANGE * abs(contactOffset)
        var torque = applied * TORQUE_UNIT * pointFactor * lever * attacker.material.attackMul * target.torqueMul
        if (weak) torque *= WEAK_POINT_MUL
        val ratio = torque / defender.flipResistance
        val flipped = ratio >= 1f

        return SquareStrikeResult(
            contact = true,
            floorHit = false,
            weakPoint = weak,
            flipped = flipped,
            flipRatio = ratio,
            slideDistance = slide,
            overshoot = slide - forward,
            appliedPower = applied,
            contactOffset = contactOffset,
            hitEdge = hitEdge,
            defenderEdge = defender.weakEdge,
            target = target,
            approachDeg = aim,
            strikePoint = strikePoint.coerceIn(-1f, 1f),
            lever = lever,
            tiltPeak = tiltPeakFor(ratio),
            flipDuration = flipDurationFor(ratio),
            defenderSpin = defenderSpinFor(applied, strikePoint, contactOffset, target, aim),
            bounceHeight = bounceHeightFor(applied, surface, defender, target),
            pushSpeed = pushSpeedFor(applied, contactOffset, target),
        )
    }

    /**
     * 노린 지점에 따라 조준각에 더할 편향.
     * 들뜬 틈은 실제 틈 방향으로 조금 휘게 해 준다(최대 [GAP_ASSIST_MAX]도).
     */
    fun targetBias(
        attackerPos: Vec2,
        defenderPos: Vec2,
        defender: TtakjiSpec,
        forwardSign: Float,
        aimDeg: Float,
        target: StrikeTarget,
    ): Float = when (target) {
        StrikeTarget.WEAK_GAP -> (weakSpotAim(attackerPos, defenderPos, defender, forwardSign) - aimDeg)
            .coerceIn(-GAP_ASSIST_MAX, GAP_ASSIST_MAX)
        // 같은 "오른쪽"이 두 사람 모두에게 자기 기준 오른쪽이 되도록 좌우를 맞춘다.
        else -> target.aimBiasDeg * (if (forwardSign < 0f) 1f else -1f)
    }

    /** 뒤집히기 전이면 들리는 최고 각, 뒤집히면 180도. */
    fun tiltPeakFor(ratio: Float): Float =
        if (ratio >= 1f) 180f else (18f + 67f * ratio.coerceIn(0f, 1f)).coerceAtMost(85f)

    fun flipDurationFor(ratio: Float): Float =
        if (ratio < 1f) 0f else (0.42f - 0.10f * ((ratio - 1f) / 2f).coerceIn(0f, 1f))

    /** 중심에서 벗어난 타격·모서리 타격일수록 회전이 크게 붙는다. */
    fun defenderSpinFor(
        power: Float,
        strikePoint: Float,
        contactOffset: Float,
        target: StrikeTarget,
        aimDeg: Float,
    ): Float {
        val off = (strikePoint.coerceIn(-1f, 1f) * 170f + contactOffset * 300f)
        val diagonal = 1f + 0.35f * (abs(aimDeg) / 30f).coerceIn(0f, 1f)
        return power * off * target.spinMul * diagonal
    }

    fun bounceHeightFor(
        power: Float,
        surface: TtakjiSurface,
        defender: TtakjiSpec,
        target: StrikeTarget,
    ): Float {
        val lightness = 1.35f - 0.12f * (defender.flipResistance - TtakjiMaterial.NEWSPAPER.flipResistance) / 12f
        return power * 60f * (0.35f + surface.bounce) * target.bounceMul * lightness.coerceIn(0.7f, 1.4f)
    }

    fun pushSpeedFor(power: Float, contactOffset: Float, target: StrikeTarget): Float =
        power * MAX_ATTACK_SPEED * PUSH_UNIT * target.pushMul * (0.78f + 0.22f * abs(contactOffset))

    /**
     * AI가 고르는 한 수: 뒤집을 수 있는 수 중 가장 약한(피로가 적은) 것을 고른다.
     * 뒤집을 수 없으면 상대를 맞히는 수 중 가장 센 것을 고른다. skill < 1 이면 조준이 흔들린다.
     */
    fun bestStrike(
        attackerPos: Vec2,
        attacker: TtakjiSpec,
        defenderPos: Vec2,
        defender: TtakjiSpec,
        defenderAngleDeg: Float,
        surface: TtakjiSurface,
        forwardSign: Float,
        maxPower: Float,
        random: kotlin.random.Random,
        skill: Float = 1f,
    ): SquareStrike {
        var bestFlip: SquareStrike? = null
        var bestContact: SquareStrike? = null
        var bestContactRatio = -1f
        var sweep = -30f
        while (sweep <= 30f) {
            for (p in 4..10) {
                val power = p / 10f
                if (power > maxPower + 1e-4f) continue
                for (edge in listOf(-0.95f, -0.5f, 0f, 0.5f, 0.95f)) {
                    // 사람이 고를 수 있는 노림 지점도 AI가 함께 저울질한다.
                    for (target in AI_TARGETS) {
                        val result = strike(
                            attackerPos, attacker, defenderPos, defender, defenderAngleDeg,
                            surface, sweep, power, edge, forwardSign, target,
                        )
                        val candidate = SquareStrike(sweep, power, edge, target)
                        if (result.flipped) {
                            val current = bestFlip
                            if (current == null || candidate.power < current.power - 1e-4f) bestFlip = candidate
                        } else if (result.contact && result.flipRatio > bestContactRatio) {
                            bestContactRatio = result.flipRatio
                            bestContact = candidate
                        }
                    }
                }
            }
            sweep += 3f
        }
        val ideal = bestFlip ?: bestContact ?: SquareStrike(0f, 0.6f, 0f)
        val miss = (1f - skill.coerceIn(0f, 1f))
        val aim = ideal.aimDeg + (random.nextFloat() * 2f - 1f) * 9f * miss
        val power = (ideal.power + (random.nextFloat() * 2f - 1f) * 0.18f * miss)
            .coerceIn(0.2f, 1f)
        return SquareStrike(aim, power, ideal.strikePoint, ideal.target)
    }

    /** AI가 저울질하는 노림 지점. */
    val AI_TARGETS = listOf(
        StrikeTarget.AUTO,
        StrikeTarget.CENTER,
        StrikeTarget.DIAGONAL,
        StrikeTarget.WEAK_GAP,
        StrikeTarget.CORNER,
    )

    /**
     * bestStrike 가 돌려준 조준을 그대로 계산해 보는 편의 함수(AI 흔들림까지 반영된 최종 결과).
     */
    fun resolve(
        attackerPos: Vec2,
        attacker: TtakjiSpec,
        defenderPos: Vec2,
        defender: TtakjiSpec,
        defenderAngleDeg: Float,
        surface: TtakjiSurface,
        strike: SquareStrike,
        forwardSign: Float,
    ): SquareStrikeResult = strike(
        attackerPos, attacker, defenderPos, defender, defenderAngleDeg,
        surface, strike.aimDeg, strike.power, strike.strikePoint, forwardSign, strike.target,
    )

    /** from 에서 to 를 보는 조준각(0 = 상대 정면). */
    fun aimFromTo(from: Vec2, to: Vec2, forwardSign: Float): Float {
        val d = (to - from).normalized()
        val y = if (forwardSign < 0f) -d.y else d.y
        return Math.toDegrees(kotlin.math.atan2(d.x.toDouble(), y.toDouble())).toFloat()
    }

    /**
     * 상대 딱지의 들뜬 날개 틈이 있는 변의 중앙에서 조금 옆을 노리는 조준각.
     * 화면의 "노려라" 힌트와 테스트가 쓴다.
     */
    fun weakSpotAim(
        attackerPos: Vec2,
        defenderPos: Vec2,
        defender: TtakjiSpec,
        forwardSign: Float,
    ): Float {
        val off = defender.halfSize * 0.3f
        val local = when (defender.weakEdge) {
            0 -> Vec2(off, -defender.halfSize)
            1 -> Vec2(defender.halfSize, off)
            2 -> Vec2(off, defender.halfSize)
            else -> Vec2(-defender.halfSize, off)
        }
        return aimFromTo(attackerPos, defenderPos + local * 0.55f, forwardSign)
    }
}

/** AI가 내는 한 수. */
data class SquareStrike(
    val aimDeg: Float,
    val power: Float,
    val strikePoint: Float,
    val target: StrikeTarget = StrikeTarget.AUTO,
)
