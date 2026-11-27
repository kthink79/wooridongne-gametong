package com.game.gametong.render

import com.game.gametong.game.MarbleBody
import com.game.gametong.game.SquareTtakjiMatch
import com.game.gametong.game.SquareTtakjiFlight
import com.game.gametong.game.Side
import com.game.gametong.game.TtakjiPose
import com.game.gametong.game.TtakjiSpec
import com.game.gametong.game.TtakjiSurface
import com.game.gametong.game.Vec2
import com.game.gametong.game.physics3d.Quat
import com.game.gametong.game.physics3d.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * 네모 딱지치기 3D 장면. 운동장 시멘트 바닥 + 분필 원 + 두툼한 네 겹 종이 딱지.
 * 해가 낮게 비껴 비추는 노을빛 운동장이다.
 */
object TtakjiScene3D {

    val env = Environment(
        skyTop = Color4.rgb(0xF2D6A8),
        skyBottom = Color4.rgb(0xDFB073),
        sunDir = Vec3(-0.55f, -0.62f, -0.56f).normalized(),
        sunColor = Color4(1.0f, 0.84f, 0.60f, 1f),
        ambientSky = Color4(0.52f, 0.52f, 0.56f, 1f),
        ambientGround = Color4(0.50f, 0.40f, 0.28f, 1f),
        hazeColor = Color4.rgb(0xE0BE8C),
        hazeStrength = 0.20f,
        vignette = 0.20f,
        sunGlow = 0.65f,
    )

    private val mineColor = Color4.rgb(0x2F55C8)
    private val mineFold = Color4.rgb(0x5B80E2)
    private val theirsColor = Color4.rgb(0xF5C41E)
    private val theirsFold = Color4.rgb(0xD2482E)

    /** 딱지 자세: 경첩축으로 들리고, 수직으로 돌고, 옆으로 기운다. */
    fun quatOf(pose: TtakjiPose): Quat {
        val axis = World.axis3(pose.tiltAxis)
        val forward = Vec3(axis.z, 0f, -axis.x).normalized()
        val qYaw = Quat.axisAngle(Vec3.UP, Math.toRadians(pose.yaw.toDouble()).toFloat())
        val qTilt = Quat.axisAngle(axis, Math.toRadians(pose.tilt.toDouble()).toFloat())
        val qRoll = Quat.axisAngle(
            if (forward.length < 1e-4f) Vec3.FORWARD else forward,
            Math.toRadians(pose.roll.toDouble()).toFloat(),
        )
        return (qYaw * qTilt * qRoll).normalized()
    }

    fun camera(aspect: Float): Camera = Camera.overBoard(
        center = Vec3(5f, 0f, 7.5f),
        width = 10.4f,
        depth = 14.6f,
        pitchDeg = 44f,
        yawDeg = 0f,
        fovYDeg = 44f,
        aspect = aspect,
        margin = 1.0f,
    )

    fun fieldPoint(unitX: Float, unitY: Float, aspect: Float): Vec2? {
        val p = camera(aspect).groundPointAt(unitX, unitY, aspect) ?: return null
        return Vec2(p.x / World.SCALE, p.z / World.SCALE)
    }

