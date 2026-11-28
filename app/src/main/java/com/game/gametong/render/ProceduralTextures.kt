package com.game.gametong.render

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** 절차적으로 그리는 무늬 종류. 앱에 이미지를 넣지 않고 코드로 만든다(용량 0, 해상도 자유). */
enum class TexKind {
    NONE,
    /** 학교 책상 나뭇결. */
    WOOD,
    /** 운동장 시멘트(분필이 잘 묻는 거친 바닥). */
    CEMENT,
    /** 노을빛 운동장 흙바닥. */
    DIRT,
    /** 종이 결(딱지·슬리브). 색은 재질 색으로 곱한다. */
    PAPER,
    /** 무광 고무(지우개 몸통). */
    RUBBER,
    /** 유리구슬 속 회오리 무늬. */
    MARBLE,
    /** 부드러운 원형(그림자·먼지). */
    SOFT,
    /** 흙먼지. */
    DUST,
    /** 지우개 가루. */
    CRUMB,
    /** 분필 가루. */
    CHALK,
    /** 가이드 점의 빛무리. */
    GLOW,

    /**
     * 사진에서 뽑은 원목 책상 결. 앱에 넣은 교실 책상 사진(`desk_classroom_bg.jpg`)의
     * 나뭇결을 평탄화해 만든 텍스처(`desk_wood.jpg`)를 그대로 쓴다. 절차적 무늬가 아니라
     * 사진이라 결이 사실적이고, 3D 책상 판이 배경 사진과 같은 나무로 이어진다.
     */
    PHOTO_WOOD,

    /** 교실 배경 사진(창가·의자·공책, 얕은 심도). 하늘 대신 화면 뒤에 깔린다. */
    PHOTO_ROOM,
}

/**
 * 절차적 텍스처 생성기.
 *
 * 모든 무늬는 **시드가 고정된 주기 잡음**으로 만든다. 잡음의 격자를 타일 크기에 맞춰 감아
 * 이어 붙이므로, 바닥에 여러 번 반복해 깔아도 이음매가 보이지 않는다.
 * 좌표는 0~1 로 정규화해 두어 어떤 해상도로 구워도 같은 그림이 나온다.
 */
object ProceduralTextures {

    const val DEFAULT_SIZE = 256

    private const val TAU = (PI * 2.0).toFloat()

