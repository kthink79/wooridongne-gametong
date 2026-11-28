package com.game.gametong.render

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * 셰이더 컴파일·프로그램 링크 helper.
 *
 * 실패하면 조용히 넘어가지 않고 [error] 에 이유를 남긴다(검은 화면의 원인을 찾기 위해).
 */
class GlProgram(
    vertexSource: String,
    fragmentSource: String,
    attributeNames: List<String>,
    uniformNames: List<String>,
) {
    var programId: Int = 0
        private set
    var error: String? = null
        private set

    private val attributes = HashMap<String, Int>()
    private val uniforms = HashMap<String, Int>()

    init {
        build(vertexSource, fragmentSource, attributeNames, uniformNames)
    }

    private fun build(
        vertexSource: String,
        fragmentSource: String,
        attributeNames: List<String>,
        uniformNames: List<String>,
    ) {
        val vs = compile(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (vs == 0 || fs == 0) {
            error = "shader compile failed"
            return
        }
        val id = GLES20.glCreateProgram()
        GLES20.glAttachShader(id, vs)
        GLES20.glAttachShader(id, fs)
        GLES20.glLinkProgram(id)
        val status = IntArray(1)
        GLES20.glGetProgramiv(id, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            error = "link failed: " + GLES20.glGetProgramInfoLog(id)
            GLES20.glDeleteProgram(id)
            GLES20.glDeleteShader(vs)
            GLES20.glDeleteShader(fs)
            return
        }
        programId = id
        for (name in attributeNames) attributes[name] = GLES20.glGetAttribLocation(id, name)
        for (name in uniformNames) uniforms[name] = GLES20.glGetUniformLocation(id, name)
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
    }

    fun use() {
        if (programId != 0) GLES20.glUseProgram(programId)
    }

    fun attrib(name: String): Int = attributes[name] ?: -1
    fun uniform(name: String): Int = uniforms[name] ?: -1

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            error = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    fun release() {
        if (programId != 0) {
            GLES20.glDeleteProgram(programId)
            programId = 0
        }
    }
}

object GlBuffers {
    fun floats(data: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(data)
                position(0)
            }

    fun shorts(data: ShortArray): java.nio.ShortBuffer =
        ByteBuffer.allocateDirect(data.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply {
                put(data)
                position(0)
            }
}

/** 셰이더 소스. GLSL ES 1.00(GL ES 2.0)으로 맞춰 구형 기기에서도 돌아간다. */
object Shaders {

    /** 조명을 받는 물체·바닥. */
    const val LIT_VERTEX = """
        attribute vec3 aPos;
        attribute vec3 aNrm;
        attribute vec2 aUv;
        uniform mat4 uModel;
        uniform mat4 uViewProj;
        uniform mat3 uNormalMat;
        uniform vec2 uUvScale;
        varying vec3 vN;
        varying vec3 vW;
        varying vec2 vUv;
        void main() {
            vec4 world = uModel * vec4(aPos, 1.0);
            vW = world.xyz;
            vN = normalize(uNormalMat * aNrm);
            vUv = aUv * uUvScale;
            gl_Position = uViewProj * world;
        }
    """

    const val LIT_FRAGMENT = """
        precision mediump float;
        varying vec3 vN;
        varying vec3 vW;
        varying vec2 vUv;
        uniform sampler2D uTex;
        uniform float uUseTex;
        uniform vec4 uColor;
        uniform vec3 uSunDir;
        uniform vec3 uSunColor;
        uniform vec3 uAmbSky;
        uniform vec3 uAmbGround;
        uniform vec3 uCamera;
        uniform vec3 uHazeColor;
        uniform vec2 uHazeRange;
        uniform float uHazeStrength;
        uniform float uSpecular;
        uniform float uShininess;
        uniform float uRim;
        uniform float uGloss;
        uniform float uUnlit;
        uniform vec4 uChalk;
        uniform vec3 uChalkColor;

        void main() {
            vec3 n = normalize(vN);
            vec3 V = normalize(uCamera - vW);
            vec3 base = uColor.rgb;
            if (uUseTex > 0.5) {
                base *= texture2D(uTex, vUv).rgb;
            }
            if (uUnlit > 0.5) {
                gl_FragColor = vec4(base, uColor.a);
                return;
            }
            vec3 L = normalize(uSunDir);
            float ndl = max(dot(n, L), 0.0);
            // 하늘광·바닥 반사광(반구 조명)
            vec3 amb = mix(uAmbGround, uAmbSky, n.y * 0.5 + 0.5);
            vec3 color = base * (amb + uSunColor * ndl);

            // 하이라이트
            vec3 H = normalize(L + V);
            float spec = pow(max(dot(n, H), 0.0), uShininess) * uSpecular;
            color += uSunColor * spec;

            // 유리·윤기(가장자리)
            float fres = pow(1.0 - max(dot(n, V), 0.0), 3.0) * uRim;
            color += mix(uAmbSky, vec3(1.0), 0.4) * fres;

            // 코팅 책상의 번들거림
            if (uGloss > 0.0) {
                float g = pow(max(dot(n, H), 0.0), 120.0) * uGloss;
                color += uSunColor * g * 1.4;
            }

            // 분필·페인트 원(바닥에만)
            if (uChalk.w > 0.001) {
                float d = length(vW.xz - uChalk.xy);
                float ring = 1.0 - smoothstep(max(uChalk.w * 0.3, 0.001), uChalk.w, abs(d - uChalk.z));
                float grain = 0.65 + 0.35 * texture2D(uTex, vW.xz * 7.0).r;
                color = mix(color, uChalkColor * (0.85 + 0.15 * ndl), ring * 0.9 * grain);
            }

            // 거리 안개: 무대 안은 걷히고, 무대 밖 먼 바닥만 세기만큼 잠긴다(최대 1이 아닌 hazeStrength).
            float dist = length(uCamera - vW);
            float haze = clamp((dist - uHazeRange.x) / max(uHazeRange.y, 0.001), 0.0, 1.0);
            color = mix(color, uHazeColor, haze * uHazeStrength);

            // 톤 매핑(부드러운 하이라이트 롤오프)
            color = color / (color + vec3(1.0)) * 1.38;
            gl_FragColor = vec4(color, uColor.a);
        }
    """

    /** 하늘·햇빛·비네트. 화면 전체를 덮는 그라데이션. */
    const val SKY_VERTEX = """
        attribute vec3 aPos;
        attribute vec2 aUv;
        varying vec2 vUv;
        void main() {
            vUv = aUv;
            gl_Position = vec4(aPos.xy, 0.999, 1.0);
        }
    """

    const val SKY_FRAGMENT = """
        precision mediump float;
        varying vec2 vUv;
        uniform vec3 uTop;
        uniform vec3 uBottom;
        uniform vec3 uSunColor;
        uniform vec2 uSunPos;
        uniform float uGlow;
        uniform float uVignette;
        uniform float uUseTex;
        uniform sampler2D uTex;
        uniform float uUvTop;
        uniform float uUvBottom;
        uniform vec3 uTint;
        void main() {
            // 배경 사진이 있으면 하늘 대신 사진을 깐다(사진의 책상 끝선을 3D 책상에 맞춰 앉힌다).
            if (uUseTex > 0.5) {
                float pv = mix(uUvBottom, uUvTop, clamp(vUv.y, 0.0, 1.0));
                vec3 photo = texture2D(uTex, vec2(vUv.x, pv)).rgb * uTint;
                vec2 cc = vUv - vec2(0.5, 0.5);
                float vig = 1.0 - uVignette * dot(cc, cc) * 1.6;
                gl_FragColor = vec4(photo * clamp(vig, 0.0, 1.0), 1.0);
                return;
            }
            float t = clamp(vUv.y, 0.0, 1.0);
            vec3 color = mix(uBottom, uTop, pow(t, 0.85));
            float d = length(vUv - uSunPos);
            float glow = exp(-d * d * 18.0) * uGlow;
            float halo = exp(-d * 4.5) * uGlow * 0.55;
            color += uSunColor * (glow + halo);
            // 비네트
            vec2 c = vUv - vec2(0.5, 0.5);
            float vig = 1.0 - uVignette * dot(c, c) * 1.6;
            color *= clamp(vig, 0.0, 1.0);
            gl_FragColor = vec4(color, 1.0);
        }
    """

    /** 정점 색을 쓰는 평면 지오메트리(그림자·가이드·파티클). */
    const val FLAT_VERTEX = """
        attribute vec3 aPos;
        attribute vec2 aUv;
        attribute vec4 aColor;
        uniform mat4 uViewProj;
        varying vec2 vUv;
        varying vec4 vColor;
        void main() {
            vUv = aUv;
            vColor = aColor;
            gl_Position = uViewProj * vec4(aPos, 1.0);
        }
    """

    const val FLAT_FRAGMENT = """
        precision mediump float;
        varying vec2 vUv;
        varying vec4 vColor;
        uniform sampler2D uTex;
        uniform float uUseTex;
        void main() {
            vec4 c = vColor;
            if (uUseTex > 0.5) {
                c.a *= texture2D(uTex, vUv).a;
            }
            gl_FragColor = c;
        }
    """

    /** 정점 색(텍스처의 rgb 도 곱한다). */
    const val COLOR_VERTEX = """
        attribute vec3 aPos;
        attribute vec2 aUv;
        attribute vec4 aColor;
        uniform mat4 uViewProj;
        varying vec2 vUv;
        varying vec4 vColor;
        void main() {
            vUv = aUv;
            vColor = aColor;
            gl_Position = uViewProj * vec4(aPos, 1.0);
        }
    """

    const val COLOR_FRAGMENT = """
        precision mediump float;
        varying vec2 vUv;
        varying vec4 vColor;
        uniform sampler2D uTex;
        void main() {
            vec4 t = texture2D(uTex, vUv);
            gl_FragColor = vec4(vColor.rgb * t.rgb, vColor.a * t.a);
        }
    """
}