    fun build(
        match: SquareTtakjiMatch,
        flight: SquareTtakjiFlight?,
        attacker: Side?,
        aim: GroundAim?,
        particles: List<ParticleView>,
        aspect: Float = Camera.PORTRAIT_ASPECT,
        timeSeconds: Float = 0f,
    ): Scene {
        val groundSurface = match.surface
        val center = Vec3(5f, 0f, 7.5f)
        val camera = camera(aspect)

        val items = ArrayList<ItemSpec>(16)
        val shadows = ArrayList<ShadowSpec>(4)

        // 바닥: 노면별 색
        val groundTint = when (groundSurface) {
            TtakjiSurface.MARU -> Color4.rgb(0xE4C290)
            TtakjiSurface.DIRT -> Color4.rgb(0xD3B184)
            TtakjiSurface.ASPHALT -> Color4.rgb(0xA8A6A0)
        }
        val groundTexture = when (groundSurface) {
            TtakjiSurface.MARU -> TexKind.WOOD
            else -> TexKind.CEMENT
        }
        // 바닥에 그려진 분필 원(아이들이 치던 자리)
        val chalkCenter = Vec3(5.0f, 0f, 7.6f)

        // 두 딱지의 자세
        val playerPose: TtakjiPose
        val aiPose: TtakjiPose
        if (flight != null) {
            val attacking = attacker ?: Side.PLAYER
            playerPose = if (attacking == Side.PLAYER) flight.attackerPose else flight.defenderPose
            aiPose = if (attacking == Side.PLAYER) flight.defenderPose else flight.attackerPose
        } else {
            playerPose = TtakjiPose(match.playerHome)
            aiPose = TtakjiPose(match.aiHome)
        }

        val playerSpec = match.playerSpec
        val aiSpec = match.aiSpec
        val flippedSide = flight?.let { f ->
            if (f.result.flipped) (attacker ?: Side.PLAYER).other() else null
        }

        addTtakji(items, shadows, playerPose, playerSpec, mineColor, mineFold, flippedSide == Side.PLAYER, true)
        addTtakji(items, shadows, aiPose, aiSpec, theirsColor, theirsFold, flippedSide == Side.AI, false)

        // 바닥에 떨어진 먼지·분필가루
        val sprites = ArrayList<SpriteSpec>(80)
        if (aim != null) addGroundGuide(sprites, aim, timeSeconds, Color4(1f, 0.55f, 0.30f, 0.95f))
        for (p in particles) {
            sprites += SpriteSpec(
                pos = Vec3(p.x * World.SCALE, p.z * World.SCALE + 0.004f, p.y * World.SCALE),
                size = p.size * World.SCALE * 1.1f,
                color = when (p.kind) {
                    ParticleKindView.CRUMB -> Color4(0.98f, 0.94f, 0.85f, p.alpha)
                    ParticleKindView.DUST -> Color4(0.86f, 0.74f, 0.54f, p.alpha * 0.5f)
                    ParticleKindView.CHALK -> Color4(0.96f, 0.96f, 0.93f, p.alpha * 0.55f)
                    ParticleKindView.SPARK -> Color4(1f, 0.8f, 0.45f, p.alpha)
                },
                texture = if (p.kind == ParticleKindView.CRUMB) TexKind.CRUMB else TexKind.DUST,
            )
        }

        // 공중에 떠다니는 먼지
        val motes = ArrayList<SpriteSpec>(16)
        for (i in 0 until 14) {
            val t = timeSeconds * 0.1f + i
            motes += SpriteSpec(
                pos = Vec3(
                    5f + sin(t * 0.7f + i) * 4.2f,
                    0.25f + 0.9f * ((i * 0.31f + timeSeconds * 0.05f) % 1f),
                    3f + ((i * 0.77f + timeSeconds * 0.04f) % 1f) * 9f,
                ),
                size = 0.018f + 0.01f * (i % 3),
                color = Color4(1f, 0.9f, 0.72f, 0.14f),
                texture = TexKind.GLOW,
                additive = true,
            )
        }

        return Scene(
            camera = camera,
            env = env,
            ground = GroundSpec(
                center = Vec3(5f, 0f, 7.5f),
                width = 26f,
                depth = 30f,
                texture = groundTexture,
                uvScale = 10f,
                color = groundTint,
                chalkCenter = chalkCenter,
                chalkRadius = 4.6f,
                chalkWidth = 0.12f,
                chalkColor = Color4(0.96f, 0.95f, 0.90f, 1f),
                gloss = if (groundSurface == TtakjiSurface.MARU) 0.25f else 0.03f,
            ),
            items = items,
            shadows = shadows,
            sprites = sprites,
            motes = motes,
        )
    }

