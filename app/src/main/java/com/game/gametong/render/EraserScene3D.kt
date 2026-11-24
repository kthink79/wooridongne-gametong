package com.game.gametong.render

import com.game.gametong.game.Board
import com.game.gametong.game.DeskSurface
import com.game.gametong.game.EraserBody
import com.game.gametong.game.Side
import com.game.gametong.game.Vec2
import com.game.gametong.game.physics3d.Quat
import com.game.gametong.game.physics3d.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * 무대 좌표(1000 × 1500)와 3D 세계 좌표를 잇는 공통 도구.
 * 무대 100 단위 = 세계 1 단위(≈1 m)라, 지우개 폭 244 는 2.44 m? 가 아니라
 * 무대 1000 = 10 m 로 잡아 지우개가 24 cm 정도 되게 한다.
 */
object World {
    const val SCALE = 0.01f

    /** 무대 좌표 → 세계 좌표(높이는 무대 단위). */
    fun at(x: Float, y: Float, height: Float = 0f): Vec3 =
        Vec3(x * SCALE, height * SCALE, y * SCALE)

    fun v(px: Float, py: Float) = Vec2(px, py)

    /** 회전한 상자가 바닥에 닿도록 들어 올릴 높이(가장 낮은 꼭짓점을 0으로). */
    fun liftToGround(half: Vec3, quat: Quat): Float {
        var lowest = Float.MAX_VALUE
        for (sx in intArrayOf(-1, 1)) {
            for (sy in intArrayOf(-1, 1)) {
                for (sz in intArrayOf(-1, 1)) {
                    val local = Vec3(half.x * sx, half.y * sy, half.z * sz)
                    val y = quat.rotate(local).y
                    if (y < lowest) lowest = y
                }
            }
        }
        return -lowest
    }

    /** 무대의 2D 회전축(수평)을 3D 축으로. */
    fun axis3(axis: Vec2): Vec3 = Vec3(axis.x, 0f, axis.y).normalized().let {
        if (it.length < 1e-4f) Vec3.RIGHT else it
    }

    /** 지우개 자세: 진행축 텀블 + 앞뒤 젖힘 + 옆 기움 + 수직 회전을 모두 합친 사원수. */
    fun eraserQuat(body: EraserBody): Quat {
        val tumbleAxis = axis3(body.tumbleAxis)
        val forward = Vec3(tumbleAxis.z, 0f, -tumbleAxis.x).normalized()
        val qYaw = Quat.axisAngle(Vec3.UP, Math.toRadians(body.spin.toDouble()).toFloat())
        val qTumble = Quat.axisAngle(tumbleAxis, Math.toRadians(body.tumble.toDouble()).toFloat())
        val qPitch = Quat.axisAngle(tumbleAxis, Math.toRadians(body.pitchDeg.toDouble()).toFloat())
        val qRoll = Quat.axisAngle(
            if (forward.length < 1e-4f) Vec3.FORWARD else forward,
            Math.toRadians((body.lean + body.rollDeg).toDouble()).toFloat(),
        )
        return (qYaw * qTumble * qPitch * qRoll).normalized()
    }

    /** 무대 좌표의 방향 벡터를 세계 좌표로. */
    fun dir(px: Float, py: Float): Vec3 = Vec3(px, 0f, py).normalized()

    fun lerpColor(a: Color4, b: Color4, t: Float): Color4 = Color4(
        a.r + (b.r - a.r) * t,
        a.g + (b.g - a.g) * t,
        a.b + (b.b - a.b) * t,
        a.a + (b.a - a.a) * t,
    )

    /** 둘레를 따라 도는 점(가이드 점·먼지 배치). */
    fun circlePoint(cx: Float, cz: Float, radius: Float, angleRad: Float): Vec3 =
        Vec3(cx + cos(angleRad) * radius, 0f, cz + sin(angleRad) * radius)
}

/** 지우개 경기의 색·재질 팔레트. */
object EraserPalette {
    val cream = Color4.rgb(0xF6EEDC)
    val creamShade = Color4.rgb(0xE8DDC4)
    val playerSleeve = Color4.rgb(0xC7322A)
    val aiSleeve = Color4.rgb(0x1F3F9E)
    val woodWarm = Color4.rgb(0xE9CFA3)
    val woodDeep = Color4.rgb(0xA06B37)
    val ink = Color4.rgb(0x3A2C1E)

