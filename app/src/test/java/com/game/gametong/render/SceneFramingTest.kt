package com.game.gametong.render

import com.game.gametong.game.DeskSurface
import com.game.gametong.game.EraserBody
import com.game.gametong.game.EraserType
import com.game.gametong.game.EraserWorld
import com.game.gametong.game.MarbleBody
import com.game.gametong.game.MarbleHoleGame
import com.game.gametong.game.Side
import com.game.gametong.game.SquareTtakjiMatch
import com.game.gametong.game.TtakjiMaterial
import com.game.gametong.game.TtakjiSpec
import com.game.gametong.game.TtakjiSurface
import com.game.gametong.game.Vec2
import com.game.gametong.game.physics3d.Vec3
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 장면이 **화면 안에 제대로 담기는지** 검증한다.
 * 카메라 배치가 어긋나면 경기가 화면 밖에서 벌어지므로, 무대 네 모서리와
 * 주요 물체가 화면 안(0~1)에 맺히는지 본다.
 */
class SceneFramingTest {

    private val aspect = 2f / 3f

    private fun world(): EraserWorld {
        val player = EraserBody(Side.PLAYER, EraserType.STANDARD, Vec2(300f, 1200f), Vec2(300f, 1200f))
        val opponent = EraserBody(Side.AI, EraserType.STANDARD, Vec2(650f, 400f), Vec2(650f, 400f))
        return EraserWorld(player, opponent, surface = DeskSurface.WOOD)
    }

    @Test
    fun `지우개 무대 네 모서리가 화면 안에 들어온다`() {
        val w = world()
        val scene = EraserScene3D.build(
            bodies = w.bodies,
            activeSide = Side.PLAYER,
            surface = DeskSurface.WOOD,
            aim = null,
            particles = emptyList(),
            aspect = aspect,
        )
        for (x in listOf(0f, 1000f)) {
            for (y in listOf(0f, 1500f)) {
                val p = Vec3(x * World.SCALE, 0f, y * World.SCALE)
                val uv = scene.project(p, aspect)
                assertNotNull("무대 모서리($x, $y)가 화면 밖", uv)
                assertTrue("u=${uv!![0]}", uv[0] in -0.01f..1.01f)
                assertTrue("v=${uv[1]}", uv[1] in -0.01f..1.01f)
            }
        }
    }

    @Test
    fun `지우개 두 개가 화면 안에 보인다`() {
        val w = world()
        val scene = EraserScene3D.build(
            bodies = w.bodies,
            activeSide = Side.AI,
            surface = DeskSurface.WOOD,
            aim = EraserAim(Vec2(300f, 1200f), Vec2(300f, 700f), 0.6f),
            particles = listOf(ParticleView(500f, 700f, 20f, 8f, 1f, ParticleKindView.CRUMB)),
            aspect = aspect,
        )
        assertTrue("물체가 있어야 한다: ${scene.items.size}", scene.items.size > 5)
        assertTrue("그림자가 있어야 한다: ${scene.shadows.size}", scene.shadows.size >= 2)
        for (body in w.bodies) {
            val uv = scene.project(Vec3(body.pos.x * World.SCALE, 0.05f, body.pos.y * World.SCALE), aspect)
            assertNotNull("지우개(${body.side})가 화면 밖", uv)
            assertTrue("u=${uv!![0]}", uv[0] in 0f..1f)
            assertTrue("v=${uv[1]}", uv[1] in 0f..1f)
        }
    }

    @Test
    fun `네모 딱지 두 장이 화면 안에 들어온다`() {
        val match = SquareTtakjiMatch(
            surface = TtakjiSurface.DIRT,
            playerSpec = TtakjiSpec(TtakjiMaterial.COLOR_PAPER, 2),
            aiSpec = TtakjiSpec(TtakjiMaterial.CARDSTOCK, 2),
        )
        val scene = TtakjiScene3D.build(
            match = match,
            flight = null,
            attacker = null,
            aim = GroundAim(match.playerHome, Vec2(0f, -1f), 0.8f, Vec2(500f, 730f)),
            particles = emptyList(),
            aspect = aspect,
        )
        for (home in listOf(match.playerHome, match.aiHome)) {
            val uv = scene.project(Vec3(home.x * World.SCALE, 0.02f, home.y * World.SCALE), aspect)
            assertNotNull("딱지($home)가 화면 밖", uv)
            assertTrue("u=${uv!![0]}", uv[0] in 0f..1f)
            assertTrue("v=${uv[1]}", uv[1] in 0f..1f)
        }
        assertTrue("접힌 종이 레이어가 있어야 한다: ${scene.items.size}", scene.items.size >= 4)
    }

    @Test
    fun `구슬과 구멍이 화면 안에 들어온다`() {
        val game = MarbleHoleGame(twoPlayer = true)
        val scene = MarbleScene3D.build(
            marbles = listOf(game.playerMarble, game.aiMarble),
            king = null,
            holes = game.holes,
            playRadius = null,
            aim = GroundAim(game.playerStart, Vec2(0.2f, -1f), 0.7f, game.holes[0]),
            particles = listOf(ParticleView(500f, 500f, 0f, 10f, 1f, ParticleKindView.DUST)),
            aspect = aspect,
            fieldHeight = 1080f,
        )
        for (m in listOf(game.playerMarble, game.aiMarble)) {
            val uv = scene.project(Vec3(m.pos.x * World.SCALE, 0.05f, m.pos.y * World.SCALE), aspect)
            assertNotNull("구슬(${m.id})이 화면 밖", uv)
            assertTrue("u=${uv!![0]}", uv[0] in 0f..1f)
            assertTrue("v=${uv[1]}", uv[1] in 0f..1f)
        }
        for (hole in game.holes) {
            val uv = scene.project(Vec3(hole.x * World.SCALE, 0f, hole.y * World.SCALE), aspect)
            assertNotNull("구멍($hole)이 화면 밖", uv)
        }
        assertTrue("유리구슬은 투명 물체로 그린다", scene.items.any { it.transparent })
    }

    @Test
    fun `원 안 구슬 판도 화면 안에 담긴다`() {
        val king = MarbleBody(0, Vec2(500f, 1010f), radius = 34f, king = true)
        val glasses = (1..6).map {
            MarbleBody(it, Vec2(400f + it * 30f, 520f), radius = 24f)
        }
        val scene = MarbleScene3D.build(
            marbles = listOf(king) + glasses,
            king = king,
            holes = emptyList(),
            playRadius = 300f,
            aim = null,
            particles = emptyList(),
            aspect = aspect,
            fieldHeight = 1060f,
        )
        val uv = scene.project(Vec3(5f, 0f, 5.2f), aspect)
        assertNotNull(uv)
        assertTrue("u=${uv!![0]}", uv[0] in 0.1f..0.9f)
        assertTrue("v=${uv[1]}", uv[1] in 0.1f..0.9f)
    }
}
