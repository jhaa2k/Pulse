package com.vexo.studio.engine.gl

import android.opengl.GLSurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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

@Composable
fun VexoPreviewSurface(
    aspectRatio: AspectRatioPreset,
    sceneSnapshot: RenderSceneSnapshot?,
    onTransformGesture: (zoomDelta: Float, panX: Float, panY: Float) -> Unit,
    modifier: Modifier = Modifier,
    fillBounds: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val renderer = remember { VexoGlRenderer(context, isExportRenderer = false) }
    val glView = remember {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(2)
            preserveEGLContextOnPause = true
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
    }

    LaunchedEffect(sceneSnapshot) {
        if (sceneSnapshot != null) {
            renderer.submitSnapshot(sceneSnapshot)
        }
    }

    DisposableEffect(lifecycleOwner) {
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
        }
    }

    val ratio = aspectRatio.widthRatio.toFloat() / aspectRatio.heightRatio.toFloat()

    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    onTransformGesture(zoom, pan.x, pan.y)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val previewModifier = if (fillBounds) {
            Modifier.fillMaxSize()
        } else if (ratio >= 1f) {
            Modifier
                .fillMaxSize()
                .aspectRatio(ratio, matchHeightConstraintsFirst = false)
        } else {
            Modifier
                .fillMaxHeight()
                .aspectRatio(ratio, matchHeightConstraintsFirst = true)
        }

        AndroidView(
            factory = { glView },
            modifier = previewModifier
        )
    }
}
