package com.game.gametong.render

import android.content.Context
import android.opengl.GLSurfaceView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay

/**
 * 게임 화면의 3D 뷰. 표면(GLSurfaceView)에 장면을 그리고, 그 위에 Compose UI(버튼·글자)를 얹는다.
 */
class GameGlSurfaceView(context: Context, channel: SceneChannel) : GLSurfaceView(context) {
    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(MsaaConfigChooser())
        setRenderer(SceneRenderer(channel))
        renderMode = RENDERMODE_CONTINUOUSLY
        preserveEGLContextOnPause = true
    }
}

/**
 * 4~8배 멀티샘플링(계단 현상 제거)을 우선 고르고, 없으면 기본 설정으로 떨어진다.
 * 구형 기기에서도 절대 검은 화면이 되지 않도록 세 단계로 물러선다.
 */
private class MsaaConfigChooser : GLSurfaceView.EGLConfigChooser {
    override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig? {
        val attempts = listOf(
            intArrayOf(
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 24,
                EGL10.EGL_SAMPLE_BUFFERS, 1,
                EGL10.EGL_SAMPLES, 4,
                EGL10.EGL_RENDERABLE_TYPE, 4,
                EGL10.EGL_NONE,
            ),
            intArrayOf(
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 16,
                EGL10.EGL_SAMPLE_BUFFERS, 1,
                EGL10.EGL_SAMPLES, 2,
                EGL10.EGL_RENDERABLE_TYPE, 4,
                EGL10.EGL_NONE,
            ),
            intArrayOf(
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 16,
                EGL10.EGL_RENDERABLE_TYPE, 4,
                EGL10.EGL_NONE,
            ),
            intArrayOf(
                EGL10.EGL_RED_SIZE, 5,
                EGL10.EGL_GREEN_SIZE, 6,
                EGL10.EGL_BLUE_SIZE, 5,
                EGL10.EGL_DEPTH_SIZE, 16,
                EGL10.EGL_NONE,
            ),
        )
        for (attribs in attempts) {
            val count = IntArray(1)
            if (!egl.eglChooseConfig(display, attribs, null, 0, count)) continue
            if (count[0] <= 0) continue
            val configs = arrayOfNulls<EGLConfig>(count[0])
            if (!egl.eglChooseConfig(display, attribs, configs, count[0], count)) continue
            configs.firstOrNull { it != null }?.let { return it }
        }
        return null
    }
}

/**
 * Compose 화면에 3D 뷰를 끼워 넣는다.
 * [channel] 에 매 프레임 새 장면을 넣으면 GL 스레드가 그것을 그린다.
 */
@androidx.compose.runtime.Composable
fun Game3DView(
    channel: SceneChannel,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = remember { androidx.compose.runtime.mutableStateOf<GameGlSurfaceView?>(null) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            GameGlSurfaceView(context, channel).also { view.value = it }
        },
    )
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> view.value?.onResume()
                Lifecycle.Event.ON_PAUSE -> view.value?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            view.value?.onPause()
        }
    }
}
