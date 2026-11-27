package com.vexo.studio.engine.gl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix as AndroidMatrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import com.vexo.studio.engine.audio.AudioProviderSnapshot
import com.vexo.studio.engine.scene.KeyframeEvaluator
import com.vexo.studio.engine.scene.MeasureEvaluator
import com.vexo.studio.engine.scene.ParticleSimulator
import com.vexo.studio.engine.scene.SceneGraph
import com.vexo.studio.model.BarsSegmentType
import com.vexo.studio.model.BarsShapePath
import com.vexo.studio.model.BlendMode
import com.vexo.studio.model.CompositionNode
import com.vexo.studio.model.ImageShape
import com.vexo.studio.model.ImageSourceType
import com.vexo.studio.model.LayerCommon
import com.vexo.studio.model.LayerConfig
import com.vexo.studio.model.LayerNode
import com.vexo.studio.model.MirrorMode
import com.vexo.studio.model.ShapeKind
import com.vexo.studio.model.VexoProject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class TrackMetadataSnapshot(
    val title: String = "No Track",
    val artist: String = "Vexo Studio",
    val album: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val artwork: Bitmap? = null,
    val artworkVersion: Int = 0
)

data class RenderSceneSnapshot(
    val project: VexoProject,
    val audioSnapshots: Map<Int, AudioProviderSnapshot>,
    val trackMeta: TrackMetadataSnapshot,
    val isPlaying: Boolean,
    val totalTimeSec: Float,
    val playingTimeSec: Float,
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val showSafeArea: Boolean = false,
    val previewCompositionIndex: Int = 0
)

/**
 * Shared OpenGL ES 2.0 renderer used by both live GLSurfaceView preview and
 * deterministic offline MediaCodec EGL video export.
 */
