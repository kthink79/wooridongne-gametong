package com.game.gametong.game.physics3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 고정 타임스텝 적분기 검증. */
class FixedStepTest {

    @Test
    fun `60fps 한 프레임은 120Hz 스텝 두 번으로 나뉜다`() {
        val loop = FixedTimestep(step = 1f / 120f)
        var steps = 0
        loop.advance(1f / 60f) { steps++ }
        assertEquals(2, steps)
        assertEquals(0f, loop.alpha, 0.001f)
    }

    @Test
    fun `프레임이 흔들려도 누적 시간은 보존된다`() {
        val loop = FixedTimestep(step = 1f / 120f)
        var steps = 0
        // 16.6ms, 8.3ms, 25ms 를 번갈아 넣어도 총 스텝은 총 시간 / 스텝
        val frames = floatArrayOf(1f / 60f, 1f / 120f, 1f / 40f, 1f / 60f, 1f / 120f)
        var total = 0f
        for (f in frames) {
            total += f
            loop.advance(f) { steps++ }
        }
        assertEquals((total / (1f / 120f)).toDouble(), steps.toDouble(), 1.0)
    }

    @Test
    fun `아주 긴 프레임은 최대 스텝까지만 돌리고 나머지는 버린다`() {
        val loop = FixedTimestep(step = 1f / 120f, maxSteps = 4)
        var steps = 0
        loop.advance(0.5f) { steps++ }
        assertEquals(4, steps)
        assertTrue("버린 시간이 기록되어야 한다: ${loop.droppedSeconds}", loop.droppedSeconds > 0.4f)
    }

    @Test
    fun `두 스텝 사이 보간 계수를 돌려준다`() {
        val loop = FixedTimestep(step = 1f / 120f)
        loop.advance(1.5f / 120f) { }
        assertEquals(0.5f, loop.alpha, 0.01f)
    }

    @Test
    fun `리셋하면 누적 시간이 사라진다`() {
        val loop = FixedTimestep(step = 1f / 120f)
        loop.advance(1.5f / 120f) { }
        loop.reset()
        assertEquals(0f, loop.alpha, 0.001f)
        assertEquals(0f, loop.accumulator, 0.001f)
    }
}
