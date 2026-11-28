package com.game.gametong.render

import com.game.gametong.game.Board
import com.game.gametong.game.DeskSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 배경 사진 앉히기 검사.
 *
 * 사진 속 책상 끝선(`EraserScene3D.PHOTO_DESK_EDGE_V`)이 3D 책상 판의 먼 끝선과
 * 화면에서 정확히 같은 줄에 오는지, 그 상태에서 지우개 무대가 화면에 다 들어오는지 본다.
 */
class BackdropFitTest {

    private val aspect = Camera.PORTRAIT_ASPECT

    @Test
    fun `사진의 책상 끝선이 화면의 책상 끝선 자리에 온다`() {
        val deskEdgeV = 0.203f
        val (top, bottom) = BackdropFit.fit(deskEdgeV, EraserScene3D.PHOTO_DESK_EDGE_V)
        val shown = BackdropFit.photoVAt(deskEdgeV, top, bottom)
        assertEquals(EraserScene3D.PHOTO_DESK_EDGE_V, shown, 1e-5f)
    }

    @Test
    fun `사진은 화면 위아래로 뒤집히지 않고 이어진다`() {
        val (top, bottom) = BackdropFit.fit(0.2f, 0.26f)
        // 화면 위(0)는 사진의 더 위쪽(작은 값), 화면 아래(1)는 더 아래쪽(큰 값)
        assertTrue(BackdropFit.photoVAt(0f, top, bottom) < BackdropFit.photoVAt(1f, top, bottom))
    }

    @Test
    fun `지우개 무대는 늘 배경 사진 위쪽 빈 자리보다 아래에 있다`() {
        val cam = EraserScene3D.camera(aspect)
        val w = Board.WIDTH * 0.01f
        val h = Board.HEIGHT * 0.01f
        val groundD = h + 1.9f
        val edge = cam.projectToUnit(
            com.game.gametong.game.physics3d.Vec3(w * 0.5f, 0f, h * 0.5f - groundD * 0.5f),
            aspect,
        )!!
        // 배경(사진)이 보이는 띠가 화면 위쪽에 남아 있어야 사진이 의미가 있다
        assertTrue("3D 책상 먼 끝이 너무 위: ${edge[1]}", edge[1] > 0.12f)
        assertTrue("3D 책상 먼 끝이 너무 아래: ${edge[1]}", edge[1] < 0.35f)
        // 무대 네 모서리가 화면 안에 들어온다
        for (x in floatArrayOf(0f, w)) {
            for (z in floatArrayOf(0f, h)) {
                val uv = cam.projectToUnit(com.game.gametong.game.physics3d.Vec3(x, 0f, z), aspect)!!
                assertTrue("무대가 화면 밖 ($x,$z): ${uv[0]},${uv[1]}", uv[0] in 0.0..1.0 && uv[1] in 0.0..1.0)
            }
        }
    }

    @Test
    fun `나무 책상 장면에는 사진 배경이 붙는다`() {
        val bodies = listOf(
            com.game.gametong.game.EraserBody(
                com.game.gametong.game.Side.PLAYER,
                com.game.gametong.game.EraserType.STANDARD,
                com.game.gametong.game.Vec2(500f, 960f),
                com.game.gametong.game.Vec2(500f, 960f),
            ),
            com.game.gametong.game.EraserBody(
                com.game.gametong.game.Side.AI,
                com.game.gametong.game.EraserType.STANDARD,
                com.game.gametong.game.Vec2(500f, 540f),
                com.game.gametong.game.Vec2(500f, 540f),
            ),
        )
        val scene = EraserScene3D.build(
            bodies = bodies,
            activeSide = com.game.gametong.game.Side.PLAYER,
            surface = DeskSurface.WOOD,
            aim = null,
            particles = emptyList(),
            aspect = aspect,
        )
        val backdrop = scene.backdrop
        assertTrue("배경 사진이 없다", backdrop != null)
        assertEquals(TexKind.PHOTO_ROOM, backdrop!!.tex)
        assertEquals(TexKind.PHOTO_WOOD, scene.ground.texture)
        // 사진 배경은 화면 위쪽(하늘 자리)만 덮고, 사진의 책상 끝선은 3D 책상 먼 끝에 온다
        val shown = BackdropFit.photoVAt(0.5f, backdrop.uvTop, backdrop.uvBottom)
        assertTrue("사진이 뒤집혔다", shown > backdrop.uvTop)
    }

    @Test
    fun `철판 책상에는 사진 나뭇결을 쓰지 않는다`() {
        val bodies = listOf(
            com.game.gametong.game.EraserBody(
                com.game.gametong.game.Side.PLAYER,
                com.game.gametong.game.EraserType.STANDARD,
                com.game.gametong.game.Vec2(500f, 960f),
                com.game.gametong.game.Vec2(500f, 960f),
            ),
        )
        val scene = EraserScene3D.build(
            bodies = bodies,
            activeSide = com.game.gametong.game.Side.PLAYER,
            surface = DeskSurface.STEEL,
            aim = null,
            particles = emptyList(),
            aspect = aspect,
        )
        assertEquals(TexKind.CEMENT, scene.ground.texture)
    }
}
