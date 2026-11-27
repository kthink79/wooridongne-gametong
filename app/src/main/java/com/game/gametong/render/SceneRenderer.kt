package com.game.gametong.render

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.util.Log
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

/** 지금 그릴 장면을 담는 그릇. 게임 화면이 매 프레임 새 장면을 넣고, GL 스레드가 읽는다. */
class SceneChannel {
    @Volatile
    var scene: Scene? = null

    @Volatile
    var paused: Boolean = false
}

/**
 * 장면([Scene])을 실제로 그리는 OpenGL ES 2.0 렌더러.
 *
 * - 하늘·햇빛 → 바닥 → 그림자 → 물체(불투명 → 유리) → 파티클 → 가이드 순으로 그린다.
 * - 모든 무늬는 [ProceduralTextures] 로 앱 안에서 굽는다(에셋 없음).
 * - 그림자는 빛 방향으로 눕힌 실루엣이라 물체가 기울면 그림자도 따라 기운다.
 */
class SceneRenderer(private val channel: SceneChannel) : GLSurfaceView.Renderer {

    private var lit: GlProgram? = null
    private var flat: GlProgram? = null
    private var color: GlProgram? = null
    private var sky: GlProgram? = null

    private val meshes = HashMap<MeshKind, GpuMesh>()
    private val textures = HashMap<TexKind, Int>()

    private var viewWidth = 1
    private var viewHeight = 1
    private var skyVbo = 0

    // 매 프레임 만드는 동적 지오메트리
    private var dynamicVbo = 0
    private var dynamicBuffer: FloatBuffer? = null
    private var dynamicCapacity = 0

    private var lastError: String? = null

    /** 이번 프레임 카메라~무대 중심 거리(안개 시작 거리를 여기서 잡는다). */
    private var frameCameraDistance = 1f

    val aspect: Float get() = if (viewHeight <= 0) 1f else viewWidth.toFloat() / viewHeight

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.09f, 0.07f, 0.05f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        // 종이처럼 얇은 메쉬는 뒷면도 보여야 하므로 컬링을 끈다(장면이 가벼워 비용은 무시할 만하다).
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        // 컨텍스트가 새로 만들어졌으면 이전 자원은 이미 사라졌다.
        meshes.clear()
        textures.clear()

        lit = GlProgram(
            Shaders.LIT_VERTEX, Shaders.LIT_FRAGMENT,
            listOf("aPos", "aNrm", "aUv"),
            listOf(
                "uModel", "uViewProj", "uNormalMat", "uUvScale", "uTex", "uUseTex", "uColor",
                "uSunDir", "uSunColor", "uAmbSky", "uAmbGround", "uCamera", "uHazeColor",
                "uHazeRange", "uHazeStrength", "uSpecular", "uShininess", "uRim", "uGloss", "uUnlit",
                "uChalk", "uChalkColor",
            ),
        )
        flat = GlProgram(
            Shaders.FLAT_VERTEX, Shaders.FLAT_FRAGMENT,
            listOf("aPos", "aUv", "aColor"),
            listOf("uViewProj", "uTex", "uUseTex"),
        )
        color = GlProgram(
            Shaders.COLOR_VERTEX, Shaders.COLOR_FRAGMENT,
            listOf("aPos", "aUv", "aColor"),
            listOf("uViewProj", "uTex"),
        )
        sky = GlProgram(
            Shaders.SKY_VERTEX, Shaders.SKY_FRAGMENT,
            listOf("aPos", "aUv"),
            listOf("uTop", "uBottom", "uSunColor", "uSunPos", "uGlow", "uVignette"),
        )

        lit?.error?.let { Log.w(TAG, "lit program: $it") }
        sky?.error?.let { Log.w(TAG, "sky program: $it") }