    private fun addTtakji(
        items: MutableList<ItemSpec>,
        shadows: MutableList<ShadowSpec>,
        pose: TtakjiPose,
        spec: TtakjiSpec,
        faceColor: Color4,
        foldColor: Color4,
        flipped: Boolean,
        mine: Boolean,
    ) {
        val size = spec.size * World.SCALE
        val thickness = (3f + spec.thick * 1.6f) * World.SCALE
        val quat = quatOf(pose)
        val half = Vec3(size * 0.5f, thickness * 0.5f, size * 0.5f)
        val lift = World.liftToGround(half, quat)
        // 지면에 붙어 있을 때는 들린 높이만 더한다.
        val center = Vec3(
            pose.pos.x * World.SCALE,
            lift + pose.z * World.SCALE,
            pose.pos.y * World.SCALE,
        )
        shadows += SceneKit.shadowForBox(
            center = center,
            quat = quat,
            half = half,
            sunDir = env.sunDir,
            alpha = if (pose.z > 4f) 0.18f else 0.30f,
            softness = if (pose.z > 4f) 1.8f else 1.3f,
        )
        items += ItemSpec(
            mesh = MeshKind.TTAKJI,
            model = Mat4.trs(center, quat, Vec3(size, thickness, size)),
            color = if (flipped) foldColor else faceColor,
            texture = TexKind.PAPER,
            uvScale = 1f,
            specular = 0.06f,
            shininess = 12f,
        )
        // 접힌 네 조각의 색 차이(윗면이 살짝 다른 색으로 보이게)
        items += ItemSpec(
            mesh = MeshKind.TTAKJI,
            model = Mat4.trs(
                center + quat.rotate(Vec3(0f, thickness * 0.14f, 0f)),
                quat,
                Vec3(size * 0.97f, thickness * 0.35f, size * 0.97f),
            ),
            color = if (flipped) faceColor else foldColor,
            texture = TexKind.PAPER,
            uvScale = 1.2f,
            specular = 0.05f,
            shininess = 10f,
        )
    }
}

/** 구슬치기 3D 장면. 노을빛 흙바닥 + 구슬 구멍 + 유리구슬. */
object MarbleScene3D {

    val env = Environment(
        skyTop = Color4.rgb(0xF6D9A0),
        skyBottom = Color4.rgb(0xE0985A),
        sunDir = Vec3(-0.30f, -0.46f, -0.84f).normalized(),
        sunColor = Color4(1.0f, 0.78f, 0.50f, 1f),
        ambientSky = Color4(0.48f, 0.48f, 0.58f, 1f),
        ambientGround = Color4(0.52f, 0.38f, 0.24f, 1f),
        hazeColor = Color4.rgb(0xE6B57E),
        hazeStrength = 0.22f,
        vignette = 0.24f,
        sunGlow = 0.95f,
    )

    /** 유리구슬 색. */
    val glassColors = listOf(
        Color4(0.85f, 0.28f, 0.26f), // 빨강
        Color4(0.28f, 0.52f, 0.92f), // 파랑
        Color4(0.36f, 0.82f, 0.52f), // 초록
        Color4(0.95f, 0.82f, 0.30f), // 노랑
        Color4(0.72f, 0.42f, 0.92f), // 보라
        Color4(0.95f, 0.95f, 0.92f), // 무색
    )

    /**
     * 당구대를 비스듬히 내려다보는 시점.
     * 각도를 낮춰(36도) 구슬이 **굴러가는 방향과 부딪혀 튀는 방향**이 화면에서 길게 보이게 한다.
     */
    fun camera(aspect: Float, fieldHeight: Float = 1060f): Camera = Camera.overBoard(
        center = Vec3(5f, 0f, fieldHeight * World.SCALE * 0.5f),
        width = 10.6f,
        depth = fieldHeight * World.SCALE + 0.8f,
        pitchDeg = 36f,
        yawDeg = 0f,
        fovYDeg = 44f,
        aspect = aspect,
        margin = 1.0f,
    )

    fun fieldPoint(unitX: Float, unitY: Float, aspect: Float, fieldHeight: Float): Vec2? {
        val p = camera(aspect, fieldHeight).groundPointAt(unitX, unitY, aspect) ?: return null
        return Vec2(p.x / World.SCALE, p.z / World.SCALE)
    }

