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

    // ── 책상 표면 · 타격 위치 ────────────────────────────────────────

    @Test
    fun `표면이 미끄러우면 같은 세기도 더 멀리 밀린다`() {
        fun pushed(surface: DeskSurface): Float {
            val m = AlkkaegiMatch(EraserType.STANDARD, EraserType.STANDARD, surface = surface, seed = 3L)
            // 두 지우개가 책상 양 끝에 있어 상대까지 닿을 만큼은 세게 민다.
            m.flick(Vec2(0f, -1600f))
            m.world.settle()
            return m.world.opponent.pos.y
        }
        val coated = pushed(DeskSurface.COATED)
        val wood = pushed(DeskSurface.WOOD)
        val dusty = pushed(DeskSurface.DUSTY)
        assertTrue("코팅 $coated < 나무 $wood", coated < wood)
        assertTrue("나무 $wood < 먼지 $dusty", wood < dusty)
    }

    @Test
    fun `시작 자리는 책상 양 끝 쪽으로 충분히 벌려 둔다`() {
        val gap = AlkkaegiMatch.PLAYER_HOME.distanceTo(AlkkaegiMatch.AI_HOME)
        val contact = EraserType.STANDARD.radius * 2f
        assertTrue("간격 $gap 은 한 지우개 지름($contact)의 세 배 이상", gap > contact * 3f)
        // 양쪽 모두 지우개 반지름만큼은 책상 안에 있다.
        assertTrue("플레이어 자리 ${AlkkaegiMatch.PLAYER_HOME.y}", AlkkaegiMatch.PLAYER_HOME.y + EraserType.STANDARD.radius < Board.HEIGHT)
        assertTrue("AI 자리 ${AlkkaegiMatch.AI_HOME.y}", AlkkaegiMatch.AI_HOME.y - EraserType.STANDARD.radius > 0f)
        // 첫 수에 바로 부딪히지 않는다.
        val m = match()
        m.flick(Vec2(0f, -900f))
        m.world.settle()
        assertEquals("상대는 그대로", AlkkaegiMatch.AI_HOME, m.world.opponent.pos)
    }

    @Test
    fun `표면에 따라 내 지우개가 미끄러진 거리가 달라진다`() {
        val m = AlkkaegiMatch(EraserType.STANDARD, EraserType.STANDARD, surface = DeskSurface.DUSTY)
        m.flick(Vec2(0f, -300f))
        m.world.settle()
        val slide = m.settleFlick()
        assertEquals(RoundOutcome.NONE, slide)
        assertTrue("먼지 책상에서는 짧게 멈춘다: ${m.lastSlide}", m.lastSlide in 1f..320f)
    }

    @Test
    fun `중심을 벗어나 때리면 지우개가 돌며 휜다`() {
        fun endX(offset: Float): Vec2 {
            val m = AlkkaegiMatch(EraserType.STANDARD, EraserType.STANDARD, seed = 3L)
            m.flick(Vec2(0f, -950f), offset)
            m.world.settle()
            return m.world.player.pos
        }
        val center = endX(0f)
        val edge = endX(0.9f)
        assertEquals("가운데 타격은 곧게", 500f, center.x, 3f)
        assertTrue("끝 타격은 옆으로 휜다: ${edge.x}", edge.x > center.x + 4f)
        assertTrue(kotlin.math.abs(edge.y - center.y) < 120f)
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
