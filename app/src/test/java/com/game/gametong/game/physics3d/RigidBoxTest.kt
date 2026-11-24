package com.game.gametong.game.physics3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** 직육면체 강체(지우개)의 3D 물리 검증. */
class RigidBoxTest {

    private fun box(
        x: Float = 0f,
        y: Float = 0.5f,
        z: Float = 0f,
        quat: Quat = Quat.IDENTITY,
    ) = RigidBox(
        half = Vec3(0.5f, 0.22f, 0.39f),
        mass = 1f,
        pos = Vec3(x, y, z),
        quat = quat,
    )

    private fun settle(b: RigidBox, seconds: Float = 3f, table: DeskMaterial = DeskMaterial.RAW_WOOD) {
        val dt = 1f / 120f
        var t = 0f
        while (t < seconds) {
            b.step(dt, table = table)
            t += dt
        }
    }

    @Test
    fun `바닥에 떨어뜨리면 평평히 놓여 멈춘다`() {
        val b = box(y = 0.6f)
        settle(b)
        assertEquals(0f, b.lowestPoint(), 0.01f)
        assertTrue("거의 서 있어야 한다: ${b.upAxis}", b.upAxis.y > 0.98f)
        assertTrue("멈춰야 한다: v=${b.vel.length}, w=${b.omega.length}", b.atRest())
    }

    @Test
    fun `중심에서 벗어난 임펄스는 각운동량을 만든다`() {
        val b = box()
        settle(b, 0.5f)
        // 무게중심에서 벗어난 지점(윗면 모서리)을 옆으로 치면 병진과 함께 회전이 붙는다.
        b.applyImpulse(Vec3(0.9f, 0f, 0f), Vec3(0.5f, 0.22f, 0.39f))
        assertTrue("각운동량이 붙어야 한다: ${b.omega.length}", b.omega.length > 1f)
        assertTrue("병진도 함께 붙는다: ${b.vel.x}", b.vel.x > 0.5f)
        // 시간이 지나면 바닥에 다시 눕는다(튕겨 돌다가 멈춘다)
        settle(b, 2.5f)
        assertEquals(0f, b.lowestPoint(), 0.02f)
        assertTrue("결국 멈춘다: v=${b.vel.length} w=${b.omega.length}", b.vel.length < 0.1f)
    }

    @Test
    fun `미끄러운 철제 책상이 거친 합판보다 멀리 간다`() {
        val steel = box(y = 0.22f)
        val plywood = box(y = 0.22f)
        steel.vel = Vec3(2.4f, 0f, 0f)
        plywood.vel = Vec3(2.4f, 0f, 0f)
        settle(steel, 2.5f, DeskMaterial.STEEL)
        settle(plywood, 2.5f, DeskMaterial.PLYWOOD)
        assertTrue(
            "철제 ${steel.pos.x} > 합판 ${plywood.pos.x}",
            steel.pos.x > plywood.pos.x + 0.05f,
        )
    }

    @Test
    fun `먼지 쌓인 책상에서는 거의 미끄러지지 않는다`() {
        val dusty = box(y = 0.22f)
        dusty.vel = Vec3(1.6f, 0f, 0f)
        settle(dusty, 2f, DeskMaterial.DUSTY)
        assertTrue("먼지 책상에서 미끄러진 거리 ${dusty.pos.x}", dusty.pos.x < 0.6f)
    }

    @Test
    fun `반발계수가 큰 재질에서 더 높이 튄다`() {
        val springy = box(y = 1.2f)
        val dull = box(y = 1.2f)
        var peakSpringy = 0f
        var peakDull = 0f
        val dt = 1f / 120f
        repeat(240) {
            springy.step(dt, table = DeskMaterial.PLASTIC)
            dull.step(dt, table = DeskMaterial.DUSTY)
            peakSpringy = maxOf(peakSpringy, springy.pos.y)
            peakDull = maxOf(peakDull, dull.pos.y)
        }
        assertTrue("플라스틱 $peakSpringy vs 먼지 $peakDull", peakSpringy >= peakDull - 0.01f)
    }

    @Test
    fun `사원수 회전은 벡터 길이를 보존한다`() {
        val q = Quat.axisAngle(Vec3(0.3f, 0.8f, 0.5f), 0.9f)
        val v = Vec3(1.2f, -0.4f, 0.7f)
        val r = q.rotate(v)
        assertEquals(v.length, r.length, 1e-4f)
    }

    @Test
    fun `책상 밖으로 나가 떨어지면 fallenOut 이 참이다`() {
        val b = RigidBox(Vec3(0.5f, 0.22f, 0.39f), 1f, pos = Vec3(4.6f, 0.22f, 3f))
        val bounds = Rect2(-5f, -7.5f, 5f, 7.5f)
        // 책상 가장자리 쪽으로 밀어낸다
        b.vel = Vec3(2.2f, 0f, 0f)
        val dt = 1f / 120f
        repeat(400) { b.step(dt, bounds = bounds) }
        assertTrue("바깥으로 나가야 한다: ${b.pos}", !bounds.contains(b.pos.x, b.pos.z))
        assertTrue("책상 아래로 떨어져야 한다: ${b.pos.y}", b.fallenOut(bounds, fallY = 0.1f))
    }

    @Test
    fun `관성 모멘트가 큰 축은 같은 임펄스에도 덜 돈다`() {
        val b = box()
        // 긴 축(폭 0.5)보다 짧은 축(높이 0.22)을 축으로 돌릴 때 관성이 작다
        b.applyImpulse(Vec3(0f, 0f, 0.6f), Vec3(0f, 0.22f, 0f))
        val aroundX = abs(b.omega.x)
        val b2 = box()
        b2.applyImpulse(Vec3(0f, 0.6f, 0f), Vec3(0.5f, 0f, 0f))
        val aroundZ = abs(b2.omega.z)
        assertTrue("같은 임펄스에 회전이 달라야 한다: $aroundX vs $aroundZ", aroundX != aroundZ)
    }

    @Test
    fun `놓아둔 상자는 저절로 움직이지 않는다`() {
        val b = box(y = 0.22f)
        settle(b, 1f)
        val x = b.pos.x
        val z = b.pos.z
        settle(b, 2f)
        assertFalse("저절로 굴러가면 안 된다: ${b.pos}", abs(b.pos.x - x) > 0.05f)
        assertFalse(abs(b.pos.z - z) > 0.05f)
    }
}