    fun build(
        marbles: List<MarbleBody>,
        king: MarbleBody?,
        holes: List<Vec2>,
        playRadius: Float?,
        aim: GroundAim?,
        particles: List<ParticleView>,
        aspect: Float = Camera.PORTRAIT_ASPECT,
        timeSeconds: Float = 0f,
        fieldHeight: Float = 1060f,
        /** 방금 부딪힌 자리(충격 링·불티). */
        impacts: List<MarbleImpactFx> = emptyList(),
        /** 구슬이 지나간 자리(굴러가는 잔상). */
        trails: List<MarbleTrailDot> = emptyList(),
    ): Scene {
        val center = Vec3(5f, 0f, fieldHeight * World.SCALE * 0.5f)
        val camera = camera(aspect, fieldHeight)

        val items = ArrayList<ItemSpec>(32)
        val shadows = ArrayList<ShadowSpec>(12)

        // 구멍: 파인 흙 + 가장자리 테두리
        for (hole in holes) {
            val p = Vec3(hole.x * World.SCALE, 0.001f, hole.y * World.SCALE)
            items += ItemSpec(
                mesh = MeshKind.DISC,
                model = Mat4.trs(p, Quat.IDENTITY, Vec3(0.98f, 1f, 0.98f)),
                color = Color4(0.16f, 0.10f, 0.06f, 1f),
                texture = TexKind.DIRT,
                uvScale = 1f,
                specular = 0.02f,
                shininess = 4f,
                unlit = false,
            )
            items += ItemSpec(
                mesh = MeshKind.RING,
                model = Mat4.trs(
                    Vec3(p.x, 0.003f, p.z),
                    Quat.IDENTITY,
                    Vec3(1.16f, 1f, 1.16f),
                ),
                color = Color4(0.55f, 0.40f, 0.24f, 0.95f),
                texture = TexKind.DIRT,
                uvScale = 1f,
                specular = 0.02f,
                shininess = 4f,
            )
        }

        // 원 안 구슬 쳐내기: 바닥에 그린 원
        if (playRadius != null) {
            items += ItemSpec(
                mesh = MeshKind.RING,
                model = Mat4.trs(
                    Vec3(5f, 0.002f, fieldHeight * World.SCALE * 0.49f),
                    Quat.IDENTITY,
                    Vec3(playRadius * World.SCALE * 2f, 1f, playRadius * World.SCALE * 2f),
                ),
                color = Color4(0.42f, 0.24f, 0.14f, 0.9f),
                unlit = true,
            )
        }

        // 유리구슬
        var colorIndex = 0
        for (m in marbles) {
            if (m.sunk) continue
            val isKing = king != null && m.id == king.id
            val radius = m.radius * World.SCALE
            val center3 = Vec3(m.pos.x * World.SCALE, radius * 0.96f, m.pos.y * World.SCALE)
            val tint = if (isKing) {
                Color4(0.92f, 0.92f, 0.95f)
            } else {
                glassColors[colorIndex++ % glassColors.size]
            }
            shadows += SceneKit.shadowForSphere(center3, radius, env.sunDir, 0.26f)
            // 굴러간 자세를 그대로 쓴다 — 속 회오리 무늬가 진행 방향으로 돌아간다.
            // (예전처럼 위(Y)축으로만 돌리면 제자리에서 팽이처럼 도는 것처럼 보인다.)
            val quat = m.rollQuat
            items += ItemSpec(
                mesh = MeshKind.SPHERE,
                model = Mat4.trs(
                    center3,
                    quat,
                    Vec3(radius * 2f, radius * 2f, radius * 2f),
                ),
                color = tint,
                texture = TexKind.MARBLE,
                uvScale = 1f,
                specular = if (isKing) 1.1f else 0.95f,
                shininess = if (isKing) 110f else 78f,
                rim = 0.85f,
                alpha = if (isKing) 0.92f else 0.88f,
                transparent = true,
            )
        }

        val sprites = ArrayList<SpriteSpec>(96)
        if (aim != null) addGroundGuide(sprites, aim, timeSeconds, Color4(1f, 0.90f, 0.62f, 0.95f))

        // 굴러가는 잔상: 빠를수록 길고 밝게 남는다.
        for (t in trails) {
            val a = t.life.coerceIn(0f, 1f)
            if (a <= 0.02f) continue
            sprites += SpriteSpec(
                pos = Vec3(t.pos.x * World.SCALE, 0.008f, t.pos.y * World.SCALE),
                size = t.radius * World.SCALE * (0.55f + 0.75f * a),
                color = Color4(1f, 0.92f, 0.74f, 0.32f * a),
                texture = TexKind.GLOW,
                additive = true,
            )
        }

        // 부딪힌 자리: 바닥에 번지는 충격 링 + 튀는 불티.
        for (fx in impacts) {
            val k = (1f - fx.age / fx.life).coerceIn(0f, 1f)
            if (k <= 0f) continue
            val strength = (fx.strength / 900f).coerceIn(0.18f, 1f)
            val r = (fx.ringRadius * (1f + 0.9f * (1f - k))) * World.SCALE
            items += ItemSpec(
                mesh = MeshKind.RING,
                model = Mat4.trs(
                    Vec3(fx.pos.x * World.SCALE, 0.006f, fx.pos.y * World.SCALE),
                    Quat.IDENTITY,
                    Vec3(r * 2f, 1f, r * 2f),
                ),
                color = Color4(1f, 0.86f, 0.55f, 0.55f * k * strength),
                unlit = true,
            )
            val sparks = 6
            for (i in 0 until sparks) {
                val angle = i * (6.2831855f / sparks) + fx.seed
                val d = r * (0.6f + 0.9f * (1f - k))
                sprites += SpriteSpec(
                    pos = Vec3(
                        fx.pos.x * World.SCALE + cos(angle) * d,
                        0.012f + 0.10f * (1f - k) * (i % 2),
                        fx.pos.y * World.SCALE + sin(angle) * d,
                    ),
                    size = 0.035f + 0.03f * strength * k,
                    color = Color4(1f, 0.82f + 0.15f * k, 0.45f, 0.85f * k),
                    texture = TexKind.GLOW,
                    additive = true,
                )
            }
        }

        for (p in particles) {
            sprites += SpriteSpec(
                pos = Vec3(p.x * World.SCALE, p.z * World.SCALE + 0.004f, p.y * World.SCALE),
                size = p.size * World.SCALE * 1.2f,
                color = Color4(0.88f, 0.74f, 0.52f, p.alpha * 0.55f),
                texture = TexKind.DUST,
            )
        }

        val motes = ArrayList<SpriteSpec>(14)
        for (i in 0 until 14) {
            val t = timeSeconds * 0.09f + i
            motes += SpriteSpec(
                pos = Vec3(
                    5f + sin(t * 0.8f + i) * 4.5f,
                    0.2f + 0.8f * ((i * 0.27f + timeSeconds * 0.05f) % 1f),
                    1.5f + ((i * 0.61f + timeSeconds * 0.03f) % 1f) * (fieldHeight * World.SCALE - 2f),
                ),
                size = 0.016f + 0.012f * (i % 3),
                color = Color4(1f, 0.88f, 0.66f, 0.16f),
                texture = TexKind.GLOW,
                additive = true,
            )
        }

        return Scene(
            camera = camera,
            env = env,
            ground = GroundSpec(
                center = center,
                width = 24f,
                depth = 30f,
                texture = TexKind.DIRT,
                uvScale = 14f,
                color = Color4.rgb(0xD8B084),
                gloss = 0.0f,
            ),
            items = items,
            shadows = shadows,
            sprites = sprites,
            motes = motes,
        )
    }
}

