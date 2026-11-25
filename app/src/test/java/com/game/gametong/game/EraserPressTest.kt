package com.game.gametong.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 지우개 **가장자리 누르기** 조작 검증.
 *
 * 뒤로 당겼다 놓는 알까기식이 아니라, 지우개 가장자리를 눌러 그 방향으로 미는 방식이다.
 * 누른 자리가 회전(모먼트)을, 미는 속도가 힘을 정한다.
 */
class EraserPressTest {

    private fun body(type: EraserType = EraserType.STANDARD) =
        EraserBody(Side.PLAYER, type, Vec2(500f, 900f), Vec2(500f, 900f))

    @Test
    fun `지우개 위를 누르면 중심 기준 좌표를 돌려준다`() {
        val b = body()
        val center = EraserPress.localPress(b, Vec2(500f, 900f))
        assertNotNull(center)
        assertEquals(0f, center!!.x, 0.01f)

        val rightEdge = EraserPress.localPress(b, Vec2(500f + b.radius * 1.15f, 900f))
        assertNotNull(rightEdge)
        assertTrue("오른쪽 가장자리 ${rightEdge!!.x}", rightEdge.x > 0.9f)

        assertNull("지우개 밖을 누르면 조작이 아니다", EraserPress.localPress(b, Vec2(500f + b.radius * 3f, 900f)))
    }

    @Test
    fun `빠르게 밀수록 세게 나간다`() {
        val slow = EraserPress.push(Vec2.ZERO, Vec2(0f, -1f), 260f)
        val mid = EraserPress.push(Vec2.ZERO, Vec2(0f, -1f), 900f)
        val fast = EraserPress.push(Vec2.ZERO, Vec2(0f, -1f), 2400f)
        assertTrue(slow.power < mid.power)
        assertTrue(mid.power < fast.power)
        assertTrue(mid.velocity.length() < fast.velocity.length())
        assertTrue(fast.canShoot)
    }

    @Test
    fun `너무 느리게 만지면 스친 것으로 본다`() {
        val weak = EraserPress.push(Vec2.ZERO, Vec2(0f, -1f), 30f)
        assertFalse(weak.canShoot)
        assertEquals(0f, weak.velocity.length(), 0.0001f)
    }

    @Test
    fun `민 방향으로 날아간다`() {
        val p = EraserPress.push(Vec2.ZERO, Vec2(1f, 1f), 900f)
        val dir = p.velocity.normalized()
        assertEquals(0.7071f, dir.x, 0.01f)
        assertEquals(0.7071f, dir.y, 0.01f)
    }

    @Test
    fun `오른쪽 가장자리를 누르면 오른쪽으로 휘고 왼쪽은 왼쪽으로 휜다`() {
        val right = EraserPress.push(Vec2(1f, 0f), Vec2(0f, -1f), 900f)
        val left = EraserPress.push(Vec2(-1f, 0f), Vec2(0f, -1f), 900f)
        assertTrue("오른쪽 ${right.strikeOffset}", right.strikeOffset > 0.4f)
        assertTrue("왼쪽 ${left.strikeOffset}", left.strikeOffset < -0.4f)
        assertEquals(0f, EraserPress.push(Vec2.ZERO, Vec2(0f, -1f), 900f).strikeOffset, 0.001f)
    }

    @Test
    fun `뒤 가장자리를 누르고 밀면 앞으로 젖혀진다`() {
        // 필드 y 는 아래로 +, 미는 방향이 위(-y)이면 뒤 가장자리는 +y 쪽이다.
        val back = EraserPress.push(Vec2(0f, 0.9f), Vec2(0f, -1f), 900f)
        val front = EraserPress.push(Vec2(0f, -0.9f), Vec2(0f, -1f), 900f)
        assertTrue("뒤 ${back.tip}", back.tip > 0.5f)
        assertTrue("앞 ${front.tip}", front.tip < -0.5f)
    }

    @Test
    fun `젖힘은 지우개를 앞으로 들리게 한다`() {
        val w = EraserWorld(
            player = EraserBody(Side.PLAYER, EraserType.STANDARD, Vec2(500f, 1200f), Vec2(500f, 1200f)),
            opponent = EraserBody(Side.AI, EraserType.STANDARD, Vec2(120f, 180f), Vec2(120f, 180f)),
        )
        w.flick(Side.PLAYER, Vec2(0f, -700f), offset = 0f, tip = 0.8f)
        repeat(6) { w.step() }
        assertTrue("앞으로 젖혀져야 한다: ${w.player.pitchDeg}", w.player.pitchDeg > 2f)
    }

    @Test
    fun `누른 뒤 손가락 추적이 속도와 방향을 낸다`() {
        val tracker = PressTracker(windowMs = 90L)
        tracker.begin(Vec2(0f, 0f), 0L)
        tracker.sample(Vec2(0f, -100f), 50L)
        tracker.sample(Vec2(0f, -200f), 100L)
        assertEquals("지나온 거리", 200f, tracker.travel, 1f)
        assertEquals("방향", -1f, tracker.direction().y, 0.01f)
        val speed = tracker.speed(100L)
        assertTrue("속도 $speed", speed in 1500f..2600f)
        assertTrue("빠른 순간을 놓치지 않는다", tracker.effectiveSpeed(100L) >= speed - 1f)
    }

    @Test
    fun `천천히 끌다가 마지막에 확 밀면 세게 잡힌다`() {
        val tracker = PressTracker(windowMs = 90L)
        var t = 0L
        tracker.begin(Vec2(0f, 0f), t)
        repeat(10) {
            t += 80L
            tracker.sample(Vec2(0f, -10f * (it + 1)), t)
        }
        val slow = tracker.speed(t)
        t += 40L
        tracker.sample(Vec2(0f, -320f), t)
        val fast = tracker.speed(t)
        assertTrue("천천히 $slow → 빠르게 $fast", fast > slow * 2f)
    }
}
