package com.game.gametong.render

import com.game.gametong.game.physics3d.Quat
import com.game.gametong.game.physics3d.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** 0~1 로 다루는 RGBA 색. */
data class Color4(val r: Float, val g: Float, val b: Float, val a: Float = 1f) {
    fun scale(s: Float) = Color4(r * s, g * s, b * s, a)
    fun withAlpha(alpha: Float) = Color4(r, g, b, alpha)

    companion object {
        val WHITE = Color4(1f, 1f, 1f, 1f)
        val BLACK = Color4(0f, 0f, 0f, 1f)
        fun rgb(hex: Int, alpha: Float = 1f) = Color4(
            ((hex shr 16) and 0xFF) / 255f,
            ((hex shr 8) and 0xFF) / 255f,
            (hex and 0xFF) / 255f,
            alpha,
        )
    }
}

/**
 * 카메라. 위치·바라보는 점·화각만 정하면 화면에 어떻게 맺힐지 계산된다.
 * 45도 책상 시점·노을 운동장 시점 모두 여기서 만든다.
 */
data class Camera(
    val eye: Vec3,
    val target: Vec3,
    val up: Vec3 = Vec3.UP,
    val fovYDeg: Float = 42f,
    val near: Float = 0.05f,
    val far: Float = 200f,
) {
    fun view(): FloatArray = Mat4.lookAt(eye, target, up)

    fun projection(aspect: Float): FloatArray =
        Mat4.perspective(fovYDeg, aspect, near, far)

    fun viewProjection(aspect: Float): FloatArray =
        Mat4.multiply(projection(aspect), view())

    /**
     * 세계 좌표 → 화면 좌표(0~1, 좌상단 기준). 카메라 뒤에 있으면 null.
     */
    fun projectToUnit(point: Vec3, aspect: Float): FloatArray? {
        val clip = Mat4.transform(viewProjection(aspect), point)
        if (clip[3] <= 0.001f) return null
        return floatArrayOf(clip[0] * 0.5f + 0.5f, 0.5f - clip[1] * 0.5f, clip[2])
    }

    /** 화면 좌표(0~1) → 바닥 위 점. 손가락 위치를 3D 책상 좌표로 옮길 때 쓴다. */
    fun groundPointAt(unitX: Float, unitY: Float, aspect: Float, groundY: Float = 0f): Vec3? =
        Mat4.unprojectToGround(viewProjection(aspect), unitX * 2f - 1f, 1f - unitY * 2f, groundY)

    companion object {
        /**
         * 무대를 화면에 꽉 채우는 3인칭 시점.
         *
         * @param center 무대 중심(세계 좌표)
         * @param width 무대 폭
         * @param depth 무대 앞뒤 길이
         * @param pitchDeg 내려다보는 각(도). 45면 45도 책상 시점.
         * @param yawDeg 좌우로 도는 각(도).
         */
        fun overBoard(
            center: Vec3,
            width: Float,
            depth: Float,
            pitchDeg: Float = 45f,
            yawDeg: Float = 0f,
            fovYDeg: Float = 42f,
            aspect: Float = 2f / 3f,
            margin: Float = 1.0f,
        ): Camera {
            val pitch = Math.toRadians(pitchDeg.toDouble())
            val yaw = Math.toRadians(yawDeg.toDouble())
            val dir = Vec3(
                (sin(yaw) * cos(pitch)).toFloat(),
                sin(pitch).toFloat(),
                (cos(yaw) * cos(pitch)).toFloat(),
            )
            fun at(dist: Float) = Camera(
                eye = center + dir * dist,
                target = center,
                fovYDeg = fovYDeg,
                near = 0.05f,
                far = dist * 4f + 20f,
            )
            // 무대 네 모서리가 화면의 [fill] 비율 안에 들어오는 가장 가까운 거리를 이분 탐색으로 찾는다.
            val halfW = width * 0.5f
            val halfD = depth * 0.5f
            val corners = listOf(
                center + Vec3(-halfW, 0f, -halfD),
                center + Vec3(halfW, 0f, -halfD),
                center + Vec3(-halfW, 0f, halfD),
                center + Vec3(halfW, 0f, halfD),
            )
            val fill = (0.95f / margin).coerceIn(0.6f, 0.98f)
            val radius = 0.5f * sqrt(width * width + depth * depth)
            var lo = radius * 0.3f
            var hi = radius * 10f
            repeat(26) {
                val mid = (lo + hi) * 0.5f
                val cam = at(mid)
                var worst = 0f
                for (corner in corners) {
                    val uv = cam.projectToUnit(corner, aspect)
                    if (uv == null) {
                        worst = 4f
                        break
                    }
                    val nx = kotlin.math.abs(uv[0] * 2f - 1f)
                    val ny = kotlin.math.abs(uv[1] * 2f - 1f)
                    if (nx > worst) worst = nx
                    if (ny > worst) worst = ny
                }
                if (worst > fill) lo = mid else hi = mid
            }
            return at(hi)
        }

        /** 세로 화면(2:3)에서 위에서 살짝 내려다보는 기본값. */
        const val PORTRAIT_ASPECT = 2f / 3f
    }
}

