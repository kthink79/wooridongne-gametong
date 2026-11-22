package com.game.gametong.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 2.5D 블록(딱지·지우개)을 그리기 위한 자세.
 *
 * - [yawDeg]: 수직축 회전(지면에서 도는 각)
 * - [tiltDeg]: 수평 경첩축([tiltAxisDeg] 방향)을 축으로 든 각. 90도를 넘으면 넘어가는 중.
 * - [rollDeg]: 진행 방향축을 축으로 옆으로 기운 각
 * - [lift]: 바닥에서 뜬 높이(화면 단위)
 */
data class BlockPose(
    val cx: Float,
    val cy: Float,
    val width: Float,
    val depth: Float,
    val height: Float,
    val yawDeg: Float = 0f,
    val tiltDeg: Float = 0f,
    val tiltAxisDeg: Float = 0f,
    val rollDeg: Float = 0f,
    val lift: Float = 0f,
)

/** 카메라: 살짝 위에서 비스듬히 내려다보는 정도. */
private const val DEPTH_SQUASH = 0.92f
private const val HEIGHT_RAISE = 0.86f

private data class P3(val x: Float, val y: Float, val z: Float)

private data class Corner(val screen: Offset, val depth: Float)

/**
 * 기울 때의 받침 모서리. 넘어가는 쪽(맞은 쪽)이 들리도록, 진행 방향의 **앞 모서리**를 축으로 돈다.
 * (축 방향과 수직인 방향의 반폭만큼 떨어진 바닥 모서리)
 */
private fun pivotOf(pose: BlockPose): P3 {
    val a = Math.toRadians(pose.tiltAxisDeg.toDouble())
    val nx = -sin(a).toFloat()
    val ny = cos(a).toFloat()
    val extent = abs(nx) * (pose.width / 2f) + abs(ny) * (pose.depth / 2f)
    return P3(-nx * extent, -ny * extent, 0f)
}

private fun transform(pose: BlockPose, p: P3): P3 {
    // 0) 넘어갈 때는 받침 모서리를 기준으로 돈다(바닥에서 미끄러지지 않는다)
    val pivot = if (pose.tiltDeg != 0f) pivotOf(pose) else P3(0f, 0f, 0f)
    // 1) 수직축(yaw)
    val yaw = Math.toRadians(pose.yawDeg.toDouble())
    val cy = cos(yaw).toFloat()
    val sy = sin(yaw).toFloat()
    val lx = p.x - pivot.x
    val ly = p.y - pivot.y
    var x = lx * cy - ly * sy
    var y = lx * sy + ly * cy
    var z = p.z - pivot.z

    // 2) 수평 경첩축(tilt) — 로드리게스 회전
    if (pose.tiltDeg != 0f) {
        val a = Math.toRadians(pose.tiltAxisDeg.toDouble())
        val ax = cos(a).toFloat()
        val ay = sin(a).toFloat()
        val t = Math.toRadians(pose.tiltDeg.toDouble())
        val ct = cos(t).toFloat()
        val st = sin(t).toFloat()
        val dot = ax * x + ay * y
        // a × p
        val cx = ay * z
        val cyv = -ax * z
        val cz = ax * y - ay * x
        val nx = x * ct + cx * st + ax * dot * (1f - ct)
        val ny = y * ct + cyv * st + ay * dot * (1f - ct)
        val nz = z * ct + cz * st
        x = nx
        y = ny
        z = nz
    }

    // 3) 진행축 롤
    if (pose.rollDeg != 0f) {
        val r = Math.toRadians(pose.rollDeg.toDouble())
        val cr = cos(r).toFloat()
        val sr = sin(r).toFloat()
        val nx = x * cr + z * sr
        val nz = -x * sr + z * cr
        x = nx
        z = nz
    }
    // 4) 받침 모서리를 원래 자리로 되돌린다
    return P3(x + pivot.x, y + pivot.y, z + pivot.z)
}

private fun project(pose: BlockPose, p: P3): Corner {
    // 높이 뜬 만큼 카메라에 가까워 살짝 커진다(원근감).
    val persp = 1f + (p.z + pose.lift) * 0.0011f
    val screen = Offset(
        pose.cx + p.x * persp,
        pose.cy + (p.y * DEPTH_SQUASH - (p.z + pose.lift) * HEIGHT_RAISE) * persp,
    )
    // 카메라 깊이: +y 가 가까이, +z 가 가까이
    return Corner(screen, p.y * 0.9f + p.z * 0.6f)
}

/** 블록의 8개 꼭짓점(바닥 4 + 위 4)을 화면 좌표로. */
private fun corners(pose: BlockPose): List<Corner> {
    val hw = pose.width / 2f
    val hd = pose.depth / 2f
    val h = pose.height
    val local = listOf(
        P3(-hw, -hd, 0f), P3(hw, -hd, 0f), P3(hw, hd, 0f), P3(-hw, hd, 0f),
        P3(-hw, -hd, h), P3(hw, -hd, h), P3(hw, hd, h), P3(-hw, hd, h),
    )
    return local.map { project(pose, transform(pose, it)) }
}

