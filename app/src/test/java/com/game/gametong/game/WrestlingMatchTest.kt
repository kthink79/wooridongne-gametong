package com.game.gametong.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WrestlingMatchTest {

    private fun match() = WrestlingMatch(EraserType.STANDARD, EraserType.STANDARD, seed = 7L)

    /** AI 턴을 약하게 밀어 소비한다(턴만 넘김). */
    private fun burnAiTurn(m: WrestlingMatch) {
        m.flick(Vec2(0f, 120f))
        m.world.settle()
        m.settleFlick()
    }

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
            if (m.phase != MatchPhase.FINISHED) burnAiTurn(m)
        }
        assertEquals(3, m.playerCounts)
        assertEquals(Side.PLAYER, m.winner)
        assertEquals(MatchPhase.FINISHED, m.phase)
        assertTrue(m.isFinished)
    }

    @Test
    fun `카운트 뒤에도 두 지우개는 처음 자리로 돌아가지 않는다`() {
        val m = match()
        m.flick(Vec2(0f, -1000f))
        m.world.settle()
        val perched = m.world.player.pos
        m.settleFlick()
        m.applyCount()
        // 올라탄 자리는 풀리지만, 두 지우개 모두 처음 홈으로 되돌아가지는 않는다.
        assertFalse(m.world.player.fallen)
        assertNull(m.world.player.perchedOn)
        assertEquals(0f, m.world.player.z, 0.001f)
        assertTrue(
            "플레이어가 홈으로 돌아가면 안 된다: ${m.world.player.pos}",
            m.world.player.pos != WrestlingMatch.PLAYER_HOME,
        )
        assertTrue(
            "지나간 자리 근처에서 이어져야 한다: $perched -> ${m.world.player.pos}",
            m.world.player.pos.distanceTo(perched) < 320f,
        )
        assertEquals(Side.AI, m.turn)
    }

    @Test
    fun `카운트 뒤 턴이 상대에게 넘어간다`() {
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
        // 상대가 닿지 않게만 밀면 차례가 돌아온다.
        m.flick(Vec2(0f, 180f))
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

    @Test
    fun `비스듬히 걸쳐만 있으면 빠져나와 카운트가 안 된다`() {
        val m = match()
        // 가장자리에 겨우 걸칠 만큼만 세게: 무게중심이 윗면 밖이라 핀이 아니다.
        m.flick(Vec2(0f, -640f))
        m.world.settle()
        assertEquals(Side.AI, m.world.player.perchedOn)
        assertEquals(TurnOutcome.SLIP, m.settleFlick())
        assertEquals(0, m.playerCounts)
        assertEquals(MatchPhase.AIMING, m.phase)
        // 빠져나온 뒤에는 상대 위가 아니라 책상 위에 있다
        assertNull(m.world.player.perchedOn)
        assertEquals(0f, m.world.player.z, 0.01f)
    }

    @Test
    fun `빠져나온 뒤에도 두 지우개는 지금 자리에서 이어진다`() {
        val m = match()
        m.flick(Vec2(0f, -640f))
        m.world.settle()
        m.settleFlick()
        assertTrue(m.world.player.pos != WrestlingMatch.PLAYER_HOME)
        assertEquals(Side.AI, m.turn)
    }

    // ── 실기 피드백: "위로 올라갔는데 빠져 나왔다며 비정상적으로 떨어진다" ─────────

    @Test
    fun `확실히 올라타면 빠져나왔다고 하지 않고 카운트로 인정한다`() {
        val m = match()
        m.flick(Vec2(0f, -900f))
        m.world.settle()
        val attacker = m.world.bodyOf(Side.PLAYER)
        val defender = m.world.bodyOf(Side.AI)
        assertEquals("상대 위에 있어야 한다", Side.AI, attacker.perchedOn)
        val ratio = attacker.pos.distanceTo(defender.pos) / defender.radius
        assertTrue("무게중심이 윗면 안: $ratio", ratio < 0.95f)
        assertTrue("기운 각 ${attacker.lean}", attacker.lean <= Board.PIN_MAX_LEAN)
        assertEquals(TurnOutcome.PIN, m.settleFlick())
        assertEquals(Side.PLAYER, m.countSide)
    }

    @Test
    fun `올라탄 지우개는 상대 높이에 떠 있고 아래로 꺼지지 않는다`() {
        val m = match()
        m.flick(Vec2(0f, -1000f))
        m.world.settle()
        val attacker = m.world.player
        val defender = m.world.opponent
        assertTrue("높이 ${attacker.z} (상대 두께 ${defender.blockHeight})", attacker.z >= defender.blockHeight * 0.5f)
        assertTrue(
            "겹침 ${m.world.overlapRatio(attacker, defender)}",
            m.world.overlapRatio(attacker, defender) >= Board.PIN_OVERLAP,
        )
    }

    @Test
    fun `올라탄 뒤에는 상대 위에서 데굴데굴 구르지 않는다`() {
        val m = match()
        m.flick(Vec2(0f, -1000f))
        m.world.settle()
        val quarter = (m.world.player.tumble % 90f + 90f) % 90f
        assertTrue("놓인 면이 반듯해야 한다: $quarter", quarter < 1.5f || quarter > 88.5f)
    }

    @Test
    fun `살짝 스쳐 걸치지 못하면 그 자리에서 튕겨 나가지 않는다`() {
        val m = match()
        // 겨우 닿을 만큼만 민다 — 걸치지 못하고 내려온다.
        m.flick(Vec2(0f, -630f))
        m.world.settle()
        if (m.world.player.perchedOn == null) {
            val speed = m.world.player.speed
            assertTrue("튕겨 나가지 않아야 한다: $speed", speed < 250f)
        }
    }

    @Test
    fun `올라타면 완전히 덮지 않고 걸쳐 올라간다`() {
        val m = match()
        m.flick(Vec2(0f, -1000f))
        m.world.settle()
        val overlap = m.world.overlapRatio(m.world.player, m.world.opponent)
        // 걸침은 가장자리에 얹히는 정도(완전히 위에 올라앉지는 않는다)
        assertTrue("겹침 $overlap", overlap in 0.25f..0.75f)
        assertTrue("들린 높이 ${m.world.player.z}", m.world.player.z > 0f)
        assertTrue("기운 각 ${m.world.player.lean}", m.world.player.lean in 1f..Board.PIN_MAX_LEAN)
        assertTrue(m.world.pinQualityFor(Side.PLAYER) > 0f)
    }

    @Test
    fun `걸쳐 앉지 않았으면 핀 품질은 0이다`() {
        val m = match()
        assertEquals(0f, m.world.pinQualityFor(Side.PLAYER), 0.0001f)
        m.flick(Vec2(0f, -560f))
        m.world.settle()
        assertEquals(0f, m.world.pinQualityFor(Side.PLAYER), 0.0001f)
    }

    @Test
    fun `지우개가 책상 밖으로 떨어지면 링아웃 패배다`() {
        val m = match()
        m.flick(Vec2(1800f, 0f))
        m.world.settle()
        assertTrue(m.world.player.fallen)
        assertEquals(TurnOutcome.FELL, m.settleFlick())
        assertEquals(MatchPhase.FINISHED, m.phase)
        assertEquals(Side.AI, m.winner)
    }

    @Test
    fun `표면을 바꿔도 같은 규칙으로 진행된다`() {
        val m = WrestlingMatch(
            EraserType.STANDARD,
            EraserType.STANDARD,
            surface = DeskSurface.COATED,
            seed = 7L,
        )
        m.flick(Vec2(0f, -1000f))
        m.world.settle()
        val outcome = m.settleFlick()
        assertTrue(outcome == TurnOutcome.PIN || outcome == TurnOutcome.SLIP)
        assertEquals(DeskSurface.COATED, m.world.surface)
    }
}