/** 하늘·조명·안개. 노을 운동장과 오후의 교실을 이 값만 바꿔 연출한다. */
data class Environment(
    val skyTop: Color4,
    val skyBottom: Color4,
    val sunDir: Vec3,
    val sunColor: Color4,
    val ambientSky: Color4,
    val ambientGround: Color4,
    val hazeColor: Color4 = Color4(0.85f, 0.72f, 0.55f, 1f),
    /** 안개의 최대 세기(0~1). 1이면 먼 곳이 안개색으로 완전히 덮인다. */
    val hazeStrength: Float = 0.22f,
    val vignette: Float = 0.30f,
    /** 화면 위쪽에 번지는 햇빛(0~1). */
    val sunGlow: Float = 0.55f,
    /**
     * 안개가 시작되는 거리 = 카메라~무대 중심 거리 × 이 값.
     * 1.45면 무대 네 모서리(중심에서 반지름의 1.4배 안팎)보다 바깥에서만 안개가 끼기 시작한다.
     */
    val hazeNearScale: Float = 1.45f,
    /** 시작 거리에서 최대 세기에 이르는 거리(같은 배수). */
    val hazeSpanScale: Float = 1.25f,
)

/**
 * 거리 안개 계산.
 *
 * 예전에는 셰이더가 거리 8~26에서 무조건 안개색으로 덮고 [Environment.hazeStrength] 는 쓰지도 않았다.
 * 그래서 45도로 내려다보는 세로 화면에서는 **운동장 바닥 앞쪽과 딱지까지** 안개에 잠겨
 * 뿌옇게 보였다(카메라 거리가 20 안팎). 이제는 카메라 거리에 맞춰 시작점을 잡아
 * **무대 안은 안개가 0** 이고, 무대 밖 먼 바닥만 세기만큼 옅게 잠긴다.
 */
object Haze {
    /** 카메라~무대 중심 거리로 시작 거리·번짐 거리를 잡는다. */
    fun rangeFor(cameraDistance: Float, env: Environment): FloatArray {
        val d = cameraDistance.coerceAtLeast(0.001f)
        return floatArrayOf(d * env.hazeNearScale, d * env.hazeSpanScale)
    }

    /** 어떤 거리의 안개 세기(0~[Environment.hazeStrength]). */
    fun factorAt(distance: Float, range: FloatArray, strength: Float): Float {
        val near = range[0]
        val span = range[1].coerceAtLeast(0.001f)
        val t = ((distance - near) / span).coerceIn(0f, 1f)
        return t * strength.coerceIn(0f, 1f)
    }
}

