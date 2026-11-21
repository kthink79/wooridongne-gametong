package com.game.gametong.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class EraserAiTest {

    private fun wrestlingWorld() = WrestlingMatch(EraserType.STANDARD, EraserType.STANDARD).world

    @Test
    fun `레슬링 AI는 올라타는 각을 찾는다`() {
        val world = wrestlingWorld()
        val velocity = EraserAi.choose(world, Side.PLAYER, AiMode.WRESTLING, 1f, Random(1))
        val sim = world.copy()
        sim.flick(Side.PLAYER, velocity)
        sim.settle()
        val ratio = sim.overlapRatio(sim.player, sim.opponent)
        assertTrue("핀 비율 0.3 이상이어야 한다: $ratio", ratio >= Board.PIN_OVERLAP)
    }

    @Test
    fun `알까기 AI는 상대를 자기 쪽 가장자리로 민다`() {
        val world = AlkkaegiMatch(EraserType.STANDARD, EraserType.STANDARD).world
        val startY = world.opponent.pos.y
        val velocity = EraserAi.choose(world, Side.PLAYER, AiMode.ALKKAEGI, 1f, Random(1))
        val sim = world.copy()
        sim.flick(Side.PLAYER, velocity)
        sim.settle()
        // 상대를 자기 진영 가장자리(위쪽)로 최소 150단위 밀어내거나 떨어뜨려야 한다
        assertTrue(
            "상대가 밀려야 한다: $startY -> ${sim.opponent.pos.y}",
            sim.opponent.fallen || sim.opponent.pos.y < startY - 150f,
        )
        assertTrue(!sim.player.fallen)
    }

    @Test
    fun `AI 발사는 항상 유효한 속도다`() {
        for (accuracy in listOf(0.2f, 0.5f, 0.9f)) {
            val velocity = EraserAi.choose(wrestlingWorld(), Side.PLAYER, AiMode.WRESTLING, accuracy, Random(4))
            assertTrue(velocity.length() > 100f)
        }
    }

    @Test
    fun `같은 시드면 같은 한 수를 둔다`() {
        val a = EraserAi.choose(wrestlingWorld(), Side.PLAYER, AiMode.WRESTLING, 0.6f, Random(9))
        val b = EraserAi.choose(wrestlingWorld(), Side.PLAYER, AiMode.WRESTLING, 0.6f, Random(9))
        assertEquals(a, b)
    }

    @Test
    fun `조준 보조는 상대 쪽 정지 지점을 알려준다`() {
        val world = wrestlingWorld()
        val rest = EraserAi.predictRest(world, Side.PLAYER, Vec2(0f, -1000f))
        val defender = world.opponent.pos
        assertTrue(rest.x >= 0f)
        assertTrue("정지 지점이 상대 근처여야 한다: $rest", rest.distanceTo(defender) < 260f)
    }

    @Test
    fun `후보 발사는 여러 각도와 세기를 포함한다`() {
        val candidates = EraserAi.candidates(wrestlingWorld(), Side.PLAYER)
        assertTrue(candidates.size >= 50)
        val speeds = candidates.map { it.length() }
        assertTrue(speeds.max() > speeds.min() * 2f)
    }
}
