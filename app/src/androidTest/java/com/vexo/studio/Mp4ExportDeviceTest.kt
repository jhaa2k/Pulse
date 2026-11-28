package com.vexo.studio

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vexo.studio.engine.gl.TrackMetadataSnapshot
import com.vexo.studio.export.ExportProgress
import com.vexo.studio.export.VexoVideoExporter
import com.vexo.studio.model.AspectRatioPreset
import com.vexo.studio.model.VideoResolution
import com.vexo.studio.model.VexoTemplates
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises actual device codecs, EGL, shaders, muxing and file publishing. */
@RunWith(AndroidJUnit4::class)
class Mp4ExportDeviceTest {
    @Test fun trimmedSpectrumExportsPlayableSynchronizedMp4(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val wav = File.createTempFile("export_test_", ".wav", context.cacheDir)
        val output = File.createTempFile("export_test_", ".mp4", context.cacheDir)
        try {
            writeTone(wav, 44100, 2)
            val base = VexoTemplates.monochromeStudio()
            val project = base.copy(
                canvas = base.canvas.copy(aspectRatio = AspectRatioPreset.RATIO_16_9),
                exportSettings = base.exportSettings.copy(
                    aspectRatio = AspectRatioPreset.RATIO_16_9,
                    resolution = VideoResolution.HD_720,
                    fps = 24, videoBitrateMbps = 4, audioBitrateKbps = 128,
                    trimStartMs = 500L, trimEndMs = 1500L,
                    useHevcIfSupported = false, customOutputUri = Uri.fromFile(output).toString()
                )
            )
            var progress = ExportProgress()
            var lastRunningProgress = 0f
            withTimeout(180_000L) {
                VexoVideoExporter(context).export(project, Uri.fromFile(wav).toString(),
                    TrackMetadataSnapshot(title = "Test tone", durationMs = 2000L)) { update ->
                    if (update.isRunning) {
                        assertTrue("Progress must not regress", update.progress + .0001f >= lastRunningProgress)
                        lastRunningProgress = update.progress
                    }
                    progress = update
                }
            }
            assertFalse(progress.isRunning)
            assertNull(progress.errorMessage)
            assertNotNull(progress.publishedUri)
            assertTrue(output.length() > 0)
            val video = inspectTrack(output, "video/")
            val audio = inspectTrack(output, "audio/")
            assertEquals(1280, video.width)
            assertEquals(720, video.height)
            assertTrue("Expected approximately 24 video frames", video.samples in 23..25)
            assertTrue("Expected AAC samples", audio.samples > 20)
            assertTrue("Video/audio end timestamps differ too much", abs(video.lastUs - audio.lastUs) < 150_000L)
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(output.absolutePath)
                val frame = retriever.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST)
                assertNotNull("Export must decode a real video frame", frame)
                frame?.recycle()
            } finally { retriever.release() }
        } finally {
            wav.delete()
            output.delete()
        }
    }

    @Test fun portraitWaveGlowAndMotionExportProducesVisiblePixels(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val wav = File.createTempFile("wave_effect_", ".wav", context.cacheDir)
        val output = File.createTempFile("wave_effect_", ".mp4", context.cacheDir)
        try {
            writeTone(wav,44100,2)
            val bars = com.vexo.studio.model.LayerNode(
                com.vexo.studio.model.LayerType.BARS, com.vexo.studio.model.LayerCommon(name="Test waveform"),
                com.vexo.studio.model.LayerConfig.Bars(
                    creativePath=com.vexo.studio.model.CreativeSpectrumPath.CIRCLE,
                    creativeStyle=com.vexo.studio.model.CreativeSpectrumStyle.LINE,
                    signedWaveform=true,curveSubdivisions=4,barCount=120,mirror=false,
                    circleRadius=.26f,creativeStrokeWidth=8f,colorFromArgb=0xFFFFFFFF.toInt(),colorToArgb=0xFF69EEFF.toInt()))
            val glow = com.vexo.studio.model.LayerNode(
                com.vexo.studio.model.LayerType.BLUR_EFFECT, com.vexo.studio.model.LayerCommon(name="Glow"),
                com.vexo.studio.model.LayerConfig.BlurEffect(sourceCompositionIndex=1,blurRadius=5f,
                    blendModeContent=com.vexo.studio.model.BlendMode.ADD,showUnblurredContent=true))
            val trail = com.vexo.studio.model.LayerNode(
                com.vexo.studio.model.LayerType.MOTION_BLUR_EFFECT, com.vexo.studio.model.LayerCommon(name="Trail",opacity=.4f),
                com.vexo.studio.model.LayerConfig.MotionBlurEffect(targetCompositionIndex=1,
                    motionSource=com.vexo.studio.model.MotionSource.MANUAL,manualMotionX=.02f))
            val ratio=AspectRatioPreset.RATIO_9_16
            val project=com.vexo.studio.model.VexoProject(
                canvas=com.vexo.studio.model.CanvasConfig(aspectRatio=ratio),
                compositions=listOf(
                    com.vexo.studio.model.CompositionNode(index=0,layers=listOf(glow,trail)),
                    com.vexo.studio.model.CompositionNode(index=1,layers=listOf(bars))),
                exportSettings=com.vexo.studio.model.ExportSettings(aspectRatio=ratio,
                    resolution=VideoResolution.HD_720,fps=24,videoBitrateMbps=4,
                    trimEndMs=1000L,customOutputUri=Uri.fromFile(output).toString()))
            withTimeout(180_000L) {
                VexoVideoExporter(context).export(project,Uri.fromFile(wav).toString(),
                    TrackMetadataSnapshot(title="Wave effect",durationMs=2000L)){}
            }
            val video=inspectTrack(output,"video/")
            assertEquals(720,video.width);assertEquals(1280,video.height)
            assertTrue(inspectTrack(output,"audio/").samples>20)
            val retriever=MediaMetadataRetriever()
            try {
                retriever.setDataSource(output.absolutePath)
                val frame=checkNotNull(retriever.getFrameAtTime(500_000L,MediaMetadataRetriever.OPTION_CLOSEST))
                try {
                    var bright=0
                    for(y in 0 until frame.height step 4) for(x in 0 until frame.width step 4) {
                        val color=frame.getPixel(x,y)
                        if(android.graphics.Color.red(color)>40 || android.graphics.Color.green(color)>40 || android.graphics.Color.blue(color)>40)bright++
                    }
                    assertTrue("Wave/effects output must not be entirely black",bright>20)
                } finally { frame.recycle() }
            } finally { retriever.release() }
        } finally { wav.delete();output.delete() }
    }

    private data class Track(val samples: Int, val lastUs: Long, val width: Int, val height: Int)

    private fun inspectTrack(file: File, prefix: String): Track {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val index = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith(prefix) == true
            } ?: error("Missing track: " + prefix)
            val format = extractor.getTrackFormat(index)
            extractor.selectTrack(index)
            var count = 0
            var last = -1L
            while (extractor.sampleTime >= 0L) {
                last = maxOf(last, extractor.sampleTime)
                count++
                if (!extractor.advance()) break
            }
            return Track(count, last,
                if (format.containsKey(MediaFormat.KEY_WIDTH)) format.getInteger(MediaFormat.KEY_WIDTH) else 0,
                if (format.containsKey(MediaFormat.KEY_HEIGHT)) format.getInteger(MediaFormat.KEY_HEIGHT) else 0)
        } finally { extractor.release() }
    }

    private fun writeTone(file: File, rate: Int, seconds: Int) {
        val frames = rate * seconds
        val size = frames * 2
        val wav = ByteBuffer.allocate(44 + size).order(ByteOrder.LITTLE_ENDIAN)
        wav.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36 + size)
        wav.put("WAVEfmt ".toByteArray(Charsets.US_ASCII)).putInt(16)
        wav.putShort(1.toShort()).putShort(1.toShort()).putInt(rate).putInt(rate * 2)
        wav.putShort(2.toShort()).putShort(16.toShort())
        wav.put("data".toByteArray(Charsets.US_ASCII)).putInt(size)
        repeat(frames) { i -> wav.putShort((sin(2.0 * PI * 440.0 * i / rate) * 14000).toInt().toShort()) }
        file.writeBytes(wav.array())
    }
}
