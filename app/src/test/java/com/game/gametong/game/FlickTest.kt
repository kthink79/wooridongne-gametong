package com.game.gametong.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlickTest {

    @Test
    fun `드래그 반대 방향으로 날아간다`() {
        // start - now 로 만든 당김 벡터. 손가락을 아래로 당기면(now가 아래) 벡터는 위를 가리키고,
        // 그 방향으로 지우개가 날아간다.
        val pullDown = Vec2(0f, -200f)
        val velocity = Flick.velocityFromDrag(pullDown, EraserType.STANDARD)
        assertEquals(0f, velocity.x, 0.01f)
        assertTrue(velocity.y < 0f)
    }

    @Test
    fun `당긴 길이가 길수록 세진다`() {
        val soft = Flick.velocityFromDrag(Vec2(0f, -100f), EraserType.STANDARD)
        val hard = Flick.velocityFromDrag(Vec2(0f, -300f), EraserType.STANDARD)
        assertTrue(hard.length() > soft.length())
    }

    @Test
    fun `최대 드래그를 넘겨도 최고 속도를 넘지 않는다`() {
        val velocity = Flick.velocityFromDrag(Vec2(0f, -5000f), EraserType.STANDARD)
        assertEquals(Board.MAX_FLICK_SPEED, velocity.length(), 0.5f)
        assertEquals(1f, Flick.powerOf(Vec2(0f, -5000f)), 0.0001f)
    }

    @Test
    fun `너무 짧은 드래그는 발사되지 않는다`() {
        val velocity = Flick.velocityFromDrag(Vec2(0f, 5f), EraserType.STANDARD)
        assertEquals(Vec2.ZERO, velocity)
    }

    @Test
    fun `날렵한 지우개가 점보보다 빠르다`() {
        val drag = Vec2(0f, -300f)
        val nimble = Flick.velocityFromDrag(drag, EraserType.NIMBLE)
        val jumbo = Flick.velocityFromDrag(drag, EraserType.JUMBO)
        assertTrue(nimble.length() > jumbo.length())
    }

    @Test
    fun `힘 게이지는 0과 1 사이로 제한된다`() {
        assertEquals(0f, Flick.powerOf(Vec2.ZERO), 0.0001f)
        assertEquals(1f, Flick.powerOf(Vec2(Board.MAX_DRAG * 2f, 0f)), 0.0001f)
    }
}
