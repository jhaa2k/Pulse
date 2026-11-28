package com.vexo.studio.engine.gl

import android.opengl.GLSurfaceView
import android.graphics.Bitmap
import android.view.PixelCopy
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import com.vexo.studio.ui.components.BackdropBlur
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import com.vexo.studio.ui.theme.StudioText as Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.vexo.studio.ui.theme.LocalVexoPalette
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
    renderScale: Float = 1f,
    showDiagnostics:Boolean=false,
    adaptiveQuality:Boolean=false,
    glassBlurDp:Float?=null,
    onGlassFrame:((Bitmap?)->Unit)?=null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val gestureCallback = rememberUpdatedState(onTransformGesture)
    val lastRequest = remember { longArrayOf(0L) }
    var resumed by remember(lifecycleOwner){mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}

    val renderer = remember { VexoGlRenderer(context, isExportRenderer = false) }
    var diagnostics by remember{mutableStateOf(PreviewDiagnostics())}
    var renderError by remember{mutableStateOf<String?>(null)}
    val controller=remember(renderScale,targetFps,adaptiveQuality){AdaptivePreviewController(renderScale,targetFps)}
    var effectiveScale by remember(controller){mutableStateOf(controller.scale)}
    val currentPlaying by rememberUpdatedState(sceneSnapshot?.isPlaying==true)
    LaunchedEffect(showDiagnostics,controller,resumed,continuousRendering){
        renderer.diagnosticsEnabled=showDiagnostics||adaptiveQuality
        var last:PreviewDiagnostics?=renderer.diagnostics
        while(true){
            val d=renderer.diagnostics
            if(showDiagnostics)diagnostics=d
            renderError=renderer.previewError
            if(adaptiveQuality&&resumed&&continuousRendering&&currentPlaying&&d!==last){
                effectiveScale=controller.observe(d.fps,d.cpuFrameMs,renderError!=null)
            }
            last=d
            delay(500)
        }
    }
    val glView = remember {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(2)
            setZOrderOnTop(false)
            holder.setFormat(android.graphics.PixelFormat.OPAQUE)
            setEGLConfigChooser(PreviewEglConfigChooser(
                (context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)?.isLowRamDevice==true
            ))
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
    val callback by rememberUpdatedState(onGlassFrame)
    val density=LocalDensity.current.density
    LaunchedEffect(glView,resumed,glassBlurDp,viewSize,density,onGlassFrame!=null){
        if(!resumed||glassBlurDp==null||onGlassFrame==null||viewSize.width<=0||viewSize.height<=0){callback?.invoke(null);return@LaunchedEffect}
        val scale=(480f/maxOf(viewSize.width,viewSize.height)).coerceAtMost(1f)
        val w=(viewSize.width*scale).toInt().coerceAtLeast(1)
        val h=(viewSize.height*scale).toInt().coerceAtLeast(1)
        while(true){
            if(glView.holder.surface.isValid){
                val captured=copyGlassFrame(glView,w,h)
                if(captured!=null){
                    val blurred=withContext(Dispatchers.Default){runCatching{BackdropBlur.apply(captured,glassBlurDp.coerceIn(0f,48f)*density*scale,480)}.getOrNull()}
                    if(blurred!==captured)captured.recycle()
                    if(blurred!=null)callback?.invoke(blurred)
                }
            }
            delay(300)
        }
    }
    LaunchedEffect(viewSize,effectiveScale){
        if(viewSize.width>0&&viewSize.height>0){
            val scale=effectiveScale.coerceIn(.5f,1f)
            glView.holder.setFixedSize((viewSize.width*scale).toInt().coerceAtLeast(16),(viewSize.height*scale).toInt().coerceAtLeast(16))
            glView.requestRender()
        }
    }
    LaunchedEffect(continuousRendering, targetFps) {
        glView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        lastRequest[0] = 0L
        glView.requestRender()
    }

    // Publish the newest snapshot immediately; request frames at the selected cadence.
    LaunchedEffect(sceneSnapshot){if(sceneSnapshot!=null)renderer.submitSnapshot(sceneSnapshot)}
    val currentSnapshot by rememberUpdatedState(sceneSnapshot)
    LaunchedEffect(targetFps,continuousRendering,glView,resumed){
        if(!resumed)return@LaunchedEffect
        val interval=(1000L/targetFps.coerceIn(15,60)).coerceAtLeast(1)
        var last:RenderSceneSnapshot?=null
        while(true){
            val snap=currentSnapshot
            if(snap!=null&&(continuousRendering||snap!==last)){
                renderer.submitSnapshot(snap);glView.requestRender();last=snap
            }
            delay(interval)
        }
    }

    DisposableEffect(lifecycleOwner) {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            glView.onResume()
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {glView.onResume();resumed=true;glView.requestRender()}
                Lifecycle.Event.ON_PAUSE -> {resumed=false;glView.onPause()}
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
        val fitW=if(fillBounds)maxWidth.value else min(maxWidth.value,maxHeight.value*ratio).coerceAtLeast(0f)
        val fitH=if(fillBounds)maxHeight.value else fitW/ratio
        val density=LocalDensity.current
        val childW=with(density){fitW.dp.roundToPx()}.coerceAtLeast(1)
        val childH=with(density){fitH.dp.roundToPx()}.coerceAtLeast(1)
        AndroidView(
            factory={
                android.widget.FrameLayout(context).apply{
                    setBackgroundColor(android.graphics.Color.BLACK)
                    clipChildren=true
                    clipToPadding=true
                    (glView.parent as? android.view.ViewGroup)?.removeView(glView)
                    addView(glView,android.widget.FrameLayout.LayoutParams(childW,childH,android.view.Gravity.CENTER))
                }
            },
            update={host->
                val lp=glView.layoutParams as android.widget.FrameLayout.LayoutParams
                if(lp.width!=childW||lp.height!=childH){lp.width=childW;lp.height=childH;glView.layoutParams=lp}
                val next=IntSize(childW,childH)
                if(viewSize!=next)viewSize=next
            },
            modifier=Modifier.fillMaxSize()
        )
        if(renderError!=null){
            Text(renderError.orEmpty(),modifier=Modifier.align(Alignment.Center).background(Color.Black.copy(alpha=.9f)).padding(12.dp),style=MaterialTheme.typography.bodySmall,color=LocalVexoPalette.current.textPrimary)
        }
        if(showDiagnostics){
            val palette=LocalVexoPalette.current
            val d=diagnostics
            Text("%.1f FPS · %.1f ms CPU · %d%% preview\n%d × %d · %d comps / %d layers\n%d composition slots · %.1f MiB textures + %.1f MiB FBO".format(d.fps,d.cpuFrameMs,(effectiveScale*100).toInt(),d.width,d.height,d.compositions,d.layers,d.compositionSlots,d.textureBytes/1048576.0,d.framebufferBytes/1048576.0),modifier=Modifier.align(Alignment.TopStart).background(Color.Black.copy(alpha=.85f)).padding(5.dp),style=MaterialTheme.typography.labelSmall,color=palette.accent)
        }
    }
}

/** PixelCopy reads the real GL surface. Only one bounded capture is in flight. */
private suspend fun copyGlassFrame(view:GLSurfaceView,w:Int,h:Int):Bitmap?=suspendCancellableCoroutine{continuation->
    val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
    try{
        PixelCopy.request(view,bitmap,{result->
            if(result==PixelCopy.SUCCESS&&continuation.isActive)continuation.resume(bitmap)
            else{
                bitmap.recycle()
                if(continuation.isActive)continuation.resume(null)
            }
        },Handler(Looper.getMainLooper()))
    }catch(_:IllegalArgumentException){
        bitmap.recycle()
        if(continuation.isActive)continuation.resume(null)
    }
}