/**
 * 바닥에 그리는 조준 가이드(점선 + 도착 링).
 * [prediction] 이 있으면 당구대 조준선처럼 **처음 부딪힐 자리**에서 선을 끊고,
 * 맞은 구슬이 날아갈 방향을 함께 보여 준다.
 */
data class GroundAim(
    val from: Vec2,
    val dir: Vec2,
    val power: Float,
    val target: Vec2? = null,
    val prediction: com.game.gametong.game.MarbleAimPrediction? = null,
)

/** 구슬이 지나간 자리 하나(굴러가는 잔상). */
data class MarbleTrailDot(
    val pos: Vec2,
    /** 0(다 사라짐) ~ 1(방금 남김). */
    val life: Float,
    val radius: Float,
)

/**
 * 구슬 충격 연출 하나.
 * @param strength 맞부딪힌 세기(무대 단위/초)
 * @param age 지난 시간(초)
 * @param ringRadius 바닥에 번지는 링의 기준 반지름(무대 단위)
 * @param seed 불티가 퍼지는 각도를 조금씩 다르게 하는 값
 */
data class MarbleImpactFx(
    val pos: Vec2,
    val strength: Float,
    val age: Float,
    val life: Float,
    val ringRadius: Float = 26f,
    val seed: Float = 0f,
)