    fun sleeveColor(side: Side): Color4 = if (side == Side.PLAYER) playerSleeve else aiSleeve

    /** 책상 표면별 연출 값. */
    data class SurfaceLook(
        val tint: Color4,
        val gloss: Float,
        val uvScale: Float,
        val roughness: Float,
    )

    fun look(surface: DeskSurface): SurfaceLook = when (surface) {
        DeskSurface.COATED -> SurfaceLook(Color4.rgb(0xF3E6C8), 0.55f, 1.2f, 0.5f)
        DeskSurface.WOOD -> SurfaceLook(Color4.rgb(0xE9CFA3), 0.20f, 1.0f, 1.0f)
        DeskSurface.PLYWOOD -> SurfaceLook(Color4.rgb(0xD9BE92), 0.08f, 1.4f, 1.5f)
        DeskSurface.DUSTY -> SurfaceLook(Color4.rgb(0xD8C9AC), 0.03f, 1.1f, 2.0f)
        DeskSurface.PLASTIC -> SurfaceLook(Color4.rgb(0xE9E6DE), 0.45f, 1.3f, 0.7f)
        DeskSurface.STEEL -> SurfaceLook(Color4.rgb(0xD6DADD), 0.62f, 1.6f, 0.6f)
    }
}

/** 지우개 대전(알까기·레슬링) 3D 장면. 45도 학교 나무 책상 위 시점. */
object EraserScene3D {

    const val BOARD_W = Board.WIDTH
    const val BOARD_H = Board.HEIGHT

    /** 따뜻한 오후 교실 빛. 창문 쪽(왼쪽 위)에서 해가 든다. */
    val env = Environment(
        skyTop = Color4.rgb(0xF7E7C6),
        skyBottom = Color4.rgb(0xC99B62),
        sunDir = Vec3(-0.42f, -0.80f, -0.42f).normalized(),
        sunColor = Color4(1.0f, 0.87f, 0.66f, 1f),
        ambientSky = Color4(0.46f, 0.47f, 0.52f, 1f),
        ambientGround = Color4(0.40f, 0.30f, 0.20f, 1f),
        hazeColor = Color4.rgb(0xD9B98A),
        hazeStrength = 0.25f,
        vignette = 0.34f,
        sunGlow = 0.75f,
    )

    /**
     * 한 프레임 장면을 만든다.
     *
     * @param aim 드래그 중이면 조준선(없으면 null)
     * @param aspect 화면 비율(2:3 세로)
     */
    /** 이 무대를 담는 카메라(화면 비율별). 터치 좌표를 책상 위 점으로 되돌릴 때도 같은 값을 쓴다. */
    fun camera(aspect: Float): Camera = Camera.overBoard(
        center = Vec3(BOARD_W * World.SCALE * 0.5f, 0f, BOARD_H * World.SCALE * 0.5f),
        width = BOARD_W * World.SCALE * 0.98f,
        depth = BOARD_H * World.SCALE * 0.94f,
        pitchDeg = 52f,
        yawDeg = 0f,
        fovYDeg = 40f,
        aspect = aspect,
        margin = 1.0f,
    )

    /** 화면 좌표(0~1) → 책상 좌표. 손가락이 닿은 곳을 3D 위 정확한 자리로 옮긴다. */
    fun fieldPoint(unitX: Float, unitY: Float, aspect: Float): Vec2? {
        val p = camera(aspect).groundPointAt(unitX, unitY, aspect) ?: return null
        return Vec2(p.x / World.SCALE, p.z / World.SCALE)
    }

