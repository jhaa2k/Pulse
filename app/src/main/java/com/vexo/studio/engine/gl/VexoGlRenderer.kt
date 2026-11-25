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
    val showSafeArea: Boolean = false
)

/**
 * Shared OpenGL ES 2.0 renderer used by both live GLSurfaceView preview and
 * deterministic offline MediaCodec EGL video export.
 */
class VexoGlRenderer(
    private val context: Context,
    private val isExportRenderer: Boolean = false
) : GLSurfaceView.Renderer {

    private val pendingScene = AtomicReference<RenderSceneSnapshot?>()
    private val fboPool = FboPool()

    private var colorShader: GlShaderProgram? = null
    private var imageShader: GlShaderProgram? = null
    private var blurShader: GlShaderProgram? = null
    private var rgbSplitShader: GlShaderProgram? = null
    private var mirrorShader: GlShaderProgram? = null

    private var viewportW = 1280
    private var viewportH = 720
    private var lastFrameNs = System.nanoTime()

    @Volatile
    var lastFrameDurationMs: Float = 0f
        private set

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
    private val textureCache = mutableMapOf<String, Int>()
    private val bitmapSizes = mutableMapOf<Int, Pair<Int, Int>>()
    private val particleSims = mutableMapOf<String, ParticleSimulator>()
    private val barDelayHistory = mutableMapOf<String, ArrayDeque<FloatArray>>()
    private var lastProjectId: String? = null
    private var lastPositionMs = -1L
    private var lastArtworkVersion = -1
    private var artworkTexId = 0

    fun submitSnapshot(snapshot: RenderSceneSnapshot) {
        pendingScene.set(snapshot)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        initGlResources()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
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
            renderScene(snap, deltaSec, targetWidth = viewportW, targetHeight = viewportH)
        } else {
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        }

        lastFrameDurationMs = (System.nanoTime() - nowNs) / 1_000_000f
    }

    fun initGlResources() {
        releaseGlResources()
        colorShader = GlShaderProgram(VexoShaders.COLOR_VERTEX, VexoShaders.COLOR_FRAGMENT)
        imageShader = GlShaderProgram(VexoShaders.TEXTURE_VERTEX, VexoShaders.IMAGE_FRAGMENT)
        blurShader = GlShaderProgram(VexoShaders.TEXTURE_VERTEX, VexoShaders.BLUR_FRAGMENT)
        rgbSplitShader = GlShaderProgram(VexoShaders.TEXTURE_VERTEX, VexoShaders.RGB_SPLIT_FRAGMENT)
        mirrorShader = GlShaderProgram(VexoShaders.TEXTURE_VERTEX, VexoShaders.MIRROR_FRAGMENT)
        ensureInternalTextures()
    }

    fun releaseGlResources() {
        colorShader?.release(); colorShader = null
        imageShader?.release(); imageShader = null
        blurShader?.release(); blurShader = null
        rgbSplitShader?.release(); rgbSplitShader = null
        mirrorShader?.release(); mirrorShader = null
        fboPool.releaseAll()
        bitmapSizes.clear()
        if (textureCache.isNotEmpty()) {
            val ids = textureCache.values.toIntArray()
            GLES20.glDeleteTextures(ids.size, ids, 0)
            textureCache.clear()
        }
        if (artworkTexId != 0) {
            bitmapSizes.remove(artworkTexId)
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
        viewportW = targetWidth.coerceAtLeast(16)
        viewportH = targetHeight.coerceAtLeast(16)

        val position = scene.trackMeta.positionMs
        if (lastProjectId != scene.project.id || (lastPositionMs >= 0L && (position < lastPositionMs - 100L || position - lastPositionMs > 750L))) {
            fboPool.releaseByPrefix("trail_")
            particleSims.clear()
            barDelayHistory.clear()
        }
        if (lastProjectId != scene.project.id) fboPool.releaseByPrefix("comp_")
        lastProjectId = scene.project.id
        lastPositionMs = position
        if (scene.trackMeta.artworkVersion != lastArtworkVersion || (artworkTexId == 0 && scene.trackMeta.artwork != null)) {
            lastArtworkVersion = scene.trackMeta.artworkVersion
            uploadArtworkTexture(scene.trackMeta.artwork)
        }

        val resolved = SceneGraph.resolve(scene.project)
        val compTextures = mutableMapOf<Int, Int>()

        // Render sub-compositions first (index != 0) into offscreen FBOs
        for (comp in resolved.order) {
            if (comp.index == 0) continue
            val fbo = fboPool.obtain("comp_${comp.index}", viewportW, viewportH)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo.fboId)
            GLES20.glViewport(0, 0, fbo.width, fbo.height)
            clearBackground(0x00000000)
            renderComposition(comp, scene, deltaSec, fbo.width, fbo.height, compTextures)
            compTextures[comp.index] = fbo.textureId
        }

        // Render Final Composition (index 0) to default framebuffer
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, viewportW, viewportH)
        clearBackground(scene.project.canvas.backgroundColorArgb)

        val finalComp = resolved.order.firstOrNull { it.index == 0 }
            ?: scene.project.compositions.firstOrNull()
        if (finalComp != null) {
            renderComposition(finalComp, scene, deltaSec, viewportW, viewportH, compTextures)
        }

        if (!isExportRenderer && scene.showSafeArea) {
            drawSafeAreaOverlay(viewportW, viewportH)
        }
    }

    private fun renderComposition(
        comp: CompositionNode,
        scene: RenderSceneSnapshot,
        deltaSec: Float,
        w: Int,
        h: Int,
        compTextures: Map<Int, Int>
    ) {
        Matrix.orthoM(orthoMatrix, 0, 0f, w.toFloat(), h.toFloat(), 0f, -1f, 1f)
        if (!isExportRenderer && comp.index == 0 && (scene.zoom != 1f || scene.panX != 0f || scene.panY != 0f)) {
            Matrix.setIdentityM(tempMatrix, 0)
            Matrix.translateM(tempMatrix, 0, w * 0.5f + scene.panX, h * 0.5f + scene.panY, 0f)
            Matrix.scaleM(tempMatrix, 0, scene.zoom, scene.zoom, 1f)
            Matrix.translateM(tempMatrix, 0, -w * 0.5f, -h * 0.5f, 0f)
            Matrix.multiplyMM(orthoMatrix, 0, orthoMatrix.copyOf(), 0, tempMatrix, 0)
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
        val c = layer.common
        val providerIdx = when (val cfg = layer.config) {
            is LayerConfig.Bars -> cfg.audioProviderIndex
            else -> c.measurePos.providerIndex
        }
        val audioSnap = scene.audioSnapshots[providerIdx] ?: scene.audioSnapshots.values.firstOrNull()
        val trackNorm = if (scene.trackMeta.durationMs > 0) {
            (scene.trackMeta.positionMs.toFloat() / scene.trackMeta.durationMs).coerceIn(0f, 1f)
        } else 0f

        val keyframePos = KeyframeEvaluator.evaluate(
            scene.project.keyframes.firstOrNull { it.targetLayerId == c.id && it.propertyKey == "posX" },
            scene.trackMeta.positionMs,
            0f
        )

        val dPos = MeasureEvaluator.evaluatePair(
            c.measurePos, audioSnap, scene.totalTimeSec, scene.playingTimeSec, trackNorm, scene.isPlaying, keyframePos
        )
        val dScale = MeasureEvaluator.evaluatePair(
            c.measureScale, scene.audioSnapshots[c.measureScale.providerIndex] ?: audioSnap, scene.totalTimeSec, scene.playingTimeSec, trackNorm, scene.isPlaying
        )
        val dRot = MeasureEvaluator.evaluateScalar(
            c.measureRot, scene.audioSnapshots[c.measureRot.providerIndex] ?: audioSnap, scene.totalTimeSec, scene.playingTimeSec, trackNorm, scene.isPlaying
        )
        val dOpacity = MeasureEvaluator.evaluateScalar(
            c.measureOpacity, scene.audioSnapshots[c.measureOpacity.providerIndex] ?: audioSnap, scene.totalTimeSec, scene.playingTimeSec, trackNorm, scene.isPlaying
        )

        val tx = (c.posX + dPos.x) * w
        val ty = (c.posY + dPos.y) * h
        val sx = c.scaleX + dScale.x
        val sy = if (c.scaleIsUniform) sx else (c.scaleY + dScale.y)
        val rot = c.rotationDeg + dRot * 20f
        val opacity = (c.opacity + dOpacity).coerceIn(0f, 1f)

        Matrix.setIdentityM(tempMatrix, 0)
        Matrix.translateM(tempMatrix, 0, tx, ty, 0f)
        if (rot != 0f) Matrix.rotateM(tempMatrix, 0, rot, 0f, 0f, 1f)
        if (sx != 1f || sy != 1f) Matrix.scaleM(tempMatrix, 0, sx, sy, 1f)
        Matrix.translateM(tempMatrix, 0, -c.alignmentX * w, -c.alignmentY * h, 0f)
        Matrix.multiplyMM(mvpMatrix, 0, orthoMatrix, 0, tempMatrix, 0)

        applyBlendMode(c.blendMode)

        when (val cfg = layer.config) {
            is LayerConfig.Bars -> drawBarsLayer(c.id, cfg, audioSnap, opacity, w, h)
            is LayerConfig.Image -> drawImageLayer(cfg, scene, audioSnap, trackNorm, opacity, w, h, compTextures)
            is LayerConfig.Text -> drawTextLayer(cfg, scene.trackMeta, opacity, w, h)
            is LayerConfig.Shape -> drawShapeLayer(cfg, opacity, w, h)
            is LayerConfig.Particles -> drawParticlesLayer(c.id, cfg, audioSnap, scene, deltaSec, opacity, w, h)
            is LayerConfig.BlurEffect -> drawBlurEffect(cfg, opacity, w, h, compTextures)
            is LayerConfig.RgbSplitEffect -> drawRgbSplitEffect(cfg, audioSnap, scene, trackNorm, opacity, w, h, compTextures)
            is LayerConfig.MotionBlurEffect -> drawMotionBlurEffect(c.id, cfg, opacity, w, h, compTextures)
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
            drawBarsLayer("${layerId}_segment2", cfg.copy(segment1 = cfg.segment2, segment2 = BarsSegmentType.NONE, barWidth = cfg.segment2Width, barHeightMultiplier = cfg.segment2HeightMultiplier, fixedHeight = cfg.segment2FixedHeight, mirror = cfg.segment2Mirror, colorFromArgb = cfg.segment2ColorFrom, colorToArgb = cfg.segment2ColorTo), audioSnap, opacity, w, h)
            return
        }
        val rawBars = audioSnap?.bars ?: FloatArray(64) { i ->
            0.08f + 0.14f * kotlin.math.abs(sin(i * 0.22f))
        }
        val delayedBars = applyReactionDelay(layerId, rawBars, cfg.reactionDelay)
        val count = delayedBars.size
        if (count == 0) return
        if (cfg.segment1 == BarsSegmentType.LINE && count > 1) {
            drawConnectedSpectrum(cfg, delayedBars, opacity, w, h)
            return
        }
        val minDim = minOf(w, h).toFloat()

        // Ensure vertex scratch capacity (each bar = 6 vertices = 12 floats pos + 24 floats color)
        val neededVerts = count * 12
        if (vertexScratch.size < neededVerts) {
            vertexScratch = FloatArray(neededVerts)
            colorScratch = FloatArray(count * 24)
            vertexBuf = allocFloatBuffer(vertexScratch.size)
            colorBuf = allocFloatBuffer(colorScratch.size)
        }

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

            when (cfg.shapePath) {
                BarsShapePath.HORIZONTAL_LINE, BarsShapePath.LETTER -> {
                    val slotW = (w * 0.86f) / count
                    val bw = (slotW * cfg.barWidth.coerceIn(0.05f, 1.5f)).coerceAtLeast(1.5f)
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
                }
                BarsShapePath.VERTICAL_LINE -> {
                    val slotH = (h * 0.86f) / count
                    val bh = (slotH * cfg.barWidth.coerceIn(0.05f, 1.5f)).coerceAtLeast(1.5f)
                    val cy = h * 0.07f + i * slotH + slotH * 0.5f
                    val cx = w * 0.5f
                    val leftX = if (cfg.mirror) cx - magPx * 0.5f else cx
                    val rightX = if (cfg.mirror) cx + magPx * 0.5f else cx + magPx
                    emitQuad(leftX, cy - bh * 0.5f, rightX, cy - bh * 0.5f, leftX, cy + bh * 0.5f, rightX, cy + bh * 0.5f, barColor0, gradColor)
                }
                BarsShapePath.CIRCLE, BarsShapePath.SIDED_POLYGON -> {
                    val cx = w * 0.5f
                    val cy = h * 0.5f
                    val angle = Math.toRadians(cfg.pathAngleDeg.toDouble()).toFloat() +
                        (i.toFloat() / count) * 2f * PI.toFloat() * (1f - cfg.pathGap.coerceIn(0f, 0.9f))
                    val baseR = if (cfg.shapePath == BarsShapePath.SIDED_POLYGON) {
                        val sides = cfg.polygonSides.coerceIn(3, 12)
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
                    val halfW = ((2f * PI.toFloat() * baseR) / count) * cfg.barWidth.coerceIn(0.05f, 1.4f) * 0.5f
                    val tipW = if (cfg.segment1 == BarsSegmentType.SHARP_BARS) halfW * 0.25f else halfW

                    emitQuad(
                        cx + nx * rIn - px * halfW, cy + ny * rIn - py * halfW,
                        cx + nx * rIn + px * halfW, cy + ny * rIn + py * halfW,
                        cx + nx * rOut - px * tipW, cy + ny * rOut - py * tipW,
                        cx + nx * rOut + px * tipW, cy + ny * rOut + py * tipW,
                        barColor0, gradColor
                    )
                }
            }
        }

        drawColorVertices(vIdx / 2, opacity)
    }

    private fun drawConnectedSpectrum(cfg: LayerConfig.Bars, bars: FloatArray, opacity: Float, w: Int, h: Int) {
        val closed = cfg.shapePath == BarsShapePath.CIRCLE || cfg.shapePath == BarsShapePath.SIDED_POLYGON
        val count = bars.size
        val minDim = minOf(w, h).toFloat()
        val segments = if (closed && cfg.pathGap <= 0f) count else count - 1
        val needed = segments * 12
        if (vertexScratch.size < needed || colorScratch.size < segments * 24) {
            vertexScratch = FloatArray(needed)
            colorScratch = FloatArray(segments * 24)
            vertexBuf = allocFloatBuffer(vertexScratch.size)
            colorBuf = allocFloatBuffer(colorScratch.size)
        }
        fun point(i: Int): Pair<Float, Float> {
            val idx = if (cfg.flipInput) count - 1 - i else i
            val gain = cfg.heightScale * cfg.barHeightMultiplier
            val raw = (bars[idx] * kotlin.math.abs(gain)).coerceIn(0f, kotlin.math.abs(cfg.maxHeightScale))
            val amp = raw * minDim * 0.42f * if (gain < 0f) -1f else 1f
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
        for (i in 0 until segments) {
            val (x0, y0) = point(i)
            val (x1, y1) = point((i + 1) % count)
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
        audioSnap: AudioProviderSnapshot?,
        trackNorm: Float,
        opacity: Float,
        w: Int,
        h: Int,
        compTextures: Map<Int, Int>
    ) {
        val texId = when (cfg.sourceType) {
            ImageSourceType.COMPOSITION -> compTextures[cfg.sourceCompositionIndex] ?: return
            ImageSourceType.ALBUM_ART -> if (artworkTexId != 0) artworkTexId else ensureInternalTex("internalres:vignette80")
            ImageSourceType.INTERNAL -> ensureInternalTex(cfg.internalRes)
            ImageSourceType.IMPORTED -> ensureUriTex(cfg.customUri)
            ImageSourceType.SOLID_COLOR -> ensureInternalTex("internalres:white")
        }
        if (texId == 0) return

        val colorBlend = MeasureEvaluator.evaluateScalar(
            cfg.measureColorBlend, audioSnap, scene.totalTimeSec, scene.playingTimeSec, trackNorm, scene.isPlaying
        ).coerceIn(0f, 1f)
        val tint = MeasureEvaluator.blendArgb(cfg.colorArgb, cfg.colorToArgb, colorBlend)

        val shader = imageShader ?: return
        shader.use()
        GLES20.glUniformMatrix4fv(shader.uniform("uMvp"), 1, false, mvpMatrix, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glUniform1i(shader.uniform("uTex"), 0)
        GLES20.glUniform4f(
            shader.uniform("uTint"),
            ((tint ushr 16) and 0xFF) / 255f,
            ((tint ushr 8) and 0xFF) / 255f,
            (tint and 0xFF) / 255f,
            ((tint ushr 24) and 0xFF) / 255f
        )
        GLES20.glUniform1f(shader.uniform("uSaturation"), cfg.saturation)
        val shapeCode = when (cfg.shape) {
            ImageShape.NONE -> 0
            ImageShape.CIRCLE -> 1
            ImageShape.SIDED_POLYGON -> 2
        }
        GLES20.glUniform1i(shader.uniform("uShape"), shapeCode)
        GLES20.glUniform1i(shader.uniform("uColorKeyEnabled"), if (cfg.colorKeyEnabled) 1 else 0)
        GLES20.glUniform3f(
            shader.uniform("uKeyColor"),
            ((cfg.colorKeyArgb ushr 16) and 0xFF) / 255f,
            ((cfg.colorKeyArgb ushr 8) and 0xFF) / 255f,
            (cfg.colorKeyArgb and 0xFF) / 255f
        )
        GLES20.glUniform1f(shader.uniform("uOpacity"), opacity * cfg.opacityStrength.coerceIn(0f, 1f))

        val (sourceW, sourceH) = if (cfg.sourceType == ImageSourceType.COMPOSITION) w to h else bitmapSizes[texId] ?: (w to h)
        val imageRatio = sourceW.toFloat() / sourceH.coerceAtLeast(1)
        val shapeSquare = cfg.shape != ImageShape.NONE
        val frameW = if (shapeSquare) minOf(w,h).toFloat() else w.toFloat()
        val frameH = if (shapeSquare) minOf(w,h).toFloat() else h.toFloat()
        val frameRatio = frameW / frameH.coerceAtLeast(1f)
        val crop = cfg.keepAspectRatioAndCropToFit
        val (drawW, drawH) = if (cfg.keepAspectRatio && !crop && !shapeSquare) {
            if(imageRatio > frameRatio) frameW to frameW / imageRatio else frameH * imageRatio to frameH
        } else frameW to frameH
        val uvX = if (crop || shapeSquare) minOf(1f, frameRatio / imageRatio) else 1f
        val uvY = if (crop || shapeSquare) minOf(1f, imageRatio / frameRatio) else 1f
        GLES20.glUniform1i(shader.uniform("uImageFxEnabled"), 1)
        GLES20.glUniform2f(shader.uniform("uUvScale"), uvX, uvY)
        val blur = if(cfg.blurEnabled) cfg.blurRadius.coerceIn(0, 64).toFloat() / cfg.blurDivider.coerceAtLeast(1) else 0f
        GLES20.glUniform2f(shader.uniform("uImageBlurStep"), blur / sourceW.coerceAtLeast(1), blur / sourceH.coerceAtLeast(1))
        GLES20.glUniform1f(shader.uniform("uKeyStrength"), cfg.transparencyStrength.coerceIn(0f,1f))
        val left = (w - drawW) * 0.5f
        val top = (h - drawH) * 0.5f
        val maskIsComp = cfg.maskImage.startsWith("composition:")
        val maskTex = when {
            maskIsComp -> cfg.maskImage.substringAfter(':').toIntOrNull()?.let { compTextures[it] } ?: 0
            cfg.maskImage.startsWith("internalres:") -> ensureInternalTex(cfg.maskImage)
            cfg.maskImage.startsWith("content://") || cfg.maskImage.startsWith("file://") -> ensureUriTex(cfg.maskImage)
            else -> 0
        }
        // Texture creation can change unit 0 binding; explicitly restore the source.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glUniform1i(shader.uniform("uMaskEnabled"), if (maskTex != 0) 1 else 0)
        GLES20.glUniform1i(shader.uniform("uMaskMode"), cfg.maskMode.ordinal)
        GLES20.glUniform1i(shader.uniform("uMaskFlipV"), if (maskIsComp != (cfg.sourceType == ImageSourceType.COMPOSITION)) 1 else 0)
        if (maskTex != 0) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTex)
            GLES20.glUniform1i(shader.uniform("uMaskTex"), 1)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        }
        drawTexturedQuad(shader, left, top, left + drawW, top + drawH, flipV = cfg.flipV xor (cfg.sourceType == ImageSourceType.COMPOSITION), flipU = cfg.flipU)
        GLES20.glUniform1i(shader.uniform("uMaskEnabled"), 0)
        GLES20.glUniform1i(shader.uniform("uImageFxEnabled"), 0)
    }

    private fun drawTextLayer(
        cfg: LayerConfig.Text,
        meta: TrackMetadataSnapshot,
        opacity: Float,
        w: Int,
        h: Int
    ) {
        val resolved = cfg.text
            .replace("{Title}", meta.title, ignoreCase = true)
            .replace("{Artist}", meta.artist, ignoreCase = true)
            .replace("{Album}", meta.album, ignoreCase = true)
            .replace("{Time}", formatTimeMs(meta.positionMs), ignoreCase = true)

        val key = "txt_${resolved}_${cfg.fontSize}_${cfg.bold}_${cfg.italic}_${cfg.colorArgb}_${cfg.alignment}_${cfg.letterSpacing}_${cfg.lineSpacing}_${cfg.strokeWidth}_${cfg.strokeColorArgb}_${cfg.shadowRadius}"
        val texId = textureCache[key] ?: run {
            val bmp = renderTextBitmap(resolved, cfg)
            val id = uploadBitmapToGl(bmp)
            bmp.recycle()
            if (textureCache.size > 48) {
                val oldestKey = textureCache.keys.firstOrNull { it.startsWith("txt_") }
                if (oldestKey != null) {
                    val oldId = textureCache.remove(oldestKey) ?: 0
                    bitmapSizes.remove(oldId)
                    if (oldId != 0) GLES20.glDeleteTextures(1, intArrayOf(oldId), 0)
                }
            }
            textureCache[key] = id
            id
        }
        if (texId == 0) return

        val shader = imageShader ?: return
        shader.use()
        GLES20.glUniformMatrix4fv(shader.uniform("uMvp"), 1, false, mvpMatrix, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glUniform1i(shader.uniform("uTex"), 0)
        GLES20.glUniform4f(shader.uniform("uTint"), 1f, 1f, 1f, 1f)
        GLES20.glUniform1f(shader.uniform("uSaturation"), 1f)
        GLES20.glUniform1i(shader.uniform("uShape"), 0)
        GLES20.glUniform1i(shader.uniform("uColorKeyEnabled"), 0)
        GLES20.glUniform1f(shader.uniform("uOpacity"), opacity)

        val boxW = w * 0.8f
        val boxH = h * 0.24f
        drawTexturedQuad(shader, (w - boxW) * 0.5f, (h - boxH) * 0.5f, (w + boxW) * 0.5f, (h + boxH) * 0.5f, flipV = false)
    }

    private fun drawShapeLayer(
        cfg: LayerConfig.Shape,
        opacity: Float,
        w: Int,
        h: Int
    ) {
        val cx = w * 0.5f
        val cy = h * 0.5f
        val r = minOf(w, h) * 0.32f
        val sides = when (cfg.kind) {
            ShapeKind.RECTANGLE -> 4
            ShapeKind.ELLIPSE -> 48
            ShapeKind.POLYGON -> cfg.sides.coerceIn(3, 24)
            ShapeKind.STAR -> cfg.sides.coerceIn(4, 16) * 2
            ShapeKind.LINE -> 2
        }

        val needed = (sides + 2) * 12
        if (vertexScratch.size < needed) {
            vertexScratch = FloatArray(needed)
            colorScratch = FloatArray((sides + 2) * 24)
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

        val strokeW = cfg.strokeWidth.coerceIn(1f, 32f)
        for (i in 0 until sides) {
            val a0 = (i.toFloat() / sides) * 2f * PI.toFloat() + (if (cfg.kind == ShapeKind.RECTANGLE) PI.toFloat() * 0.25f else 0f)
            val a1 = ((i + 1).toFloat() / sides) * 2f * PI.toFloat() + (if (cfg.kind == ShapeKind.RECTANGLE) PI.toFloat() * 0.25f else 0f)
            val r0 = if (cfg.kind == ShapeKind.STAR && i % 2 == 1) r * 0.48f else r
            val r1 = if (cfg.kind == ShapeKind.STAR && (i + 1) % 2 == 1) r * 0.48f else r

            val x0 = cx + cos(a0) * r0
            val y0 = cy + sin(a0) * r0
            val x1 = cx + cos(a1) * r1
            val y1 = cy + sin(a1) * r1

            // Fill triangle
            if (((cfg.fillColorArgb ushr 24) and 0xFF) > 0 && cfg.kind != ShapeKind.LINE) {
                pushVert(cx, cy, cfg.fillColorArgb)
                pushVert(x0, y0, cfg.gradientEndArgb)
                pushVert(x1, y1, cfg.gradientEndArgb)
            }
            // Stroke edge quad
            val x0i = cx + cos(a0) * (r0 - strokeW)
            val y0i = cy + sin(a0) * (r0 - strokeW)
            val x1i = cx + cos(a1) * (r1 - strokeW)
            val y1i = cy + sin(a1) * (r1 - strokeW)
            pushVert(x0i, y0i, cfg.strokeColorArgb)
            pushVert(x0, y0, cfg.strokeColorArgb)
            pushVert(x1, y1, cfg.strokeColorArgb)
            pushVert(x0i, y0i, cfg.strokeColorArgb)
            pushVert(x1, y1, cfg.strokeColorArgb)
            pushVert(x1i, y1i, cfg.strokeColorArgb)
        }
        drawColorVertices(vIdx / 2, opacity)
    }

    private fun drawParticlesLayer(
        layerId: String,
        cfg: LayerConfig.Particles,
        audioSnap: AudioProviderSnapshot?,
        scene: RenderSceneSnapshot,
        deltaSec: Float,
        opacity: Float,
        w: Int,
        h: Int
    ) {
        val sim = particleSims.getOrPut(layerId) { ParticleSimulator() }
        sim.step(
            cfg = cfg,
            snapshot = audioSnap,
            deltaSec = deltaSec,
            totalTimeSec = scene.totalTimeSec,
            playingTimeSec = scene.playingTimeSec,
            isPlaying = scene.isPlaying
        )
        val count = sim.activeCount
        if (count <= 0) return

        val needed = count * 12
        if (vertexScratch.size < needed) {
            vertexScratch = FloatArray(needed)
            colorScratch = FloatArray(count * 24)
            vertexBuf = allocFloatBuffer(vertexScratch.size)
            colorBuf = allocFloatBuffer(colorScratch.size)
        }

        val minDim = minOf(w, h).toFloat()
        var vIdx = 0
        var cIdx = 0

        for (i in 0 until count) {
            val px = sim.outX[i] * w
            val py = sim.outY[i] * h
            val half = sim.outSize[i] * minDim * 0.5f
            val col = MeasureEvaluator.blendArgb(cfg.colorFromArgb, cfg.colorToArgb, sim.outProgress[i])
            val r = ((col ushr 16) and 0xFF) / 255f
            val g = ((col ushr 8) and 0xFF) / 255f
            val b = (col and 0xFF) / 255f
            val a = (((col ushr 24) and 0xFF) / 255f) * sim.outAlpha[i]

            val x0 = px - half; val y0 = py - half
            val x1 = px + half; val y1 = py + half
            vertexScratch[vIdx++] = x0; vertexScratch[vIdx++] = y0
            vertexScratch[vIdx++] = x1; vertexScratch[vIdx++] = y0
            vertexScratch[vIdx++] = x0; vertexScratch[vIdx++] = y1
            vertexScratch[vIdx++] = x0; vertexScratch[vIdx++] = y1
            vertexScratch[vIdx++] = x1; vertexScratch[vIdx++] = y0
            vertexScratch[vIdx++] = x1; vertexScratch[vIdx++] = y1

            repeat(6) {
                colorScratch[cIdx++] = r; colorScratch[cIdx++] = g
                colorScratch[cIdx++] = b; colorScratch[cIdx++] = a
            }
        }
        drawColorVertices(vIdx / 2, opacity)
    }

    private fun drawBlurEffect(
        cfg: LayerConfig.BlurEffect,
        opacity: Float,
        w: Int,
        h: Int,
        compTextures: Map<Int, Int>
    ) {
        val srcTex = compTextures[cfg.sourceCompositionIndex] ?: return
        val shader = blurShader ?: return
        val ping = fboPool.obtain("blur_ping", w / 2, h / 2)
        val pong = fboPool.obtain("blur_pong", w / 2, h / 2)

        val savedFbo = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, savedFbo, 0)

        // Filter passes overwrite transparent targets; blending would attenuate alpha again.
        GLES20.glDisable(GLES20.GL_BLEND)
        // Pass 1: Horizontal into ping
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, ping.fboId)
        GLES20.glViewport(0, 0, ping.width, ping.height)
        clearBackground(0x00000000)
        shader.use()
        val idM = FloatArray(16).apply { Matrix.orthoM(this, 0, 0f, ping.width.toFloat(), ping.height.toFloat(), 0f, -1f, 1f) }
        GLES20.glUniformMatrix4fv(shader.uniform("uMvp"), 1, false, idM, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, srcTex)
        GLES20.glUniform1i(shader.uniform("uTex"), 0)
        GLES20.glUniform2f(shader.uniform("uDirection"), 1f / ping.width, 0f)
        GLES20.glUniform1f(shader.uniform("uRadius"), cfg.blurRadius * cfg.blurMultiplier * 0.5f)
        drawTexturedQuad(shader, 0f, 0f, ping.width.toFloat(), ping.height.toFloat(), flipV = true)

        // Pass 2: Vertical into pong
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, pong.fboId)
        GLES20.glViewport(0, 0, pong.width, pong.height)
        clearBackground(0x00000000)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ping.textureId)
        GLES20.glUniform2f(shader.uniform("uDirection"), 0f, 1f / pong.height)
        drawTexturedQuad(shader, 0f, 0f, pong.width.toFloat(), pong.height.toFloat(), flipV = true)

        // Restore target FBO
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, savedFbo[0])
        GLES20.glViewport(0, 0, w, h)

        val img = imageShader ?: return
        img.use()
        GLES20.glUniformMatrix4fv(img.uniform("uMvp"), 1, false, mvpMatrix, 0)
        GLES20.glUniform1i(img.uniform("uShape"), 0)
        GLES20.glUniform1i(img.uniform("uColorKeyEnabled"), 0)
        GLES20.glUniform1f(img.uniform("uSaturation"), 1f)
        GLES20.glUniform4f(img.uniform("uTint"), 1f, 1f, 1f, 1f)

        if (cfg.showUnblurredContentUnder) {
            applyBlendMode(cfg.blendModeContent)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, srcTex)
            GLES20.glUniform1f(img.uniform("uOpacity"), opacity)
            drawTexturedQuad(img, 0f, 0f, w.toFloat(), h.toFloat(), flipV = true)
        }

        applyBlendMode(BlendMode.ADD)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, pong.textureId)
        GLES20.glUniform1f(img.uniform("uOpacity"), opacity * 0.85f)
        drawTexturedQuad(img, 0f, 0f, w.toFloat(), h.toFloat(), flipV = true)

        if (cfg.showUnblurredContent) {
            applyBlendMode(cfg.blendModeContent)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, srcTex)
            GLES20.glUniform1f(img.uniform("uOpacity"), opacity)
            drawTexturedQuad(img, 0f, 0f, w.toFloat(), h.toFloat(), flipV = true)
        }
    }

    private fun drawRgbSplitEffect(
        cfg: LayerConfig.RgbSplitEffect,
        audioSnap: AudioProviderSnapshot?,
        scene: RenderSceneSnapshot,
        trackNorm: Float,
        opacity: Float,
        w: Int,
        h: Int,
        compTextures: Map<Int, Int>
    ) {
        val srcTex = compTextures[cfg.targetCompositionIndex] ?: return
        val delta = MeasureEvaluator.evaluatePair(
            cfg.splitMeasure, audioSnap, scene.totalTimeSec, scene.playingTimeSec, trackNorm, scene.isPlaying
        )
        val offX = (cfg.splitAmountX + delta.x * 0.015f) * cfg.splitMultiplier * 0.4f
        val offY = (cfg.splitAmountY + delta.y * 0.015f) * cfg.splitMultiplier * 0.4f

        val shader = rgbSplitShader ?: return
        applyBlendMode(cfg.blendModeContent)
        shader.use()
        GLES20.glUniformMatrix4fv(shader.uniform("uMvp"), 1, false, mvpMatrix, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, srcTex)
        GLES20.glUniform1i(shader.uniform("uTex"), 0)
        GLES20.glUniform2f(shader.uniform("uOffset"), offX, offY)
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
    }

    private fun drawMotionBlurEffect(
        layerId: String,
        cfg: LayerConfig.MotionBlurEffect,
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
        img.use()

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
        val persist = (1f - (0.35f / cfg.blurAmountMultiplier.coerceIn(0.4f, 3f))).coerceIn(0.25f, 0.85f)
        // Decay the entire previous frame, including pixels outside the current source.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ensureInternalTex("internalres:white"))
        GLES20.glBlendFuncSeparate(GLES20.GL_ZERO, GLES20.GL_ONE_MINUS_SRC_ALPHA, GLES20.GL_ZERO, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUniform1f(img.uniform("uOpacity"), 1f - persist)
        drawTexturedQuad(img, 0f, 0f, w.toFloat(), h.toFloat(), flipV = true)
        applyBlendMode(BlendMode.ALPHA)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, srcTex)
        GLES20.glUniform1f(img.uniform("uOpacity"), 1f)
        drawTexturedQuad(img, 0f, 0f, w.toFloat(), h.toFloat(), flipV = true)

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
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, trailFbo.textureId)
        GLES20.glUniform4f(img.uniform("uTint"), ((cfg.colorArgb ushr 16) and 255)/255f, ((cfg.colorArgb ushr 8) and 255)/255f, (cfg.colorArgb and 255)/255f, ((cfg.colorArgb ushr 24) and 255)/255f)
        GLES20.glUniform1f(img.uniform("uOpacity"), opacity)
        drawTexturedQuad(img, 0f, 0f, w.toFloat(), h.toFloat(), flipV = true)
        if (cfg.showUnblurredContent) {
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
            opacity
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
        when (mode) {
            BlendMode.ALPHA, BlendMode.PRE_MUL_ALPHA ->
                GLES20.glBlendFuncSeparate(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA, GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            BlendMode.ADD, BlendMode.ADD_ALPHA ->
                GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
            BlendMode.SCREEN ->
                GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_COLOR)
            BlendMode.MULTIPLY ->
                GLES20.glBlendFunc(GLES20.GL_DST_COLOR, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            BlendMode.OVERWRITE -> Unit
        }
    }

    private fun clearBackground(argb: Int) {
        GLES20.glClearColor(
            ((argb ushr 16) and 0xFF) / 255f,
            ((argb ushr 8) and 0xFF) / 255f,
            (argb and 0xFF) / 255f,
            ((argb ushr 24) and 0xFF) / 255f
        )
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
    }

    private fun applyReactionDelay(layerId: String, current: FloatArray, delayFrames: Int): FloatArray {
        val d = delayFrames.coerceIn(0, 9)
        if (d == 0) return current
        val q = barDelayHistory.getOrPut(layerId) { ArrayDeque() }
        q.addLast(current.copyOf())
        while (q.size > d + 1) q.removeFirst()
        return q.first()
    }

    private fun uploadArtworkTexture(bitmap: Bitmap?) {
        if (artworkTexId != 0) {
            bitmapSizes.remove(artworkTexId)
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

    private fun ensureUriTex(uriStr: String?): Int {
        if (uriStr.isNullOrBlank()) return 0
        textureCache[uriStr]?.let { return it }
        val bmp = decodeBoundedBitmap(uriStr) ?: return 0
        val id = uploadBitmapToGl(bmp)
        bmp.recycle()
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
            var sample = 1
            while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val raw = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null

            val rotation = context.contentResolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f

            if (rotation == 0f) raw else {
                val m = AndroidMatrix().apply { postRotate(rotation) }
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true).also {
                    if (it !== raw) raw.recycle()
                }
            }
        }.getOrNull()
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
            typeface = Typeface.create(Typeface.SANS_SERIF, style)
            textAlign = when (cfg.alignment) {
                -1 -> Paint.Align.LEFT
                1 -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            letterSpacing = cfg.letterSpacing
            if (cfg.shadowRadius > 0f) {
                setShadowLayer(cfg.shadowRadius, 0f, 2f, 0xCC000000.toInt())
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
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmapSizes[ids[0]] = bitmap.width to bitmap.height
        return ids[0]
    }

    private fun allocFloatBuffer(size: Int): FloatBuffer =
        ByteBuffer.allocateDirect(size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    private fun formatTimeMs(ms: Long): String {
        val sec = (ms / 1000L).coerceAtLeast(0L)
        return "%d:%02d".format(sec / 60, sec % 60)
    }
}