/** 점선 조준 가이드 + 도착 지점 링을 만든다. */
fun addGroundGuide(
    sprites: MutableList<SpriteSpec>,
    aim: GroundAim,
    timeSeconds: Float,
    color: Color4,
    maxFieldLength: Float = 900f,
) {
    val from = Vec3(aim.from.x * World.SCALE, 0.012f, aim.from.y * World.SCALE)
    val dir = aim.dir
    val len = kotlin.math.sqrt(dir.x * dir.x + dir.y * dir.y)
    if (len < 1e-4f) return
    val ux = dir.x / len
    val uy = dir.y / len
    val prediction = aim.prediction
    // 구슬에 먼저 닿으면 그 자리에서 선을 끊는다(당구대 조준선처럼).
    val guideLength = if (prediction != null && !prediction.wall) {
        kotlin.math.min(maxFieldLength * aim.power.coerceIn(0.15f, 1f), prediction.distance)
    } else {
        maxFieldLength * aim.power.coerceIn(0.15f, 1f)
    }
    val reach = guideLength * World.SCALE
    val spacing = 0.30f
    val count = (reach / spacing).toInt().coerceAtMost(120)
    for (i in 1..count) {
        val d = i * spacing
        val fade = 1f - (i.toFloat() / (count + 3))
        val pulse = 0.7f + 0.3f * sin(timeSeconds * 7f - i * 0.6f)
        sprites += SpriteSpec(
            pos = Vec3(from.x + ux * d, 0.012f, from.z + uy * d),
            size = 0.05f + 0.018f * aim.power,
            color = Color4(color.r, color.g, color.b, (0.35f + 0.6f * fade) * pulse),
            texture = TexKind.GLOW,
            additive = true,
        )
    }

    // 고스트 링(맞을 자리) + 맞은 구슬이 날아갈 방향 + 내 구슬이 꺾여 갈 방향
    if (prediction != null) {
        val c = prediction.contact
        val ringR = 0.30f
        for (i in 0 until 26) {
            val a = i * (6.2831855f / 26f) + timeSeconds * 0.8f
            sprites += SpriteSpec(
                pos = Vec3(
                    c.x * World.SCALE + cos(a) * ringR,
                    0.013f,
                    c.y * World.SCALE + sin(a) * ringR,
                ),
                size = 0.038f,
                color = Color4(1f, 1f, 0.95f, 0.55f),
                texture = TexKind.GLOW,
                additive = true,
            )
        }
        if (!prediction.wall && prediction.targetDir.length() > 1e-3f) {
            val td = prediction.targetDir
            val objectLen = 2.2f
            for (i in 1..10) {
                val d = i * (objectLen / 10f)
                sprites += SpriteSpec(
                    pos = Vec3(
                        c.x * World.SCALE + td.x * d,
                        0.012f,
                        c.y * World.SCALE + td.y * d,
                    ),
                    size = 0.045f,
                    color = Color4(0.65f, 0.95f, 1f, 0.30f + 0.35f * (1f - i / 11f)),
                    texture = TexKind.GLOW,
                    additive = true,
                )
            }
        }
    }
    val target = aim.target
    if (target != null) {
        val r = 0.34f
        for (i in 0 until 24) {
            val a = i * (6.2831855f / 24f) + timeSeconds * 1.5f
            sprites += SpriteSpec(
                pos = Vec3(
                    target.x * World.SCALE + cos(a) * r,
                    0.013f,
                    target.y * World.SCALE + sin(a) * r,
                ),
                size = 0.045f,
                color = Color4(color.r, color.g, color.b, 0.85f),
                texture = TexKind.GLOW,
                additive = true,
            )
        }
    }
}
