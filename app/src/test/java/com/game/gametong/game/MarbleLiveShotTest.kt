package com.game.gametong.game

import com.game.gametong.game.physics3d.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 구슬치기 **실시간 굴림** 검증.
 *
 * 예전에는 shoot() 한 번에 끝까지 계산해 결과만 보여 줬다. 이제는 화면이 프레임마다
 * [MarbleCircleGame.advanceShot] 을 불러 구슬이 실제로 굴러가고 부딪히는 과정을 보여 준다.
 * 그 과정이 (1) 여러 프레임에 걸쳐 일어나고 (2) 끝까지 계산한 결과와 같고
 * (3) 충돌·굴림이 기록되는지 확인한다.
 */
class MarbleLiveShotTest {

    @Test
    fun `한 수는 여러 프레임에 걸쳐 조금씩 굴러간다`() {
        val game = MarbleCircleGame(twoPlayer = true, marbleCount = 3)
        val start = game.king.pos
        assertTrue(game.beginShot(Side.PLAYER, 0f, 0.75f))
        assertTrue("굴리기 시작해야 한다", game.inFlight)

        val p0 = game.king.pos
        assertTrue(game.advanceShot(1f / 60f))
        val p1 = game.king.pos
        val moved = p1.distanceTo(p0)
        assertTrue("한 프레임에 멈추면 안 된다: $moved", moved in 1f..200f)
        assertTrue("한 프레임에 끝까지 가면 안 된다", p1.distanceTo(start) < 200f)

        var frames = 1
        while (game.advanceShot(1f / 60f) && frames < 900) frames++
        assertTrue("여러 프레임에 걸쳐 굴러가야 한다: $frames", frames > 8)

        val result = game.finishShot()
        assertEquals(Side.PLAYER, result.side)
        assertFalse(game.inFlight)
        assertTrue("차례가 넘어가야 한다", game.turn == Side.AI)
    }

    @Test
    fun `실시간으로 굴린 결과는 한 번에 계산한 결과와 같다`() {
        fun live(): Pair<CircleShotResult, Vec2> {
            val g = MarbleCircleGame(twoPlayer = true, marbleCount = 4, random = Random(11))
            g.beginShot(Side.PLAYER, 12f, 0.8f)
            var guard = 0
            while (g.advanceShot(1f / 50f) && guard < 900) guard++
            return g.finishShot() to g.king.pos
        }

        val batch = MarbleCircleGame(twoPlayer = true, marbleCount = 4, random = Random(11))
        val expected = batch.shoot(Side.PLAYER, 12f, 0.8f)
        val (liveResult, livePos) = live()

        assertEquals(expected.claimed, liveResult.claimed)
        assertEquals(expected.travelled, liveResult.travelled, 1.5f)
        assertEquals(batch.king.pos.x, livePos.x, 1.5f)
        assertEquals(batch.king.pos.y, livePos.y, 1.5f)
    }

    @Test
    fun `구슬끼리 부딪히면 부딪힌 지점이 기록된다`() {
        val a = MarbleBody(1, Vec2(500f, 500f), Vec2(900f, 0f), radius = 24f)
        val b = MarbleBody(2, Vec2(575f, 500f), Vec2.ZERO, radius = 24f)
        val world = MarbleWorld(marbles = mutableListOf(a, b))
        var hits = emptyList<MarbleImpact>()
        repeat(30) {
            world.step()
            hits = hits + world.drainImpacts().filter { !it.wall }
        }
        assertTrue("충돌이 기록되어야 한다", hits.isNotEmpty())
        val hit = hits.first()
        assertTrue("세기 ${hit.strength}", hit.strength > 150f)
        assertTrue("접점 x=${hit.x}", hit.x in 480f..600f)
        assertTrue(hits.any { (it.a == 1 && it.b == 2) || (it.a == 2 && it.b == 1) })
    }

    @Test
    fun `바닥 경계에 부딪힌 것도 기록된다`() {
        val m = MarbleBody(1, Vec2(200f, 500f), Vec2(-900f, 0f), radius = 24f)
        val world = MarbleWorld(marbles = mutableListOf(m))
        var wall = false
        repeat(25) {
            world.step()
            if (world.drainImpacts().any { it.wall }) wall = true
        }
        assertTrue("경계 충돌이 기록되어야 한다", wall)
    }

