package com.game.gametong.game.physics3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 네모 딱지 3D 동역학(공기압·바닥 충격량·회전 모멘텀) 검증. */
class TtakjiDynamicsTest {

    private fun ttakji(thickness: Float = 1f, mass: Float = 1f) =
        TtakjiDynamics(size = 190f, thickness = thickness, mass = mass)

    @Test
    fun `던져 올리면 목표 높이만큼 떠올랐다 내려앉는다`() {
        val d = ttakji()
        d.launch(altitude = 40f, spin = 200f, lever = 0.5f)
        var peak = 0f
        var t = 0f
        while (t < 3f) {
            d.step(1f / 120f)
            peak = maxOf(peak, d.z)
            t += 1f / 120f
        }
        assertTrue("최고 높이 $peak", peak in 30f..70f)
        assertEquals("결국 바닥에 내려앉는다", 0f, d.z, 0.01f)
    }

    @Test
    fun `바닥을 몇 번 튄다`() {
        val d = ttakji()
        d.launch(altitude = 55f, spin = 0f, lever = 0f)
        var t = 0f
        while (t < 3f) {
            d.step(1f / 120f, surfaceBounce = 0.6f)
            t += 1f / 120f
        }
        assertTrue("바운스 횟수 ${d.bounces}", d.bounces >= 1)
    }

    @Test
    fun `회전은 지수적으로 죽는다`() {
        val d = ttakji()
        d.launch(altitude = 30f, spin = 600f, lever = 1f)
        val initial = d.yawRate
        var t = 0f
        while (t < 1.5f) {
            d.step(1f / 120f)
            t += 1f / 120f
        }
        assertTrue("처음 ${initial} -> 나중 ${d.yawRate}", d.yawRate < initial * 0.2f)
        assertTrue("회전각은 누적된다: ${d.yaw}", kotlin.math.abs(d.yaw) > 5f)
    }

    @Test
    fun `가벼운 종이일수록 공기압이 세다`() {
        val light = ttakji(thickness = 1f, mass = 1f)
        val heavy = ttakji(thickness = 3f, mass = 3f)
        assertTrue("가벼운 ${light.airLift()} > 무거운 ${heavy.airLift()}", light.airLift() > heavy.airLift())
    }

    @Test
    fun `공중에서는 회전이 더 오래 간다`() {
        // 공기압은 회전이 빠를 때만 조금 더 뜨게 한다(과하지 않게)
        val d = ttakji()
        d.launch(altitude = 30f, spin = 500f, lever = 1f)
        val lift = d.airLift()
        assertTrue("공기압은 중력보다 훨씬 작아야 한다: $lift", lift < TtakjiDynamics.GRAVITY * 0.3f)
    }

    @Test
    fun `리셋하면 처음 자세로 돌아간다`() {
        val d = ttakji()
        d.launch(altitude = 40f, spin = 300f, lever = 0.8f)
        repeat(30) { d.step(1f / 120f) }
        d.reset()
        assertEquals(0f, d.z, 0.001f)
        assertEquals(0f, d.yaw, 0.001f)
        assertEquals(0f, d.vz, 0.001f)
    }
}
