package com.game.gametong.game

import kotlin.random.Random

enum class RoundOutcome { NONE, PLAYER, AI }

/**
 * 지우개 알까기(낙하) 모드.
 *
 * 번갈아 한 번씩 튕겨 상대 지우개를 책상 밖으로 떨어뜨린다. 내 지우개가 떨어지면
 * 그 라운드는 상대 승리. 3판 2선승.
 *
 * 책상 표면([surface])의 상태에 따라 마찰이 달라 미끄러지는 거리·회전·튕김이 바뀌고,
 * 내 지우개의 어느 지점을 때리는지([flick] 의 strikeOffset)에 따라 회전이 붙어 궤적이 휜다.
 */
class AlkkaegiMatch(
    val playerType: EraserType = EraserType.STANDARD,
    val aiType: EraserType = EraserType.STANDARD,
    var surface: DeskSurface = DeskSurface.WOOD,
    seed: Long = 20261121L,
) : DuelMatch {
    val random = Random(seed)

    var playerRounds: Int = 0
        private set
    var aiRounds: Int = 0
        private set
    var round: Int = 1
        private set
    override var turn: Side = Side.PLAYER
        private set
    override var phase: MatchPhase = MatchPhase.AIMING
        private set
    override var winner: Side? = null
        private set
    var lastRoundWinner: Side? = null
        private set
    var turnsInRound: Int = 0
        private set

    /** 마지막 수에서 지우개가 실제로 미끄러진 거리(표면 마찰 체감용). */
    var lastSlide: Float = 0f
        private set

    override val world: EraserWorld = newWorld()
    override val isFinished: Boolean get() = phase == MatchPhase.FINISHED
    private var aiThrough = 0

    fun newWorld(): EraserWorld = EraserWorld(
        player = EraserBody(Side.PLAYER, playerType, PLAYER_HOME, PLAYER_HOME),
        opponent = EraserBody(Side.AI, aiType, AI_HOME, AI_HOME),
        surface = surface,
    )

    val isPlayerTurn: Boolean get() = phase == MatchPhase.AIMING && turn == Side.PLAYER
    val isAiTurn: Boolean get() = phase == MatchPhase.AIMING && turn == Side.AI

    /** 알까기는 평평하게 놓고 미는 놀이라 젖힘을 주지 않는다. */
    override fun flick(velocity: Vec2, strikeOffset: Float, tip: Float) {
        if (phase != MatchPhase.AIMING) return
        if (velocity.length() < 40f) return
        lastSlide = 0f
        world.flick(turn, velocity, strikeOffset, 0f)
        phase = MatchPhase.MOVING
    }

    fun settleFlick(): RoundOutcome {
        check(phase == MatchPhase.MOVING) { "움직이는 중일 때만 정산할 수 있다" }
        val attacker = world.bodyOf(turn)
        val defender = world.bodyOf(turn.other())
        turnsInRound++
        lastSlide = attacker.home.distanceTo(attacker.pos)

        if (attacker.fallen || defender.fallen) {
            // 내가 떨어졌으면 상대 승리(같이 떨어져도 마찬가지) — 남는 쪽이 라운드를 가져간다.
            val roundWinner = if (attacker.fallen) turn.other() else turn
            return finishRound(roundWinner)
        }

        if (turnsInRound >= Board.MAX_TURNS_PER_ROUND) {
            return finishRound(closerToCenter())
        }

        turn = turn.other()
        phase = MatchPhase.AIMING
        return RoundOutcome.NONE
    }

    /** 라운드 결과 연출 후 호출. 다음 라운드를 열거나 경기를 끝낸다. */
    fun nextRound() {
        if (phase != MatchPhase.ROUND_END) return
        val roundWinner = lastRoundWinner ?: return
        if (playerRounds >= ROUNDS_TO_WIN || aiRounds >= ROUNDS_TO_WIN) {
            winner = if (playerRounds > aiRounds) Side.PLAYER else Side.AI
            phase = MatchPhase.FINISHED
            return
        }
        round++
        turnsInRound = 0
        lastSlide = 0f
        world.player.resetHome()
        world.opponent.resetHome()
        world.immovableSide = null
        world.surface = surface
        turn = roundWinner.other()
        phase = MatchPhase.AIMING
    }

    override fun aiVelocity(accuracy: Float, strikeOffset: Float): Vec2 {
        aiThrough++
        val noisyRandom = Random(random.nextLong() xor (aiThrough * 6151L))
        return EraserAi.choose(world, turn, AiMode.ALKKAEGI, accuracy, noisyRandom, strikeOffset, tip = 0f)
    }

    private fun finishRound(roundWinner: Side): RoundOutcome {
        lastRoundWinner = roundWinner
        if (roundWinner == Side.PLAYER) playerRounds++ else aiRounds++
        phase = MatchPhase.ROUND_END
        return if (roundWinner == Side.PLAYER) RoundOutcome.PLAYER else RoundOutcome.AI
    }

    private fun closerToCenter(): Side {
        val center = Vec2(Board.WIDTH / 2f, Board.HEIGHT / 2f)
        val playerDist = world.player.pos.distanceTo(center)
        val aiDist = world.opponent.pos.distanceTo(center)
        // 포기하지 않은 쪽이 유리하도록, 더 가까운 쪽이 라운드를 가져간다.
        return if (playerDist <= aiDist) Side.PLAYER else Side.AI
    }

    companion object {
        const val ROUNDS_TO_WIN = 2
        val PLAYER_HOME = Vec2(500f, 970f)
        val AI_HOME = Vec2(500f, 530f)
    }
}
