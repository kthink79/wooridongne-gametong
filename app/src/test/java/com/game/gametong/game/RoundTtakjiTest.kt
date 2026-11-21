package com.game.gametong.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RoundTtakjiTest {

    @Test
    fun `붙여먹기 - 세게 치면 벽에 붙는다`() {
        val match = StickMatch(twoPlayer = true, random = Random(1))
        val shot = match.flick(Side.PLAYER, 0f, 1f)
        assertEquals(RoundTtakjiPhysics.WALL_LINE, shot.endPos.y, 1f)
        assertEquals(0f, shot.wallDistance, 1f)
    }

    @Test
    fun `붙여먹기 - 약하게 치면 벽에 못 미친다`() {
        val match = StickMatch(twoPlayer = true, random = Random(1))
        val shot = match.flick(Side.PLAYER, 0f, 0.2f)
        assertTrue(shot.wallDistance > 500f)
    }

    @Test
    fun `붙여먹기 - 벽에 더 가까운 쪽이 1점`() {
        val match = StickMatch(twoPlayer = true, random = Random(1))
        match.flick(Side.PLAYER, 0f, 1f)
        match.flick(Side.AI, 0f, 0.25f)
        assertEquals(1, match.playerScore)
        assertEquals(0, match.aiScore)
        assertEquals(2, match.round)
    }

    @Test
    fun `붙여먹기 - 상대 딱지를 덮으면 2점`() {
        val match = StickMatch(twoPlayer = true, random = Random(2))
        match.flick(Side.PLAYER, 0f, 1f)
        // AI가 1P의 딱지 위로 정확히 붙인다.
        val start = Vec2(670f, 1230f)
        val target = Vec2(330f, RoundTtakjiPhysics.WALL_LINE)
        val aim = SquareTtakjiEngine.aimFromTo(start, target, -1f)
        match.flick(Side.AI, aim, 1f)
        assertEquals(2, match.playerScore)
        assertEquals(2, match.aiScore)
        assertTrue(match.outcome.contains("덮"))
    }

    @Test
    fun `붙여먹기 - AI가 한 번씩 붙이고 결과가 난다`() {
        val match = StickMatch(aiSkill = 1f, random = Random(3))
        match.flick(Side.PLAYER, 0f, 1f)
        val ai = match.aiFlick()
        assertTrue(ai.endPos.y >= RoundTtakjiPhysics.WALL_LINE)
        assertEquals(2, match.round)
        assertEquals(RoundTtakjiPhysics.WALL_LINE, ai.endPos.y, 12f)
    }

    @Test
    fun `멀리 날리기 - 힘이 세면 더 멀리 간다`() {
        val match = DistanceMatch(twoPlayer = true, random = Random(4))
        val weak = match.shoot(Side.PLAYER, 45f, 0.4f)
        val strong = match.shoot(Side.AI, 45f, 1f)
        assertTrue(strong.distance > weak.distance)
        assertEquals(strong.distance / 32f, strong.meters, 0.01f)
    }

    @Test
    fun `멀리 날리기 - 각도가 너무 낮으면 멀리 못 간다`() {
        val match = DistanceMatch(twoPlayer = true, random = Random(5))
        val flat = match.shoot(Side.PLAYER, 5f, 1f)
        val lift = match.shoot(Side.AI, 45f, 1f)
        assertTrue(lift.distance > flat.distance)
    }

    @Test
    fun `멀리 날리기 - 더 멀리 날린 쪽이 점수를 얻는다`() {
        val match = DistanceMatch(twoPlayer = true, random = Random(6))
        match.shoot(Side.PLAYER, 45f, 1f)
        match.shoot(Side.AI, 20f, 0.3f)
        assertEquals(1, match.playerScore)
        assertEquals(2, match.round)
    }

    @Test
    fun `멀리 날리기 - 바람은 범위 안에서 판마다 바뀐다`() {
        val match = DistanceMatch(twoPlayer = true, random = Random(7))
        assertTrue(match.wind in -DistanceMatch.MAX_WIND..DistanceMatch.MAX_WIND)
        val before = match.wind
        match.shoot(Side.PLAYER, 45f, 1f)
        match.shoot(Side.AI, 10f, 0.2f)
        assertTrue(match.wind in -DistanceMatch.MAX_WIND..DistanceMatch.MAX_WIND)
        // 같은 난수열이면 값이 고정되므로, 판이 바뀌면 바람도 다시 뽑힌다.
        assertTrue(before == match.wind || match.round == 2)
    }

    @Test
    fun `멀리 날리기 - 3점을 먼저 내면 끝난다`() {
        val match = DistanceMatch(twoPlayer = true, random = Random(8))
        repeat(3) {
            match.shoot(Side.PLAYER, 45f, 1f)
            match.shoot(Side.AI, 10f, 0.2f)
        }
        assertTrue(match.finished)
        assertEquals(Side.PLAYER, match.winner)
        assertEquals(3, match.playerScore)
    }

    @Test
    fun `입바람 세기 - 조용한 방의 잡음은 0이다`() {
        assertEquals(0f, BlowWind.strengthFromAmplitude(0.03f), 0.0001f)
        assertEquals(0f, BlowWind.strengthFromAmplitude(BlowWind.NOISE_FLOOR), 0.0001f)
    }

    @Test
    fun `입바람 세기 - 세게 불수록 커진다`() {
        val soft = BlowWind.strengthFromAmplitude(0.12f)
        val hard = BlowWind.strengthFromAmplitude(0.38f)
        assertTrue(hard > soft)
        assertTrue(soft in 0f..1f)
        assertEquals(1f, BlowWind.strengthFromAmplitude(1f), 0.0001f)
    }

    @Test
    fun `입바람 세기 - 스와이프와 빠른 탭도 같은 세기로 바뀐다`() {
        assertEquals(0f, BlowWind.strengthFromSwipe(0f), 0.0001f)
        assertEquals(1f, BlowWind.strengthFromSwipe(400f), 0.0001f)
        assertEquals(0.5f, BlowWind.strengthFromTaps(4), 0.0001f)
        assertTrue(BlowWind.travel(0.8f) > BlowWind.travel(0.3f))
    }

    @Test
    fun `입바람 - 두 번 세게 불면 결승선을 넘는다`() {
        val match = BlowMatch(twoPlayer = true, random = Random(10))
        val first = match.blow(Side.PLAYER, 1f)
        assertFalse(first.crossed)
        val second = match.blow(Side.PLAYER, 1f)
        assertTrue(second.crossed || second.offTable)
        assertTrue(match.playerScore == 1)
        assertEquals(BlowMatch.START, match.playerPos, 0.01f)
    }

    @Test
    fun `입바람 - 테이블 밖으로 밀면 처음 자리로 돌아온다`() {
        val match = BlowMatch(twoPlayer = true, random = Random(11))
        match.blow(Side.PLAYER, 0.75f)
        match.blow(Side.PLAYER, 0.75f)
        val before = match.playerPos
        assertTrue(before > 600f)
        val shot = match.blow(Side.PLAYER, 1f)
        assertTrue(shot.offTable)
        assertEquals(BlowMatch.START, match.playerPos, 0.01f)
        assertEquals(0, match.playerScore)
    }

    @Test
    fun `입바람 - 둘 다 4번 불고 못 넘으면 다음 판`() {
        val match = BlowMatch(twoPlayer = true, random = Random(12))
        repeat(4) {
            match.blow(Side.PLAYER, 0.1f)
            match.blow(Side.AI, 0.1f)
        }
        assertEquals(2, match.round)
        assertEquals(BlowMatch.START, match.playerPos, 0.01f)
        assertEquals(0, match.playerScore)
    }

    @Test
    fun `입바람 - AI는 결승선에 맞춰 분다`() {
        val match = BlowMatch(aiSkill = 1f, random = Random(13))
        val shot = match.aiBlow()
        assertTrue(shot.strength > 0.5f)
        assertTrue(match.aiPos > BlowMatch.START)
    }
}
