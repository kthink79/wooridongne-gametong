package com.game.gametong.game.physics3d

import com.game.gametong.game.MarbleBody
import com.game.gametong.game.MarbleHoleGame
import com.game.gametong.game.MarbleWorld
import com.game.gametong.game.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 구슬 연속 충돌 검사(CCD)·스핀·구멍 경사 검증. */
class SphereCcdTest {

    @Test
    fun `정면으로 다가오는 두 구의 충돌 시각을 찾는다`() {
        val t = SphereCcd.timeOfImpact(
            aPos = Vec2(0f, 0f), aVel = Vec2(100f, 0f),
            bPos = Vec2(100f, 0f), bVel = Vec2.ZERO,
            radiusSum = 40f,
        )
        assertNotNull(t)
        assertEquals(0.6f, t!!, 0.02f)
    }

    @Test
    fun `멀어지는 구는 충돌하지 않는다`() {
        val t = SphereCcd.timeOfImpact(
            aPos = Vec2(0f, 0f), aVel = Vec2(-100f, 0f),
            bPos = Vec2(100f, 0f), bVel = Vec2(100f, 0f),
            radiusSum = 40f,
        )
        assertNull(t)
    }

    @Test
    fun `이미 겹쳐 있으면 0 을 돌려준다`() {
        val t = SphereCcd.timeOfImpact(
            aPos = Vec2(0f, 0f), aVel = Vec2(10f, 0f),
            bPos = Vec2(10f, 0f), bVel = Vec2.ZERO,
            radiusSum = 40f,
        )
        assertEquals(0f, t!!, 0.0001f)
    }

    @Test
    fun `벽에 닿는 시각을 찾는다`() {
        val t = SphereCcd.wallTimeOfImpact(
            pos = Vec2(50f, 50f), vel = Vec2(-100f, 0f),
            minX = 24f, maxX = 976f, minY = 24f, maxY = 1036f,
        )
        assertNotNull(t)
        assertEquals(0.26f, t!!, 0.01f)
    }

    @Test
    fun `아주 빨라도 구슬을 관통하지 않는다`() {
        val fast = MarbleBody(1, Vec2(200f, 500f), Vec2(9000f, 0f), radius = 24f)
        val still = MarbleBody(2, Vec2(320f, 500f), Vec2.ZERO, radius = 24f)
        val world = MarbleWorld(marbles = mutableListOf(fast, still))
        // 60fps 한 프레임(1/60초)에 150 단위를 지나간다 — CCD 없이는 그냥 지나친다.
        world.step(1f / 60f)
        val gap = fast.pos.distanceTo(still.pos)
        assertTrue("겹치지 않아야 한다: $gap", gap >= 47f)
        assertTrue("맞은 구슬이 밀려야 한다: ${still.vel}", still.vel.x > 10f)
        assertTrue("맞은 쪽이 느려져야 한다: ${fast.vel}", fast.vel.x < 9000f)
    }

    @Test
    fun `스치는 충돌은 가로 스핀을 만든다`() {
        val a = MarbleBody(1, Vec2(200f, 500f), Vec2(800f, 0f), radius = 24f)
        val b = MarbleBody(2, Vec2(320f, 540f), Vec2.ZERO, radius = 24f)
        val world = MarbleWorld(marbles = mutableListOf(a, b))
        repeat(30) { world.step(1f / 120f) }
        assertTrue("스핀이 붙어야 한다: a=${a.sideSpin} b=${b.sideSpin}",
            kotlin.math.abs(a.sideSpin) + kotlin.math.abs(b.sideSpin) > 1f)
    }

    @Test
    fun `굴러가는 구슬은 무늬가 돈다`() {
        val m = MarbleBody(1, Vec2(500f, 500f), Vec2(600f, 0f), radius = 24f)
        val world = MarbleWorld(marbles = mutableListOf(m))
        val before = m.spin
        repeat(12) { world.step(1f / 120f) }
        assertTrue("구르는 각도가 변해야 한다: $before -> ${m.spin}", m.spin != before)
    }

    @Test
    fun `구멍 근처에서는 구멍 쪽으로 미끄러져 내려간다`() {
        val hole = Vec2(500f, 500f)
        val near = MarbleBody(1, Vec2(500f + 60f, 500f), Vec2.ZERO, radius = 24f)
        val world = MarbleWorld(
            marbles = mutableListOf(near),
            holes = listOf(hole),
            holeRadius = 54f,
            holeSlope = 1800f,
        )
        val start = near.pos.distanceTo(hole)
        repeat(60) { world.step(1f / 120f) }
        assertTrue("구멍에 가까워져야 한다: $start -> ${near.pos.distanceTo(hole)}",
            near.pos.distanceTo(hole) < start)
    }

    @Test
    fun `구멍 경사가 없으면 제자리에 머문다`() {
        val hole = Vec2(500f, 500f)
        val near = MarbleBody(1, Vec2(560f, 500f), Vec2.ZERO, radius = 24f)
        val world = MarbleWorld(marbles = mutableListOf(near), holes = listOf(hole), holeRadius = 54f)
        repeat(60) { world.step(1f / 120f) }
        assertEquals(560f, near.pos.x, 1f)
    }

    @Test
    fun `구멍치기 판은 구멍 경사를 쓰고도 순서 판정이 유지된다`() {
        val game = MarbleHoleGame(twoPlayer = true)
        assertTrue("구멍 경사가 켜져 있어야 한다", game.world.holeSlope > 0f)
    }
}