    /** 종류별 픽셀(ARGB). 투명도가 있는 무늬는 알파도 채운다. */
    fun pixels(kind: TexKind, size: Int = DEFAULT_SIZE): IntArray {
        val out = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val u = x.toFloat() / size
                val v = y.toFloat() / size
                out[y * size + x] = when (kind) {
                    TexKind.NONE -> argb(255, 255, 255, 255)
                    TexKind.PHOTO_WOOD, TexKind.PHOTO_ROOM -> argb(255, 255, 255, 255)
                    TexKind.WOOD -> wood(u, v)
                    TexKind.CEMENT -> cement(u, v)
                    TexKind.DIRT -> dirt(u, v)
                    TexKind.PAPER -> paper(u, v)
                    TexKind.RUBBER -> rubber(u, v)
                    TexKind.MARBLE -> marble(u, v)
                    TexKind.SOFT -> soft(u, v)
                    TexKind.DUST -> dust(u, v)
                    TexKind.CRUMB -> crumb(u, v)
                    TexKind.CHALK -> chalk(u, v)
                    TexKind.GLOW -> glow(u, v)
                }
            }
        }
        return out
    }

    // ── 무늬 ────────────────────────────────────────────────────

    /** 나뭇결: 결을 따라 흐르는 물결 + 나이테 + 미세 기공. */
    private fun wood(u: Float, v: Float): Int {
        // 결은 v 방향으로 흐른다(책상 앞뒤). 모든 주파수는 정수라 이음매가 없다.
        val warp = pnoise(u * 3f, v * 1f, 3, 1) * 0.35f + sin((u * 9f + v * 2f) * TAU) * 0.05f
        val ring = (v * 26f + warp * 6f) % 1f
        val line = smooth(0.0f, 0.16f, abs(ring - 0.5f) * 2f)
        val fine = pnoise(u * 40f, v * 220f, 40, 220) * 0.12f
        val knot = if (pnoise(u * 2f, v * 2f, 2, 2) > 0.87f) 0.10f else 0f
        val shade = (0.70f + 0.22f * line + fine - knot).coerceIn(0.35f, 1f)
        return argb(255, (146 * shade).toInt(), (92 * shade).toInt(), (48 * shade).toInt())
    }

    /** 시멘트: 회색 얼룩 + 자잘한 자갈 + 가는 금. */
    private fun cement(u: Float, v: Float): Int {
        val base = 0.60f + pnoise(u * 6f, v * 6f, 6, 6) * 0.18f + pnoise(u * 26f, v * 26f, 26, 26) * 0.10f
        val grit = if (pnoise(u * 90f, v * 90f, 90, 90) > 0.88f) 0.12f else 0f
        val crackSeed = pnoise(u * 3f, v * 3f, 3, 3)
        val crack = if (abs(crackSeed - 0.5f) < 0.010f) -0.15f else 0f
        val s = (base + grit + crack).coerceIn(0.3f, 1f)
        return argb(255, (178 * s).toInt(), (172 * s).toInt(), (158 * s).toInt())
    }

    /** 흙바닥: 마른 흙 + 자갈 + 밟힌 자국. */
    private fun dirt(u: Float, v: Float): Int {
        val blot = pnoise(u * 4f, v * 4f, 4, 4)
        val fine = pnoise(u * 55f, v * 55f, 55, 55)
        val pebble = if (pnoise(u * 14f, v * 14f, 14, 14) > 0.80f) 0.18f else 0f
        val s = (0.54f + blot * 0.26f + fine * 0.14f - pebble * 0.5f).coerceIn(0.25f, 1f)
        return argb(
            255,
            (170 * s + pebble * 42).toInt().coerceIn(0, 255),
            (120 * s + pebble * 30).toInt().coerceIn(0, 255),
            (74 * s).toInt().coerceIn(0, 255),
        )
    }

    /** 종이: 아주 옅은 섬유 결(색은 재질 색으로 곱한다). */
    private fun paper(u: Float, v: Float): Int {
        val fiber = pnoise(u * 70f, v * 70f, 70, 70) * 0.09f + pnoise(u * 180f, v * 12f, 180, 12) * 0.06f
        val speck = if (pnoise(u * 120f, v * 120f, 120, 120) > 0.93f) 0.07f else 0f
        val s = (0.95f + fiber - speck).coerceIn(0.7f, 1f)
        val c = (255 * s).toInt()
        return argb(255, c, c, c)
    }

    /** 고무: 무광 크림색 + 미세 기공. */
    private fun rubber(u: Float, v: Float): Int {
        val pore = pnoise(u * 130f, v * 130f, 130, 130) * 0.09f
        val wear = pnoise(u * 8f, v * 8f, 8, 8) * 0.06f
        val s = (0.91f + wear - pore).coerceIn(0.6f, 1f)
        val c = (250 * s).toInt()
        return argb(255, c, (c * 0.985f).toInt(), (c * 0.94f).toInt())
    }

    /** 유리구슬 속 회오리: 극좌표로 감기는 띠 + 흰 코어. */
    private fun marble(u: Float, v: Float): Int {
        val dx = u - 0.5f
        val dy = v - 0.5f
        val r = sqrt(dx * dx + dy * dy)
        val a = atan2(dy.toDouble(), dx.toDouble()).toFloat()
        val spiral = sin((a * 3f + r * 24f).toDouble()).toFloat()
        val band = smooth(0.15f, 0.55f, spiral)
        val core = smooth(0.45f, 0.12f, r)
        val s = (0.35f + band * 0.55f + core * 0.25f).coerceIn(0f, 1f)
        val c = (255 * s).toInt()
        val alpha = (120 + band * 110 + core * 40).toInt().coerceIn(0, 255)
        return argb(alpha, c, c, c)
    }

    private fun soft(u: Float, v: Float): Int {
        val dx = u - 0.5f
        val dy = v - 0.5f
        val r = sqrt(dx * dx + dy * dy) * 2f
        val a = 1f - smooth(0.15f, 1f, r)
        return argb((a * 255).toInt().coerceIn(0, 255), 255, 255, 255)
    }

    private fun dust(u: Float, v: Float): Int {
        val dx = u - 0.5f
        val dy = v - 0.5f
        val r = sqrt(dx * dx + dy * dy) * 2f
        val a = (1f - smooth(0.0f, 1f, r)) * (0.75f + pnoise(u * 30f, v * 30f, 30, 30) * 0.35f)
        return argb((a * 200).toInt().coerceIn(0, 255), 255, 250, 240)
    }

    private fun crumb(u: Float, v: Float): Int {
        val dx = u - 0.5f
        val dy = v - 0.5f
        val wob = 0.32f + pnoise(u * 8f, v * 8f, 8, 8) * 0.16f
        val d = sqrt(dx * dx + dy * dy)
        val a = if (d < wob) 1f else (1f - smooth(wob, wob + 0.10f, d)).coerceIn(0f, 1f)
        return argb((a * 255).toInt(), 252, 246, 232)
    }

    private fun chalk(u: Float, v: Float): Int {
        val g = 0.80f + pnoise(u * 60f, v * 60f, 60, 60) * 0.20f
        return argb(255, (255 * g).toInt(), (255 * g).toInt(), (250 * g).toInt())
    }

    private fun glow(u: Float, v: Float): Int {
        val dx = u - 0.5f
        val dy = v - 0.5f
        val r = sqrt(dx * dx + dy * dy) * 2f
        val a = (1f - smooth(0f, 0.85f, r)).pow(1.6f)
        return argb((a * 255).toInt().coerceIn(0, 255), 255, 246, 214)
    }

    // ── 도구 ────────────────────────────────────────────────────

    private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        ((a.coerceIn(0, 255)) shl 24) or
            ((r.coerceIn(0, 255)) shl 16) or
            ((g.coerceIn(0, 255)) shl 8) or
            (b.coerceIn(0, 255))

    /**
     * 주기 잡음(값 잡음). 격자를 [px]×[py] 로 감아 두어 텍스처를 이어 붙여도 이음매가 없다.
     * [px]·[py] 는 주파수(정수)와 같게 준다.
     */
    fun pnoise(x: Float, y: Float, px: Int, py: Int): Float {
        val xi = floor(x).toInt()
        val yi = floor(y).toInt()
        val xf = x - xi
        val yf = y - yi
        fun hash(a: Int, b: Int): Float {
            val aa = if (px > 0) ((a % px) + px) % px else a
            val bb = if (py > 0) ((b % py) + py) % py else b
            var h = aa * 374761393 + bb * 668265263
            h = (h xor (h shr 13)) * 1274126177
            h = h xor (h shr 16)
            return ((h and 0x7FFFFFFF) % 100000).toFloat() / 100000f
        }
        val v00 = hash(xi, yi)
        val v10 = hash(xi + 1, yi)
        val v01 = hash(xi, yi + 1)
        val v11 = hash(xi + 1, yi + 1)
        val sx = xf * xf * (3f - 2f * xf)
        val sy = yf * yf * (3f - 2f * yf)
        val a = v00 + (v10 - v00) * sx
        val b = v01 + (v11 - v01) * sx
        return a + (b - a) * sy
    }

    /** 주기 없는 잡음(작은 장식용). */
    fun noise(x: Float, y: Float): Float = pnoise(x, y, 0, 0)

    private fun smooth(edge0: Float, edge1: Float, x: Float): Float {
        if (abs(edge1 - edge0) < 1e-6f) return if (x < edge0) 0f else 1f
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
