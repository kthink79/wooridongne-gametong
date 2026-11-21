package com.game.gametong.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class GeometryTest {

    @Test
    fun `떨어져 있으면 겹침이 없다`() {
        assertEquals(0f, Geometry.circleOverlapArea(100f, 100f, 250f), 0.001f)
        assertEquals(0f, Geometry.overlapRatio(100f, 100f, 250f), 0.001f)
    }

    @Test
    fun `중심이 같으면 작은 원 전체가 겹친다`() {
        val area = Geometry.circleOverlapArea(100f, 150f, 0f)
        assertEquals((PI * 100 * 100).toFloat(), area, 1f)
    }

    @Test
    fun `겹침 비율은 항상 0과 1 사이`() {
        for (d in 0..300 step 10) {
            val ratio = Geometry.overlapRatio(122f, 152f, d.toFloat())
            assertTrue("d=$d ratio=$ratio", ratio in 0f..1f)
        }
    }

    @Test
    fun `두 원의 순서를 바꿔도 넓이는 같다`() {
        val a = Geometry.circleOverlapArea(96f, 152f, 120f)
        val b = Geometry.circleOverlapArea(152f, 96f, 120f)
        assertEquals(a, b, 0.01f)
    }

    @Test
    fun `가까울수록 겹침이 커진다`() {
        val near = Geometry.overlapRatio(122f, 122f, 140f)
        val far = Geometry.overlapRatio(122f, 122f, 200f)
        assertTrue(near > far)
        assertTrue(near >= Board.PIN_OVERLAP)
        assertTrue(far < Board.PIN_OVERLAP)
    }
}