/** 바닥(책상·운동장). 분필 원·구멍 같은 바닥 무늬도 여기서 정한다. */
data class GroundSpec(
    val center: Vec3,
    val width: Float,
    val depth: Float,
    val texture: TexKind,
    val uvScale: Float = 1f,
    val color: Color4 = Color4.WHITE,
    /** 분필·페인트로 그린 원(중심 x,z / 반지름 / 선 두께). 없으면 null. */
    val chalkCenter: Vec3? = null,
    val chalkRadius: Float = 0f,
    val chalkWidth: Float = 0.06f,
    val chalkColor: Color4 = Color4.WHITE,
    /** 바닥 광택(코팅 책상). */
    val gloss: Float = 0.0f,
    /** 바닥 위 나뭇결 방향(0=가로, 90=세로). */
    val grainDeg: Float = 0f,
)

/** 그릴 물체 하나. 메쉬 + 배치 행렬 + 재질. */
data class ItemSpec(
    val mesh: MeshKind,
    val model: FloatArray,
    val color: Color4 = Color4.WHITE,
    val texture: TexKind = TexKind.NONE,
    val uvScale: Float = 1f,
    val specular: Float = 0.15f,
    val shininess: Float = 24f,
    val rim: Float = 0.0f,
    val alpha: Float = 1f,
    /** 빛을 받지 않고 색 그대로(발광·가이드). */
    val unlit: Boolean = false,
    /** 유리처럼 뒤가 비치는 물체 — 불투명 물체 뒤에 그린다. */
    val transparent: Boolean = false,
)

/** 바닥에 드리우는 그림자. 빛 방향으로 눕힌 실루엣이다. */
data class ShadowSpec(
    val points: List<Vec3>,
    val alpha: Float,
    val softness: Float = 1f,
)

/**
 * 하늘 대신 화면 뒤에 깔리는 **배경 사진**.
 *
 * [uvTop]/[uvBottom] 은 화면 위·아래 끝에서 각각 보이는 사진의 세로 위치(0=사진 위, 1=사진 아래)다.
 * 이 두 값으로 사진을 밀고 당겨, 사진에 찍힌 책상 끝선을 3D 책상 판의 먼 끝선에 정확히 맞춘다.
 */
data class Backdrop(
    val tex: TexKind,
    val uvTop: Float,
    val uvBottom: Float,
    val tint: Color4 = Color4.WHITE,
)

/** 배경 사진을 3D 책상에 맞춰 앉히는 계산(순수 함수라 테스트로 확인한다). */
object BackdropFit {
    /**
     * 사진 속 책상 끝선([photoEdgeV], 사진 위에서부터 0~1)을 화면의 [deskEdgeV]
     * (위에서부터 0~1) 자리에 놓이게 하는 (uvTop, uvBottom) 을 구한다.
     *
     * [scale] 은 사진을 화면보다 몇 배 크게/작게 볼지다. 1이면 화면 세로와 사진 세로가 1:1.
     */
    fun fit(deskEdgeV: Float, photoEdgeV: Float, scale: Float = 1f): Pair<Float, Float> {
        val s = if (scale <= 0.01f) 1f else scale
        val top = photoEdgeV - deskEdgeV.coerceIn(0f, 1f) * s
        return top to (top + s)
    }

    /** (uvTop, uvBottom) 으로 화면 세로 위치 [screenV](위에서부터) 에 보이는 사진 세로 위치. */
    fun photoVAt(screenV: Float, uvTop: Float, uvBottom: Float): Float =
        uvBottom + (uvTop - uvBottom) * (1f - screenV)
}

/** 화면을 향하는 작은 그림(가루·먼지·불티). */
data class SpriteSpec(
    val pos: Vec3,
    val size: Float,
    val color: Color4,
    val texture: TexKind = TexKind.SOFT,
    val rotationDeg: Float = 0f,
    /** 빛을 더하는 발광(가이드 점). */
    val additive: Boolean = false,
)