/** 윗면(가장자리 순서대로) 화면 좌표. 윗면에 무늬를 그릴 때 쓴다. */
fun topFace(pose: BlockPose): List<Offset> {
    val c = corners(pose)
    return listOf(c[4].screen, c[5].screen, c[6].screen, c[7].screen)
}

/** 윗면의 (u, v) 위치(−0.5 ~ 0.5)를 화면 좌표로. 무늬·라벨을 얹을 때 쓴다. */
fun topFacePoint(pose: BlockPose, u: Float, v: Float): Offset {
    val quad = topFace(pose)
    val tx = u + 0.5f
    val ty = v + 0.5f
    val top = Offset(
        quad[0].x + (quad[1].x - quad[0].x) * tx,
        quad[0].y + (quad[1].y - quad[0].y) * tx,
    )
    val bottom = Offset(
        quad[3].x + (quad[2].x - quad[3].x) * tx,
        quad[3].y + (quad[2].y - quad[3].y) * tx,
    )
    return Offset(top.x + (bottom.x - top.x) * ty, top.y + (bottom.y - top.y) * ty)
}

/**
 * 바닥 그림자. 블록이 기울면 바닥에 닿은 쪽으로 그림자도 따라가고, 뜨면 작아지고 옅어진다.
 * (그림자를 중심에 고정하면 기울 때 그림자가 공중에 뜬 블록과 따로 놀아 보인다.)
 */
fun DrawScope.drawBlockShadow(pose: BlockPose, alpha: Float = 0.20f) {
    val c = corners(pose)
    // 바닥면 네 꼭짓점의 화면 영역
    val ground = listOf(c[0].screen, c[1].screen, c[2].screen, c[3].screen)
    val minX = ground.minOf { it.x }
    val maxX = ground.maxOf { it.x }
    val minY = ground.minOf { it.y }
    val maxY = ground.maxOf { it.y }
    val shrink = (1f - pose.lift / 420f).coerceIn(0.55f, 1f)
    val w = (maxX - minX).coerceAtLeast(pose.width * 0.35f) * 0.95f * shrink
    val d = (maxY - minY).coerceAtLeast(pose.depth * 0.30f) * 0.95f * shrink
    val centerX = (minX + maxX) / 2f
    val centerY = (minY + maxY) / 2f
    drawOval(
        color = Color(0x33000000).copy(alpha = alpha * shrink),
        topLeft = Offset(centerX - w / 2f + 3f, centerY - d / 2f + 5f),
        size = androidx.compose.ui.geometry.Size(w, d),
    )
}

/**
 * 입체 블록 한 개를 그린다. 면을 깊이 순으로 정렬해 그리므로 어느 각도로 기울어도 앞뒤가 맞는다.
 * [topFaceColor] 는 윗면, [sideColor] 는 옆·바닥면 색이다.
 */
fun DrawScope.drawBlock(
    pose: BlockPose,
    topFaceColor: Color,
    sideColor: Color,
    borderColor: Color? = null,
    borderWidth: Float = 2.5f,
    shadow: Boolean = true,
) {
    if (shadow) drawBlockShadow(pose)
    val c = corners(pose)
    val faces = listOf(
        // 윗면
        listOf(4, 5, 6, 7) to topFaceColor,
        // 옆면 4개
        listOf(0, 1, 5, 4) to sideColor,
        listOf(1, 2, 6, 5) to sideColor,
        listOf(2, 3, 7, 6) to sideColor,
        listOf(3, 0, 4, 7) to sideColor,
        // 바닥
        listOf(0, 3, 2, 1) to sideColor,
    ).sortedBy { (ids, _) -> ids.map { c[it].depth }.average().toFloat() }

    for ((ids, color) in faces) {
        val path = Path().apply {
            moveTo(c[ids[0]].screen.x, c[ids[0]].screen.y)
            for (i in 1 until ids.size) lineTo(c[ids[i]].screen.x, c[ids[i]].screen.y)
            close()
        }
        drawPath(path, color)
        if (borderColor != null) {
            drawPath(
                path,
                borderColor,
                style = Stroke(width = borderWidth, join = StrokeJoin.Round),
            )
        }
    }
}

/** 윗면 위에 얹는 얇은 선(접힘선·들뜬 틈·라벨 가장자리 등). */
fun DrawScope.drawOnTopFace(
    pose: BlockPose,
    u1: Float,
    v1: Float,
    u2: Float,
    v2: Float,
    color: Color,
    width: Float,
) {
    drawLine(
        color = color,
        start = topFacePoint(pose, u1, v1),
        end = topFacePoint(pose, u2, v2),
        strokeWidth = width,
    )
}

/** 윗면 위의 띠(지우개 종이 라벨 등). */
fun DrawScope.drawTopFaceBand(
    pose: BlockPose,
    vFrom: Float,
    vTo: Float,
    color: Color,
) {
    val quad = listOf(
        topFacePoint(pose, -0.5f, vFrom),
        topFacePoint(pose, 0.5f, vFrom),
        topFacePoint(pose, 0.5f, vTo),
        topFacePoint(pose, -0.5f, vTo),
    )
    val path = Path().apply {
        moveTo(quad[0].x, quad[0].y)
        for (i in 1 until quad.size) lineTo(quad[i].x, quad[i].y)
        close()
    }
    drawPath(path, color)
}
