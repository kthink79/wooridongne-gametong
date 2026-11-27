package com.game.gametong.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EraserWorldTest {

    private fun world(
        playerType: EraserType = EraserType.STANDARD,
        aiType: EraserType = EraserType.STANDARD,
    ) = EraserWorld(
        player = EraserBody(Side.PLAYER, playerType, Vec2(500f, 1040f), Vec2(500f, 1040f)),
        opponent = EraserBody(Side.AI, aiType, Vec2(500f, 460f), Vec2(500f, 460f)),
    )

    @Test
    fun `마찰로 결국 멈춘다`() {
        val w = world()
        w.flick(Side.PLAYER, Vec2(0f, -800f))
        val took = w.settle()
        assertTrue(took > 0f)
        assertTrue(took < Board.MAX_SIM_SECONDS)
        assertTrue(w.player.isResting())
        assertTrue(w.player.pos.y < 1040f)
    }

    @Test
    fun `가만히 두면 움직이지 않는다`() {
        val w = world()
        repeat(300) { w.step() }
        assertEquals(Vec2(500f, 1040f), w.player.pos)
        assertEquals(Vec2(500f, 460f), w.opponent.pos)
        assertTrue(w.settled)
    }

    @Test
    fun `책상 밖으로 나가면 떨어진 것으로 판정한다`() {
        val w = world()
        // 위쪽 상대를 지나치지 않도록 옆으로 날려 보낸다
        w.flick(Side.PLAYER, Vec2(2500f, 0f))
        w.settle()
        assertTrue(w.player.fallen)
        assertFalse(w.opponent.fallen)
        assertTrue(w.anyFallen)
    }

    @Test
    fun `세게 부딪히면 상대 지우개가 밀린다`() {
        val w = world()
        w.flick(Side.PLAYER, Vec2(0f, -1200f))
        w.settle()
        assertTrue("상대가 밀려야 한다: ${w.opponent.pos.y}", w.opponent.pos.y < 460f - 100f)
    }

    @Test
    fun `멈춘 뒤에도 두 지우개가 서로 파고들지 않는다`() {
        val w = world()
        w.flick(Side.PLAYER, Vec2(0f, -1100f))
        w.settle()
        if (!w.player.fallen && !w.opponent.fallen) {
            val dist = w.player.pos.distanceTo(w.opponent.pos)
            val contact = w.player.radius + w.opponent.radius
            assertTrue("dist=$dist contact=$contact", dist >= contact - 4f)
        }
    }

    @Test
    fun `수비 지우개는 고정된 동안 움직이지 않는다`() {
        val w = world()
        w.immovableSide = Side.AI
        w.flick(Side.PLAYER, Vec2(0f, -1000f))
        w.settle()
        assertEquals(Vec2(500f, 460f), w.opponent.pos)
        assertTrue(w.player.climbing)
    }

    // ── 3D 자세 · 표면 마찰 · 타격 위치 회전 ───────────────────────────

    @Test
    fun `표면이 미끄러울수록 지우개가 멀리 간다`() {
        fun restY(surface: DeskSurface): Float {
            val w = EraserWorld(
                player = EraserBody(Side.PLAYER, EraserType.STANDARD, Vec2(500f, 1300f), Vec2(500f, 1300f)),
                opponent = EraserBody(Side.AI, EraserType.STANDARD, Vec2(120f, 180f), Vec2(120f, 180f)),
                surface = surface,
            )
            w.flick(Side.PLAYER, Vec2(0f, -900f))
            w.settle()
            return w.player.pos.y
        }
        val coated = restY(DeskSurface.COATED)
        val wood = restY(DeskSurface.WOOD)
        val plywood = restY(DeskSurface.PLYWOOD)
        val dusty = restY(DeskSurface.DUSTY)
        assertTrue("코팅 $coated < 나무 $wood", coated < wood)
        assertTrue("나무 $wood < 합판 $plywood", wood < plywood)
        assertTrue("합판 $plywood < 먼지 $dusty", plywood < dusty)
    }

    @Test
    fun `표면이 거칠면 회전이 빨리 죽는다`() {
        fun spinLeft(surface: DeskSurface): Float {
            val w = EraserWorld(
                player = EraserBody(Side.PLAYER, EraserType.STANDARD, Vec2(500f, 1200f), Vec2(500f, 1200f)),
                opponent = EraserBody(Side.AI, EraserType.STANDARD, Vec2(120f, 180f), Vec2(120f, 180f)),
                surface = surface,
            )
            w.flick(Side.PLAYER, Vec2(0f, -900f), 0.9f)
            w.settle()
            return kotlin.math.abs(w.player.spin)
        }
        assertTrue(spinLeft(DeskSurface.COATED) > spinLeft(DeskSurface.DUSTY))
    }

    @Test
    fun `중심에서 벗어난 타격은 회전을 붙이고 궤적을 휜다`() {
        fun endX(offset: Float): Vec2 {
            val w = EraserWorld(
                player = EraserBody(Side.PLAYER, EraserType.STANDARD, Vec2(500f, 1250f), Vec2(500f, 1250f)),
                opponent = EraserBody(Side.AI, EraserType.STANDARD, Vec2(120f, 180f), Vec2(120f, 180f)),
            )
            w.flick(Side.PLAYER, Vec2(0f, -950f), offset)
            w.settle()
            return w.player.pos
        }
        val left = endX(-0.9f)
        val middle = endX(0f)
        val right = endX(0.9f)
        assertEquals("가운데를 때리면 곧게 간다", 500f, middle.x, 3f)
        assertTrue("왼끝을 때리면 왼쪽으로 휜다: ${left.x}", left.x < middle.x - 4f)
        assertTrue("오른끝을 때리면 오른쪽으로 휜다: ${right.x}", right.x > middle.x + 4f)
    }

    @Test
    fun `지우개는 넘어가며 전진한다`() {
        val w = world()
        w.flick(Side.PLAYER, Vec2(0f, -1200f))
        var reported = 0f
        var moves = 0
        while (!w.settled && moves < 900) {
            w.step()
            reported = maxOf(reported, w.player.tumble)
            moves++
        }
        // 지름의 절반을 갈 때마다 90도씩 넘어간다 — 한 번의 미끄러짐에 여러 번 넘어간다.
        assertTrue("텀블 각 $reported", reported > 90f)
        assertTrue("회전축은 진행 방향의 수직이다: ${w.player.tumbleAxis}", kotlin.math.abs(w.player.tumbleAxis.x) > 0.9f)
    }

    @Test
    fun `멈추면 바닥에 놓인 면이 반듯해진다`() {
        val w = world()
        w.flick(Side.PLAYER, Vec2(0f, -1200f))
        w.settle()
        val quarter = (w.player.tumble % 90f + 90f) % 90f
        assertTrue("남은 각 $quarter", quarter < 1.5f || quarter > 88.5f)
    }

    @Test
    fun `걸쳐 올라가면 높이와 기운 각이 생긴다`() {
        // 레슬링과 같은 간격(420)에서 올라탄다
        val w = EraserWorld(
            player = EraserBody(Side.PLAYER, EraserType.STANDARD, Vec2(500f, 960f), Vec2(500f, 960f)),
            opponent = EraserBody(Side.AI, EraserType.STANDARD, Vec2(500f, 540f), Vec2(500f, 540f)),
        )
        w.immovableSide = Side.AI
        w.flick(Side.PLAYER, Vec2(0f, -1000f))
        w.settle()
        assertTrue(w.player.perched)
        assertEquals(Side.AI, w.player.perchedOn)
        assertTrue("높이 ${w.player.z}", w.player.z > 5f)
        assertTrue("기운 각 ${w.player.lean}", w.player.lean > 1f)
        val overlap = w.overlapRatio(w.player, w.opponent)
        assertTrue("겹침 $overlap", overlap in 0.2f..0.8f)
        assertTrue(w.pinQualityFor(Side.PLAYER) > 0f)
    }

    @Test
    fun `빠져나오면 걸침이 풀린다`() {
        val w = EraserWorld(
            player = EraserBody(Side.PLAYER, EraserType.STANDARD, Vec2(500f, 960f), Vec2(500f, 960f)),
            opponent = EraserBody(Side.AI, EraserType.STANDARD, Vec2(500f, 540f), Vec2(500f, 540f)),
        )
        w.immovableSide = Side.AI
        w.flick(Side.PLAYER, Vec2(0f, -1000f))
        w.settle()
        w.slipOff(Side.PLAYER)
        assertEquals(null, w.player.perchedOn)
        assertEquals(0f, w.player.z, 0.001f)
        assertEquals(0f, w.pinQualityFor(Side.PLAYER), 0.0001f)
        w.settle()
        assertTrue(w.player.pos.distanceTo(w.opponent.pos) > w.player.radius * 0.85f)
    }

    @Test
    fun `표면 정보가 복사된 세계에도 그대로 따라온다`() {
        val w = EraserWorld(
            player = EraserBody(Side.PLAYER, EraserType.STANDARD, Vec2(500f, 1000f), Vec2(500f, 1000f)),
            opponent = EraserBody(Side.AI, EraserType.STANDARD, Vec2(500f, 500f), Vec2(500f, 500f)),
            surface = DeskSurface.PLYWOOD,
        )
        val sim = w.copy()
        assertEquals(DeskSurface.PLYWOOD, sim.surface)
        sim.flick(Side.PLAYER, Vec2(0f, -800f))
        sim.settle()
        assertTrue(sim.player.pos != w.player.pos)
        assertEquals(Vec2(500f, 1000f), w.player.pos)
    }

    @Test
    fun `겹침 비율은 한쪽이 떨어지면 0`() {
        val w = world()
        w.player.fallen = true
        assertEquals(0f, w.pinRatioFor(Side.PLAYER), 0.0001f)
    }

    // ── 세로 서기(tumble) · 세워진 지우개 위 올라타기 ─────────────────

    @Test
    fun `세워진 지우개는 멈추면 넘어져 눕는다`() {
        val w = world()
        w.player.tumble = 90f
        assertTrue("90도는 세워진 자세", w.player.standing)
        var guard = 0
        while (!w.settled && guard < 1200) {
            w.step()
            guard++
        }
        assertTrue("넘어지지 않고 서서 굳으면 안 된다: ${w.player.tumble}", guard < 1200)
        assertFalse("세워진 채로 남으면 안 된다", w.player.standing)
        val flat = w.player.tumblePose
        assertTrue("눕는 자세(180도 배수)여야 한다: ${w.player.tumble}", flat < 1f || flat > 179f)
    }

    @Test
    fun `세워진 지우개 위로는 올라탈 수 없다`() {
        // 세워진 지우개 바로 앞에서 밀어 올라타기를 시도한다(서 있는 동안에 닿게).
        val w = EraserWorld(
            player = EraserBody(Side.PLAYER, EraserType.STANDARD, Vec2(500f, 800f), Vec2(500f, 800f)),
            opponent = EraserBody(Side.AI, EraserType.STANDARD, Vec2(500f, 540f), Vec2(500f, 540f)),
        )
        w.immovableSide = Side.AI
        w.opponent.tumble = 90f
        w.flick(Side.PLAYER, Vec2(0f, -1000f))
        w.settle()
        assertNull("세워진 지우개 위에 걸쳐 앉으면 안 된다", w.player.perchedOn)
        assertEquals(0f, w.pinQualityFor(Side.PLAYER), 0.0001f)
    }

    @Test
    fun `세워진 지우개에 부딪히면 받침을 잃고 넘어진다`() {
        val w = EraserWorld(
            player = EraserBody(Side.PLAYER, EraserType.STANDARD, Vec2(500f, 800f), Vec2(500f, 800f)),
            opponent = EraserBody(Side.AI, EraserType.STANDARD, Vec2(500f, 540f), Vec2(500f, 540f)),
        )
        w.immovableSide = Side.AI
        w.opponent.tumble = 90f
        w.flick(Side.PLAYER, Vec2(0f, -1000f))
        w.settle()
        assertFalse("맞은 지우개는 넘어져 있어야 한다: ${w.opponent.tumble}", w.opponent.standing)
        assertTrue("부딪힌 세기가 남아 있어야 한다: ${w.lastImpact}", w.lastImpact > 100f)
    }
}
