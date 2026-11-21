package com.game.gametong.game

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class AiMode { ALKKAEGI, WRESTLING }

/**
 * 후보 발사를 실제 물리로 시뮬레이션해 고르는 AI.
 * - 레슬링: 올라타서 겹침(핀) 비율이 가장 큰 발사를 고른다.
 * - 알까기: 상대를 책상 모서리로 몰아내는 발사를 고른다(내가 떨어지면 크게 감점).
 * accuracy(0~1)가 낮을수록 조준에 흔들림이 섞인다.
 */
object EraserAi {

    data class Candidate(val velocity: Vec2, val score: Float)

    fun candidates(world: EraserWorld, attacker: Side): List<Vec2> {
        val att = world.bodyOf(attacker)
        val def = world.bodyOf(attacker.other())
        val baseAngle = Math.toDegrees(
            atan2((def.pos.y - att.pos.y).toDouble(), (def.pos.x - att.pos.x).toDouble())
        ).toFloat()
        val speeds = floatArrayOf(0.30f, 0.45f, 0.60f, 0.75f, 0.90f, 1.0f)
        val out = ArrayList<Vec2>(13 * speeds.size)
        var delta = -36
        while (delta <= 36) {
            for (speed in speeds) {
                out += Vec2.polar(
                    baseAngle + delta,
                    Board.MAX_FLICK_SPEED * speed * att.type.speedMul,
                )
            }
            delta += 6
        }
        return out
    }

    fun rank(world: EraserWorld, attacker: Side, mode: AiMode): List<Candidate> {
        val att = world.bodyOf(attacker)
        return candidates(world, attacker).map { velocity ->
            val sim = world.copy()
            sim.immovableSide = if (mode == AiMode.WRESTLING) attacker.other() else null
            sim.flick(attacker, velocity)
            sim.settle()
            val simAtt = sim.bodyOf(attacker)
            val simDef = sim.bodyOf(attacker.other())
            val score = when {
                simAtt.fallen -> -1000f
                mode == AiMode.WRESTLING -> {
                    val ratio = if (simDef.fallen) 0f else sim.overlapRatio(simAtt, simDef)
                    ratio * 100f + if (ratio >= Board.PIN_OVERLAP) 40f else 0f
                }
                simDef.fallen -> 1000f
                else -> -edgeDistance(simDef.pos) - 0.15f * edgeDistance(simAtt.pos)
            }
            Candidate(velocity, score)
        }.sortedByDescending { it.score }
    }

    fun choose(
        world: EraserWorld,
        attacker: Side,
        mode: AiMode,
        accuracy: Float = 0.82f,
        random: Random = Random(0),
    ): Vec2 {
        val ranked = rank(world, attacker, mode)
        if (ranked.isEmpty()) return Vec2.ZERO
        // 실력이 낮으면 상위 후보 중 무작위, 조준에도 흔들림을 준다.
        val pickWindow = ((1f - accuracy) * 8f).toInt().coerceIn(0, 6)
        val picked = if (pickWindow > 0 && random.nextFloat() > accuracy) {
            ranked[random.nextInt(pickWindow + 1)]
        } else {
            ranked.first()
        }
        val base = picked.velocity
        val angleNoise = (random.nextFloat() - 0.5f) * (1f - accuracy) * 26f
        val powerNoise = 1f - (1f - accuracy) * 0.25f * random.nextFloat()
        return rotate(base, angleNoise) * powerNoise.coerceIn(0.7f, 1f)
    }

    /** 조준 보조: 이 발사로 지우개가 어디에 멈출지 미리 계산한다. */
    fun predictRest(world: EraserWorld, side: Side, velocity: Vec2): Vec2 {
        val sim = world.copy()
        sim.flick(side, velocity)
        sim.settle()
        val body = sim.bodyOf(side)
        return if (body.fallen) Vec2(-1f, -1f) else body.pos
    }

    private fun edgeDistance(pos: Vec2): Float =
        min(min(pos.x, Board.WIDTH - pos.x), min(pos.y, Board.HEIGHT - pos.y))

    private fun rotate(v: Vec2, degrees: Float): Vec2 {
        if (abs(degrees) < 0.01f) return v
        val rad = Math.toRadians(degrees.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        return Vec2(v.x * c - v.y * s, v.x * s + v.y * c)
    }
}
