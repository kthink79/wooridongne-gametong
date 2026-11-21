package com.game.gametong.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlkkaegiMatchTest {

    private fun match() = AlkkaegiMatch(EraserType.STANDARD, EraserType.STANDARD, seed = 3L)

    @Test
    fun `시작은 내 턴 1라운드`() {
        val m = match()
        assertEquals(Side.PLAYER, m.turn)
        assertEquals(1, m.round)
        assertEquals(0, m.playerRounds)
        assertEquals(0, m.aiRounds)
        assertNull(m.winner)
    }

    @Test
    fun `상대 지우개를 책상 밖으로 밀면 내 라운드`() {
        val m = match()
        m.flick(Vec2(0f, -1600f))
        m.world.settle()
        assertTrue("상대가 떨어져야 한다", m.world.opponent.fallen)
        val outcome = m.settleFlick()
        assertEquals(RoundOutcome.PLAYER, outcome)
        assertEquals(1, m.playerRounds)
        assertEquals(MatchPhase.ROUND_END, m.phase)
    }

    @Test
    fun `내 지우개가 먼저 떨어지면 상대 라운드`() {
        val m = match()
        m.flick(Vec2(0f, 1600f))
        m.world.settle()
        assertTrue(m.world.player.fallen)
        val outcome = m.settleFlick()
        assertEquals(RoundOutcome.AI, outcome)
        assertEquals(1, m.aiRounds)
    }

    @Test
    fun `두 판을 먼저 이기면 경기가 끝난다`() {
        val m = match()
        // 1라운드: 내가 상대를 떨어뜨린다
        m.flick(Vec2(0f, -1600f))
        m.world.settle()
        m.settleFlick()
        m.nextRound()
        assertEquals(2, m.round)
        assertEquals(Side.AI, m.turn) // 진 쪽이 다음 라운드를 시작
        // 2라운드: 상대(AI)가 자기 지우개를 떨어뜨린다
        m.flick(Vec2(0f, -1600f))
        m.world.settle()
        assertEquals(RoundOutcome.PLAYER, m.settleFlick())
        m.nextRound()
        assertEquals(2, m.playerRounds)
        assertEquals(Side.PLAYER, m.winner)
        assertEquals(MatchPhase.FINISHED, m.phase)
    }

    @Test
    fun `새 라운드는 자기 자리에서 시작한다`() {
        val m = match()
        m.flick(Vec2(0f, -1600f))
        m.world.settle()
        m.settleFlick()
        m.nextRound()
        assertEquals(AlkkaegiMatch.PLAYER_HOME, m.world.player.pos)
        assertEquals(AlkkaegiMatch.AI_HOME, m.world.opponent.pos)
        assertEquals(MatchPhase.AIMING, m.phase)
        assertEquals(0, m.turnsInRound)
    }

    @Test
    fun `한 번 튕기고 나면 턴이 넘어간다`() {
        val m = match()
        m.flick(Vec2(0f, -300f))
        m.world.settle()
        assertEquals(RoundOutcome.NONE, m.settleFlick())
        assertEquals(Side.AI, m.turn)
        assertEquals(MatchPhase.AIMING, m.phase)
    }

    @Test
    fun `최대 턴이 지나면 라운드가 끝난다`() {
        val m = match()
        var turns = 0
        while (m.phase == MatchPhase.AIMING && turns < Board.MAX_TURNS_PER_ROUND + 2) {
            val dir = if (m.turn == Side.PLAYER) -45f else 45f
            m.flick(Vec2(0f, dir))
            m.world.settle()
            m.settleFlick()
            turns++
        }
        assertEquals(Board.MAX_TURNS_PER_ROUND, turns)
        assertEquals(MatchPhase.ROUND_END, m.phase)
        assertEquals(1, m.playerRounds + m.aiRounds)
    }

    @Test
    fun `움직이지 않을 때는 정산할 수 없다`() {
        val m = match()
        var failed = false
        try {
            m.settleFlick()
        } catch (e: IllegalStateException) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun `라운드 결과 연출 전에는 다음 라운드가 열리지 않는다`() {
        val m = match()
        m.nextRound()
        assertEquals(1, m.round)
        assertEquals(MatchPhase.AIMING, m.phase)
    }
}
