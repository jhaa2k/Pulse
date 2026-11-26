package com.vexo.studio.engine.gl

import android.opengl.GLSurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.rememberUpdatedState
import android.os.SystemClock
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vexo.studio.model.AspectRatioPreset
import kotlin.math.min

/**
 * Jetpack Compose wrapper around [GLSurfaceView] hosting [VexoGlRenderer]
 * as the OpenGL ES 2.0 rendering target for real-time audio visualization shaders.
 */
@Composable
fun VexoPreviewSurface(
    aspectRatio: AspectRatioPreset,
    sceneSnapshot: RenderSceneSnapshot?,
    onTransformGesture: (zoomDelta: Float, panX: Float, panY: Float) -> Unit,
    modifier: Modifier = Modifier,
    fillBounds: Boolean = false,
    targetFps: Int = 60,
    continuousRendering: Boolean = true,
    renderScale: Float = 1f
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val gestureCallback = rememberUpdatedState(onTransformGesture)
    val lastRequest = remember { longArrayOf(0L) }

    val renderer = remember { VexoGlRenderer(context, isExportRenderer = false) }
    val glView = remember {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(2)
            holder.setFormat(android.graphics.PixelFormat.OPAQUE)
            setEGLConfigChooser(8, 8, 8, 8, 0, 0)
            preserveEGLContextOnPause = true
            setRenderer(renderer)
            renderMode = if (continuousRendering) {
                GLSurfaceView.RENDERMODE_CONTINUOUSLY
            } else {
                GLSurfaceView.RENDERMODE_WHEN_DIRTY
            }
        }
    }

    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(viewSize,renderScale){
        if(viewSize.width>0&&viewSize.height>0){
            val scale=renderScale.coerceIn(.5f,1f)
            glView.holder.setFixedSize((viewSize.width*scale).toInt().coerceAtLeast(16),(viewSize.height*scale).toInt().coerceAtLeast(16))
            glView.requestRender()
        }
    }
    LaunchedEffect(continuousRendering, targetFps) {
        glView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        lastRequest[0] = 0L
        glView.requestRender()
    }

    LaunchedEffect(sceneSnapshot) {
        if (sceneSnapshot != null) {
            renderer.submitSnapshot(sceneSnapshot)
            val now = SystemClock.elapsedRealtimeNanos()
            val interval = 1_000_000_000L / targetFps.coerceIn(15, 60)
            if (now - lastRequest[0] >= interval - 2_000_000L) {
                lastRequest[0] = now
                glView.requestRender()
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            glView.onResume()
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glView.onResume()
                Lifecycle.Event.ON_PAUSE -> glView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            glView.queueEvent { renderer.releaseGlResources() }
            glView.onPause()
        }
    }

    val ratio = (aspectRatio.widthRatio.toFloat() / aspectRatio.heightRatio.toFloat())
        .coerceIn(0.25f, 4f)

    BoxWithConstraints(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    gestureCallback.value(zoom, pan.x, pan.y)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val surfaceModifier = if (fillBounds) {
            Modifier.fillMaxSize()
        } else {
            val availW = maxWidth
            val availH = maxHeight
            val fitW = min(availW.value, availH.value * ratio).coerceAtLeast(0f)
            val fitH = fitW / ratio
            Modifier.size(
                width = androidx.compose.ui.unit.Dp(fitW.coerceAtLeast(0f)),
                height = androidx.compose.ui.unit.Dp(fitH.coerceAtLeast(0f))
            )
        }

        AndroidView(
            factory = { glView },
            update = {},
            modifier = surfaceModifier.onSizeChanged{viewSize=it}
        )
    }
}