    fun build(
        bodies: List<EraserBody>,
        activeSide: Side,
        surface: DeskSurface,
        aim: EraserAim?,
        particles: List<ParticleView>,
        aspect: Float = Camera.PORTRAIT_ASPECT,
        timeSeconds: Float = 0f,
        perchedSide: Side? = null,
    ): Scene {
        val look = EraserPalette.look(surface)
        val center = Vec3(BOARD_W * World.SCALE * 0.5f, 0f, BOARD_H * World.SCALE * 0.5f)
        val camera = camera(aspect)

        val items = ArrayList<ItemSpec>(24)
        val shadows = ArrayList<ShadowSpec>(8)

        // 책상 상판: 두께가 있는 나무 판. 윗면은 바닥면(GroundSpec)이 그린다.
        val deskThickness = 0.34f
        val deskW = BOARD_W * World.SCALE + 1.7f
        // 뒤쪽(책상 위쪽)은 소품이 놓일 자리까지 넉넉히 넓힌다.
        val deskD = BOARD_H * World.SCALE + 3.4f
        items += ItemSpec(
            mesh = MeshKind.BOX,
            model = Mat4.trs(
                Vec3(center.x, -deskThickness * 0.5f - 0.002f, center.z - 0.75f),
                Quat.IDENTITY,
                Vec3(deskW, deskThickness, deskD),
            ),
            color = Color4.rgb(0x7A4E28),
            texture = TexKind.WOOD,
            uvScale = 3.5f,
            specular = 0.08f,
            shininess = 20f,
        )

        // 교실 배경 소품(공책·연필·의자 등받이) — 책상 뒤쪽
        items += ItemSpec(
            mesh = MeshKind.BOX,
            model = Mat4.trs(
                Vec3(2.1f, 0.02f, -1.5f),
                Quat.axisAngle(Vec3.UP, Math.toRadians(-9.0).toFloat()),
                Vec3(2.7f, 0.05f, 3.3f),
            ),
            color = Color4.rgb(0xF6F1E3),
            texture = TexKind.PAPER,
            uvScale = 1.4f,
            specular = 0.05f,
            shininess = 8f,
        )
        items += ItemSpec(
            mesh = MeshKind.CYLINDER,
            model = Mat4.trs(
                Vec3(6.6f, 0.09f, -2.4f),
                Quat.axisAngle(Vec3.RIGHT, Math.toRadians(90.0).toFloat())
                    * Quat.axisAngle(Vec3.UP, Math.toRadians(24.0).toFloat()),
                Vec3(0.11f, 2.6f, 0.11f),
            ),
            color = Color4.rgb(0xE8B93C),
            texture = TexKind.NONE,
            specular = 0.20f,
            shininess = 20f,
        )
        items += ItemSpec(
            mesh = MeshKind.ERASER,
            model = Mat4.trs(
                Vec3(9.1f, 0.62f, -2.0f),
                Quat.IDENTITY,
                Vec3(2.4f, 2.0f, 0.22f),
            ),
            color = Color4.rgb(0x8B5A2B),
            texture = TexKind.WOOD,
            uvScale = 1.2f,
            specular = 0.12f,
            shininess = 24f,
        )

        // 지우개들: 그림자 → 몸통 → 종이 슬리브
        val ordered = bodies.sortedBy { it.z }
        for (body in ordered) {
            if (body.fallen) continue
            val w = body.blockWidth * World.SCALE
            val h = body.blockHeight * World.SCALE
            val d = body.blockDepth * World.SCALE
            val quat = World.eraserQuat(body)
            val half = Vec3(w * 0.5f, h * 0.5f, d * 0.5f)
            val lift = World.liftToGround(half, quat)
            val airborne = body.z * World.SCALE
            // 상대 위에 걸쳐 있으면 그 높이 + 상대 두께만큼 위로
            val center3 = Vec3(body.pos.x * World.SCALE, lift + airborne, body.pos.y * World.SCALE)
            val isActive = body.side == activeSide

            shadows += SceneKit.shadowForBox(
                center = center3,
                quat = quat,
                half = Vec3(w * 0.52f, h * 0.5f, d * 0.52f),
                sunDir = env.sunDir,
                alpha = if (airborne > 0.02f) 0.20f else 0.34f,
                softness = if (airborne > 0.02f) 1.6f else 1.1f,
            )

            // 몸통(무광 고무)
            items += ItemSpec(
                mesh = MeshKind.ERASER,
                model = Mat4.trs(center3, quat, Vec3(w, h, d)),
                color = EraserPalette.cream,
                texture = TexKind.RUBBER,
                uvScale = 2.2f,
                specular = 0.06f,
                shininess = 10f,
            )
            // 종이 슬리브(가운데 띠)
            val sleeve = EraserPalette.sleeveColor(body.side)
            items += ItemSpec(
                mesh = MeshKind.SLEEVE,
                model = Mat4.trs(center3, quat, Vec3(w * 0.36f, h * 1.03f, d * 1.03f)),
                color = sleeve,
                texture = TexKind.PAPER,
                uvScale = 1.6f,
                specular = 0.05f,
                shininess = 8f,
            )
            // 슬리브 옆의 흰 테두리(종이 끝선)
            items += ItemSpec(
                mesh = MeshKind.SLEEVE,
                model = Mat4.trs(
                    center3 + quat.rotate(Vec3(w * 0.19f, 0f, 0f)),
                    quat,
                    Vec3(w * 0.03f, h * 1.02f, d * 1.02f),
                ),
                color = EraserPalette.creamShade,
                texture = TexKind.PAPER,
                uvScale = 1f,
                specular = 0.04f,
                shininess = 6f,
            )

            // 내 차례 표시: 지우개 둘레의 점선 링
            if (isActive) {
                val ringY = 0.004f
                val pulse = 0.55f + 0.25f * sin(timeSeconds * 4f)
                addGroundRing(
                    items,
                    center = Vec3(center3.x, ringY, center3.z),
                    radius = body.radius * World.SCALE * 1.12f,
                    color = Color4(1f, 0.92f, 0.72f, 0.55f * pulse),
                    width = 0.03f,
                )
            }
        }

        // 걸쳐 올라간 지우개 표시
        if (perchedSide != null) {
            val perched = bodies.firstOrNull { it.side == perchedSide && it.perched }
            if (perched != null) {
                items += ItemSpec(
                    mesh = MeshKind.RING,
                    model = Mat4.trs(
                        Vec3(perched.pos.x * World.SCALE, 0.01f, perched.pos.y * World.SCALE),
                        Quat.IDENTITY,
                        Vec3(perched.radius * 2.2f * World.SCALE, 1f, perched.radius * 2.2f * World.SCALE),
                    ),
                    color = Color4(1f, 0.78f, 0.20f, 0.85f),
                    unlit = true,
                )
            }
        }

        // 조준선(바닥에 투영)
        val sprites = ArrayList<SpriteSpec>(64)
        if (aim != null) addAim(sprites, aim, timeSeconds)

        // 지우개 가루·먼지
        for (p in particles) {
            sprites += SpriteSpec(
                pos = Vec3(p.x * World.SCALE, (p.z * World.SCALE) + 0.004f, p.y * World.SCALE),
                size = p.size * World.SCALE * 0.9f,
                color = when (p.kind) {
                    ParticleKindView.CRUMB -> Color4(0.98f, 0.95f, 0.88f, p.alpha * 0.95f)
                    ParticleKindView.DUST -> Color4(0.86f, 0.78f, 0.62f, p.alpha * 0.45f)
                    ParticleKindView.CHALK -> Color4(0.97f, 0.97f, 0.94f, p.alpha * 0.5f)
                    ParticleKindView.SPARK -> Color4(1f, 0.85f, 0.5f, p.alpha)
                },
                texture = when (p.kind) {
                    ParticleKindView.CRUMB -> TexKind.CRUMB
                    ParticleKindView.DUST -> TexKind.DUST
                    ParticleKindView.CHALK -> TexKind.DUST
                    ParticleKindView.SPARK -> TexKind.GLOW
                },
                additive = p.kind == ParticleKindView.SPARK,
            )
        }

        // 공중에 떠도는 먼지(빛줄기 속)
        val motes = ArrayList<SpriteSpec>(24)
        for (i in 0 until 18) {
            val t = timeSeconds * 0.12f + i * 0.7f
            val x = (BOARD_W * World.SCALE) * ((i * 0.37f + t * 0.05f) % 1f)
            val z = (BOARD_H * World.SCALE) * ((i * 0.53f + t * 0.03f) % 1f)
            val y = 0.25f + 0.55f * ((i * 0.19f + t * 0.08f) % 1f)
            motes += SpriteSpec(
                pos = Vec3(x + sin(t + i) * 0.05f, y, z),
                size = 0.02f + 0.015f * ((i % 3) + 1),
                color = Color4(1f, 0.94f, 0.78f, 0.10f + 0.08f * ((i % 4) / 3f)),
                texture = TexKind.GLOW,
                additive = true,
            )
        }

        return Scene(
            camera = camera,
            env = env,
            ground = GroundSpec(
                center = center,
                width = BOARD_W * World.SCALE + 1.7f,
                depth = BOARD_H * World.SCALE + 1.9f,
                texture = if (surface == DeskSurface.STEEL || surface == DeskSurface.PLASTIC) {
                    TexKind.CEMENT
                } else {
                    TexKind.WOOD
                },
                uvScale = look.uvScale * 5.0f,
                color = look.tint,
                gloss = look.gloss,
            ),
            items = items,
            shadows = shadows,
            sprites = sprites,
            motes = motes,
        )
    }

