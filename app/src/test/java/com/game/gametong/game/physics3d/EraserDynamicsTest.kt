package com.game.gametong.game.physics3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 지우개 3D 기울기 동역학 검증. */
class EraserDynamicsTest {

    private fun dyn(mass: Float = 1f, halfHeight: Float = 22f) =
        EraserDynamics(halfWidth = 122f, halfHeight = halfHeight, halfDepth = 95f, mass = mass)

    private fun run(d: EraserDynamics, seconds: Float, table: DeskMaterial = DeskMaterial.RAW_WOOD, edge: Float = 0f): Float {
        val dt = 1f / 120f
        var t = 0f
        var peak = 0f
        while (t < seconds) {
            d.step(dt, table, supported = true, edgeFraction = edge)
            peak = maxOf(peak, d.tiltDeg)
            t += dt
        }
        return peak
    }

    @Test
    fun `세게 칠수록 더 크게 젖혀진다`() {
        val soft = dyn().apply { strike(0f, 400f, 0.3f) }
        val hard = dyn().apply { strike(0f, 1500f, 0.3f) }
        val softPeak = run(soft, 1.2f)
        val hardPeak = run(hard, 1.2f)
        assertTrue("약한 타격 $softPeak < 센 타격 $hardPeak", hardPeak > softPeak * 1.5f)
        assertTrue("센 타격은 눈에 보이게 기울어야 한다: $hardPeak", hardPeak > 4f)
    }

    @Test
    fun `끝을 때리면 옆으로 기운다`() {
        val left = dyn().apply { strike(-0.9f, 1400f, 0.3f) }
        run(left, 0.25f)
        assertTrue("왼끝을 때리면 왼쪽으로: ${left.roll}", left.roll < -1f)

        val right = dyn().apply { strike(0.9f, 1400f, 0.3f) }
        run(right, 0.25f)
        assertTrue("오른끝을 때리면 오른쪽으로: ${right.roll}", right.roll > 1f)
    }

    @Test
    fun `마찰이 큰 책상에서는 기울기가 빨리 가라앉는다`() {
        val dusty = dyn().apply { strike(0f, 1200f, 0.3f) }
        val steel = dyn().apply { strike(0f, 1200f, 0.3f) }
        run(dusty, 0.6f, DeskMaterial.DUSTY)
        run(steel, 0.6f, DeskMaterial.STEEL)
        assertTrue(
            "먼지 ${dusty.tiltDeg} < 철제 ${steel.tiltDeg}",
            dusty.tiltDeg < steel.tiltDeg,
        )
    }

    @Test
    fun `결국 제자리로 돌아온다`() {
        val d = dyn().apply { strike(0.4f, 900f, 0.4f) }
        run(d, 3f)
        assertEquals(0f, d.tiltDeg, 0.5f)
    }

    @Test
    fun `책상 가장자리에 걸치면 받침이 줄어 넘어진다`() {
        val d = dyn()
        d.strike(0f, 1200f, 0.15f)
        var flopped = false
        val dt = 1f / 120f
        repeat(240) {
            if (d.step(dt, DeskMaterial.RAW_WOOD, supported = true, edgeFraction = 1f)) flopped = true
        }
        assertTrue("책상 끝에서는 넘어져야 한다", flopped)
        assertTrue("넘어짐 횟수가 기록된다: ${d.flops}", d.flops >= 1)
    }

    @Test
    fun `공중에서는 되돌리는 힘이 없다`() {
        val d = dyn().apply { strike(0f, 1200f, 0.3f) }
        val dt = 1f / 120f
        var t = 0f
        var peak = 0f
        while (t < 0.6f) {
            d.step(dt, DeskMaterial.RAW_WOOD, supported = false)
            peak = maxOf(peak, d.tiltDeg)
            t += dt
        }
        assertTrue("공중에서도 자세가 유지된다: $peak", peak > 1f)
    }

    @Test
    fun `두꺼운 지우개는 무게중심이 높아 더 쉽게 젖혀진다`() {
        // 지렛대가 길어지고 받침 각이 작아져, 같은 타격에도 더 크게 기운다.
        val thin = dyn(halfHeight = 18f).apply { strike(0f, 1000f, 0.3f) }
        val thick = dyn(halfHeight = 44f).apply { strike(0f, 1000f, 0.3f) }
        val thinPeak = run(thin, 0.6f)
        val thickPeak = run(thick, 0.6f)
        assertTrue("얇은 $thinPeak < 두꺼운 $thickPeak", thickPeak > thinPeak)
    }

    @Test
    fun `넘어짐 이벤트는 한 번만 꺼낸다`() {
        val d = dyn()
        d.strike(0f, 1500f, 0.1f)
        val dt = 1f / 120f
        var flops = 0
        repeat(240) {
            if (d.step(dt, DeskMaterial.RAW_WOOD, supported = true, edgeFraction = 1f)) flops++
        }
        assertTrue(flops >= 1)
        assertFalse("이미 꺼낸 이벤트는 다시 나오지 않는다", d.consumeFlop())
    }
}
