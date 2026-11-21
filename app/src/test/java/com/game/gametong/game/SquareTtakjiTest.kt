package com.game.gametong.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SquareTtakjiTest {

    private val player = Vec2(500f, 1150f)
    private val ai = Vec2(500f, 730f)
    private val paper = TtakjiSpec(TtakjiMaterial.COLOR_PAPER, thickness = 1, weakEdge = 2)

    private fun strike(
        power: Float = 1f,
        aim: Float = 0f,
        point: Float = 0f,
        defender: TtakjiSpec = paper,
        surface: TtakjiSurface = TtakjiSurface.DIRT,
    ) = SquareTtakjiEngine.strike(
        attackerPos = player,
        attacker = TtakjiSpec(TtakjiMaterial.COLOR_PAPER, 1, 0),
        defenderPos = ai,
        defender = defender,
        defenderAngleDeg = 0f,
        surface = surface,
        aimDeg = aim,
        power = power,
        strikePoint = point,
    )

    @Test
    fun `정면으로 세게 치면 상대 딱지가 뒤집힌다`() {
        val result = strike(power = 1f, point = 0.9f)
        assertTrue(result.contact)
        assertTrue(result.flipped)
        assertTrue(result.flipRatio >= 1f)
    }

    @Test
    fun `약하게 치면 맞히긴 해도 뒤집히지 않는다`() {
        val result = strike(power = 0.7f, point = 0f, defender = paper.copy(weakEdge = 0))
        assertTrue(result.contact)
        assertFalse(result.flipped)
        assertTrue(result.flipRatio in 0.1f..0.99f)
    }

    @Test
    fun `들뜬 날개 틈을 맞히면 같은 힘으로도 더 잘 뒤집힌다`() {
        val weak = strike(power = 0.6f, point = 0.45f, defender = paper.copy(weakEdge = 2))
        val plain = strike(power = 0.6f, point = 0.45f, defender = paper.copy(weakEdge = 0))
        assertTrue(weak.contact && plain.contact)
        assertTrue(weak.weakPoint)
        assertFalse(plain.weakPoint)
        assertTrue(weak.flipRatio > plain.flipRatio * 1.5f)
        assertTrue(weak.flipped)
        assertFalse(plain.flipped)
    }

    @Test
    fun `날개 틈에서 벗어난 곳을 맞히면 약점 판정이 아니다`() {
        val result = strike(power = 0.6f, point = 0.45f, defender = paper.copy(weakEdge = 1))
        assertTrue(result.contact)
        assertFalse(result.weakPoint)
        assertEquals(2, result.hitEdge)
    }

    @Test
    fun `때리는 위치가 끝일수록 토크가 커진다`() {
        val center = strike(power = 0.8f, point = 0f, defender = paper.copy(weakEdge = 0))
        val edge = strike(power = 0.8f, point = 0.95f, defender = paper.copy(weakEdge = 0))
        assertTrue(edge.flipRatio > center.flipRatio)
    }

    @Test
    fun `두께가 두꺼울수록 잘 안 뒤집힌다`() {
        val thin = strike(power = 0.8f, point = 0.5f, defender = paper.copy(thickness = 1, weakEdge = 0))
        val thick = strike(power = 0.8f, point = 0.5f, defender = paper.copy(thickness = 3, weakEdge = 0))
        assertTrue(thin.flipRatio > thick.flipRatio)
        assertTrue(
            paper.copy(thickness = 3).flipResistance > paper.copy(thickness = 1).flipResistance
        )
    }

    @Test
    fun `무거운 재질일수록 잘 안 뒤집힌다`() {
        val light = strike(
            power = 0.9f, point = 0.5f,
            defender = TtakjiSpec(TtakjiMaterial.NEWSPAPER, 1, 0),
        )
        val heavy = strike(
            power = 0.9f, point = 0.5f,
            defender = TtakjiSpec(TtakjiMaterial.CARDBOARD, 1, 0),
        )
        assertTrue(light.flipRatio > heavy.flipRatio)
    }

    @Test
    fun `노면이 미끄러울수록 더 멀리 미끄러진다`() {
        val spec = TtakjiSpec(TtakjiMaterial.COLOR_PAPER, 1)
        val maru = SquareTtakjiEngine.slideDistance(900f, TtakjiSurface.MARU, spec)
        val dirt = SquareTtakjiEngine.slideDistance(900f, TtakjiSurface.DIRT, spec)
        val asphalt = SquareTtakjiEngine.slideDistance(900f, TtakjiSurface.ASPHALT, spec)
        assertTrue(maru > dirt)
        assertTrue(dirt > asphalt)
    }

    @Test
    fun `아스팔트에서는 힘이 모자라면 상대에게 닿지 않는다`() {
        val short = strike(power = 0.45f, surface = TtakjiSurface.ASPHALT)
        assertFalse(short.contact)
        assertFalse(short.floorHit)
    }

    @Test
    fun `세게 헛스윙하면 바닥을 쳐 부상한다`() {
        val whiff = strike(power = 1f, aim = 60f)
        assertFalse(whiff.contact)
        assertTrue(whiff.floorHit)
        val stamina = SquareTtakjiEngine.settleStamina(
            stamina = 100f, appliedPower = 1f, floorHit = true,
            surface = TtakjiSurface.DIRT, injuryTurns = 0,
        )
        assertEquals(TtakjiSurface.DIRT.injuryTurns, stamina.injuryTurns)
        assertEquals(2, stamina.injuryTurns)
    }

    @Test
    fun `마루에서의 헛스윙은 부상이 1턴으로 짧다`() {
        assertEquals(1, TtakjiSurface.MARU.injuryTurns)
        val result = SquareTtakjiEngine.settleStamina(
            stamina = 100f, appliedPower = 1f, floorHit = true,
            surface = TtakjiSurface.MARU, injuryTurns = 1,
        )
        assertEquals(1, result.injuryTurns)
    }

    @Test
    fun `부상 중에는 파워 상한이 걸린다`() {
        assertEquals(1f, SquareTtakjiEngine.powerCap(100f, 0), 0.001f)
        assertEquals(0.5f, SquareTtakjiEngine.powerCap(100f, 2), 0.001f)
        assertEquals(0.5f, SquareTtakjiEngine.appliedPower(1.2f, 100f, 1), 0.001f)
    }

    @Test
    fun `피로도가 떨어지면 낼 수 있는 힘이 줄어든다`() {
        val full = SquareTtakjiEngine.powerCap(100f, 0)
        val tired = SquareTtakjiEngine.powerCap(20f, 0)
        assertTrue(full > tired)
        assertTrue(SquareTtakjiEngine.appliedPower(1f, 20f, 0) < 0.85f)
    }

    @Test
    fun `살살 치면 피로도가 오히려 회복된다`() {
        val soft = SquareTtakjiEngine.settleStamina(
            stamina = 50f, appliedPower = 0.4f, floorHit = false,
            surface = TtakjiSurface.DIRT, injuryTurns = 0,
        )
        assertTrue(soft.soft)
        assertTrue(soft.stamina > 50f)
    }

    @Test
    fun `세게 치면 피로도가 줄어든다`() {
        val hard = SquareTtakjiEngine.settleStamina(
            stamina = 80f, appliedPower = 1f, floorHit = false,
            surface = TtakjiSurface.DIRT, injuryTurns = 0,
        )
        assertFalse(hard.soft)
        assertEquals(68f, hard.stamina, 0.001f)
    }

    @Test
    fun `부상은 두 턴 동안 유지되고 그 뒤 풀린다`() {
        val match = SquareTtakjiMatch(
            surface = TtakjiSurface.DIRT,
            playerSpec = TtakjiSpec(TtakjiMaterial.COLOR_PAPER, 1),
            aiSpec = TtakjiSpec(TtakjiMaterial.COLOR_PAPER, 1),
            twoPlayer = true,
            random = Random(3),
        )
        val whiff = match.attack(Side.PLAYER, 70f, 1f, 0f)
        assertTrue(whiff.floorHit)
        assertEquals(2, match.playerInjury)

        val capped1 = match.attack(Side.PLAYER, 0f, 1f, 0.9f)
        assertEquals(0.5f, capped1.appliedPower, 0.001f)
        assertEquals(1, match.playerInjury)

        val capped2 = match.attack(Side.PLAYER, 0f, 1f, 0.9f)
        assertEquals(0.5f, capped2.appliedPower, 0.001f)
        assertEquals(0, match.playerInjury)

        val free = match.attack(Side.PLAYER, 0f, 1f, 0.9f)
        assertTrue(free.appliedPower > 0.6f)
    }

    @Test
    fun `뒤집으면 점수를 얻고 차례가 넘어간다`() {
        val match = SquareTtakjiMatch(
            surface = TtakjiSurface.DIRT,
            playerSpec = TtakjiSpec(TtakjiMaterial.COLOR_PAPER, 1),
            aiSpec = TtakjiSpec(TtakjiMaterial.COLOR_PAPER, 1),
            twoPlayer = true,
            random = Random(5),
        )
        assertEquals(Side.PLAYER, match.turn)
        val result = match.attack(Side.PLAYER, 0f, 1f, 0.9f)
        assertTrue(result.flipped)
        assertEquals(1, match.playerScore)
        assertEquals(Side.AI, match.turn)
        assertTrue(match.aiSpec.weakEdge in intArrayOf(2, 1, 3))
    }

    @Test
    fun `3점을 먼저 내면 경기가 끝난다`() {
        val match = SquareTtakjiMatch(
            surface = TtakjiSurface.MARU,
            playerSpec = TtakjiSpec(TtakjiMaterial.COLOR_PAPER, 1),
            aiSpec = TtakjiSpec(TtakjiMaterial.COLOR_PAPER, 1),
            twoPlayer = true,
            random = Random(9),
        )
        repeat(2) { match.attack(Side.PLAYER, 0f, 1f, 0.95f) }
        assertFalse(match.finished)
        match.attack(Side.PLAYER, 0f, 1f, 0.95f)
        assertTrue(match.finished)
        assertEquals(Side.PLAYER, match.winner)
        assertEquals(3, match.playerScore)
    }

    @Test
    fun `AI는 뒤집을 수 있는 한 수를 찾아낸다`() {
        val match = SquareTtakjiMatch(
            surface = TtakjiSurface.MARU,
            playerSpec = TtakjiSpec(TtakjiMaterial.COLOR_PAPER, 1),
            aiSpec = TtakjiSpec(TtakjiMaterial.COLOR_PAPER, 1),
            aiSkill = 1f,
            random = Random(11),
        )
        val result = match.aiAttack()
        assertTrue(result.contact)
        assertTrue(result.flipped)
        assertEquals(1, match.aiScore)
        assertEquals(Side.PLAYER, match.turn)
    }

    @Test
    fun `약점 조준은 상대의 들뜬 날개 쪽을 향한다`() {
        val defender = TtakjiSpec(TtakjiMaterial.COLOR_PAPER, 1, weakEdge = 1)
        val aim = SquareTtakjiEngine.weakSpotAim(player, ai, defender, -1f)
        // 오른쪽 변을 노리므로 정면(0도)보다 오른쪽으로 기운다.
        assertTrue(aim > 0f)
    }

    @Test
    fun `bestStrike 는 뒤집는 수 중 가장 약한 힘을 고른다`() {
        val chosen = SquareTtakjiEngine.bestStrike(
            attackerPos = player,
            attacker = TtakjiSpec(TtakjiMaterial.COLOR_PAPER, 1),
            defenderPos = ai,
            defender = paper.copy(weakEdge = 2),
            defenderAngleDeg = 0f,
            surface = TtakjiSurface.DIRT,
            forwardSign = -1f,
            maxPower = 1f,
            random = Random(13),
            skill = 1f,
        )
        val result = SquareTtakjiEngine.resolve(
            player, TtakjiSpec(TtakjiMaterial.COLOR_PAPER, 1), ai, paper.copy(weakEdge = 2),
            0f, TtakjiSurface.DIRT, chosen, -1f,
        )
        assertTrue(result.flipped)
        assertTrue(chosen.power < 1f)
    }
}
