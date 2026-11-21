package com.game.gametong.game

import kotlin.random.Random

/**
 * 네모 딱지치기 한 판.
 *
 * 번갈아 한 번씩 상대 딱지를 노린다. 뒤집으면 상대 딱지를 따먹고(1점) 상대가 새 딱지를 놓는다.
 * 먼저 [WIN_SCORE]점을 내거나, 서로 [TURN_LIMIT]번씩 친 뒤 점수가 높은 쪽이 이긴다.
 */
class SquareTtakjiMatch(
    val surface: TtakjiSurface,
    playerSpec: TtakjiSpec,
    aiSpec: TtakjiSpec,
    val twoPlayer: Boolean = false,
    val aiSkill: Float = 0.8f,
    private val random: Random = Random(20261121),
) {
    companion object {
        const val WIN_SCORE = 3
        const val TURN_LIMIT = 8
        const val PLAYER_HOME_Y = 1150f
        const val AI_HOME_Y = 730f
        const val HOME_X = 500f
    }

    var playerSpec: TtakjiSpec = playerSpec
        private set
    var aiSpec: TtakjiSpec = aiSpec
        private set

    init {
        // 들뜬 날개 틈은 서로 마주 보는 쪽(상대가 노려볼 수 있는 변)에만 생긴다.
        this.playerSpec = this.playerSpec.withWeakEdge(reachableWeakEdge(Side.PLAYER))
        this.aiSpec = this.aiSpec.withWeakEdge(reachableWeakEdge(Side.AI))
    }

    val playerHome: Vec2 = Vec2(HOME_X, PLAYER_HOME_Y)
    val aiHome: Vec2 = Vec2(HOME_X, AI_HOME_Y)

    var playerScore: Int = 0
        private set
    var aiScore: Int = 0
        private set

    var playerStamina: Float = 100f
        private set
    var aiStamina: Float = 100f
        private set

    /** 남은 부상 턴 수(파워 상한이 걸린 내 차례 수). */
    var playerInjury: Int = 0
        private set
    var aiInjury: Int = 0
        private set

    var playerAttacks: Int = 0
        private set
    var aiAttacks: Int = 0
        private set

    var turn: Side = Side.PLAYER
        private set

    var finished: Boolean = false
        private set
    var winner: Side? = null
        private set

    var lastResult: SquareStrikeResult? = null
        private set
    var lastSoft: Boolean = false
        private set

    val playerPowerCap: Float get() = SquareTtakjiEngine.powerCap(playerStamina, playerInjury)
    val aiPowerCap: Float get() = SquareTtakjiEngine.powerCap(aiStamina, aiInjury)

    fun specOf(side: Side): TtakjiSpec = if (side == Side.PLAYER) playerSpec else aiSpec

    fun staminaOf(side: Side): Float = if (side == Side.PLAYER) playerStamina else aiStamina

    fun injuryOf(side: Side): Int = if (side == Side.PLAYER) playerInjury else aiInjury

    fun scoreOf(side: Side): Int = if (side == Side.PLAYER) playerScore else aiScore

    fun homeOf(side: Side): Vec2 = if (side == Side.PLAYER) playerHome else aiHome

    /** 그 쪽 딱지가 상대에게 내미는 변(플레이어는 위·양옆, AI는 아래·양옆). */
    fun reachableWeakEdge(defender: Side): Int {
        val options = if (defender == Side.AI) intArrayOf(2, 1, 3) else intArrayOf(0, 1, 3)
        return options[random.nextInt(options.size)]
    }

    /** 지금 칠 차례인 쪽. 2인 대전이면 화면에서 번갈아 조작한다. */
    val activeSide: Side get() = if (finished) turn else turn

    /**
     * 한 수를 친다. [aimDeg] 0 = 상대 정면, [power] 0~1(피로·부상 상한이 적용된다),
     * [strikePoint] -1~1(내 딱지의 어느 쪽을 때리는지).
     */
    fun attack(side: Side, aimDeg: Float, power: Float, strikePoint: Float): SquareStrikeResult {
        if (finished) {
            return SquareTtakjiEngine.strike(
                homeOf(side), specOf(side), homeOf(side.other()), specOf(side.other()), 0f,
                surface, aimDeg, 0f, strikePoint, if (side == Side.PLAYER) -1f else 1f,
            )
        }
        val attackerSpec = specOf(side)
        val defenderSpec = specOf(side.other())
        val applied = SquareTtakjiEngine.appliedPower(power, staminaOf(side), injuryOf(side))
        val result = SquareTtakjiEngine.strike(
            attackerPos = homeOf(side),
            attacker = attackerSpec,
            defenderPos = homeOf(side.other()),
            defender = defenderSpec,
            defenderAngleDeg = 0f,
            surface = surface,
            aimDeg = aimDeg,
            power = applied,
            strikePoint = strikePoint,
            forwardSign = if (side == Side.PLAYER) -1f else 1f,
        )
        lastResult = result

        val stamina = SquareTtakjiEngine.settleStamina(
            stamina = staminaOf(side),
            appliedPower = applied,
            floorHit = result.floorHit,
            surface = surface,
            injuryTurns = injuryOf(side),
        )
        lastSoft = stamina.soft
        when (side) {
            Side.PLAYER -> {
                playerStamina = stamina.stamina
                playerInjury = stamina.injuryTurns
                playerAttacks++
            }
            Side.AI -> {
                aiStamina = stamina.stamina
                aiInjury = stamina.injuryTurns
                aiAttacks++
            }
        }

        if (result.flipped) {
            if (side == Side.PLAYER) playerScore++ else aiScore++
            // 뒤집힌 쪽은 새 딱지를 놓는다(새 날개 틈).
            when (side.other()) {
                Side.PLAYER -> playerSpec = playerSpec.withWeakEdge(reachableWeakEdge(Side.PLAYER))
                Side.AI -> aiSpec = aiSpec.withWeakEdge(reachableWeakEdge(Side.AI))
            }
        }

        finishIfNeeded(side)
        turn = if (finished) turn else side.other()
        return result
    }

    /** AI 차례를 한 번에 처리한다. */
    fun aiAttack(): SquareStrikeResult {
        if (twoPlayer) return attack(Side.AI, 0f, 0.5f, 0f)
        val spec = specOf(Side.AI)
        val defense = specOf(Side.PLAYER)
        val chosen = SquareTtakjiEngine.bestStrike(
            attackerPos = homeOf(Side.AI),
            attacker = spec,
            defenderPos = homeOf(Side.PLAYER),
            defender = defense,
            defenderAngleDeg = 0f,
            surface = surface,
            forwardSign = 1f,
            maxPower = aiPowerCap,
            random = random,
            skill = aiSkill,
        )
        return attack(Side.AI, chosen.aimDeg, chosen.power, chosen.strikePoint)
    }

    /** 2인 대전에서 "지금 차례인 쪽"이 치는 편의 함수. */
    fun currentAttack(aimDeg: Float, power: Float, strikePoint: Float): SquareStrikeResult =
        attack(turn, aimDeg, power, strikePoint)

    private fun finishIfNeeded(scorer: Side) {
        val scored = scoreOf(scorer)
        if (scored >= WIN_SCORE) {
            finished = true
            winner = scorer
            return
        }
        if (playerAttacks >= TURN_LIMIT && aiAttacks >= TURN_LIMIT) {
            finished = true
            winner = when {
                playerScore > aiScore -> Side.PLAYER
                aiScore > playerScore -> Side.AI
                else -> null
            }
        }
    }

    /** 남은 차례 수(서로 한 번씩 친 기준). 화면 표시용. */
    val remainingTurns: Int
        get() = ((TURN_LIMIT - playerAttacks) + (TURN_LIMIT - aiAttacks))
}
