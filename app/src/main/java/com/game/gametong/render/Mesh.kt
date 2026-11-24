package com.game.gametong.render

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 정점 하나의 구성: 위치(3) · 법선(3) · 텍스처 좌표(2).
 * 모든 메쉬는 이 형식 하나로 통일해 셰이더도 하나만 쓴다.
 */
const val VERTEX_STRIDE = 8

/** 그릴 수 있는 메쉬 종류. 렌더러가 종류별로 한 번만 GPU 버퍼를 만든다. */
enum class MeshKind {
    /** 단위 정육면체(중심 원점). */
    BOX,

    /** 네모 딱지: 네 겹 종이 + 접힌 윗면. */
    TTAKJI,

    /** 지우개 고무 몸통(둥근 모서리 직육면체). */
    ERASER,

    /** 지우개 종이 슬리브(몸통을 감싸는 띠). */
    SLEEVE,

    /** 구(유리구슬·왕구슬). */
    SPHERE,

    /** 바닥에 눕힌 원판(그림자·구멍 테두리). */
    DISC,

    /** 고리(분필 원·조준 링). */
    RING,

    /** 원기둥(연필·장애물). */
    CYLINDER,

    /** 바닥 평면. */
    PLANE,

    /** 화면을 향하는 사각형(파티클). */
    QUAD,
}

/** GPU 에 넘길 메쉬 데이터. */
class MeshData(val vertexData: FloatArray, val indices: ShortArray) {
    val vertexCount: Int get() = vertexData.size / VERTEX_STRIDE
    val indexCount: Int get() = indices.size
}

/** 메쉬를 조립하는 작은 빌더. 정점·법선·UV 를 차례로 쌓는다. */
private class MeshBuilder {
    private val vertices = ArrayList<Float>(1024)
    private val indices = ArrayList<Short>(512)

    val base: Int get() = vertices.size / VERTEX_STRIDE

    fun vertex(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, u: Float, v: Float): Int {
        vertices.add(x)
        vertices.add(y)
        vertices.add(z)
        vertices.add(nx)
        vertices.add(ny)
        vertices.add(nz)
        vertices.add(u)
        vertices.add(v)
        return base - 1
    }

    fun tri(a: Int, b: Int, c: Int) {
        indices.add(a.toShort())
        indices.add(b.toShort())
        indices.add(c.toShort())
    }

    fun quad(a: Int, b: Int, c: Int, d: Int) {
        tri(a, b, c)
        tri(a, c, d)
    }

    fun build(): MeshData = MeshData(vertices.toFloatArray(), indices.toShortArray())
}

/** 메쉬 생성기 모음. 모두 원점 중심·단위 크기로 만들어 두고, 배치할 때 행렬로 늘린다. */
object Meshes {

