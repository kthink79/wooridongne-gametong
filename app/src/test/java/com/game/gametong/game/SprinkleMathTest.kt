package com.game.gametong.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SprinkleMathTest {

    @Test
    fun `물량 뿌리기 명성은 v01 공식을 따른다`() {
        // 30 + 6×12 + 4×(5 + 2×1) = 130
        assertEquals(130, SprinkleMath.bulkPrestige(recipients = 12, rare = 5, legendary = 1))
    }

    @Test
    fun `명예 뿌리기는 수령자 5명에 1_6배`() {
        // (30 + 30 + 28) × 1.6 = 140.8 → 141
        assertEquals(141, SprinkleMath.honorPrestige(recipients = 12, rare = 5, legendary = 1))
    }

    @Test
    fun `수령자는 20명까지만 반영된다`() {
        assertEquals(SprinkleMath.bulkPrestige(20, 0, 0), SprinkleMath.bulkPrestige(60, 0, 0))
        assertEquals(150, SprinkleMath.bulkPrestige(60, 0, 0))
    }

    @Test
    fun `일일 상한 300을 넘지 않는다`() {
        assertTrue(SprinkleMath.bulkPrestige(20, 100, 5) <= SprinkleMath.DAILY_CAP)
        assertEquals(SprinkleMath.DAILY_CAP, SprinkleMath.bulkPrestige(20, 100, 5))
    }

    @Test
    fun `수집품이 없어도 최소 명성은 남는다`() {
        assertEquals(30, SprinkleMath.bulkPrestige(0, 0, 0))
    }

    @Test
    fun `음수 입력은 0으로 처리한다`() {
        assertEquals(30, SprinkleMath.bulkPrestige(-3, -1, -1))
    }
}
