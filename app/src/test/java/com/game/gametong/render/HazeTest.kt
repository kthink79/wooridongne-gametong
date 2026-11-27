package com.game.gametong.render

import com.game.gametong.game.DeskSurface
import com.game.gametong.game.EraserBody
import com.game.gametong.game.EraserType
import com.game.gametong.game.EraserWorld
import com.game.gametong.game.MarbleHoleGame
import com.game.gametong.game.Side
import com.game.gametong.game.SquareTtakjiMatch
import com.game.gametong.game.TtakjiMaterial
import com.game.gametong.game.TtakjiSpec
import com.game.gametong.game.TtakjiSurface
import com.game.gametong.game.Vec2
import com.game.gametong.game.physics3d.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 거리 안개가 **경기장을 덮지 않는지** 검증한다.
 *
 * 예전 셰이더는 거리 8~26에서 안개를 1.0(완전히 안개색)까지 올렸고 `hazeStrength` 는 쓰지도 않았다.
 * 세로 화면의 45도 카메라는 무대 중심까지 20 안팎이라, 운동장 앞쪽 바닥과 파란·노란 딱지가
 * 뿌연 안개에 잠겼다. 이제 시작 거리는 카메라 거리에서 잡고 세기만큼만 섞는다.
 */
class HazeTest {

    private val aspect = 2f / 3f

    private fun eraserScene(): Scene {
        val w = EraserWorld(
            player = EraserBody(Side.PLAYER, EraserType.STANDARD, Vec2(300f, 1200f), Vec2(300f, 1200f)),
            opponent = EraserBody(Side.AI, EraserType.STANDARD, Vec2(650f, 400f), Vec2(650f, 400f)),
            surface = DeskSurface.WOOD,
        )
        return EraserScene3D.build(
            bodies = w.bodies,
            activeSide = Side.PLAYER,
            surface = DeskSurface.WOOD,
            aim = null,
            particles = emptyList(),
            aspect = aspect,
        )
    }

    private fun ttakjiScene(): Scene {
        val match = SquareTtakjiMatch(
            surface = TtakjiSurface.DIRT,
            playerSpec = TtakjiSpec(TtakjiMaterial.COLOR_PAPER, 2),
            aiSpec = TtakjiSpec(TtakjiMaterial.CARDSTOCK, 2),
        )
        return TtakjiScene3D.build(match, null, null, null, emptyList(), aspect)
    }

    private fun marbleScene(): Scene {
        val game = MarbleHoleGame(twoPlayer = true)
        return MarbleScene3D.build(
            marbles = listOf(game.playerMarble, game.aiMarble),
            king = null,
            holes = game.holes,
            playRadius = null,
            aim = null,
            particles = emptyList(),
            aspect = aspect,
            fieldHeight = 1080f,
        )
    }

    /** 무대 네 모서리(바닥)에 안개가 끼지 않는다. */
    private fun assertFieldClear(scene: Scene, halfW: Float, halfD: Float, label: String) {
        val range = Haze.rangeFor((scene.camera.eye - scene.camera.target).length, scene.env)
        for (sx in listOf(-1f, 1f)) {
            for (sz in listOf(-1f, 1f)) {
                val p = scene.ground.center + Vec3(halfW * sx, 0f, halfD * sz)
                val dist = (p - scene.camera.eye).length
                val factor = Haze.factorAt(dist, range, scene.env.hazeStrength)
                assertEquals(
                    "$label 모서리($sx,$sz)에 안개가 꼈다: $factor " +
                        "(거리 $dist / 안개 ${range[0]}~${range[0] + range[1]})",
                    0f,
                    factor,
                    0.001f,
                )
            }
        }
    }

    @Test
    fun `지우개 책상은 안개에 잠기지 않는다`() {
        assertFieldClear(eraserScene(), halfW = 5.0f, halfD = 7.5f, label = "지우개")
    }

    @Test
    fun `네모 딱지 운동장은 안개에 잠기지 않는다`() {
        assertFieldClear(ttakjiScene(), halfW = 5.2f, halfD = 7.3f, label = "딱지")
    }

    @Test
    fun `구슬 운동장은 안개에 잠기지 않는다`() {
        assertFieldClear(marbleScene(), halfW = 5.3f, halfD = 5.4f, label = "구슬")
    }

    @Test
    fun `안개는 무대 밖에서 시작해 세기만큼만 섞인다`() {
        val env = ttakjiScene().env
        val range = Haze.rangeFor(20f, env)
        assertEquals("\"무대 바로 밖\\\" 에서는 아직 없다", 0f, Haze.factorAt(range[0] - 0.01f, range, env.hazeStrength), 1e-4f)
        assertEquals(
            "시작+번짐 거리에서 최대 세기",
            env.hazeStrength,
            Haze.factorAt(range[0] + range[1], range, env.hazeStrength),
            1e-4f,
        )
        // 아무리 멀어도 완전히 덮지 않는다(예전에는 1.0 → 안개색으로 꽉 참).
        assertEquals(
            "먼 곳에서도 세기를 넘지 않는다",
            env.hazeStrength,
            Haze.factorAt(range[0] + range[1] * 4f, range, env.hazeStrength),
            1e-4f,
        )
        assertTrue("세기가 1이면 안 된다: ${env.hazeStrength}", env.hazeStrength in 0.05f..0.40f)
    }

    @Test
    fun `세로 화면 카메라에서도 딱지 두 장이 안개 밖이다`() {
        val scene = ttakjiScene()
        val range = Haze.rangeFor((scene.camera.eye - scene.camera.target).length, scene.env)
        val match = SquareTtakjiMatch(
            surface = TtakjiSurface.DIRT,
            playerSpec = TtakjiSpec(TtakjiMaterial.COLOR_PAPER, 2),
            aiSpec = TtakjiSpec(TtakjiMaterial.CARDSTOCK, 2),
        )
        for (home in listOf(match.playerHome, match.aiHome)) {
            val p = Vec3(home.x * World.SCALE, 0.02f, home.y * World.SCALE)
            val factor = Haze.factorAt((p - scene.camera.eye).length, range, scene.env.hazeStrength)
            assertEquals("딱지($home)가 안개에 잠겼다: $factor", 0f, factor, 0.001f)
        }
    }

    @Test
    fun `가로 화면에서도 무대가 안개 밖이다`() {
        // 딱지 화면은 놀이판 상자 비율을 따라간다 — 가로가 넓어 카메라가 멀어져도 마찬가지여야 한다.
        for (a in listOf(0.8f, 1.1f)) {
            val match = SquareTtakjiMatch(
                surface = TtakjiSurface.DIRT,
                playerSpec = TtakjiSpec(TtakjiMaterial.COLOR_PAPER, 2),
                aiSpec = TtakjiSpec(TtakjiMaterial.CARDSTOCK, 2),
            )
            val scene = TtakjiScene3D.build(match, null, null, null, emptyList(), a)
            assertFieldClear(scene, halfW = 5.2f, halfD = 7.3f, label = "비율 $a 딱지")
        }
    }
}