    private fun addGroundRing(
        items: MutableList<ItemSpec>,
        center: Vec3,
        radius: Float,
        color: Color4,
        width: Float,
    ) {
        items += ItemSpec(
            mesh = MeshKind.RING,
            model = Mat4.trs(center, Quat.IDENTITY, Vec3(radius * 2f, 1f, radius * 2f)),
            color = color,
            unlit = true,
        )
    }

    private fun addAim(sprites: MutableList<SpriteSpec>, aim: EraserAim, time: Float) {
        val from = Vec3(aim.from.x * World.SCALE, 0.01f, aim.from.y * World.SCALE)
        val dirX = aim.to.x - aim.from.x
        val dirY = aim.to.y - aim.from.y
        val len = kotlin.math.sqrt(dirX * dirX + dirY * dirY)
        if (len < 1f) return
        val ux = dirX / len
        val uy = dirY / len
        val reach = len.coerceAtMost(680f) * World.SCALE
        val dotSpacing = 0.32f
        val count = (reach / dotSpacing).toInt()
        val hot = Color4(1f, 0.62f, 0.30f, 0.95f)
        val warm = Color4(1f, 0.90f, 0.66f, 0.85f)
        for (i in 1..count) {
            val d = i * dotSpacing
            val fade = 1f - (i.toFloat() / (count + 2))
            val pulse = 0.75f + 0.25f * sin(time * 8f - i * 0.5f)
            sprites += SpriteSpec(
                pos = Vec3(from.x + ux * d, 0.012f, from.z + uy * d),
                size = 0.055f + 0.02f * aim.power,
                color = Color4(
                    World.lerpColor(hot, warm, fade).r,
                    World.lerpColor(hot, warm, fade).g,
                    World.lerpColor(hot, warm, fade).b,
                    (0.55f + 0.45f * fade) * pulse,
                ),
                texture = TexKind.GLOW,
                additive = true,
            )
        }
        // 예상 정지 지점
        val predicted = aim.predicted
        if (predicted != null) {
            for (i in 0 until 20) {
                val a = i * 0.314f + time * 2f
                val r = 0.30f
                sprites += SpriteSpec(
                    pos = Vec3(
                        predicted.x * World.SCALE + cos(a) * r,
                        0.012f,
                        predicted.y * World.SCALE + sin(a) * r,
                    ),
                    size = 0.05f,
                    color = Color4(1f, 0.95f, 0.80f, 0.55f),
                    texture = TexKind.GLOW,
                    additive = true,
                )
            }
        }
    }
}

/** 조준선 정보(무대 좌표). */
data class EraserAim(
    val from: Vec2,
    val to: Vec2,
    val power: Float,
    val predicted: Vec2? = null,
)

/** 파티클을 그리기 위한 최소 정보(게임 파티클과 렌더러를 분리한다). */
data class ParticleView(
    val x: Float,
    val y: Float,
    val z: Float,
    val size: Float,
    val alpha: Float,
    val kind: ParticleKindView,
)

enum class ParticleKindView { CRUMB, DUST, CHALK, SPARK }