        createSkyQuad()

        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        dynamicVbo = ids[0]
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewWidth = width.coerceAtLeast(1)
        viewHeight = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, viewWidth, viewHeight)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val scene = channel.scene ?: return
        if (channel.paused) return

        val viewProj = scene.camera.viewProjection(aspect)
        val eye = scene.camera.eye
        frameCameraDistance = (scene.camera.eye - scene.camera.target).length.coerceAtLeast(0.001f)

        drawSky(scene, viewProj)
        drawGround(scene, viewProj, eye)
        drawShadowsAndDecals(scene, viewProj)
        drawItems(scene, viewProj, eye)
        drawSprites(scene, viewProj, eye)
    }

    // ── 하늘 ────────────────────────────────────────────────────

    private fun drawSky(scene: Scene, viewProj: FloatArray) {
        val program = sky ?: return
        if (program.programId == 0) return
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        program.use()
        val sunPoint = scene.camera.eye + scene.env.sunDir.normalized() * (scene.camera.far * 0.5f)
        val projected = scene.camera.projectToUnit(sunPoint, aspect)
        val sunU = projected?.get(0) ?: 0.75f
        val sunV = projected?.let { 1f - it[1] } ?: 0.85f
        GLES20.glUniform3f(program.uniform("uTop"), scene.env.skyTop.r, scene.env.skyTop.g, scene.env.skyTop.b)
        GLES20.glUniform3f(
            program.uniform("uBottom"),
            scene.env.skyBottom.r, scene.env.skyBottom.g, scene.env.skyBottom.b,
        )
        GLES20.glUniform3f(
            program.uniform("uSunColor"),
            scene.env.sunColor.r, scene.env.sunColor.g, scene.env.sunColor.b,
        )
        GLES20.glUniform2f(program.uniform("uSunPos"), sunU, sunV)
        GLES20.glUniform1f(program.uniform("uGlow"), scene.env.sunGlow)
        GLES20.glUniform1f(program.uniform("uVignette"), scene.env.vignette)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, skyVbo)
        val pos = program.attrib("aPos")
        val uv = program.attrib("aUv")
        GLES20.glEnableVertexAttribArray(pos)
        GLES20.glVertexAttribPointer(pos, 3, GLES20.GL_FLOAT, false, 20, 0)
        GLES20.glEnableVertexAttribArray(uv)
        GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 20, 12)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(true)
    }

    private fun createSkyQuad() {
        val data = floatArrayOf(
            -1f, -1f, 0f, 0f, 0f,
            1f, -1f, 0f, 1f, 0f,
            1f, 1f, 0f, 1f, 1f,
            -1f, -1f, 0f, 0f, 0f,
            1f, 1f, 0f, 1f, 1f,
            -1f, 1f, 0f, 0f, 1f,
        )
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        skyVbo = ids[0]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, skyVbo)
        val buffer = GlBuffers.floats(data)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.size * 4, buffer, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    // ── 바닥 ────────────────────────────────────────────────────

    private fun drawGround(scene: Scene, viewProj: FloatArray, eye: com.game.gametong.game.physics3d.Vec3) {
        val program = lit ?: return
        if (program.programId == 0) return
        val ground = scene.ground
        val model = Mat4.trs(
            pos = ground.center,
            quat = com.game.gametong.game.physics3d.Quat.IDENTITY,
            scale = com.game.gametong.game.physics3d.Vec3(ground.width, 1f, ground.depth),
        )
        drawLit(
            program = program,
            kind = MeshKind.PLANE,
            model = model,
            viewProj = viewProj,
            eye = eye,
            env = scene.env,
            color = ground.color,
            texture = ground.texture,
            uvScaleU = ground.uvScale,
            uvScaleV = ground.uvScale,
            specular = 0.12f + ground.gloss,
            shininess = 60f,
            rim = 0f,
            gloss = ground.gloss,
            chalk = ground.chalkCenter?.let {
                floatArrayOf(it.x, it.z, ground.chalkRadius, ground.chalkWidth)
            },
            chalkColor = ground.chalkColor,
            unlit = false,
        )
    }

    // ── 물체 ────────────────────────────────────────────────────

    private fun drawItems(scene: Scene, viewProj: FloatArray, eye: com.game.gametong.game.physics3d.Vec3) {
        val program = lit ?: return
        if (program.programId == 0) return
        // 불투명 먼저, 유리는 뒤에서 앞으로
        val opaque = scene.items.filter { !it.transparent }
        val glass = scene.items.filter { it.transparent }
            .sortedByDescending { (it.model[12] - eye.x) * (it.model[12] - eye.x) + (it.model[14] - eye.z) * (it.model[14] - eye.z) }

        for (item in opaque) {
            if (item.alpha < 1f) {
                GLES20.glDepthMask(false)
            }
            drawLit(
                program = program,
                kind = item.mesh,
                model = item.model,
                viewProj = viewProj,
                eye = eye,
                env = scene.env,
                color = item.color,
                texture = item.texture,
                uvScaleU = item.uvScale,
                uvScaleV = item.uvScale,
                specular = item.specular,
                shininess = item.shininess,
                rim = item.rim,
                gloss = 0f,
                chalk = null,
                chalkColor = Color4.WHITE,
                unlit = item.unlit,
            )
            if (item.alpha < 1f) GLES20.glDepthMask(true)
        }

        if (glass.isNotEmpty()) {
            GLES20.glDepthMask(false)
            for (item in glass) {
                drawLit(
                    program = program,
                    kind = item.mesh,
                    model = item.model,
                    viewProj = viewProj,
                    eye = eye,
                    env = scene.env,
                    color = item.color,
                    texture = item.texture,
                    uvScaleU = item.uvScale,
                    uvScaleV = item.uvScale,
                    specular = item.specular,
                    shininess = item.shininess,
                    rim = item.rim,
                    gloss = 0f,
                    chalk = null,
                    chalkColor = Color4.WHITE,
                    unlit = item.unlit,
                )
            }
            GLES20.glDepthMask(true)
        }
    }

    private fun drawLit(
        program: GlProgram,
        kind: MeshKind,
        model: FloatArray,
        viewProj: FloatArray,
        eye: com.game.gametong.game.physics3d.Vec3,
        env: Environment,
        color: Color4,
        texture: TexKind,
        uvScaleU: Float,
        uvScaleV: Float,
        specular: Float,
        shininess: Float,
        rim: Float,
        gloss: Float,
        chalk: FloatArray?,
        chalkColor: Color4,
        unlit: Boolean,
    ) {
        val gpu = meshOf(kind) ?: return
        program.use()
        GLES20.glUniformMatrix4fv(program.uniform("uModel"), 1, false, model, 0)
        GLES20.glUniformMatrix4fv(program.uniform("uViewProj"), 1, false, viewProj, 0)
        GLES20.glUniformMatrix3fv(program.uniform("uNormalMat"), 1, false, Mat4.normalMatrix(model), 0)
        GLES20.glUniform2f(program.uniform("uUvScale"), uvScaleU, uvScaleV)
        GLES20.glUniform4f(program.uniform("uColor"), color.r, color.g, color.b, color.a)
        GLES20.glUniform1f(program.uniform("uSpecular"), specular)
        GLES20.glUniform1f(program.uniform("uShininess"), shininess)
        GLES20.glUniform1f(program.uniform("uRim"), rim)
        GLES20.glUniform1f(program.uniform("uGloss"), gloss)
        GLES20.glUniform1f(program.uniform("uUnlit"), if (unlit) 1f else 0f)
        GLES20.glUniform3f(program.uniform("uSunDir"), env.sunDir.x, env.sunDir.y, env.sunDir.z)
        GLES20.glUniform3f(program.uniform("uSunColor"), env.sunColor.r, env.sunColor.g, env.sunColor.b)
        GLES20.glUniform3f(program.uniform("uAmbSky"), env.ambientSky.r, env.ambientSky.g, env.ambientSky.b)
        GLES20.glUniform3f(
            program.uniform("uAmbGround"),
            env.ambientGround.r, env.ambientGround.g, env.ambientGround.b,
        )
        GLES20.glUniform3f(program.uniform("uCamera"), eye.x, eye.y, eye.z)
        GLES20.glUniform3f(
            program.uniform("uHazeColor"),
            env.hazeColor.r, env.hazeColor.g, env.hazeColor.b,
        )
        // 안개 범위는 카메라 거리에서 잡는다 — 무대 안(세로 화면의 가까운 앞쪽~먼 모서리)은 걷힌다.
        val hazeRange = Haze.rangeFor(frameCameraDistance, env)
        GLES20.glUniform2f(program.uniform("uHazeRange"), hazeRange[0], hazeRange[1])
        GLES20.glUniform1f(program.uniform("uHazeStrength"), env.hazeStrength)
        GLES20.glUniform1f(program.uniform("uUnlit"), if (unlit) 1f else 0f)
        val chalkValue = chalk ?: floatArrayOf(0f, 0f, 0f, 0f)
        GLES20.glUniform4f(
            program.uniform("uChalk"),
            chalkValue[0], chalkValue[1], chalkValue[2], chalkValue[3],
        )
        GLES20.glUniform3f(
            program.uniform("uChalkColor"),
            chalkColor.r, chalkColor.g, chalkColor.b,
        )

        val tex = textureOf(texture)
        GLES20.glUniform1f(program.uniform("uUseTex"), if (tex != 0) 1f else 0f)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glUniform1i(program.uniform("uTex"), 0)

        val pos = program.attrib("aPos")
        val nrm = program.attrib("aNrm")
        val uv = program.attrib("aUv")
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, gpu.vbo)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, gpu.ibo)
        GLES20.glEnableVertexAttribArray(pos)
        GLES20.glVertexAttribPointer(pos, 3, GLES20.GL_FLOAT, false, VERTEX_STRIDE * 4, 0)
        GLES20.glEnableVertexAttribArray(nrm)
        GLES20.glVertexAttribPointer(nrm, 3, GLES20.GL_FLOAT, false, VERTEX_STRIDE * 4, 12)
        GLES20.glEnableVertexAttribArray(uv)
        GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, VERTEX_STRIDE * 4, 24)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, gpu.indexCount, GLES20.GL_UNSIGNED_SHORT, 0)
        GLES20.glDisableVertexAttribArray(pos)
        GLES20.glDisableVertexAttribArray(nrm)
        GLES20.glDisableVertexAttribArray(uv)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    // ── 그림자·바닥 무늬·파티클·가이드 ──────────────────────────

    private fun drawShadowsAndDecals(scene: Scene, viewProj: FloatArray) {
        if (scene.shadows.isEmpty() && scene.decals.isEmpty()) return
        val byTexture = HashMap<TexKind, ArrayList<Float>>()
        for (shadow in scene.shadows) {
            val verts = byTexture.getOrPut(TexKind.SOFT) { ArrayList() }
            appendShadow(verts, shadow)
        }
        for (decal in scene.decals) {
            val verts = byTexture.getOrPut(decal.texture) { ArrayList() }
            appendGroundQuad(verts, decal)
        }
        GLES20.glDepthMask(false)
        for ((tex, verts) in byTexture) {
            drawDynamic(verts, viewProj, tex, additive = false, useColor = true)
        }
        GLES20.glDepthMask(true)
    }

    /** 바닥에 붙는 그림(발밑 그림자·먼지 자국). 카메라를 보지 않고 바닥에 눕는다. */
    private fun appendGroundQuad(out: ArrayList<Float>, sprite: SpriteSpec) {
        val rad = Math.toRadians(sprite.rotationDeg.toDouble()).toFloat()
        val c = cos(rad)
        val s = sin(rad)
        val half = sprite.size * 0.5f
        val p = sprite.pos
        fun corner(dx: Float, dz: Float, u: Float, v: Float) {
            val x = dx * c - dz * s
            val z = dx * s + dz * c
            pushVertex(
                out,
                p.x + x * half, p.y, p.z + z * half,
                u, v,
                sprite.color.r, sprite.color.g, sprite.color.b, sprite.color.a,
            )
        }
        corner(-1f, -1f, 0f, 1f)
        corner(1f, -1f, 1f, 1f)
        corner(1f, 1f, 1f, 0f)
        corner(-1f, -1f, 0f, 1f)
        corner(1f, 1f, 1f, 0f)
        corner(-1f, 1f, 0f, 0f)
    }

    /** 그림자 실루엣: 볼록 껍질을 부채꼴로 깐다. UV 는 경계 상자에 맞춘다. */
    private fun appendShadow(out: ArrayList<Float>, shadow: ShadowSpec) {
        val pts = shadow.points
        if (pts.size < 3) return
        val cx = pts.sumOf { it.x.toDouble() }.toFloat() / pts.size
        val cz = pts.sumOf { it.z.toDouble() }.toFloat() / pts.size
        val minX = pts.minOf { it.x }
        val maxX = pts.maxOf { it.x }
        val minZ = pts.minOf { it.z }
        val maxZ = pts.maxOf { it.z }
        val spanX = (maxX - minX).coerceAtLeast(0.001f)
        val spanZ = (maxZ - minZ).coerceAtLeast(0.001f)
        val soft = shadow.softness
        fun uv(x: Float, z: Float): Pair<Float, Float> {
            // 경계 상자 안쪽으로 눌러 가장자리가 부드럽게 사라지게
            val u = ((x - minX) / spanX) * (1f - 0.12f * soft) + 0.06f * soft
            val v = ((z - minZ) / spanZ) * (1f - 0.12f * soft) + 0.06f * soft
            return u to v
        }
        val centerUv = uv(cx, cz)
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
            val ua = uv(a.x, a.z)
            val ub = uv(b.x, b.z)
            pushVertex(out, cx, 0.0015f, cz, centerUv.first, centerUv.second, 0f, 0f, 0f, shadow.alpha)
            pushVertex(out, a.x, 0.0015f, a.z, ua.first, ua.second, 0f, 0f, 0f, shadow.alpha)
            pushVertex(out, b.x, 0.0015f, b.z, ub.first, ub.second, 0f, 0f, 0f, shadow.alpha)
        }
    }

    private fun drawSprites(scene: Scene, viewProj: FloatArray, eye: com.game.gametong.game.physics3d.Vec3) {
        val groups = HashMap<TexKind, ArrayList<Float>>()
        val additiveGroups = HashMap<TexKind, ArrayList<Float>>()
        for (sprite in scene.sprites) {
            val target = if (sprite.additive) additiveGroups else groups
            appendSprite(target.getOrPut(sprite.texture) { ArrayList() }, sprite, scene.camera)
        }
        for (sprite in scene.motes) {
            appendSprite(groups.getOrPut(sprite.texture) { ArrayList() }, sprite, scene.camera)
        }
        GLES20.glDepthMask(false)
        for ((tex, verts) in groups) drawDynamic(verts, viewProj, tex, additive = false, useColor = true)
        if (additiveGroups.isNotEmpty()) {
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
            for ((tex, verts) in additiveGroups) drawDynamic(verts, viewProj, tex, additive = true, useColor = true)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        }
        GLES20.glDepthMask(true)
    }

    private fun appendSprite(
        out: ArrayList<Float>,
        sprite: SpriteSpec,
        camera: Camera,
    ) {
        val (right, up) = Mat4.cameraBasis(camera.eye, camera.target, camera.up)
        val rad = Math.toRadians(sprite.rotationDeg.toDouble()).toFloat()
        val c = cos(rad)
        val s = sin(rad)
        val rx = right * c + up * s
        val ry = up * c + right * (-s)
        val half = sprite.size * 0.5f
        val p = sprite.pos
        fun corner(sx: Float, sy: Float, u: Float, v: Float) {
            pushVertex(
                out,
                p.x + rx.x * half * sx + ry.x * half * sy,
                p.y + rx.y * half * sx + ry.y * half * sy,
                p.z + rx.z * half * sx + ry.z * half * sy,
                u, v,
                sprite.color.r, sprite.color.g, sprite.color.b, sprite.color.a,
            )
        }
        corner(-1f, -1f, 0f, 1f)
        corner(1f, -1f, 1f, 1f)
        corner(1f, 1f, 1f, 0f)
        corner(-1f, -1f, 0f, 1f)
        corner(1f, 1f, 1f, 0f)
        corner(-1f, 1f, 0f, 0f)
    }

    private fun pushVertex(
        out: ArrayList<Float>,
        x: Float, y: Float, z: Float,
        u: Float, v: Float,
        r: Float, g: Float, b: Float, a: Float,
    ) {
        out.add(x)
        out.add(y)
        out.add(z)
        out.add(u)
        out.add(v)
        out.add(r)
        out.add(g)
        out.add(b)
        out.add(a)
    }

    private fun drawDynamic(
        verts: ArrayList<Float>,
        viewProj: FloatArray,
        texture: TexKind,
        additive: Boolean,
        useColor: Boolean,
    ) {
        if (verts.isEmpty()) return
        val program = if (useColor) color ?: return else flat ?: return
        if (program.programId == 0) return
        val data = FloatArray(verts.size)
        for (i in verts.indices) data[i] = verts[i]
        var buffer = dynamicBuffer
        if (buffer == null || data.size > dynamicCapacity) {
            dynamicCapacity = maxOf(data.size, 4096)
            buffer = GlBuffers.floats(FloatArray(dynamicCapacity))
            dynamicBuffer = buffer
        }
        buffer.clear()
        buffer.put(data)
        buffer.position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, dynamicVbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.size * 4, buffer, GLES20.GL_STREAM_DRAW)
        program.use()
        GLES20.glUniformMatrix4fv(program.uniform("uViewProj"), 1, false, viewProj, 0)
        val tex = textureOf(if (additive) TexKind.GLOW else texture)
        if (program === color) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
            GLES20.glUniform1i(program.uniform("uTex"), 0)
        } else {
            GLES20.glUniform1f(program.uniform("uUseTex"), if (tex != 0) 1f else 0f)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
            GLES20.glUniform1i(program.uniform("uTex"), 0)
        }
        val pos = program.attrib("aPos")
        val uv = program.attrib("aUv")
        val col = program.attrib("aColor")
        val stride = 9 * 4
        GLES20.glEnableVertexAttribArray(pos)
        GLES20.glVertexAttribPointer(pos, 3, GLES20.GL_FLOAT, false, stride, 0)
        GLES20.glEnableVertexAttribArray(uv)
        GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, stride, 12)
        GLES20.glEnableVertexAttribArray(col)
        GLES20.glVertexAttribPointer(col, 4, GLES20.GL_FLOAT, false, stride, 20)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, data.size / 9)
        GLES20.glDisableVertexAttribArray(pos)
        GLES20.glDisableVertexAttribArray(uv)
        GLES20.glDisableVertexAttribArray(col)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    // ── GPU 자원 ─────────────────────────────────────────────────

    private fun meshOf(kind: MeshKind): GpuMesh? {
        meshes[kind]?.let { return it }
        val data = Meshes.of(kind)
        val ids = IntArray(2)
        GLES20.glGenBuffers(2, ids, 0)
        val gpu = GpuMesh(ids[0], ids[1], data.indexCount)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, gpu.vbo)
        val vertexBuffer = GlBuffers.floats(data.vertexData)
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            data.vertexData.size * 4,
            vertexBuffer,
            GLES20.GL_STATIC_DRAW,
        )
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, gpu.ibo)
        val indexBuffer = GlBuffers.shorts(data.indices)
        GLES20.glBufferData(
            GLES20.GL_ELEMENT_ARRAY_BUFFER,
            data.indices.size * 2,
            indexBuffer,
            GLES20.GL_STATIC_DRAW,
        )
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
        meshes[kind] = gpu
        return gpu
    }

    private fun textureOf(kind: TexKind): Int {
        if (kind == TexKind.NONE) return 0
        textures[kind]?.let { return it }
        val pixels = ProceduralTextures.pixels(kind)
        val size = ProceduralTextures.DEFAULT_SIZE
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        bitmap.recycle()
        textures[kind] = ids[0]
        return ids[0]
    }

    private class GpuMesh(val vbo: Int, val ibo: Int, val indexCount: Int)

    companion object {
        private const val TAG = "GameTong3D"
    }
}