    @Test
    fun `구슬은 진행 방향으로 굴러간다`() {
        // 필드 +y 는 세계 +z. 위로(-y) 굴러가면 꼭대기 점이 -z 쪽으로 넘어가야 한다.
        val forward = MarbleBody(1, Vec2(500f, 900f), Vec2(0f, -300f), radius = 24f)
        val worldA = MarbleWorld(marbles = mutableListOf(forward))
        repeat(2) { worldA.step() }
        val up = forward.rollQuat.rotate(Vec3.UP)
        assertTrue("윗점 z=${up.z} (진행 방향으로 넘어가야 한다)", up.z < -0.1f)

        // 반대로 가면 반대로 돈다.
        val backward = MarbleBody(1, Vec2(500f, 900f), Vec2(0f, 300f), radius = 24f)
        val worldB = MarbleWorld(marbles = mutableListOf(backward))
        repeat(2) { worldB.step() }
        val upBack = backward.rollQuat.rotate(Vec3.UP)
        assertTrue("되돌아가면 반대로 구른다: ${upBack.z}", upBack.z > 0.1f)
    }

    @Test
    fun `멈춘 구슬은 더 굴러가지 않는다`() {
        val m = MarbleBody(1, Vec2(500f, 500f), Vec2.ZERO, radius = 24f)
        val world = MarbleWorld(marbles = mutableListOf(m))
        val before = m.rollQuat
        repeat(60) { world.step() }
        assertEquals(before, m.rollQuat)
    }

    @Test
    fun `조준 예측은 처음 맞는 구슬과 그 구슬이 날아갈 방향을 낸다`() {
        val target = MarbleBody(1, Vec2(500f, 500f), radius = 24f)
        val world = MarbleWorld(marbles = mutableListOf(target))
        val from = Vec2(500f, 900f)
        val p = MarbleAim.predict(world, from, Vec2(0f, -1000f), radius = 34f, ignoreId = 0)!!
        assertEquals(1, p.targetId)
        assertFalse(p.wall)
        assertEquals("접점은 두 반지름만큼 떨어진 자리", 58f, p.contact.distanceTo(target.pos), 1.5f)
        assertEquals("맞은 구슬은 정면으로 밀린다", -1f, p.targetDir.y, 0.05f)
        assertEquals("내 구슬은 그 자리에서 선다", 0f, p.cueDir.length(), 0.05f)
        assertEquals(900f - 500f - 58f, p.distance, 2f)
    }

    @Test
    fun `스치는 조준은 내 구슬이 꺾여 갈 방향도 알려 준다`() {
        val target = MarbleBody(1, Vec2(530f, 500f), radius = 24f)
        val world = MarbleWorld(marbles = mutableListOf(target))
        val p = MarbleAim.predict(world, Vec2(500f, 900f), Vec2(0f, -1000f), radius = 34f, ignoreId = 0)!!
        assertEquals(1, p.targetId)
        assertTrue("꺾여 갈 방향이 있어야 한다: ${p.cueDir}", p.cueDir.length() > 0.2f)
        assertTrue("꺾이면 반대쪽으로 간다: ${p.cueDir.x}", p.cueDir.x < -0.05f)
        assertTrue("맞은 구슬은 반대쪽으로 밀린다: ${p.targetDir.x}", p.targetDir.x > 0.05f)
    }

    @Test
    fun `맞는 구슬이 없으면 벽까지의 거리를 낸다`() {
        val world = MarbleWorld(marbles = mutableListOf())
        val p = MarbleAim.predict(world, Vec2(500f, 900f), Vec2(0f, -1000f), radius = 24f)!!
        assertTrue(p.wall)
        assertEquals(-1, p.targetId)
        assertEquals(900f - 24f, p.distance, 1f)
    }

    @Test
    fun `방향이 없으면 예측하지 않는다`() {
        val world = MarbleWorld(marbles = mutableListOf())
        assertEquals(null, MarbleAim.predict(world, Vec2(500f, 500f), Vec2.ZERO, radius = 24f))
    }

    @Test
    fun `구멍치기도 프레임마다 굴러가며 구멍에 빠진다`() {
        val game = MarbleHoleGame(twoPlayer = true)
        val aim = MarbleAim.aimDeg(game.playerStart, game.holes[0], -1f)
        val power = MarbleAim.powerFor(
            game.playerStart.distanceTo(game.holes[0]),
            speedScale = 1f,
            friction = game.world.friction,
        )
        assertTrue(game.beginShot(Side.PLAYER, aim, power))
        var frames = 0
        while (game.advanceShot(1f / 60f) && frames < 900) frames++
        assertTrue("여러 프레임에 걸쳐 굴러가야 한다: $frames", frames > 5)
        val result = game.finishShot()
        assertTrue(result.progressed)
        assertEquals(0, result.holeIndex)
        assertEquals(1, game.playerProgress)
    }

    @Test
    fun `굴러가는 중에는 새 수를 시작하지 않는다`() {
        val game = MarbleCircleGame(twoPlayer = true, marbleCount = 3)
        assertTrue(game.beginShot(Side.PLAYER, 0f, 0.6f))
        assertFalse("굴러가는 중에 또 칠 수 없다", game.beginShot(Side.AI, 0f, 0.6f))
    }
}
