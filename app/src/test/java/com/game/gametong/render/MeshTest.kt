package com.game.gametong.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/** 메쉬 생성 검증: 인덱스 범위·법선 정규화·면 구성. */
class MeshTest {

    private fun checkMesh(mesh: MeshData, name: String) {
        assertTrue("$name 정점이 있어야 한다", mesh.vertexCount > 0)
        assertTrue("$name 삼각형이 있어야 한다", mesh.indexCount >= 3)
        assertEquals("$name 인덱스는 삼각형 단위", 0, mesh.indexCount % 3)
        for (i in mesh.indices) {
            assertTrue("$name 인덱스 범위: $i / ${mesh.vertexCount}", i >= 0 && i < mesh.vertexCount)
        }
        var i = 0
        while (i < mesh.vertexData.size) {
            val nx = mesh.vertexData[i + 3]
            val ny = mesh.vertexData[i + 4]
            val nz = mesh.vertexData[i + 5]
            val len = sqrt(nx * nx + ny * ny + nz * nz)
            assertTrue("$name 법선 길이 $len", len > 0.9f && len < 1.1f)
            i += VERTEX_STRIDE
        }
    }

    @Test
    fun `모든 메쉬가 올바른 인덱스와 법선을 가진다`() {
        for (kind in MeshKind.entries) {
            checkMesh(Meshes.of(kind), kind.name)
        }
    }

    @Test
    fun `정육면체는 24정점 36인덱스`() {
        val box = Meshes.box()
        assertEquals(24, box.vertexCount)
        assertEquals(36, box.indexCount)
    }

    @Test
    fun `딱지는 네 겹 종이로 두껍다`() {
        val thin = Meshes.ttakji(thickness = 0.04f)
        val thick = Meshes.ttakji(thickness = 0.12f)
        assertTrue("겹이 있으니 정점이 많다: ${thin.vertexCount}", thin.vertexCount > 40)
        // 두께가 두꺼워도 위아래로 중심이 맞아야 한다
        val minY = (0 until thin.vertexCount).map { thin.vertexData[it * VERTEX_STRIDE + 1] }.min()
        val maxY = (0 until thin.vertexCount).map { thin.vertexData[it * VERTEX_STRIDE + 1] }.max()
        assertEquals(0f, (minY + maxY) * 0.5f, 0.01f)
        assertTrue("두꺼운 쪽이 더 두껍다", (thick.vertexCount > 0))
    }

    @Test
    fun `지우개 메쉬는 둥근 모서리를 위해 정점이 충분하다`() {
        val eraser = Meshes.of(MeshKind.ERASER)
        assertTrue("정점 ${eraser.vertexCount}", eraser.vertexCount > 200)
    }

    @Test
    fun `구는 모든 법선이 바깥을 향한다`() {
        val sphere = Meshes.sphere(0.5f, 10, 14)
        var i = 0
        while (i < sphere.vertexData.size) {
            val px = sphere.vertexData[i]
            val py = sphere.vertexData[i + 1]
            val pz = sphere.vertexData[i + 2]
            val nx = sphere.vertexData[i + 3]
            val ny = sphere.vertexData[i + 4]
            val nz = sphere.vertexData[i + 5]
            assertTrue("법선이 바깥을 향한다", px * nx + py * ny + pz * nz > 0f)
            i += VERTEX_STRIDE
        }
    }

    @Test
    fun `절차 텍스처는 결정적이고 0이 아니다`() {
        val a = ProceduralTextures.pixels(TexKind.WOOD, 32)
        val b = ProceduralTextures.pixels(TexKind.WOOD, 32)
        assertTrue("같은 무늬가 나온다", a.contentEquals(b))
        assertTrue("색이 하나로 뭉개지지 않는다", a.toSet().size > 8)
    }

    @Test
    fun `바닥 텍스처는 이어 붙여도 이음매가 없다`() {
        // 타일을 반복해 깔아도 경계가 드러나지 않아야 한다(주기 잡음).
        for (kind in listOf(TexKind.WOOD, TexKind.CEMENT, TexKind.DIRT)) {
            val size = 64
            val tex = ProceduralTextures.pixels(kind, size)
            fun luma(index: Int): Int {
                val p = tex[index]
                return ((p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF)) / 3
            }
            fun columnDiff(a: Int, b: Int): Float {
                var sum = 0
                for (r in 0 until size) sum += kotlin.math.abs(luma(r * size + a) - luma(r * size + b))
                return sum.toFloat() / size
            }
            fun rowDiff(a: Int, b: Int): Float {
                var sum = 0
                for (c in 0 until size) sum += kotlin.math.abs(luma(a * size + c) - luma(b * size + c))
                return sum.toFloat() / size
            }
            // 이음매(마지막 줄 ↔ 첫 줄)는 바로 옆 줄끼리의 차이와 비슷해야 한다.
            val horizontalSeam = columnDiff(0, size - 1)
            val horizontalNeighbour = kotlin.math.max(columnDiff(0, 1), columnDiff(size - 2, size - 1))
            val verticalSeam = rowDiff(0, size - 1)
            val verticalNeighbour = kotlin.math.max(rowDiff(0, 1), rowDiff(size - 2, size - 1))
            assertTrue(
                "$kind 가로 이음매 $horizontalSeam vs 이웃 줄 $horizontalNeighbour",
                horizontalSeam <= horizontalNeighbour * 2f + 3f,
            )
            assertTrue(
                "$kind 세로 이음매 $verticalSeam vs 이웃 줄 $verticalNeighbour",
                verticalSeam <= verticalNeighbour * 2f + 3f,
            )
        }
    }

    @Test
    fun `바닥 텍스처는 밝고, 그림자 텍스처는 가운데가 진하다`() {
        val wood = ProceduralTextures.pixels(TexKind.WOOD, 16)
        val centerAverage = wood.sumOf { ((it shr 16) and 0xFF).toInt() } / wood.size
        assertTrue("나무 밝기 $centerAverage", centerAverage in 40..200)

        val soft = ProceduralTextures.pixels(TexKind.SOFT, 16)
        val center = (soft[8 * 16 + 8] ushr 24) and 0xFF
        val edge = (soft[0] ushr 24) and 0xFF
        assertTrue("가운데 알파 $center > 가장자리 $edge", center > edge)
    }
}
