package com.game.gametong.game

import kotlin.random.Random

enum class TurnOutcome {
    /** 상대 위에 걸쳐 앉아 카운트를 얻었다. */
    PIN,

    /** 올라타긴 했지만 비스듬히 걸쳐만 있어 상대가 빠져나왔다. */
    SLIP,

    /** 닿지 않았거나 그냥 밀리기만 했다. */
    MISS,

    /** 내 지우개가 책상 밖으로 떨어졌다(링아웃). */
    FELL,
}

/**
 * 지우개 레슬링(턴제 3카운트).
 *
 * 두 사람이 번갈아 한 번씩 튕긴다. 공격 지우개가 상대 지우개 위로 **걸쳐 올라가** 멈추면
 * (겹침 >= [Board.PIN_OVERLAP] 이고 기운 각 <= [Board.PIN_MAX_LEAN]) 1카운트.
 * 3카운트를 먼저 채우면 승리하고, 지우개가 책상 밖으로 떨어지면(링아웃) 그대로 패배한다.
 *
 * **한 번 올라가도 처음 자리로 돌아가지 않는다.** 카운트·미스·빠져나옴 뒤에도 두 지우개는
 * 지금 책상 위에 있는 자리와 자세 그대로 다음 턴을 이어 간다.
 *
 * 턴제 원샷 입력이라 P2P에서도 각 turn 의 입력 한 줄만 주고받으면 그대로 맞는다.
 */
class WrestlingMatch(
    val playerType: EraserType = EraserType.STANDARD,
    val aiType: EraserType = EraserType.STANDARD,
    val targetCounts: Int = 3,
    var surface: DeskSurface = DeskSurface.WOOD,
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

    /** 마지막 카운트의 핀 품질(0~1). 카운트 연출 문구에 쓴다. */
    var lastPinQuality: Float = 0f
        private set

    /** 마지막 수에서 걸쳐 앉은 기운 각(도). */
    var lastLean: Float = 0f
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
        surface = surface,
    )

    val isPlayerTurn: Boolean get() = phase == MatchPhase.AIMING && turn == Side.PLAYER
    val isAiTurn: Boolean get() = phase == MatchPhase.AIMING && turn == Side.AI

    /**
     * AI도 가장자리를 눌러 미는 것처럼 친다 — 사람과 같은 손맛(젖힘)을 쓴다.
     * 너무 크면 뒤집혀 버리므로 0.35 로 둔다.
     */
    override val aiTip: Float get() = 0.35f

    override fun flick(velocity: Vec2, strikeOffset: Float, tip: Float) {
        if (phase != MatchPhase.AIMING) return
        if (velocity.length() < 40f) return
        world.flick(turn, velocity, strikeOffset, tip)
        phase = MatchPhase.MOVING
    }

    /** 움직임이 멈춘 뒤 호출. 걸쳐 앉았으면 COUNTING, 아니면 다음 턴을 이어 간다. */
    fun settleFlick(): TurnOutcome {
        check(phase == MatchPhase.MOVING) { "움직이는 중일 때만 정산할 수 있다" }
        // 화면은 settle() 을 부르지 않고 바로 정산하므로, 걸친 자세를 지금 자리에 맞춰 확정한다.
        world.snapPerchPose()
        val attacker = world.bodyOf(turn)
        val defender = world.bodyOf(turn.other())
        val lean = attacker.lean
        lastLean = lean
        val ratio = world.overlapRatio(attacker, defender)
        val pinQuality = world.pinQualityFor(turn)

        val outcome = when {
            attacker.fallen -> TurnOutcome.FELL
            pinQuality > 0f -> TurnOutcome.PIN
            attacker.perchedOn != null && ratio >= Board.PIN_OVERLAP -> TurnOutcome.SLIP
            attacker.perchedOn != null -> TurnOutcome.SLIP
            else -> TurnOutcome.MISS
        }
        lastOutcome = outcome
        when (outcome) {
            TurnOutcome.PIN -> {
                countSide = turn
                lastPinQuality = pinQuality
                phase = MatchPhase.COUNTING
            }

            TurnOutcome.SLIP -> {
                lastPinQuality = 0f
                // 걸쳐만 있었으니 빠져나온다. 두 지우개는 지금 자리에서 이어진다.
                world.slipOff(turn)
                world.settle()
                prepareNextTurn()
            }

            TurnOutcome.FELL -> {
                // 링아웃 — 떨어뜨린 쪽 승리
                lastPinQuality = 0f
                winner = turn.other()
                phase = MatchPhase.FINISHED
            }

            TurnOutcome.MISS -> {
                lastPinQuality = 0f
                prepareNextTurn()
            }
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
        prepareNextTurn()
    }

    override fun aiVelocity(accuracy: Float, strikeOffset: Float): Vec2 {
        aiThrough++
        val noisyRandom = Random(random.nextLong() xor (aiThrough * 7919L))
        return EraserAi.choose(
            world = world,
            attacker = turn,
            mode = AiMode.WRESTLING,
            accuracy = accuracy,
            random = noisyRandom,
            strikeOffset = strikeOffset,
            tip = aiTip,
        )
    }

    /**
     * 다음 턴을 준비한다. **자리를 처음으로 되돌리지 않고** 지금 책상 위 위치·자세를 이어 간다.
     * (올라탄 자세만 풀고, 겹쳐 있으면 최소한만 떼어 놓는다.)
     */
    private fun prepareNextTurn() {
        val player = world.player
        val ai = world.opponent
        for (body in listOf(player, ai)) {
            body.climbing = false
            body.perchedOn = null
            body.contactCooldown = 0f
            body.overlap = 0f
            body.z = 0f
            body.lean = 0f
            if (body.fallen) {
                // 떨어진 지우개는 자기 진영 가장자리로 다시 들어온다(다른 쪽은 그대로).
                val reentry = if (body.side == Side.PLAYER) PLAYER_REENTRY else AI_REENTRY
                body.pos = reentry
                body.vel = Vec2.ZERO
                body.spin = 0f
                body.spinSpeed = 0f
                body.tumble = 0f
                body.fallen = false
            }
        }
        separateIfOverlapping()
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
        val push = (player.pos - ai.pos)
        val away = if (dir.length() > 0f) dir else Vec2(0f, if (player.side == Side.PLAYER) 1f else -1f)
        // 맞닿은 채로 다음 턴을 시작하면 같은 세기로는 늘 넘어가 버리므로 살짝 떼어 놓는다.
        val target = ai.pos + away * (contact + player.type.radius * 0.30f)
        // 화면 밖으로 밀려나지 않게 무대 안으로 잡아 둔다.
        player.pos = Vec2(
            target.x.coerceIn(60f, Board.WIDTH - 60f),
            target.y.coerceIn(60f, Board.HEIGHT - 60f),
        )
        if (push.length() == 0f) player.pos = player.pos + Vec2(0f, 1f)
    }

    companion object {
        val PLAYER_HOME = Vec2(500f, 960f)
        val AI_HOME = Vec2(500f, 540f)

        /** 떨어진 지우개가 다시 들어오는 자리(자기 진영 가장자리). */
        val PLAYER_REENTRY = Vec2(500f, 1250f)
        val AI_REENTRY = Vec2(500f, 250f)
    }
}
