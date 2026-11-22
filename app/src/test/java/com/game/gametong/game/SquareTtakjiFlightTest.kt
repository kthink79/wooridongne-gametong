package com.game.gametong.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 네모 딱지 2.5D 연출(프레임 단위 시뮬레이션) 검증.
 * 들림·웨이블·Z축 바운스·회전·대각 궤적이 실제로 계산되는지 본다.
 */
class SquareTtakjiFlightTest {

    private val playerPos = Vec2(500f, 1150f)
    private val aiPos = Vec2(500f, 730f)
    private val spec = TtakjiSpec(TtakjiMaterial.COLOR_PAPER, 1, 2)

    private fun strike(
        power: Float,
        aim: Float = 0f,
        point: Float = 0.5f,
        target: StrikeTarget = StrikeTarget.AUTO,
        surface: TtakjiSurface = TtakjiSurface.DIRT,
        defender: TtakjiSpec = spec,
    ): SquareStrikeResult = SquareTtakjiEngine.strike(
        attackerPos = playerPos,
        attacker = TtakjiSpec(TtakjiMaterial.COLOR_PAPER, 1, 0),
        defenderPos = aiPos,
        defender = defender,
        defenderAngleDeg = 0f,
        surface = surface,
        aimDeg = aim,
        power = power,
        strikePoint = point,
        forwardSign = -1f,
        target = target,
    )

    private fun flight(
        result: SquareStrikeResult,
        surface: TtakjiSurface = TtakjiSurface.DIRT,
        defender: TtakjiSpec = spec,
    ) =
        SquareTtakjiFlight(
            result = result,
            attackerHome = playerPos,
            defenderHome = aiPos,
            attackerSpec = TtakjiSpec(TtakjiMaterial.COLOR_PAPER, 1, 0),
            defenderSpec = defender,
            surface = surface,
            forwardSign = -1f,
        )

    @Test
    fun `거의 뒤집힐 뻔한 수는 70~85도까지 들렸다가 도로 내려온다`() {
        val result = strike(power = 0.7f, defender = spec.copy(weakEdge = 0))
        assertFalse(result.flipped)
        assertTrue("아슬아슬 판정이어야 한다: ${result.tiltPeak}", result.nearFlip)

        val f = flight(result, defender = spec.copy(weakEdge = 0))
        f.runToEnd()
        assertTrue("최고 각 ${f.peakTiltSeen}", f.peakTiltSeen in 70f..86f)
        assertTrue("연출이 끝나면 다시 눕는다: ${f.defenderTilt}", f.defenderTilt < 2f)
        assertTrue(f.isFinished)
    }

    @Test
    fun `넘어가는 수는 90도를 넘어 180도까지 넘어간다`() {
        val result = strike(power = 1f)
        assertTrue(result.flipped)
        val f = flight(result)
        f.runToEnd()
        assertTrue(f.overTheEdge)
        assertTrue(f.peakTiltSeen >= 179f)
        assertEquals(180f, f.defenderTilt, 1.5f)
    }

    @Test
    fun `맞은 딱지는 공중으로 튀어 올랐다가 내려앉는다`() {
        val result = strike(power = 0.8f)
        assertTrue(result.bounceHeight > 0f)
        val f = flight(result)
        var sawAir = false
        while (!f.isFinished) {
            f.advance(1f / 60f)
            if (f.defenderAirborne) sawAir = true
        }
        assertTrue("공중에 떠야 한다", sawAir)
        assertTrue("튄 높이 ${f.maxHopSeen}", f.maxHopSeen > 5f)
        assertEquals("결국 바닥에 내려앉는다", 0f, f.defenderZ, 0.01f)
    }

    @Test
    fun `중심에서 벗어난 타격은 회전을 붙인다`() {
        val center = flight(strike(power = 0.8f, point = 0f))
        center.runToEnd()
        val edge = flight(strike(power = 0.8f, point = 0.95f))
        edge.runToEnd()
        assertTrue(
            "끝을 때리면 더 많이 돈다: ${center.maxYawSeen} vs ${edge.maxYawSeen}",
            edge.maxYawSeen > center.maxYawSeen,
        )
    }

    @Test
    fun `대각으로 치면 딱지도 대각선으로 밀린다`() {
        val straight = flight(strike(power = 0.9f, aim = 0f))
        straight.runToEnd()
        val diagonal = flight(strike(power = 0.9f, aim = 22f))
        diagonal.runToEnd()
        assertEquals(500f, straight.defenderPos.x, 2f)
        assertTrue(
            "대각으로 밀려야 한다: ${diagonal.defenderPos}",
            diagonal.defenderPos.x > 520f,
        )
        assertTrue(diagonal.defenderPos.y < aiPos.y)
    }

    @Test
    fun `연출은 2초 안에 끝나고 두 딱지가 멈춘 자세로 남는다`() {
        val f = flight(strike(power = 0.9f))
        val took = f.runToEnd()
        assertTrue("걸린 시간 $took", took < 2f)
        assertTrue(f.impactHappened)
        assertTrue(f.defenderSpeed <= 0.001f)
    }

    @Test
    fun `공격 딱지는 상대 앞까지 미끄러져 들어간다`() {
        val f = flight(strike(power = 0.9f))
        // 치기 전에는 자기 자리
        assertEquals(playerPos, f.attackerPose.pos)
        f.runToEnd()
        val traveled = f.attackerPose.pos.distanceTo(playerPos)
        assertTrue("다가가야 한다: $traveled", traveled > 100f)
        assertTrue("상대를 지나치지는 않는다", f.attackerPose.pos.y > aiPos.y)
    }

    @Test
    fun `헛스윙은 충돌 없이 짧게 끝난다`() {
        val whiff = strike(power = 1f, aim = 60f)
        assertFalse(whiff.contact)
        val f = flight(whiff)
        f.runToEnd()
        assertFalse(f.impactHappened)
        assertTrue(f.isFinished)
        assertEquals(0f, f.peakTiltSeen, 0.001f)
    }

    @Test
    fun `물리 프레임을 잘게 나눠도 같은 결론이 나온다`() {
        val result = strike(power = 0.7f)
        val coarse = flight(result).apply { runToEnd(1f / 60f) }
        val fine = flight(result).apply { runToEnd(1f / 240f) }
        assertEquals(coarse.peakTiltSeen, fine.peakTiltSeen, 1.5f)
        assertEquals(coarse.defenderTilt, fine.defenderTilt, 2f)
    }
}
