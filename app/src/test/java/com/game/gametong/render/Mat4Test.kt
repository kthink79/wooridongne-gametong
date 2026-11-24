package com.game.gametong.render

import com.game.gametong.game.physics3d.Quat
import com.game.gametong.game.physics3d.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 3D 행렬·카메라 계산 검증. 화면에 제대로 맺히는지를 수치로 확인한다. */
class Mat4Test {

    private val aspect = 2f / 3f

    @Test
    fun `원점에서 바라보는 카메라 앞 점은 화면 중앙에 맺힌다`() {
        val cam = Camera(eye = Vec3(0f, 5f, 20f), target = Vec3(0f, 0f, 0f), fovYDeg = 45f)
        val uv = cam.projectToUnit(Vec3(0f, 0f, 0f), aspect)
        assertNotNull(uv)
        assertEquals(0.5f, uv!![0], 0.01f)
        assertEquals(0.5f, uv[1], 0.01f)
    }

    @Test
    fun `카메라 뒤의 점은 화면에 맺히지 않는다`() {
        val cam = Camera(eye = Vec3(0f, 5f, 20f), target = Vec3(0f, 0f, 0f), fovYDeg = 45f)
        assertTrue(cam.projectToUnit(Vec3(0f, 0f, 40f), aspect) == null)
    }

    @Test
    fun `화면 좌표를 바닥으로 되돌리면 같은 자리가 나온다`() {
        val cam = Camera.overBoard(
            center = Vec3(5f, 0f, 7.5f),
            width = 10f,
            depth = 15f,
            pitchDeg = 50f,
            aspect = aspect,
        )
        val ground = Vec3(3.2f, 0f, 11.4f)
        val uv = cam.projectToUnit(ground, aspect) ?: error("화면 밖")
        val back = cam.groundPointAt(uv[0], uv[1], aspect)
        assertNotNull(back)
        assertEquals(ground.x, back!!.x, 0.02f)
        assertEquals(ground.z, back.z, 0.02f)
    }

    @Test
    fun `투영 행렬은 화면 안쪽 점을 0~1 로 보낸다`() {
        val cam = Camera.overBoard(Vec3(5f, 0f, 7.5f), 10f, 15f, pitchDeg = 45f, aspect = aspect)
        for (x in listOf(0.2f, 5f, 9.8f)) {
            for (z in listOf(0.2f, 7.5f, 14.8f)) {
                val uv = cam.projectToUnit(Vec3(x, 0f, z), aspect)
                assertNotNull("무대 점($x, $z)이 화면 밖", uv)
                assertTrue("u=${uv!![0]}", uv[0] in -0.02f..1.02f)
                assertTrue("v=${uv[1]}", uv[1] in -0.02f..1.02f)
            }
        }
    }

    @Test
    fun `이동·회전 행렬은 점을 제자리로 옮긴다`() {
        val m = Mat4.trs(Vec3(1f, 2f, 3f), Quat.axisAngle(Vec3.UP, 0f), Vec3(2f, 2f, 2f))
        val p = Mat4.transform(m, Vec3(0.5f, 0f, 0f))
        assertEquals(2f, p[0], 0.001f)
        assertEquals(2f, p[1], 0.001f)
        assertEquals(3f, p[2], 0.001f)
    }

    @Test
    fun `90도 돌린 상자의 위쪽 축이 옆으로 눕는다`() {
        val q = Quat.axisAngle(Vec3.RIGHT, (Math.PI / 2).toFloat())
        val up = q.rotate(Vec3.UP)
        assertEquals(0f, up.x, 0.001f)
        assertEquals(0f, up.y, 0.001f)
        assertEquals(1f, up.z, 0.001f)
    }

    @Test
    fun `역행렬은 원래 행렬을 되돌린다`() {
        val m = Mat4.trs(Vec3(2f, 3f, -4f), Quat.axisAngle(Vec3(1f, 2f, 3f), 0.7f), Vec3(1f, 1f, 1f))
        val inv = Mat4.invert(m)!!
        val identity = Mat4.multiply(m, inv)
        assertEquals(1f, identity[0], 0.001f)
        assertEquals(1f, identity[5], 0.001f)
        assertEquals(1f, identity[10], 0.001f)
        assertEquals(0f, identity[1], 0.001f)
    }

    @Test
    fun `볼록 껍질은 그림자 모서리를 감싼다`() {
        val box = SceneKit.shadowForBox(
            center = Vec3(0f, 0.22f, 0f),
            quat = Quat.IDENTITY,
            half = Vec3(0.5f, 0.22f, 0.4f),
            sunDir = Vec3(-0.4f, -0.8f, -0.4f),
        )
        assertTrue("그림자 점이 3개 이상: ${box.points.size}", box.points.size >= 3)
        // 빛이 왼쪽 위에서 오므로 그림자는 오른쪽 아래(+x, +z)로 늘어난다
        assertTrue(box.points.maxOf { it.x } > 0.4f)
        assertTrue(box.points.all { it.y < 0.01f })
    }
}