    /** 중심이 원점인 정육면체(한 변 1). 면마다 법선이 따로 있어 모서리가 각지게 보인다. */
    fun box(): MeshData {
        val b = MeshBuilder()
        val faces = listOf(
            // (법선, u축, v축)
            Triple(floatArrayOf(0f, 0f, 1f), floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 1f, 0f)),
            Triple(floatArrayOf(0f, 0f, -1f), floatArrayOf(-1f, 0f, 0f), floatArrayOf(0f, 1f, 0f)),
            Triple(floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 0f, -1f), floatArrayOf(0f, 1f, 0f)),
            Triple(floatArrayOf(-1f, 0f, 0f), floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 1f, 0f)),
            Triple(floatArrayOf(0f, 1f, 0f), floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 0f, -1f)),
            Triple(floatArrayOf(0f, -1f, 0f), floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 0f, 1f)),
        )
        for ((n, u, v) in faces) {
            val start = b.base
            val half = 0.5f
            for ((su, sv) in listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f)) {
                val x = n[0] * half + u[0] * su * half + v[0] * sv * half
                val y = n[1] * half + u[1] * su * half + v[1] * sv * half
                val z = n[2] * half + u[2] * su * half + v[2] * sv * half
                b.vertex(x, y, z, n[0], n[1], n[2], (su + 1f) * 0.5f, (sv + 1f) * 0.5f)
            }
            b.quad(start, start + 1, start + 2, start + 3)
        }
        return b.build()
    }

    /**
     * 초타원체(superellipsoid). 지수 [e] 가 1이면 구, 0에 가까울수록 모서리가 둥근 상자다.
     * 지우개 고무처럼 "네모나지만 모서리가 부드러운" 덩어리에 쓴다.
     */
    fun superellipsoid(a: Float, b: Float, c: Float, e: Float, nu: Int = 20, nv: Int = 28): MeshData {
        val mb = MeshBuilder()
        val grid = Array(nu + 1) { IntArray(nv + 1) }
        fun sp(x: Float, p: Float): Float =
            if (x == 0f) 0f else sign(x) * abs(x).pow(p)

        for (i in 0..nu) {
            val u = -PI.toFloat() / 2f + PI.toFloat() * i / nu
            val cu = cos(u)
            val su = sin(u)
            for (j in 0..nv) {
                val v = -PI.toFloat() + 2f * PI.toFloat() * j / nv
                val cv = cos(v)
                val sv = sin(v)
                val x = a * sp(cu, e) * sp(cv, e)
                val y = b * sp(su, e)
                val z = c * sp(cu, e) * sp(sv, e)
                // 법선: 두 방향 접선의 외적(수치 미분)
                val du = 0.01f
                val u2 = (u + du).coerceAtMost(PI.toFloat() / 2f - 1e-4f)
                val cu2 = cos(u2)
                val su2 = sin(u2)
                val x2 = a * sp(cu2, e) * sp(cv, e)
                val y2 = b * sp(su2, e)
                val z2 = c * sp(cu2, e) * sp(sv, e)
                val v2 = v + 0.01f
                val cv2 = cos(v2)
                val sv2 = sin(v2)
                val x3 = a * sp(cu, e) * sp(cv2, e)
                val y3 = b * sp(su, e)
                val z3 = c * sp(cu, e) * sp(sv2, e)
                var nx = (y2 - y) * (z3 - z) - (z2 - z) * (y3 - y)
                var ny = (z2 - z) * (x3 - x) - (x2 - x) * (z3 - z)
                var nz = (x2 - x) * (y3 - y) - (y2 - y) * (x3 - x)
                val len = sqrt(nx * nx + ny * ny + nz * nz)
                if (len > 1e-8f) {
                    nx /= len
                    ny /= len
                    nz /= len
                    // 안쪽을 향한 법선은 뒤집는다(극점에서 수치 미분이 뒤집히는 경우)
                    if (nx * x + ny * y + nz * z < 0f) {
                        nx = -nx
                        ny = -ny
                        nz = -nz
                    }
                } else {
                    ny = if (y >= 0f) 1f else -1f
                }
                grid[i][j] = mb.vertex(x, y, z, nx, ny, nz, j.toFloat() / nv, i.toFloat() / nu)
            }
        }
        for (i in 0 until nu) {
            for (j in 0 until nv) {
                val a0 = grid[i][j]
                val a1 = grid[i][j + 1]
                val b0 = grid[i + 1][j]
                val b1 = grid[i + 1][j + 1]
                mb.quad(a0, b0, b1, a1)
            }
        }
        return mb.build()
    }

    fun sphere(radius: Float = 0.5f, nu: Int = 22, nv: Int = 30): MeshData =
        superellipsoid(radius, radius, radius, 1f, nu, nv)

    /** XZ 평면에 눕힌 원판. 위를 향한다. */
    fun disc(radius: Float = 0.5f, segments: Int = 40): MeshData {
        val b = MeshBuilder()
        val center = b.vertex(0f, 0f, 0f, 0f, 1f, 0f, 0.5f, 0.5f)
        val ring = IntArray(segments + 1)
        for (i in 0..segments) {
            val a = 2.0 * PI * i / segments
            val x = (cos(a) * radius).toFloat()
            val z = (sin(a) * radius).toFloat()
            ring[i] = b.vertex(x, 0f, z, 0f, 1f, 0f, 0.5f + x / (radius * 2f), 0.5f + z / (radius * 2f))
        }
        for (i in 0 until segments) b.tri(center, ring[i + 1], ring[i])
        return b.build()
    }

    /** 고리(안쪽 반지름 ~ 바깥 반지름). 분필 원·조준 링에 쓴다. */
    fun ring(inner: Float = 0.42f, outer: Float = 0.5f, segments: Int = 56): MeshData {
        val b = MeshBuilder()
        val innerIdx = IntArray(segments + 1)
        val outerIdx = IntArray(segments + 1)
        for (i in 0..segments) {
            val a = 2.0 * PI * i / segments
            val cx = cos(a).toFloat()
            val cz = sin(a).toFloat()
            innerIdx[i] = b.vertex(cx * inner, 0f, cz * inner, 0f, 1f, 0f, 0.5f + cx * 0.5f, 0.5f + cz * 0.5f)
            outerIdx[i] = b.vertex(cx * outer, 0f, cz * outer, 0f, 1f, 0f, 0.5f + cx * 0.5f, 0.5f + cz * 0.5f)
        }
        for (i in 0 until segments) {
            b.quad(innerIdx[i], innerIdx[i + 1], outerIdx[i + 1], outerIdx[i])
        }
        return b.build()
    }

    /** 원기둥. 아래가 y=0, 위가 y=1. */
    fun cylinder(radius: Float = 0.5f, segments: Int = 24): MeshData {
        val b = MeshBuilder()
        val sideTop = IntArray(segments + 1)
        val sideBottom = IntArray(segments + 1)
        for (i in 0..segments) {
            val a = 2.0 * PI * i / segments
            val x = (cos(a) * radius).toFloat()
            val z = (sin(a) * radius).toFloat()
            val nx = cos(a).toFloat()
            val nz = sin(a).toFloat()
            sideBottom[i] = b.vertex(x, 0f, z, nx, 0f, nz, i.toFloat() / segments, 1f)
            sideTop[i] = b.vertex(x, 1f, z, nx, 0f, nz, i.toFloat() / segments, 0f)
        }
        for (i in 0 until segments) {
            b.quad(sideBottom[i], sideTop[i], sideTop[i + 1], sideBottom[i + 1])
        }
        val topCenter = b.vertex(0f, 1f, 0f, 0f, 1f, 0f, 0.5f, 0.5f)
        val bottomCenter = b.vertex(0f, 0f, 0f, 0f, -1f, 0f, 0.5f, 0.5f)
        for (i in 0..segments) {
            val a = 2.0 * PI * i / segments
            val x = (cos(a) * radius).toFloat()
            val z = (sin(a) * radius).toFloat()
            val topIdx = b.vertex(x, 1f, z, 0f, 1f, 0f, 0.5f + x, 0.5f + z)
            val bottomIdx = b.vertex(x, 0f, z, 0f, -1f, 0f, 0.5f + x, 0.5f + z)
            if (i > 0) {
                b.tri(topCenter, topIdx - 2, topIdx)
                b.tri(bottomCenter, bottomIdx, bottomIdx - 2)
            }
        }
        return b.build()
    }

    /** XZ 평면(한 변 1, 중심 원점). 바닥에 쓴다. */
    fun plane(): MeshData {
        val b = MeshBuilder()
        val a = b.vertex(-0.5f, 0f, -0.5f, 0f, 1f, 0f, 0f, 0f)
        val c = b.vertex(0.5f, 0f, -0.5f, 0f, 1f, 0f, 1f, 0f)
        val d = b.vertex(0.5f, 0f, 0.5f, 0f, 1f, 0f, 1f, 1f)
        val e = b.vertex(-0.5f, 0f, 0.5f, 0f, 1f, 0f, 0f, 1f)
        b.quad(a, e, d, c)
        return b.build()
    }

    /** 화면을 향하는 사각형(중심 원점, 한 변 1). 파티클·그림자에 쓴다. */
    fun quad(): MeshData {
        val b = MeshBuilder()
        val a = b.vertex(-0.5f, -0.5f, 0f, 0f, 0f, 1f, 0f, 1f)
        val c = b.vertex(0.5f, -0.5f, 0f, 0f, 0f, 1f, 1f, 1f)
        val d = b.vertex(0.5f, 0.5f, 0f, 0f, 0f, 1f, 1f, 0f)
        val e = b.vertex(-0.5f, 0.5f, 0f, 0f, 0f, 1f, 0f, 0f)
        b.quad(a, c, d, e)
        return b.build()
    }

    /**
     * 네모 딱지 메쉬. 1×1 크기, 두께 [thickness](기본 0.055)의 네 겹 종이.
     *
     * - 아래 세 겹은 살짝씩 작아지며 층이 보이는 종이 단면을 만든다.
     * - 윗면은 가운데가 살짝 솟은 **접힌 네 조각**이라, 빛을 받으면 접힘선이 드러난다.
     */
    fun ttakji(thickness: Float = 0.055f, layers: Int = 4): MeshData {
        val b = MeshBuilder()
        val layerH = thickness / layers
        // 중심을 원점에 맞춘다(바닥에 놓을 때 위치 계산이 간단해진다).
        val yShift = -thickness * 0.5f
        // 아래 겹들
        for (layer in 0 until layers - 1) {
            val size = 1f - layer * 0.02f
            val h = size * 0.5f
            val y0 = layer * layerH + yShift
            val y1 = y0 + layerH
            addSlab(b, h, y0, y1)
        }
        // 맨 윗면: 가운데가 살짝 솟은 접힌 네 조각
        val topY = (layers - 1) * layerH + yShift
        val topSize = 1f - (layers - 1) * 0.02f
        val h = topSize * 0.5f
        val fold = layerH * 0.9f
        val center = b.vertex(0f, topY + fold, 0f, 0f, 1f, 0f, 0.5f, 0.5f)
        val corners = listOf(
            floatArrayOf(-h, topY, -h), floatArrayOf(h, topY, -h),
            floatArrayOf(h, topY, h), floatArrayOf(-h, topY, h),
        )
        val midPoints = listOf(
            floatArrayOf(0f, topY, -h), floatArrayOf(h, topY, 0f),
            floatArrayOf(0f, topY, h), floatArrayOf(-h, topY, 0f),
        )
        val cornerIdx = corners.map { c ->
            // 바깥으로 살짝 기운 법선
            val nx = c[0] / h * 0.35f
            val nz = c[2] / h * 0.35f
            val len = sqrt(nx * nx + 1f + nz * nz)
            b.vertex(c[0], c[1], c[2], nx / len, 1f / len, nz / len, 0.5f + c[0] * 0.5f, 0.5f + c[2] * 0.5f)
        }
        val midIdx = midPoints.map { m ->
            val nx = m[0] / h * 0.2f
            val nz = m[2] / h * 0.2f
            val len = sqrt(nx * nx + 1f + nz * nz)
            b.vertex(m[0], m[1], m[2], nx / len, 1f / len, nz / len, 0.5f + m[0] * 0.5f, 0.5f + m[2] * 0.5f)
        }
        for (i in 0 until 4) {
            val next = (i + 1) % 4
            b.tri(center, cornerIdx[i], midIdx[i])
            b.tri(center, midIdx[i], cornerIdx[next])
        }
        // 옆면
        val sideBottom = IntArray(4)
        val sideTop = IntArray(4)
        for (i in 0 until 4) {
            val c = corners[i]
            val rawX = c[0] / h
            val rawZ = c[2] / h
            val nLen = sqrt(rawX * rawX + rawZ * rawZ).coerceAtLeast(1e-5f)
            val nx = rawX / nLen
            val nz = rawZ / nLen
            sideBottom[i] = b.vertex(c[0], yShift, c[2], nx, 0f, nz, i.toFloat() / 4f, 1f)
            sideTop[i] = b.vertex(c[0], topY, c[2], nx, 0f, nz, i.toFloat() / 4f, 0f)
        }
        for (i in 0 until 4) {
            val next = (i + 1) % 4
            b.quad(sideBottom[i], sideBottom[next], sideTop[next], sideTop[i])
        }
        return b.build()
    }

    private fun addSlab(b: MeshBuilder, h: Float, y0: Float, y1: Float) {
        val corners = listOf(
            floatArrayOf(-h, -h), floatArrayOf(h, -h), floatArrayOf(h, h), floatArrayOf(-h, h),
        )
        // 윗면
        val top = corners.map { c ->
            b.vertex(c[0], y1, c[1], 0f, 1f, 0f, 0.5f + c[0] * 0.5f, 0.5f + c[1] * 0.5f)
        }
        b.quad(top[0], top[3], top[2], top[1])
        // 옆면
        for (i in 0 until 4) {
            val next = (i + 1) % 4
            val c0 = corners[i]
            val c1 = corners[next]
            val nRaw = c0[0] / h
            val zRaw = c0[1] / h
            val nLen = sqrt(nRaw * nRaw + zRaw * zRaw).coerceAtLeast(1e-5f)
            val nx = nRaw / nLen
            val nz = zRaw / nLen
            val a = b.vertex(c0[0], y0, c0[1], nx, 0f, nz, i.toFloat() / 4f, 1f)
            val bb = b.vertex(c1[0], y0, c1[1], nx, 0f, nz, (i + 1).toFloat() / 4f, 1f)
            val c = b.vertex(c1[0], y1, c1[1], nx, 0f, nz, (i + 1).toFloat() / 4f, 0f)
            val d = b.vertex(c0[0], y1, c0[1], nx, 0f, nz, i.toFloat() / 4f, 0f)
            b.quad(a, bb, c, d)
        }
    }

    /** 종류별로 한 번만 만든다. */
    private val cache = HashMap<MeshKind, MeshData>()

    fun of(kind: MeshKind): MeshData = cache.getOrPut(kind) {
        when (kind) {
            MeshKind.BOX -> box()
            MeshKind.TTAKJI -> ttakji()
            MeshKind.ERASER -> superellipsoid(0.5f, 0.32f, 0.5f, 0.38f, 18, 26)
            MeshKind.SLEEVE -> superellipsoid(0.5f, 0.34f, 0.52f, 0.42f, 14, 20)
            MeshKind.SPHERE -> sphere()
            MeshKind.DISC -> disc(0.5f, 48)
            MeshKind.RING -> ring(0.80f, 1.0f, 64)
            MeshKind.CYLINDER -> cylinder(0.5f, 24)
            MeshKind.PLANE -> plane()
            MeshKind.QUAD -> quad()
        }
    }
}
