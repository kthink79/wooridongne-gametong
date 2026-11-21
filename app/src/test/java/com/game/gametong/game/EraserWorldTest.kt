package com.game.gametong.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EraserWorldTest {

    private fun world(
        playerType: EraserType = EraserType.STANDARD,
        aiType: EraserType = EraserType.STANDARD,
    ) = EraserWorld(
        player = EraserBody(Side.PLAYER, playerType, Vec2(500f, 1040f), Vec2(500f, 1040f)),
        opponent = EraserBody(Side.AI, aiType, Vec2(500f, 460f), Vec2(500f, 460f)),
    )

    @Test
    fun `마찰로 결국 멈춘다`() {
        val w = world()
        w.flick(Side.PLAYER, Vec2(0f, -800f))
        val took = w.settle()
        assertTrue(took > 0f)
        assertTrue(took < Board.MAX_SIM_SECONDS)
        assertTrue(w.player.isResting())
        assertTrue(w.player.pos.y < 1040f)
    }

    @Test
    fun `가만히 두면 움직이지 않는다`() {
        val w = world()
        repeat(300) { w.step() }
        assertEquals(Vec2(500f, 1040f), w.player.pos)
        assertEquals(Vec2(500f, 460f), w.opponent.pos)
        assertTrue(w.settled)
    }

    @Test
    fun `책상 밖으로 나가면 떨어진 것으로 판정한다`() {
        val w = world()
        // 위쪽 상대를 지나치지 않도록 옆으로 날려 보낸다
        w.flick(Side.PLAYER, Vec2(2500f, 0f))
        w.settle()
        assertTrue(w.player.fallen)
        assertFalse(w.opponent.fallen)
        assertTrue(w.anyFallen)
    }

    @Test
    fun `세게 부딪히면 상대 지우개가 밀린다`() {
        val w = world()
        w.flick(Side.PLAYER, Vec2(0f, -1200f))
        w.settle()
        assertTrue("상대가 밀려야 한다: ${w.opponent.pos.y}", w.opponent.pos.y < 460f - 100f)
    }

    @Test
    fun `멈춘 뒤에도 두 지우개가 서로 파고들지 않는다`() {
        val w = world()
        w.flick(Side.PLAYER, Vec2(0f, -1100f))
        w.settle()
        if (!w.player.fallen && !w.opponent.fallen) {
            val dist = w.player.pos.distanceTo(w.opponent.pos)
            val contact = w.player.radius + w.opponent.radius
            assertTrue("dist=$dist contact=$contact", dist >= contact - 4f)
        }
    }

    @Test
    fun `수비 지우개는 고정된 동안 움직이지 않는다`() {
        val w = world()
        w.immovableSide = Side.AI
        w.flick(Side.PLAYER, Vec2(0f, -1000f))
        w.settle()
        assertEquals(Vec2(500f, 460f), w.opponent.pos)
        assertTrue(w.player.climbing)
    }

    @Test
    fun `겹침 비율은 한쪽이 떨어지면 0`() {
        val w = world()
        w.player.fallen = true
        assertEquals(0f, w.pinRatioFor(Side.PLAYER), 0.0001f)
    }
}