class VexoGlRenderer(
    private val context: Context,
    private val isExportRenderer: Boolean = false,
    private val highResolutionTextures: Boolean = true
) : GLSurfaceView.Renderer {

    private val pendingScene = AtomicReference<RenderSceneSnapshot?>()
    private val fboPool = FboPool()

    private var colorShader: GlShaderProgram? = null
    private var imageShader: GlShaderProgram? = null
    private var blurShader: GlShaderProgram? = null
    private var edgeShader: GlShaderProgram? = null
    private val edgeMatrix=FloatArray(16)
    private data class StaticBlurKey(val source:Int,val width:Int,val height:Int,val targetW:Int,val targetH:Int,val sigma:Float,val quality:com.vexo.studio.model.BlurQuality)
    private val staticBlurResults=mutableMapOf<String,Pair<StaticBlurKey,Int>>()
    private var rgbSplitShader: GlShaderProgram? = null
    private var mirrorShader: GlShaderProgram? = null
    private var particleShader: GlShaderProgram? = null
    private var motionShader: GlShaderProgram? = null
    private val previousMotion=mutableMapOf<String,com.vexo.studio.engine.scene.MotionPose>()
    private val measureFilter=com.vexo.studio.engine.scene.ReactiveMeasureFilter()
    private val previousMotionTarget=mutableMapOf<String,Int>()
    private var particleUv = allocFloatBuffer(12)
    private val fonts = mutableMapOf<String,Typeface>()

    private var viewportW = 1280
    private var viewportH = 720
    private var lastFrameNs = System.nanoTime()

    @Volatile
    var lastFrameDurationMs: Float = 0f
        private set
    @Volatile var diagnosticsEnabled=false
    @Volatile var diagnostics=PreviewDiagnostics()
        private set
    private val diagnosticCounter=FrameDiagnosticsCounter()

    // Reusable buffers (zero per-frame large allocations)
    private val mvpMatrix = FloatArray(16)
    private val orthoMatrix = FloatArray(16)
    private val tempMatrix = FloatArray(16)
    private val quadPosBuf: FloatBuffer = allocFloatBuffer(8)
    private val quadUvBuf: FloatBuffer = allocFloatBuffer(8).apply {
        put(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))
        position(0)
    }
    private val quadUvFlipBuf: FloatBuffer = allocFloatBuffer(8).apply {
        put(floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f))
        position(0)
    }

    private val quadUvHorizontalBuf = allocFloatBuffer(8).apply { put(floatArrayOf(1f, 0f, 0f, 0f, 1f, 1f, 0f, 1f)); position(0) }
    private val quadUvBothBuf = allocFloatBuffer(8).apply { put(floatArrayOf(1f, 1f, 0f, 1f, 1f, 0f, 0f, 0f)); position(0) }
    private var vertexScratch = FloatArray(512 * 12)
    private var colorScratch = FloatArray(512 * 24)
    private var vertexBuf: FloatBuffer = allocFloatBuffer(vertexScratch.size)
    private var colorBuf: FloatBuffer = allocFloatBuffer(colorScratch.size)

    // Caches & simulation state
    private val videoFrames = VideoFrameTextures(context,isExportRenderer)
    private val textureCache = LinkedHashMap<String,Int>(32,.75f,true)
    private val failedImageUntil=LinkedHashMap<String,Long>()
    private var imageDecodeLimit=2048
    private var gpuTextureLimit=2048
    private val lowRamDevice=(context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)?.isLowRamDevice==true
    private val bitmapSizes = mutableMapOf<Int, Pair<Int, Int>>()
    private val bitmapKeyColors=mutableMapOf<Int,Int>()
    private val particleSims = mutableMapOf<String, ParticleSimulator>()
    private val barDelayHistory = mutableMapOf<String, ArrayDeque<FloatArray>>()
    private var lastProjectId: String? = null
    private var graphProject: com.vexo.studio.model.VexoProject?=null
    private var animationTracks=com.vexo.studio.engine.scene.PreparedAnimationIndex.EMPTY
    private var cachedGraph: com.vexo.studio.engine.scene.ResolvedSceneGraph?=null
    private var cachedSlots: com.vexo.studio.engine.scene.CompositionSlotPlan?=null
    @Volatile var previewError:String?=null
        private set
    private var cachedReachable: Set<Int> = emptySet()
    private var cachedDisplay = -1
    private var cachedLayerIds: Set<String> = emptySet()
    private var cachedTrails: Set<String> = emptySet()
    private var lastPositionMs = -1L
    private var lastArtworkVersion = -1
    private var artworkTexId = 0

    fun submitSnapshot(snapshot: RenderSceneSnapshot) {
        pendingScene.set(snapshot)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        initGlResources()
    }

    private fun resetSizeDependentBuffers(){
        staticBlurResults.clear()
        fboPool.releaseAll()
        previousMotion.clear();previousMotionTarget.clear();measureFilter.clear()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        if(viewportW!=width.coerceAtLeast(16)||viewportH!=height.coerceAtLeast(16)){
            resetSizeDependentBuffers()
        }
        viewportW = width.coerceAtLeast(16)
        viewportH = height.coerceAtLeast(16)
        GLES20.glViewport(0, 0, viewportW, viewportH)
    }

    override fun onDrawFrame(gl: GL10?) {
        val nowNs = System.nanoTime()
        val deltaSec = ((nowNs - lastFrameNs) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        lastFrameNs = nowNs

        val snap = pendingScene.get()
        if (snap != null) {
            try{
                renderScene(snap, deltaSec, targetWidth = viewportW, targetHeight = viewportH)
                previewError=null
            }catch(e:IllegalStateException){
                previewError=e.message?.take(240)?: "Preview rendering failed"
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0)
                GLES20.glViewport(0,0,viewportW,viewportH)
                clearBackground(0xFF000000.toInt())
            }
        } else {
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        }

        lastFrameDurationMs = (System.nanoTime() - nowNs) / 1_000_000f
        if(diagnosticsEnabled){
            diagnosticCounter.frame(nowNs,lastFrameDurationMs)?.let{(fps,ms)->
                diagnostics=PreviewDiagnostics(fps,ms,viewportW,viewportH,cachedReachable.size,cachedLayerIds.size,bitmapSizes.values.sumOf{it.first.toLong()*it.second*4L},fboPool.allocatedBytes(),cachedSlots?.slotCount?:0)
            }
        }else diagnosticCounter.reset()
    }

    fun initGlResources() {
        releaseGlResources()
        colorShader = GlShaderProgram(VexoShaders.COLOR_VERTEX, VexoShaders.COLOR_FRAGMENT)
        imageShader = GlShaderProgram(VexoShaders.TEXTURE_VERTEX, VexoShaders.IMAGE_FRAGMENT)
        blurShader = GlShaderProgram(VexoShaders.TEXTURE_VERTEX, VexoShaders.BLUR_FRAGMENT)
        edgeShader=runCatching{GlShaderProgram(VexoShaders.TEXTURE_VERTEX,VexoShaders.EDGE_AA_FRAGMENT)}.getOrNull()
        rgbSplitShader = GlShaderProgram(VexoShaders.TEXTURE_VERTEX, VexoShaders.RGB_SPLIT_FRAGMENT)
        mirrorShader = GlShaderProgram(VexoShaders.TEXTURE_VERTEX, VexoShaders.MIRROR_FRAGMENT)
        motionShader = GlShaderProgram(VexoShaders.TEXTURE_VERTEX,VexoShaders.MOTION_FRAGMENT)
        particleShader = GlShaderProgram(VexoShaders.PARTICLE_VERTEX,VexoShaders.PARTICLE_FRAGMENT)
        val maxTexture=IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE,maxTexture,0)
        gpuTextureLimit=maxTexture[0].coerceAtLeast(16)
        imageDecodeLimit=TextureDecodePolicy.limit(false,lowRamDevice,highResolutionTextures,2048,gpuTextureLimit)
        ensureInternalTextures()
    }

    fun releaseGlResources() {
        videoFrames.close()
        diagnosticCounter.reset();diagnostics=PreviewDiagnostics()
        graphProject=null;animationTracks=com.vexo.studio.engine.scene.PreparedAnimationIndex.EMPTY;cachedGraph=null;cachedSlots=null;previewError=null;cachedReachable=emptySet();cachedDisplay=-1;cachedLayerIds=emptySet();cachedTrails=emptySet()
        particleSims.clear();barDelayHistory.clear()
        lastProjectId=null;lastPositionMs=-1L;lastArtworkVersion=-1
        particleShader?.release(); particleShader=null
        motionShader?.release(); motionShader=null
        previousMotion.clear(); previousMotionTarget.clear(); measureFilter.clear()
        fonts.clear()
        colorShader?.release(); colorShader = null
        imageShader?.release(); imageShader = null
        blurShader?.release(); blurShader = null
        edgeShader?.release();edgeShader=null
        rgbSplitShader?.release(); rgbSplitShader = null
        mirrorShader?.release(); mirrorShader = null
        staticBlurResults.clear()
        fboPool.releaseAll()
        bitmapSizes.clear()
        bitmapKeyColors.clear()
        failedImageUntil.clear()
        if (textureCache.isNotEmpty()) {
            val ids = textureCache.values.toIntArray()
            GLES20.glDeleteTextures(ids.size, ids, 0)
            textureCache.clear()
        }
        if (artworkTexId != 0) {
            bitmapSizes.remove(artworkTexId);bitmapKeyColors.remove(artworkTexId)
            GLES20.glDeleteTextures(1, intArrayOf(artworkTexId), 0)
            artworkTexId = 0
        }
    }

    fun renderScene(
        scene: RenderSceneSnapshot,
        deltaSec: Float,
        targetWidth: Int,
        targetHeight: Int
    ) {
        if (colorShader == null) initGlResources()
        if (viewportW != targetWidth.coerceAtLeast(16) || viewportH != targetHeight.coerceAtLeast(16)) {
            resetSizeDependentBuffers()
        }
        viewportW = targetWidth.coerceAtLeast(16)
        viewportH = targetHeight.coerceAtLeast(16)
        imageDecodeLimit=TextureDecodePolicy.limit(isExportRenderer,lowRamDevice,highResolutionTextures,maxOf(viewportW,viewportH),gpuTextureLimit)

        val position = scene.trackMeta.positionMs
        if (lastProjectId != scene.project.id || (lastPositionMs >= 0L && (position < lastPositionMs - 100L || position - lastPositionMs > 750L))) {
            fboPool.releaseByPrefix("trail_")
            particleSims.clear()
            barDelayHistory.clear()
            previousMotion.clear(); previousMotionTarget.clear(); measureFilter.clear()
        }
        if (lastProjectId != scene.project.id) { fboPool.releaseByPrefix("comp_"); videoFrames.close(); failedImageUntil.clear() }
        lastProjectId = scene.project.id
        lastPositionMs = position
        if (scene.trackMeta.artworkVersion != lastArtworkVersion || (artworkTexId == 0 && scene.trackMeta.artwork != null)) {
            lastArtworkVersion = scene.trackMeta.artworkVersion
            uploadArtworkTexture(scene.trackMeta.artwork)
        }

        val compTextures = mutableMapOf<Int, Int>()
        val displayIndex=if(isExportRenderer)0 else scene.previewCompositionIndex.takeIf{idx->scene.project.compositions.any{it.index==idx}}
            ?:scene.project.compositions.firstOrNull{it.index==0}?.index?:scene.project.compositions.firstOrNull()?.index?:0
        if(graphProject!==scene.project || cachedDisplay!=displayIndex){
            if(graphProject!==scene.project)animationTracks=com.vexo.studio.engine.scene.PreparedAnimationIndex.create(scene.project.keyframes)
            graphProject=scene.project;cachedDisplay=displayIndex
            cachedGraph=SceneGraph.resolveOutput(scene.project,displayIndex)
            cachedReachable=checkNotNull(cachedGraph).order.mapTo(linkedSetOf()){it.index}
            cachedSlots=com.vexo.studio.engine.scene.CompositionSlotPlan.create(checkNotNull(cachedGraph).order,cachedReachable,displayIndex)
            val activeLayers=scene.project.compositions.filter{it.index in cachedReachable}.flatMap{it.layers}.filter{it.common.visible}
            cachedLayerIds=activeLayers.map{it.common.id}.toSet()
            cachedTrails=activeLayers.filter{it.config is LayerConfig.MotionBlurEffect}.map{it.common.id}.toSet()
            previousMotion.keys.retainAll(cachedTrails)
            measureFilter.retain(cachedLayerIds)
            previousMotionTarget.keys.retainAll(cachedTrails)
            particleSims.keys.retainAll(cachedLayerIds)
            barDelayHistory.keys.removeAll{it.removeSuffix("_waveform").removeSuffix("_segment2") !in cachedLayerIds}
            fboPool.retainSceneBuffers((0 until checkNotNull(cachedSlots).slotCount).toSet(),cachedTrails)
            if(activeLayers.none{it.config is LayerConfig.BlurEffect})fboPool.releaseByPrefix("blur_")
            if(activeLayers.none{(it.config as? LayerConfig.Image)?.let{cfg->cfg.blurEnabled||cfg.blurredBorder}==true}){staticBlurResults.clear();fboPool.releaseByPrefix("image_blur_")}
        }
        val resolved=checkNotNull(cachedGraph)
        check(!resolved.hasCycle){resolved.cycleDetails?:"Cyclic composition dependency"}
        val slots=checkNotNull(cachedSlots)
        val smoothOffscreen=scene.project.exportSettings.offscreenSmoothEdges&&edgeShader!=null&&
            (if(isExportRenderer)scene.project.exportSettings.smoothEdges else !lowRamDevice)
        if(!smoothOffscreen)fboPool.releaseByPrefix("edge_scratch")
        // Render in dependency order, reusing only slots whose previous output is dead.
        for (comp in resolved.order) {
            if (comp.index !in cachedReachable || comp.index == displayIndex) continue
            val slot=slots.slotByComposition.getValue(comp.index)
            slots.retireBefore[comp.index].orEmpty().forEach{compTextures.remove(it)}
            val fbo = fboPool.obtain("comp_$slot", viewportW, viewportH)
            val smooth=smoothOffscreen&&com.vexo.studio.engine.scene.OffscreenEdges.hasGeometry(comp)
            val target=if(smooth)fboPool.obtain("edge_scratch",fbo.width,fbo.height)else fbo
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, target.fboId)
            GLES20.glViewport(0, 0, target.width, target.height)
            clearBackground(if(comp.index==0)scene.project.canvas.backgroundColorArgb else 0x00000000)
            renderComposition(comp, scene, deltaSec, target.width, target.height, compTextures,applyCamera=false)
            if(smooth)resolveCompositionEdges(target,fbo)
            compTextures[comp.index] = fbo.textureId
        }

        // Render Final Composition (index 0) to default framebuffer
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, viewportW, viewportH)
        clearBackground(scene.project.canvas.backgroundColorArgb)

        val finalComp = resolved.order.firstOrNull { it.index == displayIndex }
        if (finalComp != null) {
            renderComposition(finalComp, scene, deltaSec, viewportW, viewportH, compTextures,applyCamera=true)
        }

        if (!isExportRenderer && scene.showSafeArea) {
            drawSafeAreaOverlay(viewportW, viewportH)
        }
        trimTextureCache()

    }

    private fun resolveCompositionEdges(source:GlFbo,destination:GlFbo){
        val shader=checkNotNull(edgeShader)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,destination.fboId)
        GLES20.glViewport(0,0,destination.width,destination.height)
        GLES20.glDisable(GLES20.GL_BLEND)
        shader.use()
        Matrix.orthoM(edgeMatrix,0,0f,destination.width.toFloat(),destination.height.toFloat(),0f,-1f,1f)
        GLES20.glUniformMatrix4fv(shader.uniform("uMvp"),1,false,edgeMatrix,0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,source.textureId)
        GLES20.glUniform1i(shader.uniform("uTex"),0)
        GLES20.glUniform2f(shader.uniform("uPixel"),1f/source.width,1f/source.height)
        drawTexturedQuad(shader,0f,0f,destination.width.toFloat(),destination.height.toFloat(),flipV=true)
    }

    private fun renderComposition(
        comp: CompositionNode,
        scene: RenderSceneSnapshot,
        deltaSec: Float,
        w: Int,
        h: Int,
        compTextures: Map<Int, Int>,
        applyCamera:Boolean
    ) {
        Matrix.orthoM(orthoMatrix, 0, 0f, w.toFloat(), h.toFloat(), 0f, -1f, 1f)
        if (!isExportRenderer && applyCamera && (scene.zoom != 1f || scene.panX != 0f || scene.panY != 0f)) {
            Matrix.setIdentityM(tempMatrix, 0)
            Matrix.translateM(tempMatrix, 0, w * 0.5f + scene.panX, h * 0.5f + scene.panY, 0f)
            Matrix.scaleM(tempMatrix, 0, scene.zoom, scene.zoom, 1f)
            Matrix.translateM(tempMatrix, 0, -w * 0.5f, -h * 0.5f, 0f)
            Matrix.multiplyMM(orthoMatrix, 0, orthoMatrix.copyOf(), 0, tempMatrix, 0)
        }

        val camera=scene.project.canvas.camera
        if(applyCamera&&comp.index==0&&camera.enabled){
            val pose=com.vexo.studio.engine.scene.CinematicCamera.evaluate(camera,scene.audioSnapshots[camera.providerIndex],scene.trackMeta.positionMs,w,h)
            Matrix.setIdentityM(tempMatrix,0)
            Matrix.translateM(tempMatrix,0,w*.5f+pose.offsetX,h*.5f+pose.offsetY,0f)
            Matrix.rotateM(tempMatrix,0,pose.rotationDeg,0f,0f,1f)
            Matrix.scaleM(tempMatrix,0,pose.zoom,pose.zoom,1f)
            Matrix.translateM(tempMatrix,0,-w*.5f,-h*.5f,0f)
            Matrix.multiplyMM(orthoMatrix,0,orthoMatrix.copyOf(),0,tempMatrix,0)
        }

        for (layer in comp.layers) {
            if (!layer.common.visible) continue
            renderLayer(layer, scene, deltaSec, w, h, compTextures)
        }
    }

    private fun renderLayer(
        layer: LayerNode,
        scene: RenderSceneSnapshot,
        deltaSec: Float,
        w: Int,
        h: Int,
        compTextures: Map<Int, Int>
    ) {
        val tracks=animationTracks.forLayer(layer.common.id)
        val c = com.vexo.studio.engine.scene.TransformAnimation.evaluate(layer.common,tracks,scene.trackMeta.positionMs)
        val providerIdx = when (val cfg = layer.config) {
            is LayerConfig.Bars -> cfg.audioProviderIndex
            else -> c.measurePos.providerIndex
        }
        val audioSnap = scene.audioSnapshots[providerIdx]
        val trackNorm = if (scene.trackMeta.durationMs > 0) {
            (scene.trackMeta.positionMs.toFloat() / scene.trackMeta.durationMs).coerceIn(0f, 1f)
        } else 0f

        val keyframePos=if(c.measurePos.measureWhat==com.vexo.studio.model.MeasureWhat.KEYFRAME)tracks.evaluate("posX",scene.trackMeta.positionMs,0f)else 0f

        fun measured(key:String,m:com.vexo.studio.model.MeasureConfig,k:Float?=null):com.vexo.studio.engine.scene.EvaluatedPair {
            val raw=MeasureEvaluator.evaluatePair(m,scene.audioSnapshots[m.providerIndex],scene.totalTimeSec,scene.playingTimeSec,trackNorm,scene.isPlaying,k)
            return measureFilter.apply(c.id,key,m,raw,deltaSec)
        }
        fun scalar(key:String,m:com.vexo.studio.model.MeasureConfig):Float {
            val pair=measured(key,m)
            return MeasureEvaluator.scalarValue(m,pair)
        }
        val dPos=measured("position",c.measurePos,keyframePos)
        val dScale=measured("scale",c.measureScale)
        val dRot=scalar("rotation",c.measureRot)
        val dOpacity=scalar("opacity",c.measureOpacity)

        fun anchored(value: Float, anchor: com.vexo.studio.model.AnchorMode): Float = when(anchor) {
            com.vexo.studio.model.AnchorMode.UNSET -> value
            com.vexo.studio.model.AnchorMode.START -> value-.5f
            com.vexo.studio.model.AnchorMode.CENTER -> value
            com.vexo.studio.model.AnchorMode.END -> value+.5f
        }
        val tx = (anchored(c.posX,c.anchorX) + dPos.x) * w
        val ty = (anchored(c.posY,c.anchorY) + dPos.y) * h
        val sx = c.scaleX + dScale.x
        val sy = if (c.scaleIsUniform) sx else (c.scaleY + dScale.y)
        val shape=layer.config as? LayerConfig.Shape
        val rot = c.rotationDeg + dRot * 20f + ((scene.trackMeta.positionMs/1000.0*(shape?.spin?:0f))%360.0).toFloat()
        val opacity = (c.opacity + dOpacity).coerceIn(0f, 1f)*com.vexo.studio.engine.scene.LayerTimingMath.opacity(c.timing,scene.trackMeta.positionMs,scene.trackMeta.durationMs)
        if(opacity<=0f)return

        Matrix.setIdentityM(tempMatrix, 0)
        Matrix.translateM(tempMatrix, 0, tx, ty, 0f)
        if (rot != 0f) Matrix.rotateM(tempMatrix, 0, rot, 0f, 0f, 1f)
        if (sx != 1f || sy != 1f) Matrix.scaleM(tempMatrix, 0, sx, sy, 1f)
        Matrix.translateM(tempMatrix, 0, -c.alignmentX * w, -c.alignmentY * h, 0f)
        Matrix.multiplyMM(mvpMatrix, 0, orthoMatrix, 0, tempMatrix, 0)

        applyBlendMode(c.blendMode)

        when (val cfg = com.vexo.studio.engine.scene.LayerPropertyAnimation.evaluate(layer,tracks,scene.trackMeta.positionMs)) {
            is LayerConfig.Bars -> {
                val offset=scalar("gradient",cfg.colorBlendMeasure)
                val measured=cfg.copy(colorBlendOffset=MeasureEvaluator.colorProgress(cfg.colorBlendMeasure,cfg.colorBlendOffset+offset))
                drawBarsLayer(c.id, com.vexo.studio.engine.scene.DynamicSpectrum.evaluate(measured,audioSnap,scene.trackMeta.positionMs), audioSnap, opacity, w, h)
            }
            is LayerConfig.Image -> drawImageLayer(cfg, scene, MeasureEvaluator.colorProgress(cfg.measureColorBlend,scalar("imageColor",cfg.measureColorBlend)),scalar("imageCreative",cfg.creative.audio), opacity, w, h, compTextures)
            is LayerConfig.Text -> drawTextLayer(cfg, scene, scalar("textAnimation",cfg.animationAudio), opacity, w, h)
            is LayerConfig.Shape -> {
                drawShapeLayer(cfg.copy(radius=(cfg.radius+scalar("shapeRadius",cfg.pulse)*.15f).coerceIn(.005f,.8f),strokeWidth=(cfg.strokeWidth+scalar("shapeStroke",cfg.strokePulse)*12f).coerceIn(0f,32f)),opacity,w,h)
            }
            is LayerConfig.Particles -> drawParticlesLayer(c.id, cfg, scene, deltaSec, opacity, w, h,scalar("particleSpeed",cfg.measureOverallSpeed))
            is LayerConfig.BlurEffect -> drawBlurEffect(cfg, scalar("blur",cfg.measureBlur), opacity, w, h, compTextures)
            is LayerConfig.RgbSplitEffect -> drawRgbSplitEffect(cfg,measured("rgbSplit",cfg.splitMeasure),
                measured("rgbMultiplier",cfg.splitMultiplierMeasure).x,opacity,w,h,compTextures)
            is LayerConfig.MotionBlurEffect -> {
                val gain=scalar("motionAmount",cfg.blurAmountMeasure)
                drawMotionBlurEffect(c.id,cfg.copy(blurAmountMultiplier=(cfg.blurAmountMultiplier+gain).coerceIn(0f,3f)), com.vexo.studio.engine.scene.MotionPose(tx/w,ty/h,rot,sx,sy), deltaSec, opacity, w, h, compTextures)
            }
            is LayerConfig.MirrorEffect -> drawMirrorEffect(cfg, opacity, w, h, compTextures)
        }
    }

    private fun drawBarsLayer(
        layerId: String,
        cfg: LayerConfig.Bars,
        audioSnap: AudioProviderSnapshot?,
        opacity: Float,
        w: Int,
        h: Int
    ) {
        if (cfg.segment1 == BarsSegmentType.NONE && cfg.segment2 == BarsSegmentType.NONE) return
        if (cfg.segment2 != BarsSegmentType.NONE) {
            if (cfg.segment1 != BarsSegmentType.NONE) drawBarsLayer(layerId, cfg.copy(segment2 = BarsSegmentType.NONE), audioSnap, opacity, w, h)
            drawBarsLayer("${layerId}_segment2", cfg.copy(segment1 = cfg.segment2, segment2 = BarsSegmentType.NONE, barWidth = cfg.segment2Width, barHeightMultiplier = cfg.segment2HeightMultiplier, fixedHeight = cfg.segment2FixedHeight, mirror = cfg.segment2Mirror, creativeStyle=cfg.secondaryCreativeStyle, colorFromArgb = cfg.segment2ColorFrom, colorToArgb = cfg.segment2ColorTo, barStrokeWidth=cfg.segment2StrokeWidth,barStrokeColorArgb=cfg.segment2StrokeColorArgb,barFillOpacity=cfg.segment2FillOpacity), audioSnap, opacity, w, h)
            return
        }
        val rawBars = (if(cfg.signedWaveform) audioSnap?.waveform else audioSnap?.bars) ?: FloatArray(64)
        val sampled = SpectrumGeometry.resample(rawBars, cfg.barCount)
        val delayedBars = applyReactionDelay(if(cfg.signedWaveform)"${layerId}_waveform" else layerId, if(cfg.signedWaveform) sampled else SpectrumFilters.smooth(sampled,cfg.softness), (cfg.reactionDelay + cfg.reactionAccumulatedDelay).coerceIn(0, 9))
        val count = delayedBars.size
        if (count == 0) return
        val peaks=if(!cfg.signedWaveform && cfg.showPeaks && audioSnap!=null && audioSnap.peakBars.isNotEmpty()) SpectrumGeometry.resample(audioSnap.peakBars,count) else null
        if(cfg.creativePath!=com.vexo.studio.model.CreativeSpectrumPath.LEGACY || cfg.creativeStyle!=com.vexo.studio.model.CreativeSpectrumStyle.BARS || cfg.sampleOffset!=0f || cfg.sampleRepeats!=1 || cfg.amplitudeCurve!=1f || cfg.baselineOffset!=0f || cfg.signedWaveform || cfg.curveSubdivisions!=1 || cfg.gradientRepeats!=1 || cfg.customPathEnabled || cfg.pathStretchX!=1f || cfg.pathStretchY!=1f || cfg.barStrokeWidth!=0f || cfg.barFillOpacity!=1f || cfg.barTipWidth!=1f || cfg.barGradientMode!=com.vexo.studio.model.SpectrumGradientMode.PATH || cfg.waveDashRatio!=1f || cfg.waveLineGap!=2.5f || cfg.waveRibbonWidth!=1f || cfg.waveCurveTension!=.5f || cfg.gradientCycle || cfg.peakMarkerStyle!=com.vexo.studio.model.SpectrumPeakStyle.LINE || cfg.peakMarkerWidth!=1f || cfg.peakMarkerThickness!=1f || cfg.peakMarkerOffset!=0f || cfg.peakMarkerColorArgb!=null || cfg.reactiveStroke!=0f){
            drawCreativeSpectrum(cfg,delayedBars,peaks,opacity,w,h)
            return
        }
        if (cfg.segment1 == BarsSegmentType.LINE && count > 1) {
            drawConnectedSpectrum(cfg, delayedBars, opacity, w, h)
            return
        }
        val minDim = minOf(w, h).toFloat()

        // Grow according to actual rounded contour complexity, not worst-case per-bar capacity.
        ensureColorCapacity(count*6)
        var vIdx = 0
        var cIdx = 0

        fun emitQuad(
            x0: Float, y0: Float, x1: Float, y1: Float,
            x2: Float, y2: Float, x3: Float, y3: Float,
            argb0: Int, argb1: Int
        ) {
            val r0 = ((argb0 ushr 16) and 0xFF) / 255f
            val g0 = ((argb0 ushr 8) and 0xFF) / 255f
            val b0 = (argb0 and 0xFF) / 255f
            val a0 = ((argb0 ushr 24) and 0xFF) / 255f

            val r1 = ((argb1 ushr 16) and 0xFF) / 255f
            val g1 = ((argb1 ushr 8) and 0xFF) / 255f
            val b1 = (argb1 and 0xFF) / 255f
            val a1 = ((argb1 ushr 24) and 0xFF) / 255f

            if (cfg.segment1 == BarsSegmentType.ROUND_BARS && cfg.roundness > 0f) {
                val mesh = SpectrumGeometry.roundedQuad(x0,y0,x1,y1,x2,y2,x3,y3,cfg.roundness)
                ensureColorCapacity(vIdx/2+mesh.size/2)
                for (i in mesh.indices step 2) {
                    vertexScratch[vIdx++] = mesh[i]
                    vertexScratch[vIdx++] = mesh[i+1]
                    colorScratch[cIdx++] = (r0+r1)*0.5f
                    colorScratch[cIdx++] = (g0+g1)*0.5f
                    colorScratch[cIdx++] = (b0+b1)*0.5f
                    colorScratch[cIdx++] = (a0+a1)*0.5f
                }
                return
            }
            ensureColorCapacity(vIdx/2+6)
            // Tri 1: 0, 1, 2
            vertexScratch[vIdx++] = x0; vertexScratch[vIdx++] = y0
            vertexScratch[vIdx++] = x1; vertexScratch[vIdx++] = y1
            vertexScratch[vIdx++] = x2; vertexScratch[vIdx++] = y2
            // Tri 2: 2, 1, 3
            vertexScratch[vIdx++] = x2; vertexScratch[vIdx++] = y2
            vertexScratch[vIdx++] = x1; vertexScratch[vIdx++] = y1
            vertexScratch[vIdx++] = x3; vertexScratch[vIdx++] = y3

            fun pushColor(r: Float, g: Float, b: Float, a: Float) {
                colorScratch[cIdx++] = r; colorScratch[cIdx++] = g
                colorScratch[cIdx++] = b; colorScratch[cIdx++] = a
            }
            pushColor(r0, g0, b0, a0); pushColor(r0, g0, b0, a0); pushColor(r1, g1, b1, a1)
            pushColor(r1, g1, b1, a1); pushColor(r0, g0, b0, a0); pushColor(r1, g1, b1, a1)
        }

        val barColor0 = cfg.colorFromArgb
        val barColor1 = cfg.colorToArgb

        for (i in 0 until count) {
            val srcIdx = if (cfg.flipInput) count - 1 - i else i
            val rawAmp = delayedBars[srcIdx]
            val signedGain = cfg.heightScale * cfg.barHeightMultiplier
            val amp = (rawAmp * kotlin.math.abs(signedGain))
                .coerceIn(minOf(kotlin.math.abs(cfg.minHeightScale), kotlin.math.abs(cfg.maxHeightScale)), maxOf(kotlin.math.abs(cfg.minHeightScale), kotlin.math.abs(cfg.maxHeightScale))) * (if (signedGain < 0f) -1f else 1f)
            val gradColor = MeasureEvaluator.blendArgb(
                barColor0, barColor1,
                ((i.toFloat() / count) + cfg.colorBlendOffset).coerceIn(0f, 1f)
            )
            val altDir = if (cfg.alternatingDirection && i % 2 == 1) -1f else 1f
            val magPx = (amp * minDim * 0.42f + cfg.fixedHeight * 2f) * altDir
            val peakPx=peaks?.getOrNull(srcIdx)?.let{v->(v*kotlin.math.abs(signedGain)).coerceIn(0f,kotlin.math.abs(cfg.maxHeightScale))*minDim*.42f*(if(signedGain<0f)-1f else 1f)*altDir}
            val marker=maxOf(1f,minDim*.0025f)

            when (cfg.shapePath) {
                BarsShapePath.HORIZONTAL_LINE, BarsShapePath.LETTER -> {
                    val slotW = (w * 0.86f) / count
                    val bw = (slotW * (cfg.barWidth.coerceIn(0.05f, 1.5f) * (1f - cfg.barSpacing.coerceIn(0f, 0.95f)))).coerceAtLeast(1.5f)
                    val cx = w * 0.07f + i * slotW + slotW * 0.5f
                    val cy = h * 0.5f
                    val topY = if (cfg.mirror) cy - magPx * 0.5f else cy - magPx
                    val botY = if (cfg.mirror) cy + magPx * 0.5f else cy
                    val tipShrink = if (cfg.segment1 == BarsSegmentType.SHARP_BARS) bw * 0.45f else 0f
                    emitQuad(
                        cx - bw * 0.5f + tipShrink, topY,
                        cx + bw * 0.5f - tipShrink, topY,
                        cx - bw * 0.5f, botY,
                        cx + bw * 0.5f, botY,
                        gradColor, barColor0
                    )
                    if(peakPx!=null){val yy=cy-peakPx*(if(cfg.mirror).5f else 1f);emitQuad(cx-bw*.5f,yy-marker,cx+bw*.5f,yy-marker,cx-bw*.5f,yy,cx+bw*.5f,yy,gradColor,gradColor)}
                }
                BarsShapePath.VERTICAL_LINE -> {
                    val slotH = (h * 0.86f) / count
                    val bh = (slotH * (cfg.barWidth.coerceIn(0.05f, 1.5f) * (1f - cfg.barSpacing.coerceIn(0f, 0.95f)))).coerceAtLeast(1.5f)
                    val cy = h * 0.07f + i * slotH + slotH * 0.5f
                    val cx = w * 0.5f
                    val leftX = if (cfg.mirror) cx - magPx * 0.5f else cx
                    val rightX = if (cfg.mirror) cx + magPx * 0.5f else cx + magPx
                    emitQuad(leftX, cy - bh * 0.5f, rightX, cy - bh * 0.5f, leftX, cy + bh * 0.5f, rightX, cy + bh * 0.5f, barColor0, gradColor)
                    if(peakPx!=null){val xx=cx+peakPx*(if(cfg.mirror).5f else 1f);emitQuad(xx,cy-bh*.5f,xx+marker,cy-bh*.5f,xx,cy+bh*.5f,xx+marker,cy+bh*.5f,gradColor,gradColor)}
                }
                BarsShapePath.CIRCLE, BarsShapePath.SIDED_POLYGON -> {
                    val cx = w * 0.5f
                    val cy = h * 0.5f
                    val angle = Math.toRadians(cfg.pathAngleDeg.toDouble()).toFloat() +
                        (i.toFloat() / count) * 2f * PI.toFloat() * (1f - cfg.pathGap.coerceIn(0f, 0.9f))
                    val baseR = if (cfg.shapePath == BarsShapePath.SIDED_POLYGON) {
                        val sides = cfg.polygonSides.coerceIn(3, 24)
                        val sector = (2f * PI.toFloat()) / sides
                        val local = ((angle % sector) + sector) % sector - sector * 0.5f
                        (cfg.circleRadius * minDim) * (cos(sector * 0.5f) / cos(local).coerceAtLeast(0.2f))
                    } else {
                        cfg.circleRadius * minDim
                    }

                    val rIn = if (cfg.mirror) baseR - magPx * 0.45f else baseR
                    val rOut = if (cfg.mirror) baseR + magPx * 0.55f else baseR + magPx
                    val nx = cos(angle)
                    val ny = sin(angle)
                    val px = -ny
                    val py = nx
                    val halfW = ((2f * PI.toFloat() * baseR) / count) * (cfg.barWidth.coerceIn(0.05f, 1.4f) * (1f - cfg.barSpacing.coerceIn(0f, 0.95f))) * 0.5f
                    val tipW = if (cfg.segment1 == BarsSegmentType.SHARP_BARS) halfW * 0.25f else halfW

                    emitQuad(
                        cx + nx * rIn - px * halfW, cy + ny * rIn - py * halfW,
                        cx + nx * rIn + px * halfW, cy + ny * rIn + py * halfW,
                        cx + nx * rOut - px * tipW, cy + ny * rOut - py * tipW,
                        cx + nx * rOut + px * tipW, cy + ny * rOut + py * tipW,
                        barColor0, gradColor
                    )
                    if(peakPx!=null){
                        val rr=baseR+peakPx*(if(cfg.mirror).55f else 1f)
                        emitQuad(cx+nx*rr-px*halfW,cy+ny*rr-py*halfW,cx+nx*rr+px*halfW,cy+ny*rr+py*halfW,cx+nx*(rr+marker)-px*halfW,cy+ny*(rr+marker)-py*halfW,cx+nx*(rr+marker)+px*halfW,cy+ny*(rr+marker)+py*halfW,gradColor,gradColor)
                    }
                }
            }
        }

        drawColorVertices(vIdx / 2, opacity)
    }

    private fun ensureColorCapacity(vertices:Int){
        if(vertices*2<=vertexScratch.size && vertices*4<=colorScratch.size)return
        val capacity=maxOf(vertices,vertexScratch.size/2*2,colorScratch.size/4*2,256)
        vertexScratch=vertexScratch.copyOf(capacity*2)
        colorScratch=colorScratch.copyOf(capacity*4)
        vertexBuf=allocFloatBuffer(capacity*2);colorBuf=allocFloatBuffer(capacity*4)
    }

    private fun drawCreativeSpectrum(cfg:LayerConfig.Bars,bars:FloatArray,peaks:FloatArray?,opacity:Float,w:Int,h:Int){
        var vi=0;var ci=0
        com.vexo.studio.engine.scene.SpectrumStudioMesh.emit(cfg,bars,peaks,w,h){x,y,color->
            ensureColorCapacity(vi/2+1)
            vertexScratch[vi++]=x;vertexScratch[vi++]=y
            colorScratch[ci++]=((color ushr 16)and 255)/255f;colorScratch[ci++]=((color ushr 8)and 255)/255f;colorScratch[ci++]=(color and 255)/255f;colorScratch[ci++]=((color ushr 24)and 255)/255f
        }
        drawColorVertices(vi/2,opacity)
    }

    private fun drawConnectedSpectrum(cfg: LayerConfig.Bars, bars: FloatArray, opacity: Float, w: Int, h: Int) {
        val closed = cfg.shapePath == BarsShapePath.CIRCLE || cfg.shapePath == BarsShapePath.SIDED_POLYGON
        val count = bars.size
        val minDim = minOf(w, h).toFloat()
        val segments = if (closed && cfg.pathGap <= 0f) count else count - 1
        val copies=if(cfg.mirror)2 else 1
        val needed = segments * copies * 12
        if (vertexScratch.size < needed || colorScratch.size < segments * copies * 24) {
            vertexScratch = FloatArray(needed)
            colorScratch = FloatArray(segments * copies * 24)
            vertexBuf = allocFloatBuffer(vertexScratch.size)
            colorBuf = allocFloatBuffer(colorScratch.size)
        }
        fun point(i: Int,side:Float): Pair<Float, Float> {
            val idx = if (cfg.flipInput) count - 1 - i else i
            val gain = cfg.heightScale * cfg.barHeightMultiplier
            val raw = (bars[idx] * kotlin.math.abs(gain)).coerceIn(0f, kotlin.math.abs(cfg.maxHeightScale))
            val amp = raw * minDim * 0.42f * (if (gain < 0f) -1f else 1f)*side*(if(cfg.alternatingDirection && i%2==1)-1f else 1f)*(if(cfg.mirror).5f else 1f)
            val t = i.toFloat() / (if (closed) count else count - 1)
            if (!closed) return if (cfg.shapePath == BarsShapePath.VERTICAL_LINE) (w * 0.5f + amp) to (h * (0.07f + t * 0.86f)) else (w * (0.07f + t * 0.86f)) to (h * 0.5f - amp)
            val angle = Math.toRadians(cfg.pathAngleDeg.toDouble()).toFloat() + t * 2f * PI.toFloat() * (1f - cfg.pathGap.coerceIn(0f, 0.9f))
            val base = if (cfg.shapePath == BarsShapePath.SIDED_POLYGON) {
                val sector = 2f * PI.toFloat() / cfg.polygonSides.coerceIn(3, 24)
                val local = ((angle % sector) + sector) % sector - sector * 0.5f
                cfg.circleRadius * minDim * cos(sector * 0.5f) / cos(local).coerceAtLeast(0.2f)
            } else cfg.circleRadius * minDim
            return (w * 0.5f + cos(angle) * (base + amp)) to (h * 0.5f + sin(angle) * (base + amp))
        }
        var vi = 0; var ci = 0
        val half = (kotlin.math.abs(cfg.fixedHeight).takeIf { it > 0f } ?: (minDim / count * cfg.barWidth)).coerceIn(1f, 32f) * 0.5f
        for(copy in 0 until copies) for (i in 0 until segments) {
            val side=if(copy==0)1f else -1f
            val (x0, y0) = point(i,side)
            val (x1, y1) = point((i + 1) % count,side)
            val dx = x1 - x0; val dy = y1 - y0
            val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
            val nx = -dy / len * half; val ny = dx / len * half
            val pts = floatArrayOf(x0+nx,y0+ny,x0-nx,y0-ny,x1+nx,y1+ny,x1+nx,y1+ny,x0-nx,y0-ny,x1-nx,y1-ny)
            pts.copyInto(vertexScratch, vi); vi += 12
            val col = MeasureEvaluator.blendArgb(cfg.colorFromArgb, cfg.colorToArgb, (i.toFloat()/count + cfg.colorBlendOffset).coerceIn(0f,1f))
            repeat(6) {
                colorScratch[ci++] = ((col ushr 16) and 255)/255f
                colorScratch[ci++] = ((col ushr 8) and 255)/255f
                colorScratch[ci++] = (col and 255)/255f
                colorScratch[ci++] = ((col ushr 24) and 255)/255f
            }
        }
        drawColorVertices(vi / 2, opacity)
    }

    private fun drawImageLayer(
        cfg: LayerConfig.Image,
        scene: RenderSceneSnapshot,
        colorReactive:Float,
        creativeReactive:Float,
        opacity: Float,
        w: Int,
        h: Int,
        compTextures: Map<Int, Int>
    ) {
        val texId = when (cfg.sourceType) {
            ImageSourceType.COMPOSITION -> compTextures[cfg.sourceCompositionIndex] ?: return
            ImageSourceType.ALBUM_ART -> if (artworkTexId != 0) artworkTexId else ensureInternalTex("internalres:vignette80")
            ImageSourceType.INTERNAL -> ensureInternalTex(cfg.internalRes)
            ImageSourceType.IMPORTED -> if(cfg.videoEnabled) videoFrames.texture(cfg,scene.trackMeta.positionMs) else ensureUriTex(cfg.customUri)
            ImageSourceType.SOLID_COLOR -> ensureInternalTex("internalres:white")
        }
        if (texId == 0) return

        val colorBlend=colorReactive.coerceIn(0f,1f)
        val tint = MeasureEvaluator.blendArgb(cfg.colorArgb, cfg.colorToArgb, colorBlend)

        val sourceIsFbo=cfg.sourceType==ImageSourceType.COMPOSITION
        val (sourceW,sourceH)=if(sourceIsFbo)w to h else (if(cfg.videoEnabled&&cfg.sourceType==ImageSourceType.IMPORTED)videoFrames.size(texId)else bitmapSizes[texId])?: (w to h)
        val blur=if(cfg.blurEnabled)cfg.blurRadius.coerceIn(0,64).toFloat()/cfg.blurDivider.coerceAtLeast(1)else 0f
        val imageRatio=sourceW.toFloat()/sourceH.coerceAtLeast(1)
        val needsBorder=cfg.blurredBorder&&cfg.keepAspectRatio&&!cfg.keepAspectRatioAndCropToFit&&cfg.shape==ImageShape.NONE&&kotlin.math.abs(imageRatio-w.toFloat()/h.coerceAtLeast(1))>.001f
        val immutableSource=!sourceIsFbo&&!(cfg.sourceType==ImageSourceType.IMPORTED&&cfg.videoEnabled)
        fun filtered(sigma:Float,prefix:String):Int{
            val key=StaticBlurKey(texId,sourceW,sourceH,w,h,sigma,cfg.blurQuality)
            if(immutableSource)staticBlurResults[prefix]?.let{if(it.first==key)return it.second}
            staticBlurResults.remove(prefix)
            val result=filterTexture(texId,sourceW,sourceH,sourceIsFbo,w,h,sigma,0f,1f,cfg.blurQuality,prefix=prefix)
            if(immutableSource)staticBlurResults[prefix]=key to result
            return result
        }
        val mainTex=if(blur>.001f)filtered(blur*.8f,"image_blur_main")else texId
        val borderTex=if(needsBorder)filtered(maxOf(10f,cfg.blurRadius*2f)*.8f,"image_blur_border")else texId
        val mainIsFbo=sourceIsFbo||mainTex!=texId
        val shader = imageShader ?: return
        resetImageState(shader)
        GLES20.glUniformMatrix4fv(shader.uniform("uMvp"), 1, false, mvpMatrix, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mainTex)
        GLES20.glUniform1i(shader.uniform("uTex"), 0)
        GLES20.glUniform4f(
            shader.uniform("uTint"),
            ((tint ushr 16) and 0xFF) / 255f,
            ((tint ushr 8) and 0xFF) / 255f,
            (tint and 0xFF) / 255f,
            ((tint ushr 24) and 0xFF) / 255f
        )
        GLES20.glUniform1f(shader.uniform("uSaturation"), cfg.saturation)
        bindCreativeImage(shader,cfg.creative,scene,creativeReactive,w,h)
        val shapeCode = when (cfg.shape) {
            ImageShape.NONE -> 0
            ImageShape.CIRCLE -> 1
            ImageShape.SIDED_POLYGON -> 2
        }
        GLES20.glUniform1i(shader.uniform("uShape"), shapeCode)
        GLES20.glUniform1i(shader.uniform("uColorKeyEnabled"), if (cfg.colorKeyEnabled) 1 else 0)
        val keyColor=if(cfg.autoDetectColorKey)bitmapKeyColors[texId]?:cfg.colorKeyArgb else cfg.colorKeyArgb
        GLES20.glUniform3f(
            shader.uniform("uKeyColor"),
            ((keyColor ushr 16) and 0xFF) / 255f,
            ((keyColor ushr 8) and 0xFF) / 255f,
            (keyColor and 0xFF) / 255f
        )
        GLES20.glUniform1f(shader.uniform("uOpacity"), opacity * cfg.opacityStrength.coerceIn(0f, 1f))

        val shapeSquare = cfg.shape != ImageShape.NONE
        val frameW = if (shapeSquare) minOf(w,h).toFloat() else w.toFloat()
        val frameH = if (shapeSquare) minOf(w,h).toFloat() else h.toFloat()
        val frameRatio = frameW / frameH.coerceAtLeast(1f)
        val crop = cfg.keepAspectRatioAndCropToFit
        val (drawW, drawH) = if (cfg.keepAspectRatio && !crop && !shapeSquare && cfg.sourceType!=ImageSourceType.SOLID_COLOR && cfg.creative.gradient==com.vexo.studio.model.CreativeGradient.NONE && cfg.creative.pattern==com.vexo.studio.model.ProceduralPattern.NONE) {
            if(imageRatio > frameRatio) frameW to frameW / imageRatio else frameH * imageRatio to frameH
        } else frameW to frameH
        val maskIsComp = cfg.maskImage.startsWith("composition:")
        val maskTex = when {
            maskIsComp -> cfg.maskImage.substringAfter(':').toIntOrNull()?.let { compTextures[it] } ?: 0
            cfg.maskImage.startsWith("internalres:") -> ensureInternalTex(cfg.maskImage)
            cfg.maskImage.startsWith("content://") || cfg.maskImage.startsWith("file://") -> ensureUriTex(cfg.maskImage)
            else -> 0
        }
        fun bindImageMask(sourceFbo:Boolean){
            GLES20.glUniform1i(shader.uniform("uMaskEnabled"),if(maskTex!=0)1 else 0)
            GLES20.glUniform1i(shader.uniform("uMaskMode"),cfg.maskMode.ordinal)
            GLES20.glUniform1i(shader.uniform("uMaskFlipV"),if(maskIsComp!=sourceFbo)1 else 0)
            if(maskTex!=0){
                GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,maskTex)
                GLES20.glUniform1i(shader.uniform("uMaskTex"),1)
            }
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        }
        if(cfg.blurredBorder && cfg.keepAspectRatio && !crop && !shapeSquare && (drawW<frameW-.5f || drawH<frameH-.5f)){
            GLES20.glUniform1i(shader.uniform("uImageFxEnabled"),1)
            GLES20.glUniform1i(shader.uniform("uShape"),0)
            GLES20.glUniform1i(shader.uniform("uColorKeyEnabled"),0)
            bindImageMask(sourceIsFbo||borderTex!=texId)
            GLES20.glUniform2f(shader.uniform("uUvScale"),minOf(1f,frameRatio/imageRatio),minOf(1f,imageRatio/frameRatio))
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,borderTex)
            GLES20.glUniform2f(shader.uniform("uImageBlurStep"),0f,0f)
            drawTexturedQuad(shader,0f,0f,w.toFloat(),h.toFloat(),flipV=cfg.flipV xor (sourceIsFbo||borderTex!=texId),flipU=cfg.flipU)
            GLES20.glUniform1i(shader.uniform("uShape"),shapeCode)
            GLES20.glUniform1i(shader.uniform("uColorKeyEnabled"),if(cfg.colorKeyEnabled)1 else 0)
        }
        val uvX = if (crop || shapeSquare) minOf(1f, frameRatio / imageRatio) else 1f
        val uvY = if (crop || shapeSquare) minOf(1f, imageRatio / frameRatio) else 1f
        GLES20.glUniform1i(shader.uniform("uImageFxEnabled"), 1)
        GLES20.glUniform2f(shader.uniform("uUvScale"), uvX, uvY)
        GLES20.glUniform2f(shader.uniform("uImageBlurStep"),0f,0f)
        GLES20.glUniform1f(shader.uniform("uKeyStrength"), cfg.transparencyStrength.coerceIn(0f,1f))
        val left = (w - drawW) * 0.5f
        val top = (h - drawH) * 0.5f
        bindImageMask(mainIsFbo)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,mainTex)
        drawTexturedQuad(shader, left, top, left + drawW, top + drawH, flipV = cfg.flipV xor mainIsFbo, flipU = cfg.flipU)
        GLES20.glUniform1i(shader.uniform("uMaskEnabled"), 0)
        GLES20.glUniform1i(shader.uniform("uImageFxEnabled"), 0)
    }

    private fun drawTextLayer(
        cfg: LayerConfig.Text,
        scene: RenderSceneSnapshot,
        reactive:Float,
        opacity: Float,
        w: Int,
        h: Int
    ) {
        val meta=scene.trackMeta
        val resolved = cfg.text
            .replace("{Title}", meta.title, ignoreCase = true)
            .replace("{Artist}", meta.artist, ignoreCase = true)
            .replace("{Album}", meta.album, ignoreCase = true)
            .replace("{Time}", formatTimeMs(meta.positionMs), ignoreCase = true)
            .replace("{Duration}",formatTimeMs(meta.durationMs),ignoreCase=true)
            .replace("{Progress}",if(meta.durationMs>0) ((meta.positionMs*100L/meta.durationMs).coerceIn(0L,100L)).toString()+"%" else "0%",ignoreCase=true)

        val key = "txt_${resolved}_${cfg.fontSize}_${cfg.bold}_${cfg.italic}_${cfg.colorArgb}_${cfg.alignment}_${cfg.letterSpacing}_${cfg.lineSpacing}_${cfg.strokeWidth}_${cfg.strokeColorArgb}_${cfg.shadowRadius}_${cfg.shadowColorArgb}_${cfg.fontAssetUri}_${cfg.fontFamily}_${cfg.fontWeight}"
        val texId = textureCache[key] ?: run {
            val bmp = renderTextBitmap(resolved, cfg)
            val id = uploadBitmapToGl(bmp)
            bmp.recycle()
            textureCache[key] = id
            id
        }
        if (texId == 0) return

        val shader = imageShader ?: return
        resetImageState(shader)
        GLES20.glUniformMatrix4fv(shader.uniform("uMvp"), 1, false, mvpMatrix, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glUniform1i(shader.uniform("uTex"), 0)
        GLES20.glUniform4f(shader.uniform("uTint"), 1f, 1f, 1f, 1f)
        GLES20.glUniform1f(shader.uniform("uSaturation"), 1f)
        GLES20.glUniform1i(shader.uniform("uShape"), 0)
        GLES20.glUniform1i(shader.uniform("uColorKeyEnabled"), 0)
        GLES20.glUniform1f(shader.uniform("uOpacity"), opacity)

        val size = bitmapSizes[texId] ?: (1024 to 256)
        val ratio = size.first.toFloat()/size.second.coerceAtLeast(1)
        val progress=com.vexo.studio.engine.scene.CreativeGeometry.animationProgress(meta.positionMs,cfg.animationDelay,cfg.animationDuration,cfg.animationLoop)
        val amount=cfg.animationAmount.coerceIn(0f,1f)
        var scale=1f;var dx=0f;var alpha=opacity
        when(cfg.animation){
            com.vexo.studio.model.TextMotion.NONE->Unit
            com.vexo.studio.model.TextMotion.FADE->alpha*=progress
            com.vexo.studio.model.TextMotion.SLIDE->dx=-(1f-progress)*w*amount
            com.vexo.studio.model.TextMotion.PULSE->scale=1f+sin(progress*2f*PI.toFloat())*amount
            com.vexo.studio.model.TextMotion.REVEAL->GLES20.glUniform1f(shader.uniform("uReveal"),progress)
            com.vexo.studio.model.TextMotion.BEAT_SCALE->scale=1f+(reactive*amount).coerceIn(-.75f,2f)
        }
        GLES20.glUniform1f(shader.uniform("uOpacity"),alpha)
        val boxW = minOf(w*.8f,h*.24f*ratio)*scale.coerceAtLeast(.1f)
        val boxH = boxW/ratio
        drawTexturedQuad(shader, (w - boxW) * 0.5f+dx, (h - boxH) * 0.5f, (w + boxW) * 0.5f+dx, (h + boxH) * 0.5f, flipV = false)
        GLES20.glUniform1f(shader.uniform("uReveal"),1f)
    }

    private fun drawShapeLayer(
        cfg: LayerConfig.Shape,
        opacity: Float,
        w: Int,
        h: Int
    ) {
        val cx = w * 0.5f
        val cy = h * 0.5f
        val r = minOf(w, h) * cfg.radius.coerceIn(.005f,.8f)
        val rect=if(cfg.kind==ShapeKind.RECTANGLE) ShapeGeometry.roundedSquare(r*.70710678f,cfg.cornerRadius) else null
        val sides = when (cfg.kind) {
            ShapeKind.RECTANGLE -> rect!!.size
            ShapeKind.ELLIPSE -> SpectrumGeometry.quarterSegments(r)*4
            ShapeKind.POLYGON -> cfg.sides.coerceIn(3, 24)
            ShapeKind.STAR -> cfg.sides.coerceIn(4, 16) * 2
            ShapeKind.LINE -> 2
        }

        val needed = (sides + 2) * 18
        if (vertexScratch.size < needed || colorScratch.size < (sides + 2)*36) {
            vertexScratch = FloatArray(needed)
            colorScratch = FloatArray((sides + 2) * 36)
            vertexBuf = allocFloatBuffer(vertexScratch.size)
            colorBuf = allocFloatBuffer(colorScratch.size)
        }

        var vIdx = 0
        var cIdx = 0

        fun pushVert(px: Float, py: Float, argb: Int) {
            vertexScratch[vIdx++] = px
            vertexScratch[vIdx++] = py
            colorScratch[cIdx++] = ((argb ushr 16) and 0xFF) / 255f
            colorScratch[cIdx++] = ((argb ushr 8) and 0xFF) / 255f
            colorScratch[cIdx++] = (argb and 0xFF) / 255f
            colorScratch[cIdx++] = ((argb ushr 24) and 0xFF) / 255f
        }

        val strokeW = cfg.strokeWidth.coerceIn(0f, 32f)
        if(cfg.kind==ShapeKind.LINE){
            if(strokeW>0f){val a=cx-r;val b=cx+r;val y0=cy-strokeW*.5f;val y1=cy+strokeW*.5f
                pushVert(a,y0,cfg.strokeColorArgb);pushVert(b,y0,cfg.strokeColorArgb);pushVert(a,y1,cfg.strokeColorArgb)
                pushVert(a,y1,cfg.strokeColorArgb);pushVert(b,y0,cfg.strokeColorArgb);pushVert(b,y1,cfg.strokeColorArgb)
                drawColorVertices(vIdx/2,opacity)}
            return
        }
        for (i in 0 until sides) {
            val a0 = (i.toFloat() / sides) * 2f * PI.toFloat() + (if (cfg.kind == ShapeKind.RECTANGLE) PI.toFloat() * 0.25f else 0f)
            val a1 = ((i + 1).toFloat() / sides) * 2f * PI.toFloat() + (if (cfg.kind == ShapeKind.RECTANGLE) PI.toFloat() * 0.25f else 0f)
            val r0 = if (cfg.kind == ShapeKind.STAR && i % 2 == 1) r * cfg.starInnerRatio.coerceIn(.05f,.99f) else r
            val r1 = if (cfg.kind == ShapeKind.STAR && (i + 1) % 2 == 1) r * cfg.starInnerRatio.coerceIn(.05f,.99f) else r

            val x0 = cx + (rect?.get(i)?.first ?: cos(a0)*r0)
            val y0 = cy + (rect?.get(i)?.second ?: sin(a0)*r0)
            val x1 = cx + (rect?.get((i+1)%sides)?.first ?: cos(a1)*r1)
            val y1 = cy + (rect?.get((i+1)%sides)?.second ?: sin(a1)*r1)

            // Fill triangle
            if ((((cfg.fillColorArgb ushr 24) or (cfg.gradientEndArgb ushr 24)) and 0xFF) > 0 && cfg.kind != ShapeKind.LINE) {
                pushVert(cx, cy, cfg.fillColorArgb)
                pushVert(x0, y0, cfg.gradientEndArgb)
                pushVert(x1, y1, cfg.gradientEndArgb)
            }
            // Stroke edge quad
            if(strokeW<=0f) continue
            val scale0=(1f-strokeW/kotlin.math.hypot(x0-cx,y0-cy).coerceAtLeast(1f)).coerceAtLeast(0f)
            val scale1=(1f-strokeW/kotlin.math.hypot(x1-cx,y1-cy).coerceAtLeast(1f)).coerceAtLeast(0f)
            val x0i = cx + (x0-cx)*scale0
            val y0i = cy + (y0-cy)*scale0
            val x1i = cx + (x1-cx)*scale1
            val y1i = cy + (y1-cy)*scale1
            pushVert(x0i, y0i, cfg.strokeColorArgb)
            pushVert(x0, y0, cfg.strokeColorArgb)
            pushVert(x1, y1, cfg.strokeColorArgb)
            pushVert(x0i, y0i, cfg.strokeColorArgb)
            pushVert(x1, y1, cfg.strokeColorArgb)
            pushVert(x1i, y1i, cfg.strokeColorArgb)
        }
        drawColorVertices(vIdx / 2, opacity)
    }

    private val particleSpriteUv=floatArrayOf(0f,0f,1f,0f,0f,1f,0f,1f,1f,0f,1f,1f)

    private fun drawParticlesLayer(
        layerId: String,
        cfg: LayerConfig.Particles,
        scene: RenderSceneSnapshot,
        deltaSec: Float,
        opacity: Float,
        w: Int,
        h: Int,
        reactiveSpeed:Float
    ) {
        val sim = particleSims.getOrPut(layerId) { ParticleSimulator() }
        sim.step(
            cfg = cfg,
            snapshot = scene.audioSnapshots[cfg.measureOverallSpeed.providerIndex],
            deltaSec = deltaSec,
            totalTimeSec = scene.totalTimeSec,
            playingTimeSec = scene.playingTimeSec,
            isPlaying = scene.isPlaying,
            burstSnapshot=scene.audioSnapshots[cfg.burstProvider],
            speedOverride=if(com.vexo.studio.engine.scene.ReactiveMeasureFilter.isContinuous(cfg.measureOverallSpeed.measureWhat))reactiveSpeed else null,
            trackPositionNorm=if(scene.trackMeta.durationMs>0)(scene.trackMeta.positionMs.toFloat()/scene.trackMeta.durationMs).coerceIn(0f,1f)else 0f
        )
        val count = sim.activeCount
        if (count <= 0) return
        val copies=(if(cfg.mirrorX)2 else 1)*(if(cfg.mirrorY)2 else 1)
        val needed = count * copies * 12
        ensureColorCapacity(count*copies*6)
        if(particleUv.capacity()<needed)particleUv=allocFloatBuffer(maxOf(needed,particleUv.capacity()*2))
        particleUv.clear()
        val minDim = minOf(w, h).toFloat()
        var vIdx = 0
        var cIdx = 0

        val spriteUv=particleSpriteUv
        for(i in 0 until count){
            val col=MeasureEvaluator.blendArgb(cfg.colorFromArgb,cfg.colorToArgb,sim.outProgress[i])
            val r=((col ushr 16) and 255)/255f*((cfg.colorArgb ushr 16) and 255)/255f
            val green=((col ushr 8) and 255)/255f*((cfg.colorArgb ushr 8) and 255)/255f
            val b=(col and 255)/255f*(cfg.colorArgb and 255)/255f
            val a=((col ushr 24) and 255)/255f*sim.outAlpha[i]*((cfg.colorArgb ushr 24) and 255)/255f
            if(a<=.001f||sim.outSize[i]<=0f)continue
            val half=sim.outSize[i]*minDim*.5f
            val aspect=cfg.sizeAspectRatio.takeIf{it.isFinite()}?.coerceIn(.05f,20f)?:1f
            val widthHalf=half*kotlin.math.sqrt(aspect)
            val heightHalf=half/kotlin.math.sqrt(aspect)
            val length=widthHalf*com.vexo.studio.engine.scene.ParticleTrailMath.stretch(
                cfg.trailLength,sim.outVx[i],sim.outVy[i],w,h)
            for(mx in 0 until if(cfg.mirrorX)2 else 1) for(my in 0 until if(cfg.mirrorY)2 else 1){
                val px=(if(mx==1)1f-sim.outX[i] else sim.outX[i])*w
                val py=(if(my==1)1f-sim.outY[i] else sim.outY[i])*h
                val angle=if(cfg.velocityAngle||cfg.trailLength>0f) kotlin.math.atan2(sim.outVy[i]*(if(my==1)-1 else 1)*h,sim.outVx[i]*(if(mx==1)-1 else 1)*w) else 0f
                val ca=cos(angle);val sa=sin(angle)
                fun point(x:Float,y:Float){
                    vertexScratch[vIdx++]=px+x*ca-y*sa;vertexScratch[vIdx++]=py+x*sa+y*ca
                    colorScratch[cIdx++]=r;colorScratch[cIdx++]=green;colorScratch[cIdx++]=b;colorScratch[cIdx++]=a
                }
                point(-length,-heightHalf);point(widthHalf,-heightHalf);point(-length,heightHalf)
                point(-length,heightHalf);point(widthHalf,-heightHalf);point(widthHalf,heightHalf)
                particleUv.put(spriteUv)
            }
        }
        if(vIdx==0)return
        val tex=if(cfg.customImage.startsWith("internalres:"))ensureInternalTex(cfg.customImage) else ensureUriTex(cfg.customImage)
        val shader=particleShader ?: return
        shader.use()
        GLES20.glUniformMatrix4fv(shader.uniform("uMvp"),1,false,mvpMatrix,0)
        GLES20.glUniform1f(shader.uniform("uOpacity"),opacity)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,if(tex!=0)tex else ensureInternalTex("internalres:particle_circle_blur4"))
        GLES20.glUniform1i(shader.uniform("uTex"),0)
        vertexBuf.clear();vertexBuf.put(vertexScratch,0,vIdx);vertexBuf.position(0)
        colorBuf.clear();colorBuf.put(colorScratch,0,cIdx);colorBuf.position(0)
        particleUv.position(0)
        val pos=shader.attrib("aPos");val color=shader.attrib("aColor");val uv=shader.attrib("aUv")
        GLES20.glEnableVertexAttribArray(pos);GLES20.glVertexAttribPointer(pos,2,GLES20.GL_FLOAT,false,0,vertexBuf)
        GLES20.glEnableVertexAttribArray(color);GLES20.glVertexAttribPointer(color,4,GLES20.GL_FLOAT,false,0,colorBuf)
        GLES20.glEnableVertexAttribArray(uv);GLES20.glVertexAttribPointer(uv,2,GLES20.GL_FLOAT,false,0,particleUv)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,vIdx/2)
        GLES20.glDisableVertexAttribArray(pos);GLES20.glDisableVertexAttribArray(color);GLES20.glDisableVertexAttribArray(uv)
    }

    /** Premultiplied RGBA, progressive 2x2 prefilter, dense separable Gaussian.
     * Returned FBO texture has its top at v=1.
     */
    private fun filterTexture(source:Int,sourceW:Int,sourceH:Int,sourceIsFbo:Boolean,targetW:Int,targetH:Int,
        sigma:Float,angleDeg:Float,cross:Float,quality:com.vexo.studio.model.BlurQuality,
        mode:Int=0,radialStrength:Float=0f,centerX:Float=.5f,centerY:Float=.5f,
        threshold:Float=0f,knee:Float=.1f,prefix:String):Int{
        val shader=checkNotNull(blurShader){"Blur shader unavailable"}
        val plan=com.vexo.studio.engine.scene.SmoothBlurPlan.create(sourceW,sourceH,targetW,targetH,sigma,cross,quality,mode!=0)
        // Release obsolete levels when the radius shrinks, rather than retaining a full pyramid.
        for(i in plan.reductions.size..12)fboPool.releaseByPrefix("${prefix}_down_$i")
        val saved=IntArray(1);val viewport=IntArray(4)
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING,saved,0)
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT,viewport,0)
        val blend=GLES20.glIsEnabled(GLES20.GL_BLEND)
        var tex=source;var iw=sourceW.coerceAtLeast(1);var ih=sourceH.coerceAtLeast(1)
        var flip=sourceIsFbo;var first=true
        val matrix=FloatArray(16)
        val angle=angleDeg.coerceIn(-360f,360f)*PI.toFloat()/180f
        val dx=cos(angle);val dy=sin(angle)
        fun pass(key:String,width:Int,height:Int,kind:Int,stepX:Float,stepY:Float,radius:Float){
            val fbo=fboPool.obtain(key,width,height)
            check(fbo.textureId!=tex){"Blur cannot sample its own target"}
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,fbo.fboId)
            GLES20.glViewport(0,0,fbo.width,fbo.height)
            GLES20.glDisable(GLES20.GL_BLEND)
            clearBackground(0x00000000)
            shader.use()
            Matrix.orthoM(matrix,0,0f,fbo.width.toFloat(),fbo.height.toFloat(),0f,-1f,1f)
            GLES20.glUniformMatrix4fv(shader.uniform("uMvp"),1,false,matrix,0)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,tex)
            GLES20.glUniform1i(shader.uniform("uTex"),0)
            GLES20.glUniform2f(shader.uniform("uDirection"),stepX/iw,stepY/ih)
            GLES20.glUniform1f(shader.uniform("uRadius"),radius)
            GLES20.glUniform1i(shader.uniform("uFilterMode"),kind)
            GLES20.glUniform2f(shader.uniform("uBoxStep"),.5f/iw,.5f/ih)
            GLES20.glUniform2f(shader.uniform("uCenter"),centerX.coerceIn(0f,1f),if(flip)1f-centerY.coerceIn(0f,1f)else centerY.coerceIn(0f,1f))
            GLES20.glUniform1f(shader.uniform("uRadialStrength"),radialStrength.coerceIn(0f,1f))
            GLES20.glUniform1f(shader.uniform("uAspect"),fbo.width.toFloat()/fbo.height)
            GLES20.glUniform2f(shader.uniform("uBloom"),if(first)threshold.coerceIn(0f,1f)else 0f,knee.coerceIn(0f,.5f))
            drawTexturedQuad(shader,0f,0f,fbo.width.toFloat(),fbo.height.toFloat(),flipV=flip)
            tex=fbo.textureId;iw=fbo.width;ih=fbo.height;flip=true;first=false
        }
        try{
            for((index,size) in plan.reductions.withIndex())pass("${prefix}_down_$index",size.first,size.second,3,0f,0f,0f)
            if(mode!=0)pass("${prefix}_a",plan.width,plan.height,mode,0f,0f,0f)
            else{
                repeat(plan.rounds){
                    pass("${prefix}_a",plan.width,plan.height,0,dx,dy,plan.sigmaX*1.2f)
                    if(plan.sigmaY>.001f)pass("${prefix}_b",plan.width,plan.height,0,-dy,dx,plan.sigmaY*1.2f)
                    else if(it<plan.rounds-1)pass("${prefix}_b",plan.width,plan.height,0,0f,0f,0f)
                }
            }
            // Reuse earlier, now-unused reduction targets for tent reconstruction.
            // The source is the Gaussian ping-pong result or previous smaller level.
            for(index in plan.reconstructionIndices(targetW,targetH,quality,mode!=0)){
                val size=plan.reductions[index]
                pass("${prefix}_down_$index",size.first,size.second,4,0f,0f,0f)
            }
            return tex
        }finally{
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,saved[0])
            GLES20.glViewport(viewport[0],viewport[1],viewport[2],viewport[3])
            if(blend)GLES20.glEnable(GLES20.GL_BLEND)else GLES20.glDisable(GLES20.GL_BLEND)
        }
    }

    private fun drawBlurEffect(
        cfg: LayerConfig.BlurEffect,
        reactive: Float,
        opacity: Float,
        w: Int,
        h: Int,
        compTextures: Map<Int, Int>
    ) {
        val srcTex = compTextures[cfg.sourceCompositionIndex] ?: return
        val radius=(cfg.blurRadius+reactive).coerceIn(0f,24f)*cfg.blurMultiplier.coerceIn(0f,8f)*.75f
        val hasGlow=listOf(cfg.layer1Scale to cfg.layer1ColorArgb,cfg.layer2Scale to cfg.layer2ColorArgb,cfg.layer3Scale to cfg.layer3ColorArgb).any{(scale,col)->scale>0f&&(col ushr 24)>0}
        val radial=cfg.filterMode!=com.vexo.studio.model.BlurFilterMode.GAUSSIAN
        val radialStrength=(cfg.radialStrength*(1f+reactive/12f)).coerceIn(0f,1f)
        val sigma=radius*1.65f*minOf(w,h)/720f
        val filteredTex=if(hasGlow&&cfg.bloomIntensity>0f&&((if(radial)radialStrength else sigma)>.001f||cfg.bloomThreshold>0f)){
            filterTexture(srcTex,w,h,true,w,h,sigma,cfg.blurAngleDeg,cfg.blurCrossAxis,cfg.blurQuality,
                cfg.filterMode.ordinal,radialStrength,cfg.radialCenterX,cfg.radialCenterY,cfg.bloomThreshold,cfg.bloomKnee,"blur_scene")
        }else srcTex

        val img = imageShader ?: return
        resetImageState(img)
        GLES20.glUniformMatrix4fv(img.uniform("uMvp"), 1, false, mvpMatrix, 0)
        GLES20.glUniform1i(img.uniform("uShape"), 0)
        GLES20.glUniform1i(img.uniform("uColorKeyEnabled"), 0)
        GLES20.glUniform1f(img.uniform("uSaturation"), 1f)
        GLES20.glUniform4f(img.uniform("uTint"), 1f, 1f, 1f, 1f)

        if (cfg.showUnblurredContentUnder) {
            applyBlendMode(cfg.unblurredBlendMode ?: cfg.blendModeContent)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, srcTex)
            GLES20.glUniform1f(img.uniform("uOpacity"), opacity)
            drawTexturedQuad(img, 0f, 0f, w.toFloat(), h.toFloat(), flipV = true)
        }

        applyBlendMode(cfg.glowBlendMode ?: cfg.blendModeContent)
        val maskIsComp=cfg.maskImage.startsWith("composition:")
        val mask=when {
            maskIsComp -> cfg.maskImage.substringAfter(':').toIntOrNull()?.let{compTextures[it]} ?: 0
            cfg.maskImage=="internalres:transparent" || cfg.maskImage.isBlank() -> 0
            cfg.maskImage.startsWith("internalres:") -> ensureInternalTex(cfg.maskImage)
            else -> ensureUriTex(cfg.maskImage)
        }
        GLES20.glUniform1i(img.uniform("uMaskEnabled"),if(mask!=0)1 else 0)
        GLES20.glUniform1i(img.uniform("uMaskMode"),0)
        GLES20.glUniform1i(img.uniform("uMaskFlipV"),if(maskIsComp)0 else 1)
        if(mask!=0){
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,mask)
            GLES20.glUniform1i(img.uniform("uMaskTex"),1)
        }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,filteredTex)
        GLES20.glUniform1f(img.uniform("uOpacity"),opacity)
        GLES20.glUniform1f(img.uniform("uGlowGain"),cfg.bloomIntensity.coerceIn(0f,4f))
        fun glow(scale:Float,argb:Int) {
            if(scale<=0f||(argb ushr 24)==0||cfg.bloomIntensity<=0f)return
            val factor=scale.coerceIn(.01f,4f)
            val dx=w*(1f-factor)*.5f;val dy=h*(1f-factor)*.5f
            GLES20.glUniform4f(img.uniform("uTint"),((argb ushr 16) and 255)/255f,((argb ushr 8) and 255)/255f,(argb and 255)/255f,((argb ushr 24) and 255)/255f)
            drawTexturedQuad(img,dx,dy,w-dx,h-dy,flipV=true)
        }
        glow(cfg.layer1Scale,cfg.layer1ColorArgb)
        glow(cfg.layer2Scale,cfg.layer2ColorArgb)
        glow(cfg.layer3Scale,cfg.layer3ColorArgb)
        GLES20.glUniform1f(img.uniform("uGlowGain"),1f)
        GLES20.glUniform1i(img.uniform("uMaskEnabled"),0)
        GLES20.glUniform4f(img.uniform("uTint"),1f,1f,1f,1f)

        if (cfg.showUnblurredContent) {
            applyBlendMode(cfg.unblurredBlendMode ?: cfg.blendModeContent)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, srcTex)
            GLES20.glUniform1f(img.uniform("uOpacity"), opacity)
            drawTexturedQuad(img, 0f, 0f, w.toFloat(), h.toFloat(), flipV = true)
        }
    }

    private fun drawRgbSplitEffect(
        cfg: LayerConfig.RgbSplitEffect,
        delta: com.vexo.studio.engine.scene.EvaluatedPair,
        multiplierDelta:Float,
        opacity: Float,
        w: Int,
        h: Int,
        compTextures: Map<Int, Int>
    ) {
        val srcTex = compTextures[cfg.targetCompositionIndex] ?: return
        val multiplier=(cfg.splitMultiplier+multiplierDelta).takeIf{it.isFinite()}?.coerceIn(-100f,100f)?:0f
        val offX=((cfg.splitAmountX+delta.x*.015f)*multiplier*.4f).coerceIn(-.25f,.25f)
        val offY=((cfg.splitAmountY+delta.y*.015f)*multiplier*.4f).coerceIn(-.25f,.25f)

        if(cfg.showUnblurredContentUnder) drawEffectSource(srcTex,opacity,w,h,BlendMode.ALPHA)
        val shader = rgbSplitShader ?: return
        applyBlendMode(cfg.blendModeContent)
        shader.use()
        GLES20.glUniformMatrix4fv(shader.uniform("uMvp"), 1, false, mvpMatrix, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, srcTex)
        GLES20.glUniform1i(shader.uniform("uTex"), 0)
        GLES20.glUniform2f(shader.uniform("uOffset"),offX,offY)
        GLES20.glUniform1i(shader.uniform("uAberrationMode"),cfg.aberrationMode.ordinal)
        GLES20.glUniform1f(shader.uniform("uMix"),cfg.aberrationMix.coerceIn(0f,1f))
        GLES20.glUniform2f(shader.uniform("uCenter"),cfg.aberrationCenterX.coerceIn(0f,1f),1f-cfg.aberrationCenterY.coerceIn(0f,1f))
        fun setRgb(name: String, argb: Int) {
            GLES20.glUniform3f(
                shader.uniform(name),
                ((argb ushr 16) and 0xFF) / 255f,
                ((argb ushr 8) and 0xFF) / 255f,
                (argb and 0xFF) / 255f
            )
        }
        setRgb("uColor0", cfg.splitColor0Argb)
        setRgb("uColor1", cfg.splitColor1Argb)
        setRgb("uColor2", cfg.splitColor2Argb)
        GLES20.glUniform1f(shader.uniform("uOpacity"), opacity)
        drawTexturedQuad(shader, 0f, 0f, w.toFloat(), h.toFloat(), flipV = true)
        if(cfg.showUnblurredContent) drawEffectSource(srcTex,opacity,w,h,BlendMode.ALPHA)
    }

    private fun drawEffectSource(tex:Int,opacity:Float,w:Int,h:Int,mode:BlendMode) {
        val shader=imageShader ?: return
        resetImageState(shader)
        applyBlendMode(mode)
        GLES20.glUniformMatrix4fv(shader.uniform("uMvp"),1,false,mvpMatrix,0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,tex)
        GLES20.glUniform1f(shader.uniform("uOpacity"),opacity)
        drawTexturedQuad(shader,0f,0f,w.toFloat(),h.toFloat(),flipV=true)
    }

    private fun drawMotionBlurEffect(
        layerId: String,
        cfg: LayerConfig.MotionBlurEffect,
        pose: com.vexo.studio.engine.scene.MotionPose, deltaSec: Float,
        opacity: Float,
        w: Int,
        h: Int,
        compTextures: Map<Int, Int>
    ) {
        val srcTex = compTextures[cfg.targetCompositionIndex] ?: return
        val trailFbo = fboPool.obtain("trail_$layerId", w, h)
        val savedFbo = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, savedFbo, 0)

        val img = imageShader ?: return
        resetImageState(img)

        // Accumulate into trail FBO
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, trailFbo.fboId)
        GLES20.glViewport(0, 0, trailFbo.width, trailFbo.height)
        applyBlendMode(BlendMode.ALPHA)
        val idM = FloatArray(16).apply { Matrix.orthoM(this, 0, 0f, w.toFloat(), h.toFloat(), 0f, -1f, 1f) }
        GLES20.glUniformMatrix4fv(img.uniform("uMvp"), 1, false, idM, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, srcTex)
        GLES20.glUniform1i(img.uniform("uTex"), 0)
        GLES20.glUniform1i(img.uniform("uShape"), 0)
        GLES20.glUniform1i(img.uniform("uColorKeyEnabled"), 0)
        GLES20.glUniform1f(img.uniform("uSaturation"), 1f)
        GLES20.glUniform4f(img.uniform("uTint"), 1f, 1f, 1f, 1f)
        val previousTarget = previousMotionTarget.put(layerId,cfg.targetCompositionIndex)
        val firstFrame = previousTarget != cfg.targetCompositionIndex || !previousMotion.containsKey(layerId)
        if(firstFrame) clearBackground(0x00000000)
        val persist = if(firstFrame) 0f else MotionMath.persistence(cfg.trailPersistence,deltaSec)
        // Decay the entire previous frame, including pixels outside the current source.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ensureInternalTex("internalres:white"))
        GLES20.glBlendFuncSeparate(GLES20.GL_ZERO, GLES20.GL_ONE_MINUS_SRC_ALPHA, GLES20.GL_ZERO, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUniform1f(img.uniform("uOpacity"), 1f - persist)
        drawTexturedQuad(img, 0f, 0f, w.toFloat(), h.toFloat(), flipV = true)
        val previous=previousMotion.put(layerId,pose)
        val direction=com.vexo.studio.engine.scene.MotionSampling.evaluate(cfg,pose,if(firstFrame)null else previous)
        val motion=motionShader
        if(motion!=null){
            // Add the new weighted sample to the decayed history; opaque backgrounds no longer erase the trail.
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_ONE,GLES20.GL_ONE)
            motion.use()
            GLES20.glUniformMatrix4fv(motion.uniform("uMvp"),1,false,idM,0)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,srcTex)
            GLES20.glUniform1i(motion.uniform("uTex"),0)
            GLES20.glUniform2f(motion.uniform("uMotion"),direction.x,-direction.y)
            GLES20.glUniform2f(motion.uniform("uMotionExtra"),direction.radians,direction.zoom)
            GLES20.glUniform1f(motion.uniform("uAspect"),w.toFloat()/h.coerceAtLeast(1))
            GLES20.glUniform1f(motion.uniform("uOpacity"),1f-persist)
            drawTexturedQuad(motion,0f,0f,w.toFloat(),h.toFloat(),flipV=true)
        }
        resetImageState(img)

        // Draw trail FBO back to target
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, savedFbo[0])
        GLES20.glViewport(0, 0, w, h)
        applyBlendMode(cfg.blendModeContent)
        GLES20.glUniformMatrix4fv(img.uniform("uMvp"), 1, false, mvpMatrix, 0)
        if (cfg.showUnblurredContentUnder) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, srcTex)
            GLES20.glUniform1f(img.uniform("uOpacity"), opacity)
            drawTexturedQuad(img, 0f, 0f, w.toFloat(), h.toFloat(), flipV = true)
        }
        applyBlendMode(cfg.effectBlendMode ?: cfg.blendModeContent)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, trailFbo.textureId)
        GLES20.glUniform4f(img.uniform("uTint"), ((cfg.colorArgb ushr 16) and 255)/255f, ((cfg.colorArgb ushr 8) and 255)/255f, (cfg.colorArgb and 255)/255f, ((cfg.colorArgb ushr 24) and 255)/255f)
        GLES20.glUniform1f(img.uniform("uOpacity"), opacity)
        drawTexturedQuad(img, 0f, 0f, w.toFloat(), h.toFloat(), flipV = true)
        if (cfg.showUnblurredContent) {
            applyBlendMode(cfg.blendModeContent)
            GLES20.glUniform4f(img.uniform("uTint"), 1f, 1f, 1f, 1f)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, srcTex)
            drawTexturedQuad(img, 0f, 0f, w.toFloat(), h.toFloat(), flipV = true)
        }
    }

    private fun drawMirrorEffect(
        cfg: LayerConfig.MirrorEffect,
        opacity: Float,
        w: Int,
        h: Int,
        compTextures: Map<Int, Int>
    ) {
        val srcTex = compTextures[cfg.targetCompositionIndex] ?: return
        val shader = mirrorShader ?: return
        shader.use()
        GLES20.glUniformMatrix4fv(shader.uniform("uMvp"), 1, false, mvpMatrix, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, srcTex)
        GLES20.glUniform1i(shader.uniform("uTex"), 0)
        val modeInt = when (cfg.mirrorMode) {
            MirrorMode.HORIZONTAL -> 0
            MirrorMode.VERTICAL -> 1
            MirrorMode.HORIZONTAL_AND_VERTICAL -> 2
        }
        GLES20.glUniform1i(shader.uniform("uMode"), modeInt)
        GLES20.glUniform1i(shader.uniform("uFlip"), if (cfg.flipMirror) 1 else 0)
        GLES20.glUniform1f(shader.uniform("uRepeat"), cfg.repeatCount.coerceIn(1, 12).toFloat())
        GLES20.glUniform4f(
            shader.uniform("uTint"),
            ((cfg.colorArgb ushr 16) and 0xFF) / 255f,
            ((cfg.colorArgb ushr 8) and 0xFF) / 255f,
            (cfg.colorArgb and 0xFF) / 255f,
            opacity*((cfg.colorArgb ushr 24) and 255)/255f
        )
        drawTexturedQuad(shader, 0f, 0f, w.toFloat(), h.toFloat(), flipV = true)
    }

    private fun drawSafeAreaOverlay(w: Int, h: Int) {
        applyBlendMode(BlendMode.ALPHA)
        Matrix.orthoM(mvpMatrix, 0, 0f, w.toFloat(), h.toFloat(), 0f, -1f, 1f)
        val marginX = w * 0.08f
        val marginY = h * 0.08f
        val thick = 2f
        var vIdx = 0
        var cIdx = 0
        fun rect(x0: Float, y0: Float, x1: Float, y1: Float) {
            vertexScratch[vIdx++] = x0; vertexScratch[vIdx++] = y0
            vertexScratch[vIdx++] = x1; vertexScratch[vIdx++] = y0
            vertexScratch[vIdx++] = x0; vertexScratch[vIdx++] = y1
            vertexScratch[vIdx++] = x0; vertexScratch[vIdx++] = y1
            vertexScratch[vIdx++] = x1; vertexScratch[vIdx++] = y0
            vertexScratch[vIdx++] = x1; vertexScratch[vIdx++] = y1
            repeat(6) {
                colorScratch[cIdx++] = 0.78f; colorScratch[cIdx++] = 0.95f
                colorScratch[cIdx++] = 0.42f; colorScratch[cIdx++] = 0.55f
            }
        }
        rect(marginX, marginY, w - marginX, marginY + thick)
        rect(marginX, h - marginY - thick, w - marginX, h - marginY)
        rect(marginX, marginY, marginX + thick, h - marginY)
        rect(w - marginX - thick, marginY, w - marginX, h - marginY)
        drawColorVertices(vIdx / 2, 1f)
    }

    private fun drawColorVertices(vertexCount: Int, opacity: Float) {
        if (vertexCount <= 0) return
        val shader = colorShader ?: return
        shader.use()
        GLES20.glUniformMatrix4fv(shader.uniform("uMvp"), 1, false, mvpMatrix, 0)
        GLES20.glUniform1f(shader.uniform("uOpacity"), opacity)

        vertexBuf.position(0)
        vertexBuf.put(vertexScratch, 0, vertexCount * 2)
        vertexBuf.position(0)

        colorBuf.position(0)
        colorBuf.put(colorScratch, 0, vertexCount * 4)
        colorBuf.position(0)

        val aPos = shader.attrib("aPos")
        val aCol = shader.attrib("aColor")
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, vertexBuf)
        GLES20.glEnableVertexAttribArray(aCol)
        GLES20.glVertexAttribPointer(aCol, 4, GLES20.GL_FLOAT, false, 0, colorBuf)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aCol)
    }

    private fun drawTexturedQuad(
        shader: GlShaderProgram,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        flipV: Boolean,
        flipU: Boolean = false
    ) {
        quadPosBuf.position(0)
        quadPosBuf.put(floatArrayOf(left, top, right, top, left, bottom, right, bottom))
        quadPosBuf.position(0)
        val uvs = when { flipU && flipV -> quadUvBothBuf; flipU -> quadUvHorizontalBuf; flipV -> quadUvFlipBuf; else -> quadUvBuf }
        uvs.position(0)

        val aPos = shader.attrib("aPos")
        val aUv = shader.attrib("aUv")
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, quadPosBuf)
        GLES20.glEnableVertexAttribArray(aUv)
        GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, 0, uvs)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aUv)
    }

    private fun applyBlendMode(mode: BlendMode) {
        if (mode == BlendMode.OVERWRITE) {
            GLES20.glDisable(GLES20.GL_BLEND)
            return
        }
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendEquation(GLES20.GL_FUNC_ADD)
        when (mode) {
            BlendMode.ALPHA, BlendMode.PRE_MUL_ALPHA ->
                GLES20.glBlendFuncSeparate(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA, GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            BlendMode.ADD, BlendMode.ADD_ALPHA ->
                GLES20.glBlendFuncSeparate(GLES20.GL_ONE, GLES20.GL_ONE, GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            BlendMode.SCREEN ->
                GLES20.glBlendFuncSeparate(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_COLOR, GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            BlendMode.MULTIPLY ->
                GLES20.glBlendFuncSeparate(GLES20.GL_DST_COLOR, GLES20.GL_ONE_MINUS_SRC_ALPHA, GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            BlendMode.OVERWRITE -> Unit
        }
    }

    private fun clearBackground(argb: Int) {
        val alpha=((argb ushr 24)and 255)/255f
        GLES20.glClearColor(
            ((argb ushr 16) and 0xFF) / 255f*alpha,
            ((argb ushr 8) and 0xFF) / 255f*alpha,
            (argb and 0xFF) / 255f*alpha,
            alpha
        )
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
    }

    private fun applyReactionDelay(layerId: String, current: FloatArray, delayFrames: Int): FloatArray {
        val d = delayFrames.coerceIn(0, 9)
        if (d == 0) return current
        val q = barDelayHistory.getOrPut(layerId) { ArrayDeque() }
        if(q.firstOrNull()?.size != current.size) q.clear()
        q.addLast(current.copyOf())
        while (q.size > d + 1) q.removeFirst()
        return q.first()
    }

    private fun uploadArtworkTexture(bitmap: Bitmap?) {
        if (artworkTexId != 0) {
            bitmapSizes.remove(artworkTexId);bitmapKeyColors.remove(artworkTexId)
            GLES20.glDeleteTextures(1, intArrayOf(artworkTexId), 0)
            artworkTexId = 0
        }
        if (bitmap != null && !bitmap.isRecycled) {
            artworkTexId = uploadBitmapToGl(bitmap)
        }
    }

    private fun ensureInternalTextures() {
        ensureInternalTex("internalres:white")
        ensureInternalTex("internalres:vignette80")
        ensureInternalTex("internalres:particle_circle_blur4")
    }

    private fun ensureInternalTex(resKey: String): Int {
        textureCache[resKey]?.let { return it }
        val bmp = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when {
            resKey.contains("rainbow") -> {
                paint.shader = android.graphics.LinearGradient(0f, 0f, 256f, 256f, intArrayOf(0xFFFF3030.toInt(), 0xFFFFFF30.toInt(), 0xFF30FF60.toInt(), 0xFF30DFFF.toInt(), 0xFF4050FF.toInt(), 0xFFFF30DD.toInt(), 0xFFFF3030.toInt()), null, Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, 256f, 256f, paint)
            }
            resKey.contains("vignette") -> {
                paint.shader = RadialGradient(
                    128f, 128f, 180f,
                    intArrayOf(0x00000000, 0xCC000000.toInt()),
                    floatArrayOf(0.35f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, 256f, 256f, paint)
            }
            com.vexo.studio.engine.scene.LegacySpriteProfile.supports(resKey) -> {
                val pixels=IntArray(256*256)
                for(y in 0 until 256)for(x in 0 until 256){
                    val dx=(x-127.5f)/127.5f;val dy=(y-127.5f)/127.5f
                    val a=(com.vexo.studio.engine.scene.LegacySpriteProfile.alpha(resKey,kotlin.math.hypot(dx,dy))*255f+.5f).toInt().coerceIn(0,255)
                    pixels[y*256+x]=(a shl 24) or 0x00FFFFFF
                }
                bmp.setPixels(pixels,0,256,0,0,256,256)
            }
            resKey.contains("particle") || resKey.contains("flare") -> {
                paint.shader = RadialGradient(
                    128f, 128f, 124f,
                    intArrayOf(0xFFFFFFFF.toInt(), 0x55FFFFFF, 0x00FFFFFF),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(128f, 128f, 124f, paint)
            }
            resKey.contains("black") -> canvas.drawColor(Color.BLACK)
            resKey.contains("transparent") -> canvas.drawColor(Color.TRANSPARENT)
            else -> canvas.drawColor(Color.WHITE)
        }
        val id = uploadBitmapToGl(bmp)
        bmp.recycle()
        textureCache[resKey] = id
        return id
    }

    private fun trimTextureCache(){
        var bytes=textureCache.values.sumOf{ id->val size=bitmapSizes[id];if(size==null)0L else size.first.toLong()*size.second*4L }
        val iterator=textureCache.entries.iterator()
        val budget=if(isExportRenderer&&highResolutionTextures&&!lowRamDevice)128L*1024*1024 else 48L*1024*1024
        while(iterator.hasNext() && (bytes>budget || textureCache.size>48)){
            val entry=iterator.next()
            if(entry.key=="internalres:white"||entry.key=="internalres:vignette80"||entry.key=="internalres:particle_circle_blur4")continue
            val id=entry.value;val size=bitmapSizes.remove(id);bitmapKeyColors.remove(id)
            staticBlurResults.entries.removeAll{it.value.first.source==id}
            if(size!=null)bytes-=size.first.toLong()*size.second*4L
            GLES20.glDeleteTextures(1,intArrayOf(id),0);iterator.remove()
        }
    }

    private fun ensureUriTex(uriStr: String?): Int {
        if (uriStr.isNullOrBlank()) return 0
        textureCache[uriStr]?.let { return it }
        val now=android.os.SystemClock.elapsedRealtime()
        if((failedImageUntil[uriStr]?:0L)>now)return 0
        val bmp=decodeBoundedBitmap(uriStr)
        if(bmp==null){
            if(failedImageUntil.size>=128)failedImageUntil.remove(failedImageUntil.keys.first())
            failedImageUntil[uriStr]=now+5000L
            return 0
        }
        failedImageUntil.remove(uriStr)
        val id = try{uploadBitmapToGl(bmp)}finally{bmp.recycle()}
        textureCache[uriStr] = id
        return id
    }

    private fun decodeBoundedBitmap(uriStr: String): Bitmap? {
        return runCatching {
            val uri = Uri.parse(uriStr)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if(bounds.outWidth<=0||bounds.outHeight<=0)return null
            val sample=com.vexo.studio.engine.gl.TextureDecodePolicy.sampleSize(bounds.outWidth,bounds.outHeight,imageDecodeLimit)
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val raw = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null

            val rotation = runCatching { context.contentResolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f }.getOrDefault(0f)

            if (rotation == 0f) raw else {
                val m = AndroidMatrix().apply { postRotate(rotation) }
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true).also {
                    if (it !== raw) raw.recycle()
                }
            }
        }.getOrNull()
    }

    private fun resolveTypeface(uri: String?): Typeface {
        if(uri.isNullOrBlank()) return Typeface.SANS_SERIF
        fonts[uri]?.let { return it }
        val result=runCatching {
            val file=java.io.File.createTempFile("vexo_font",".ttf",context.cacheDir)
            try {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                    file.outputStream().use { output ->
                        val buffer=ByteArray(8192);var total=0L
                        while(true){val n=input.read(buffer);if(n<0)break;total+=n;require(total<=16L*1024*1024);output.write(buffer,0,n)}
                    }
                } ?: error("Font unavailable")
                Typeface.createFromFile(file)
            } finally {file.delete()}
        }.getOrDefault(Typeface.SANS_SERIF)
        if(fonts.size>=16)fonts.clear()
        fonts[uri]=result
        return result
    }

    private fun bindCreativeImage(shader:GlShaderProgram,cfg:com.vexo.studio.model.CreativeImageConfig,scene:RenderSceneSnapshot,reactive:Float,w:Int,h:Int){
        val amount=(cfg.amount+reactive).coerceIn(0f,1f)
        GLES20.glUniform1i(shader.uniform("uCreativeGradient"),cfg.gradient.ordinal)
        GLES20.glUniform1i(shader.uniform("uCreativeDistortion"),cfg.distortion.ordinal)
        fun color(name:String,argb:Int){GLES20.glUniform4f(shader.uniform(name),((argb ushr 16)and 255)/255f,((argb ushr 8)and 255)/255f,(argb and 255)/255f,((argb ushr 24)and 255)/255f)}
        color("uGradientFrom",cfg.gradientFrom);color("uGradientTo",cfg.gradientTo)
        GLES20.glUniform4f(shader.uniform("uGradientGeometry"),cfg.gradientCenterX,cfg.gradientCenterY,cfg.gradientAngle*PI.toFloat()/180f,cfg.gradientScale.coerceIn(.05f,4f))
        GLES20.glUniform1f(shader.uniform("uGradientMix"),cfg.gradientMix.coerceIn(0f,1f))
        // Track time keeps preview and offline export phase aligned.
        val phase=(scene.trackMeta.positionMs/1000.0*cfg.speed*6.283185307).rem(6283.185307).toFloat()
        GLES20.glUniform4f(shader.uniform("uDistort"),amount,cfg.frequency.coerceIn(2f,24f),phase,0f)
        GLES20.glUniform4f(shader.uniform("uGrade"),cfg.brightness.coerceIn(-1f,1f),cfg.contrast.coerceIn(0f,3f),cfg.temperature.coerceIn(-1f,1f),cfg.hue*PI.toFloat()/180f)
        GLES20.glUniform3f(shader.uniform("uFinishing"),cfg.vignette.coerceIn(0f,1f),cfg.scanlines.coerceIn(0f,1f),cfg.grain.coerceIn(0f,1f))
        GLES20.glUniform2f(shader.uniform("uCreativeResolution"),w.toFloat(),h.toFloat())
        GLES20.glUniform4f(shader.uniform("uPattern"),cfg.pattern.ordinal.toFloat(),cfg.patternDensity.coerceIn(1f,48f),cfg.patternIntensity.coerceIn(0f,1f),(scene.trackMeta.positionMs/1000.0*cfg.patternSpeed*6.2831853%6283.1853).toFloat())
        GLES20.glUniform4f(shader.uniform("uAperture"),cfg.aperture.ordinal.toFloat(),cfg.apertureSize.coerceIn(.01f,2f),cfg.apertureFeather.coerceIn(.001f,.5f),if(cfg.apertureInvert)1f else 0f)
        GLES20.glUniform2f(shader.uniform("uApertureCenter"),cfg.apertureX,cfg.apertureY)
    }

    private fun resetImageState(shader: GlShaderProgram) {
        shader.use()
        GLES20.glUniform1f(shader.uniform("uGlowGain"),1f)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glUniform1i(shader.uniform("uTex"),0)
        GLES20.glUniform1i(shader.uniform("uMaskEnabled"),0)
        GLES20.glUniform1i(shader.uniform("uImageFxEnabled"),0)
        GLES20.glUniform1i(shader.uniform("uShape"),0)
        GLES20.glUniform1i(shader.uniform("uColorKeyEnabled"),0)
        GLES20.glUniform1f(shader.uniform("uReveal"),1f)
        GLES20.glUniform1i(shader.uniform("uCreativeGradient"),0)
        GLES20.glUniform1i(shader.uniform("uCreativeDistortion"),0)
        GLES20.glUniform4f(shader.uniform("uGrade"),0f,1f,0f,0f)
        GLES20.glUniform4f(shader.uniform("uDistort"),0f,2f,0f,0f)
        GLES20.glUniform3f(shader.uniform("uFinishing"),0f,0f,0f)
        GLES20.glUniform2f(shader.uniform("uCreativeResolution"),1f,1f)
        GLES20.glUniform4f(shader.uniform("uPattern"),0f,1f,0f,0f)
        GLES20.glUniform4f(shader.uniform("uAperture"),0f,1f,.001f,0f)
        GLES20.glUniform2f(shader.uniform("uApertureCenter"),.5f,.5f)
        GLES20.glUniform1f(shader.uniform("uSaturation"),1f)
        GLES20.glUniform4f(shader.uniform("uTint"),1f,1f,1f,1f)
        GLES20.glUniform1f(shader.uniform("uOpacity"),1f)
    }

    private fun renderTextBitmap(text: String, cfg: LayerConfig.Text): Bitmap {
        val w = 1024
        val h = 256
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val style = when {
            cfg.bold && cfg.italic -> Typeface.BOLD_ITALIC
            cfg.bold -> Typeface.BOLD
            cfg.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cfg.colorArgb
            textSize = cfg.fontSize.toFloat().coerceIn(14f, 120f)
            val weight=if(cfg.fontWeight in 100..900)cfg.fontWeight else if(cfg.bold)700 else 400
            typeface = if(cfg.fontAssetUri.isNullOrBlank())com.vexo.studio.engine.scene.StudioFonts.resolve(context,cfg.fontFamily,weight,cfg.italic) else if(android.os.Build.VERSION.SDK_INT>=28)Typeface.create(resolveTypeface(cfg.fontAssetUri),weight,cfg.italic) else Typeface.create(resolveTypeface(cfg.fontAssetUri), when {
                cfg.italic && weight >= 700 -> Typeface.BOLD_ITALIC
                cfg.italic -> Typeface.ITALIC
                weight >= 700 -> Typeface.BOLD
                else -> Typeface.NORMAL
            })
            textAlign = when (cfg.alignment) {
                -1 -> Paint.Align.LEFT
                1 -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            letterSpacing = cfg.letterSpacing
            if (cfg.shadowRadius > 0f) {
                setShadowLayer(cfg.shadowRadius, 0f, 2f, cfg.shadowColorArgb)
            }
        }
        val lines = text.split('\n')
        val lineHeight = paint.fontSpacing * cfg.lineSpacing
        val startY = h * 0.5f - ((lines.size - 1) * lineHeight) * 0.5f + paint.textSize * 0.35f
        val xPos = when (cfg.alignment) {
            -1 -> 32f
            1 -> w - 32f
            else -> w * 0.5f
        }
        lines.forEachIndexed { idx, line ->
            val yPos = startY + idx * lineHeight
            if (cfg.strokeWidth > 0f) {
                val strokePaint = Paint(paint).apply {
                    this.style = Paint.Style.STROKE
                    strokeWidth = cfg.strokeWidth
                    color = cfg.strokeColorArgb
                }
                canvas.drawText(line, xPos, yPos, strokePaint)
            }
            canvas.drawText(line, xPos, yPos, paint)
        }
        return bmp
    }

    private fun uploadBitmapToGl(bitmap: Bitmap): Int {
        // A deleted/reuploaded source may reuse a GL texture name.
        staticBlurResults.clear()
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmapSizes[ids[0]] = bitmap.width to bitmap.height
        bitmapKeyColors[ids[0]]=runCatching{ bitmap.getPixel(0,0) }.getOrDefault(0xFF00FF00.toInt())
        return ids[0]
    }

    private fun allocFloatBuffer(size: Int): FloatBuffer =
        ByteBuffer.allocateDirect(size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    private fun formatTimeMs(ms: Long): String {
        val sec = (ms / 1000L).coerceAtLeast(0L)
        return "%d:%02d".format(sec / 60, sec % 60)
    }
}
