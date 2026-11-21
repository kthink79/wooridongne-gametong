package com.game.gametong.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MarbleGameTest {

    @Test
    fun `구슬끼리 부딪히면 서로 밀어낸다`() {
        val a = MarbleBody(1, Vec2(500f, 500f), Vec2(600f, 0f), radius = 24f)
        val b = MarbleBody(2, Vec2(540f, 500f), Vec2.ZERO, radius = 24f)
        val world = MarbleWorld(marbles = mutableListOf(a, b))
        world.resolveCollisions()
        assertTrue(b.vel.x > 0f)
        assertTrue(a.vel.x < 600f)
        assertTrue(a.pos.distanceTo(b.pos) >= 48f)
    }

    @Test
    fun `마찰 때문에 구슬은 결국 멈춘다`() {
        val marble = MarbleBody(1, Vec2(500f, 500f), Vec2(400f, 0f), radius = 24f)
        val world = MarbleWorld(marbles = mutableListOf(marble))
        world.simulateUntilRest(maxSeconds = 6f)
        assertFalse(world.moving)
        assertEquals(0f, marble.vel.length(), 0.001f)
    }

    @Test
    fun `원 안 구슬 쳐내기 - 원 밖으로 나간 구슬은 따먹는다`() {
        val game = MarbleCircleGame(twoPlayer = true, random = Random(1), marbleCount = 3)
        val glass = game.glasses.first()
        glass.pos = game.center + Vec2(360f, 0f)
        val result = game.shoot(Side.PLAYER, 0f, 0.4f)
        assertTrue(result.claimed.contains(glass.id))
        assertEquals(1, game.playerScore)
        assertTrue(glass.sunk)
    }

    @Test
    fun `원 안 구슬 쳐내기 - 왕구슬이 원 안으로 들어간다`() {
        val game = MarbleCircleGame(twoPlayer = true, random = Random(2), marbleCount = 3)
        val start = game.king.pos
        val result = game.shoot(Side.PLAYER, 0f, 0.6f)
        assertTrue(result.travelled > 200f)
        assertEquals(Side.AI, game.turn)
        assertTrue(game.king.pos.y < start.y)
    }

    @Test
    fun `원 안 구슬 쳐내기 - AI도 한 수를 둔다`() {
        val game = MarbleCircleGame(aiSkill = 1f, random = Random(3), marbleCount = 4)
        game.shoot(Side.PLAYER, 0f, 0.5f)
        val result = game.aiShoot()
        assertEquals(Side.AI, result.side)
        assertEquals(1, game.aiShots)
    }

    @Test
    fun `구멍치기 - 1번 구멍부터 순서대로 넣는다`() {
        val game = MarbleHoleGame(twoPlayer = true, random = Random(4))
        val aim = MarbleAim.aimDeg(game.playerStart, game.holes[0], -1f)
        val power = MarbleAim.powerFor(
            game.playerStart.distanceTo(game.holes[0]),
            speedScale = 1f,
            friction = game.world.friction,
        )
        val result = game.shoot(Side.PLAYER, aim, power)
        assertTrue(result.progressed)
        assertEquals(0, result.holeIndex)
        assertEquals(1, game.playerProgress)
        // 성공하면 같은 사람이 한 번 더 친다.
        assertEquals(Side.PLAYER, game.turn)
    }

    @Test
    fun `구멍치기 - 순서가 아닌 구멍에 넣으면 진행되지 않는다`() {
        val game = MarbleHoleGame(twoPlayer = true, random = Random(5))
        val aim = MarbleAim.aimDeg(game.playerStart, game.holes[1], -1f)
        val power = MarbleAim.powerFor(
            game.playerStart.distanceTo(game.holes[1]),
            speedScale = 1f,
            friction = game.world.friction,
        )
        val result = game.shoot(Side.PLAYER, aim, power)
        assertTrue(result.wrongOrder)
        assertEquals(0, game.playerProgress)
        assertEquals(Side.AI, game.turn)
    }

    @Test
    fun `구멍치기 - 세 구멍을 다 넣고 상대 구슬을 맞히면 따먹는다`() {
        val game = MarbleHoleGame(twoPlayer = true, random = Random(6))
        repeat(3) { index ->
            val hole = game.holes[index]
            val aim = MarbleAim.aimDeg(game.playerStart, hole, -1f)
            val power = MarbleAim.powerFor(
                game.playerStart.distanceTo(hole),
                speedScale = 1f,
                friction = game.world.friction,
            )
            game.shoot(Side.PLAYER, aim, power)
        }
        assertEquals(3, game.playerProgress)
        val aim = MarbleAim.aimDeg(game.playerStart, game.aiMarble.pos, -1f)
        val power = MarbleAim.powerFor(
            game.playerStart.distanceTo(game.aiMarble.pos),
            speedScale = 1f,
            friction = game.world.friction,
        )
        val result = game.shoot(Side.PLAYER, aim, power)
        assertTrue(result.hitOpponent)
        assertTrue(result.claimedOpponent)
        assertEquals(1, game.playerScore)
        assertEquals(0, game.playerProgress)
    }

    @Test
    fun `구멍치기 - 빗나가면 차례가 넘어간다`() {
        val game = MarbleHoleGame(twoPlayer = true, random = Random(7))
        val result = game.shoot(Side.PLAYER, 90f, 0.2f)
        assertFalse(result.progressed)
        assertEquals(Side.AI, game.turn)
        assertEquals(0, game.playerProgress)
    }

    @Test
    fun `구멍치기 - AI는 다음 구멍을 노린다`() {
        val game = MarbleHoleGame(aiSkill = 1f, random = Random(8))
        val aim = MarbleAim.aimDeg(game.aiStart, game.holes[0], 1f)
        val power = MarbleAim.powerFor(
            game.aiStart.distanceTo(game.holes[0]),
            speedScale = 1f,
            friction = game.world.friction,
        )
        val expected = game.shoot(Side.AI, aim, power)
        assertTrue(expected.progressed)
        assertEquals(1, game.aiProgress)
    }
}