/** 한 프레임에 그릴 모든 것. 게임 상태에서 매 프레임 새로 만든다. */
data class Scene(
    val camera: Camera,
    val env: Environment,
    val ground: GroundSpec,
    val items: List<ItemSpec> = emptyList(),
    val shadows: List<ShadowSpec> = emptyList(),
    val sprites: List<SpriteSpec> = emptyList(),
    /** 바닥에 깔리는 그림(발밑 접촉 그림자·먼지 자국). */
    val decals: List<SpriteSpec> = emptyList(),
    /** 화면 앞쪽에 크게 흐려지는 근경(책상 앞 소품 등). */
    val foreground: List<ItemSpec> = emptyList(),
    /** 화면 가장자리에 넘치는 꽃가루·먼지. */
    val motes: List<SpriteSpec> = emptyList(),
    /** 하늘 대신 까는 배경 사진(교실). 없으면 하늘 그라데이션을 그린다. */
    val backdrop: Backdrop? = null,
) {
    /** 이 장면이 화면 안에 들어오는지 검사할 때 쓰는 투영 도우미. */
    fun project(point: Vec3, aspect: Float = Camera.PORTRAIT_ASPECT): FloatArray? =
        camera.projectToUnit(point, aspect)
}

/** 장면을 만들 때 쓰는 도우미. */
object SceneKit {

    /** 바닥에 드리우는 그림자 실루엣: 물체의 여덟 꼭짓점을 빛 방향으로 눕혀 볼록 껍질을 만든다. */
    fun shadowForBox(
        center: Vec3,
        quat: Quat,
        half: Vec3,
        sunDir: Vec3,
        alpha: Float = 0.32f,
        groundY: Float = 0.001f,
        softness: Float = 1f,
    ): ShadowSpec {
        val dir = sunDir.normalized()
        val points = ArrayList<Vec3>(8)
        for (sx in intArrayOf(-1, 1)) {
            for (sy in intArrayOf(-1, 1)) {
                for (sz in intArrayOf(-1, 1)) {
                    val local = Vec3(half.x * sx, half.y * sy, half.z * sz)
                    val world = center + quat.rotate(local)
                    val t = if (dir.y < -1e-4f) (world.y - groundY) / -dir.y else 0f
                    points += Vec3(world.x + dir.x * t, groundY, world.z + dir.z * t)
                }
            }
        }
        return ShadowSpec(convexHull(points), alpha, softness)
    }

    /** 그림자 원(공 모양). */
    fun shadowForSphere(
        center: Vec3,
        radius: Float,
        sunDir: Vec3,
        alpha: Float = 0.30f,
        groundY: Float = 0.001f,
    ): ShadowSpec {
        val dir = sunDir.normalized()
        val t = if (dir.y < -1e-4f) (center.y - radius - groundY) / -dir.y else 0f
        val spread = radius * 1.25f
        val cx = center.x + dir.x * t
        val cz = center.z + dir.z * t
        val pts = ArrayList<Vec3>(16)
        for (i in 0 until 16) {
            val a = 2.0 * PI * i / 16
            pts += Vec3(
                cx + (cos(a) * spread).toFloat(),
                groundY,
                cz + (sin(a) * spread * 1.06f).toFloat(),
            )
        }
        return ShadowSpec(pts, alpha, 1.4f)
    }

    /** XZ 평면 위 점들의 볼록 껍질(반시계 방향). */
    fun convexHull(points: List<Vec3>): List<Vec3> {
        if (points.size < 3) return points
        val sorted = points.sortedWith(compareBy({ it.x }, { it.z }))
        fun cross(o: Vec3, a: Vec3, b: Vec3): Float =
            (a.x - o.x) * (b.z - o.z) - (a.z - o.z) * (b.x - o.x)
        val lower = ArrayList<Vec3>()
        for (p in sorted) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], p) <= 0f) {
                lower.removeAt(lower.size - 1)
            }
            lower += p
        }
        val upper = ArrayList<Vec3>()
        for (p in sorted.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], p) <= 0f) {
                upper.removeAt(upper.size - 1)
            }
            upper += p
        }
        lower.removeAt(lower.size - 1)
        upper.removeAt(upper.size - 1)
        return lower + upper
    }

    /** 무대 좌표(1000 × 1500)를 세계 좌표로. */
    fun field(x: Float, y: Float, height: Float = 0f): Vec3 =
        Vec3(x * WORLD_SCALE, height * WORLD_SCALE, y * WORLD_SCALE)

    private const val WORLD_SCALE = 0.01f
}
