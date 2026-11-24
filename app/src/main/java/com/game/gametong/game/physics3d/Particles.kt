package com.game.gametong.game.physics3d

import kotlin.math.abs
import kotlin.random.Random

/** 파티클 종류. 지우개 가루·흙먼지·분필가루·불티. */
enum class ParticleKind { CRUMB, DUST, CHALK, SPARK }

/** 바닥 위를 나는 작은 입자 하나. 좌표는 무대 좌표(1000 × 1500), 높이는 무대 단위. */
class Particle(
    var x: Float,
    var y: Float,
    var z: Float,
    var vx: Float,
    var vy: Float,
    var vz: Float,
    val kind: ParticleKind,
    var size: Float,
    var life: Float,
    val maxLife: Float,
    var spin: Float = 0f,
) {
    /** 0(다 사라짐) ~ 1(갓 생김). */
    val alpha: Float get() = (life / maxLife).coerceIn(0f, 1f)
    val alive: Boolean get() = life > 0f
}

/**
 * 지우개 가루·먼지 파티클 시스템.
 *
 * 물리와 같은 **고정 스텝**으로만 갱신하고, 개수는 [capacity] 로 제한한다
 * (충돌이 잦아도 프레임이 무너지지 않게). 난수는 시드로 고정해 같은 판이면 같은 연출이 나온다.
 */
class ParticleSystem(
    val capacity: Int = 180,
    seed: Int = 20261124,
) {
    private val random = Random(seed)
    private val pool = ArrayList<Particle>(capacity)

    val particles: List<Particle> get() = pool
    val size: Int get() = pool.size

    fun clear() = pool.clear()

    private fun add(p: Particle) {
        if (pool.size >= capacity) {
            // 가장 오래된(수명이 얼마 안 남은) 것을 밀어낸다.
            val oldest = pool.minByOrNull { it.life }
            if (oldest != null) pool.remove(oldest)
        }
        pool.add(p)
    }

    /**
     * 지우개 가루. 두 지우개가 부딪힌 자리에서 부스러기가 튄다.
     * [impact] 는 충돌 세기(무대 단위/초)로, 클수록 많이·멀리 튄다.
     */
    fun spawnCrumbs(x: Float, y: Float, z: Float, impact: Float, count: Int = -1) {
        val power = (impact / 700f).coerceIn(0.15f, 1.6f)
        val n = if (count >= 0) count else (4 + (power * 10f).toInt())
        repeat(n) {
            val angle = random.nextFloat() * 6.2831855f
            val speed = 60f + random.nextFloat() * 210f * power
            add(
                Particle(
                    x = x + (random.nextFloat() - 0.5f) * 22f,
                    y = y + (random.nextFloat() - 0.5f) * 22f,
                    z = z + random.nextFloat() * 10f,
                    vx = kotlin.math.cos(angle) * speed,
                    vy = kotlin.math.sin(angle) * speed,
                    vz = 90f + random.nextFloat() * 260f * power,
                    kind = ParticleKind.CRUMB,
                    size = 2.2f + random.nextFloat() * 3.4f,
                    life = 0.55f + random.nextFloat() * 0.65f,
                    maxLife = 1.2f,
                    spin = (random.nextFloat() - 0.5f) * 360f,
                )
            )
        }
    }

    /** 흙먼지·분필가루. 딱지가 바닥을 칠 때 크게 퍼진다. */
    fun spawnDust(
        x: Float,
        y: Float,
        z: Float = 0f,
        power: Float = 1f,
        count: Int = -1,
        kind: ParticleKind = ParticleKind.DUST,
    ) {
        val p = power.coerceIn(0.1f, 1.8f)
        val n = if (count >= 0) count else (5 + (p * 12f).toInt())
        repeat(n) {
            val angle = random.nextFloat() * 6.2831855f
            val speed = 40f + random.nextFloat() * 170f * p
            add(
                Particle(
                    x = x + (random.nextFloat() - 0.5f) * 40f,
                    y = y + (random.nextFloat() - 0.5f) * 40f,
                    z = z + random.nextFloat() * 8f,
                    vx = kotlin.math.cos(angle) * speed,
                    vy = kotlin.math.sin(angle) * speed,
                    vz = 40f + random.nextFloat() * 150f * p,
                    kind = kind,
                    size = 6f + random.nextFloat() * 16f * p,
                    life = 0.5f + random.nextFloat() * 0.9f,
                    maxLife = 1.4f,
                )
            )
        }
    }

    /** 구슬이 구멍에 들어갈 때 튀는 흙먼지. */
    fun spawnHoleBurst(x: Float, y: Float) {
        spawnDust(x, y, z = 0f, power = 1.3f, count = 14)
    }

    /** 고정 스텝으로 입자를 움직인다. */
    fun update(dt: Float, gravity: Float = 1600f) {
        if (pool.isEmpty()) return
        val iterator = pool.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.life -= dt
            if (p.life <= 0f) {
                iterator.remove()
                continue
            }
            p.vz -= gravity * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.z += p.vz * dt
            if (p.z <= 0f) {
                p.z = 0f
                // 바닥에 부딪히면 조금 튀고 옆으로 굴러간다.
                p.vz = if (abs(p.vz) > 60f) abs(p.vz) * 0.28f else 0f
                p.vx *= 0.62f
                p.vy *= 0.62f
                if (p.kind == ParticleKind.DUST) {
                    p.vx *= 0.3f
                    p.vy *= 0.3f
                }
            }
            val drag = if (p.kind == ParticleKind.DUST) 2.2f else 0.6f
            p.vx -= p.vx * drag * dt
            p.vy -= p.vy * drag * dt
            p.spin += dt * 90f
        }
    }
}
