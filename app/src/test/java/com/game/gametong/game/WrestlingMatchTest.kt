package com.game.gametong.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WrestlingMatchTest {

    private fun match() = WrestlingMatch(EraserType.STANDARD, EraserType.STANDARD, seed = 7L)

    @Test
    fun `시작은 내 턴이고 조준 대기 상태다`() {
        val m = match()
        assertEquals(Side.PLAYER, m.turn)
        assertEquals(MatchPhase.AIMING, m.phase)
        assertTrue(m.isPlayerTurn)
        assertEquals(0, m.playerCounts)
        assertEquals(0, m.aiCounts)
    }

    @Test
    fun `튕기면 움직이는 상태가 된다`() {
        val m = match()
        m.flick(Vec2(0f, -1000f))
        assertEquals(MatchPhase.MOVING, m.phase)
    }

    @Test
    fun `적당한 세기로 올라타면 1카운트`() {
        val m = match()
        m.flick(Vec2(0f, -1000f))
        m.world.settle()
        val outcome = m.settleFlick()
        assertEquals(TurnOutcome.PIN, outcome)
        assertEquals(MatchPhase.COUNTING, m.phase)
        assertEquals(Side.PLAYER, m.countSide)
        // 카운트는 연출이 끝나야 오른다
        assertEquals(0, m.playerCounts)
        m.applyCount()
        assertEquals(1, m.playerCounts)
    }

    @Test
    fun `너무 약하게 스치면 카운트 없이 턴이 넘어간다`() {
        val m = match()
        m.flick(Vec2(0f, -560f))
        m.world.settle()
        val outcome = m.settleFlick()
        assertEquals(TurnOutcome.MISS, outcome)
        assertEquals(0, m.playerCounts)
        assertEquals(Side.AI, m.turn)
        assertEquals(MatchPhase.AIMING, m.phase)
    }

    @Test
    fun `너무 세게 치면 넘어가 버려 카운트가 안 된다`() {
        val m = match()
        m.flick(Vec2(0f, -1500f))
        m.world.settle()
        assertEquals(TurnOutcome.MISS, m.settleFlick())
        assertEquals(0, m.playerCounts)
    }

    @Test
    fun `핀을 세 번 채우면 승리하고 경기가 끝난다`() {
        val m = match()
        repeat(3) {
            assertEquals(Side.PLAYER, m.turn)
            m.flick(Vec2(0f, -1000f))
            m.world.settle()
            assertEquals(TurnOutcome.PIN, m.settleFlick())
            m.applyCount()
            if (m.phase != MatchPhase.FINISHED) {
                // AI 턴을 소비시킨다(약하게 밀어 턴만 넘김)
                m.flick(Vec2(0f, 120f))
                m.world.settle()
                m.settleFlick()
            }
        }
        assertEquals(3, m.playerCounts)
        assertEquals(Side.PLAYER, m.winner)
        assertEquals(MatchPhase.FINISHED, m.phase)
        assertTrue(m.isFinished)
    }

    @Test
    fun `핀 뒤에는 두 지우개가 자기 자리로 돌아간다`() {
        val m = match()
        m.flick(Vec2(0f, -1000f))
        m.world.settle()
        m.settleFlick()
        m.applyCount()
        assertEquals(WrestlingMatch.PLAYER_HOME, m.world.player.pos)
        assertEquals(WrestlingMatch.AI_HOME, m.world.opponent.pos)
        assertEquals(Side.AI, m.turn)
    }

    @Test
    fun `핀 뒤에는 턴이 상대에게 넘어간다`() {
        val m = match()
        m.flick(Vec2(0f, -1000f))
        m.world.settle()
        m.settleFlick()
        m.applyCount()
        assertEquals(Side.AI, m.turn)
        assertTrue(m.isAiTurn)
        // AI 차례에는 수비(플레이어)가 고정된다
        assertEquals(Side.PLAYER, m.world.immovableSide)
    }

    @Test
    fun `수비 지우개는 턴이 도는 동안 제자리에 있다`() {
        val m = match()
        m.flick(Vec2(0f, -1000f))
        m.world.settle()
        assertEquals(WrestlingMatch.AI_HOME, m.world.opponent.pos)
    }

    @Test
    fun `한 턴씩 번갈아 간다`() {
        val m = match()
        m.flick(Vec2(0f, -1000f))
        m.world.settle()
        m.settleFlick()
        m.applyCount()
        assertEquals(Side.AI, m.turn)
        m.flick(Vec2(0f, 400f))
        m.world.settle()
        m.settleFlick()
        assertEquals(Side.PLAYER, m.turn)
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
    fun `세 카운트 전에는 승자가 없다`() {
        val m = match()
        m.flick(Vec2(0f, -1000f))
        m.world.settle()
        m.settleFlick()
        m.applyCount()
        assertNull(m.winner)
        assertNotNull(m.lastOutcome)
    }
}
