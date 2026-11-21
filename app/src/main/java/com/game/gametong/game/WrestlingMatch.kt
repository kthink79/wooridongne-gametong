package com.game.gametong.game

import kotlin.random.Random

enum class TurnOutcome { PIN, MISS, FELL }

/**
 * 지우개 레슬링(턴제 3카운트).
 *
 * 두 사람이 번갈아 한 번씩 튕긴다. 공격 지우개가 상대 지우개 위로 올라타 멈추면
 * (겹침 비율 ≥ PIN_OVERLAP) 1카운트. 3카운트를 먼저 채우면 승리.
 * 턴제 원샷 입력이라 P2P에서도 각 turn 의 입력 한 줄만 주고받으면 그대로 맞는다.
 */
class WrestlingMatch(
    val playerType: EraserType = EraserType.STANDARD,
    val aiType: EraserType = EraserType.STANDARD,
    val targetCounts: Int = 3,
    seed: Long = 20261121L,
) : DuelMatch {
    val random = Random(seed)

    var playerCounts: Int = 0
        private set
    var aiCounts: Int = 0
        private set
    override var turn: Side = Side.PLAYER
        private set
    override var phase: MatchPhase = MatchPhase.AIMING
        private set
    override var winner: Side? = null
        private set
    var lastOutcome: TurnOutcome? = null
        private set
    var countSide: Side? = null
        private set
    var turnNumber: Int = 1
        private set

    override val world: EraserWorld = newWorld()
    override val isFinished: Boolean get() = phase == MatchPhase.FINISHED
    private var aiThrough = 0

    fun newWorld(): EraserWorld = EraserWorld(
        player = EraserBody(Side.PLAYER, playerType, PLAYER_HOME, PLAYER_HOME),
        opponent = EraserBody(Side.AI, aiType, AI_HOME, AI_HOME),
        immovableSide = turn.other(),
    )

    val isPlayerTurn: Boolean get() = phase == MatchPhase.AIMING && turn == Side.PLAYER
    val isAiTurn: Boolean get() = phase == MatchPhase.AIMING && turn == Side.AI

    override fun flick(velocity: Vec2) {
        if (phase != MatchPhase.AIMING) return
        if (velocity.length() < 40f) return
        world.flick(turn, velocity)
        phase = MatchPhase.MOVING
    }

    /** 움직임이 멈춘 뒤 호출. 핀이면 COUNTING 으로 넘어가고, 아니면 다음 턴을 준비한다. */
    fun settleFlick(): TurnOutcome {
        check(phase == MatchPhase.MOVING) { "움직이는 중일 때만 정산할 수 있다" }
        val attacker = world.bodyOf(turn)
        val defender = world.bodyOf(turn.other())
        val outcome = when {
            attacker.fallen -> TurnOutcome.FELL
            world.overlapRatio(attacker, defender) >= Board.PIN_OVERLAP -> TurnOutcome.PIN
            else -> TurnOutcome.MISS
        }
        lastOutcome = outcome
        if (outcome == TurnOutcome.PIN) {
            countSide = turn
            phase = MatchPhase.COUNTING
        } else {
            prepareNextTurn(resetPositions = attacker.fallen)
        }
        return outcome
    }

    /** 카운트 연출이 끝난 뒤 호출한다. */
    fun applyCount() {
        if (phase != MatchPhase.COUNTING) return
        val side = countSide ?: return
        if (side == Side.PLAYER) playerCounts++ else aiCounts++
        val reached = if (side == Side.PLAYER) playerCounts else aiCounts
        if (reached >= targetCounts) {
            winner = side
            phase = MatchPhase.FINISHED
            return
        }
        prepareNextTurn(resetPositions = true)
    }

    override fun aiVelocity(accuracy: Float): Vec2 {
        aiThrough++
        val noisyRandom = Random(random.nextLong() xor (aiThrough * 7919L))
        return EraserAi.choose(world, turn, AiMode.WRESTLING, accuracy, noisyRandom)
    }

    private fun prepareNextTurn(resetPositions: Boolean) {
        val player = world.player
        val ai = world.opponent
        player.climbing = false
        ai.climbing = false
        player.contactCooldown = 0f
        ai.contactCooldown = 0f

        if (resetPositions) {
            player.resetHome()
            ai.resetHome()
        } else {
            separateIfOverlapping()
            if (player.pos.distanceTo(ai.pos) > Board.RESET_DISTANCE) {
                player.resetHome()
                ai.resetHome()
            }
        }

        if (player.fallen) player.resetHome()
        if (ai.fallen) ai.resetHome()

        turn = turn.other()
        world.immovableSide = turn.other()
        countSide = null
        turnNumber++
        phase = MatchPhase.AIMING
    }

    private fun separateIfOverlapping() {
        val player = world.player
        val ai = world.opponent
        val contact = player.radius + ai.radius
        val dist = player.pos.distanceTo(ai.pos)
        if (dist >= contact) return
        val dir = (player.pos - ai.pos).normalized()
        if (dir.length() == 0f) return
        player.pos = ai.pos + dir * (contact + 2f)
    }

    companion object {
        val PLAYER_HOME = Vec2(500f, 960f)
        val AI_HOME = Vec2(500f, 540f)
    }
}
