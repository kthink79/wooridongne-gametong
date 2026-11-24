package com.game.gametong.game.physics3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 지우개 가루·먼지 파티클 검증. */
class ParticleSystemTest {

    @Test
    fun `충돌하면 가루가 튄다`() {
        val ps = ParticleSystem(capacity = 200)
        ps.spawnCrumbs(x = 500f, y = 700f, z = 10f, impact = 900f)
        assertTrue("가루가 생겨야 한다: ${ps.size}", ps.size > 0)
        assertTrue(ps.particles.all { it.kind == ParticleKind.CRUMB })
    }

    @Test
    fun `세게 부딪힐수록 가루가 많다`() {
        val soft = ParticleSystem(seed = 7).also { it.spawnCrumbs(500f, 700f, 0f, impact = 200f) }
        val hard = ParticleSystem(seed = 7).also { it.spawnCrumbs(500f, 700f, 0f, impact = 1400f) }
        assertTrue("약 ${soft.size} < 강 ${hard.size}", hard.size > soft.size)
    }

    @Test
    fun `개수는 정해진 용량을 넘지 않는다`() {
        val ps = ParticleSystem(capacity = 40)
        repeat(20) { ps.spawnDust(500f, 700f, power = 1.6f) }
        assertTrue("용량 초과: ${ps.size}", ps.size <= 40)
    }

    @Test
    fun `입자는 중력에 끌려 바닥에 내려앉고 수명이 다하면 사라진다`() {
        val ps = ParticleSystem(capacity = 60, seed = 3)
        ps.spawnCrumbs(500f, 700f, 40f, impact = 800f, count = 10)
        val start = ps.size
        repeat(180) { ps.update(1f / 120f) }
        assertTrue("결국 사라져야 한다: ${ps.size} / $start", ps.size < start)
        assertEquals(0, ps.size)
    }

    @Test
    fun `분필가루는 흙먼지로 만들 수 있다`() {
        val ps = ParticleSystem(capacity = 60)
        ps.spawnDust(500f, 600f, power = 1f, kind = ParticleKind.CHALK)
        assertTrue(ps.particles.all { it.kind == ParticleKind.CHALK })
    }
}
