package com.vexo.studio

import com.vexo.studio.data.AveeJsonCodec
import com.vexo.studio.engine.audio.AudioProviderEngine
import com.vexo.studio.engine.audio.FftProcessor
import com.vexo.studio.engine.audio.PcmRingBuffer
import com.vexo.studio.engine.audio.RealtimeFftEngine
import com.vexo.studio.engine.scene.KeyframeEvaluator
import com.vexo.studio.engine.scene.ParticleSimulator
import com.vexo.studio.engine.scene.SceneGraph
import com.vexo.studio.model.AudioProviderConfig
import com.vexo.studio.model.CompositionNode
import com.vexo.studio.model.InterpolationType
import com.vexo.studio.model.Keyframe
import com.vexo.studio.model.KeyframeTrack
import com.vexo.studio.model.LayerCommon
import com.vexo.studio.model.LayerConfig
import com.vexo.studio.model.LayerNode
import com.vexo.studio.model.LayerType
import com.vexo.studio.model.VexoProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class VexoEngineTests {

    @Test
    fun ringBuffer_tracksGenerationsAndWraparoundDrops() {
        val ring = PcmRingBuffer(1024)
        val g1 = ring.resetGeneration(44100, 1)
        val samples = FloatArray(1500) { i -> sin(2.0 * PI * i / 64.0).toFloat() }
        ring.writeFloatSamples(samples, 44100, 1)

        val out = FloatArray(512)
        assertTrue(ring.readLatestWindow(out, g1))
        assertTrue(ring.totalDroppedSamples() > 0)

        val g2 = ring.resetGeneration(48000, 2)
        assertTrue(g2 > g1)
        assertFalse(ring.readLatestWindow(out, g1))
    }

    @Test
    fun fftProcessor_detectsSinePeakAndRemovesDcSilence() {
        val sampleRate = 48000
        val fftSize = 1024
        val fft = FftProcessor(fftSize)
        val mags = FloatArray(fftSize / 2)

        // 1000 Hz sine + DC offset
        val freq = 1000.0
        val input = FloatArray(fftSize) { i ->
            (0.25 + 0.8 * sin(2.0 * PI * freq * i / sampleRate)).toFloat()
        }
        fft.computeLinearMagnitudes(input, mags)

        val expectedBin = ((freq * fftSize) / sampleRate).toInt()
        var peakBin = 1
        for (i in 2 until mags.size) {
            if (mags[i] > mags[peakBin]) peakBin = i
        }
        assertTrue(kotlin.math.abs(peakBin - expectedBin) <= 1)
        assertTrue(mags[0] < 0.05f) // DC removed

        // Silence check
        val silence = FloatArray(fftSize) { 0f }
        fft.computeLinearMagnitudes(silence, mags)
        assertEquals(0f, mags.max(), 0.0001f)
    }

    @Test
    fun audioProvider_handlesMirrorAndReversedBeatRange() {
        val cfg = AudioProviderConfig(
            sampleOutCount = 32,
            mirrorSamples = true,
            beatRangeBarFirst = 0.8f,
            beatRangeBarLast = 0.2f // reversed range
        )
        val engine = AudioProviderEngine(cfg)
        val wave = FloatArray(1024) { i -> sin(2.0 * PI * 120.0 * i / 44100.0).toFloat() }
        val snap = engine.process(wave, 44100, 16f, 1, isPlaying = true, freezeOnPause = false)
        assertEquals(32, snap.bars.size)
        assertTrue(snap.beat >= 0f)
        assertTrue(snap.bass > snap.treble)
    }

    @Test
    fun sceneGraph_detectsCycleAndResolvesTopologicalOrder() {
        val comp0 = CompositionNode(
            index = 0,
            layers = listOf(
                LayerNode(
                    LayerType.BLUR_EFFECT,
                    LayerCommon(name = "Blur"),
                    LayerConfig.BlurEffect(sourceCompositionIndex = 1)
                )
            )
        )
        val comp1 = CompositionNode(
            index = 1,
            layers = listOf(
                LayerNode(
                    LayerType.IMAGE,
                    LayerCommon(name = "Sub"),
                    LayerConfig.Image(sourceType = com.vexo.studio.model.ImageSourceType.COMPOSITION, sourceCompositionIndex = 0)
                )
            )
        )
        val cyclic = VexoProject(compositions = listOf(comp0, comp1))
        val resolved = SceneGraph.resolve(cyclic)
        assertTrue(resolved.hasCycle)
    }

    @Test
    fun aveeJsonCodec_parsesAndRoundTripsPresetObjects() {
        val sampleJson = """
            [
              {"_name": "Audio Provider", "objType": "AudioProvider", "fftSize": {"v": 11}, "sampleOutCount": {"v": 96}, "lowerHz": {"v": 52.5}, "higherHz": {"v": 500}},
              {"_name": "Image", "objType": "Image", "Color": {"v": "1 1 1 1"}, "customImage": {"v": "internalres:vignette80"}},
              {"_name": "Bars/Segments", "objType": "Bars", "ShapePath": {"v": "Circle"}, "Segment1": {"v": "Bars", "barWidth": {"v": 0.6}}},
              {"_name": "Text", "objType": "Text", "text": {"v": "{Artist}"}, "fontSize": {"v": 56}},
              {"_name": "Particles", "objType": "Particles", "CountLimit": {"v": 500}, "Speed": {"v": 100}},
              {"_name": "Blur Effect", "objType": "BlurEffect", "blurRadius": {"v": 2.0}, "sourceCompositionIndex": {"v": 1}},
              {"_name": "Rgb Split", "objType": "RgbSplitEffect", "targetImage": {"v": "composition:1"}},
              {"_name": "Motion Blur Effect", "objType": "MotionBlurEffect", "TargetImage": {"v": "composition:1"}},
              {"_name": "Mirror Effect", "objType": "MirrorEffect", "TargetImage": {"v": "composition:1"}, "mirrorMode": {"v": "Horizontal"}}
            ]
        """.trimIndent()

        val imported = AveeJsonCodec.importFromText(sampleJson, "PDF Test")
        assertEquals(2048, imported.audioProviders.first().fftSize)
        assertEquals(96, imported.audioProviders.first().sampleOutCount)
        assertEquals(8, imported.compositions.sumOf { it.layers.size })

        val exported = AveeJsonCodec.exportProjectAsAveeJson(imported)
        val reimported = AveeJsonCodec.importFromText(exported, "RoundTrip")
        assertEquals(8, reimported.compositions.sumOf { it.layers.size })
    }

    @Test
    fun particleSimulator_isDeterministicAcrossSeedReset() {
        val cfg = LayerConfig.Particles(countLimit = 100, seed = 99)
        val sim1 = ParticleSimulator(200)
        val sim2 = ParticleSimulator(200)
        repeat(10) {
            sim1.step(cfg, null, 0.016f, it * 0.016f, it * 0.016f, true)
            sim2.step(cfg, null, 0.016f, it * 0.016f, it * 0.016f, true)
        }
        assertEquals(sim1.activeCount, sim2.activeCount)
        for (i in 0 until sim1.activeCount) {
            assertEquals(sim1.outX[i], sim2.outX[i], 0.0001f)
            assertEquals(sim1.outY[i], sim2.outY[i], 0.0001f)
        }
    }

    @Test
    fun keyframeEvaluator_supportsHoldLinearAndEase() {
        val track = KeyframeTrack(
            targetLayerId = "l1",
            propertyKey = "posX",
            keyframes = listOf(
                Keyframe(0L, 0f, InterpolationType.LINEAR),
                Keyframe(1000L, 1f, InterpolationType.HOLD),
                Keyframe(2000L, 2f, InterpolationType.EASE_IN_OUT)
            )
        )
        assertEquals(0.5f, KeyframeEvaluator.evaluate(track, 500L, 0f), 0.001f)
        assertEquals(1.0f, KeyframeEvaluator.evaluate(track, 1500L, 0f), 0.001f)
    }

    @Test
    fun realtimeFftEngine_streamsVisualizationDataFromPcmBuffers() = runBlocking {
        val ring = PcmRingBuffer(4096)
        val sampleRate = 48000
        val frames = 2048

        // Feed 16-bit PCM stereo buffer (180 Hz bass tone)
        val pcm16 = ByteBuffer.allocateDirect(frames * 2 * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frames) {
            val s = (sin(2.0 * PI * 180.0 * i / sampleRate) * 24000.0).toInt().toShort()
            pcm16.putShort(s)
            pcm16.putShort(s)
        }
        pcm16.flip()
        ring.writePcm16(pcm16, channels = 2, sampleRateHz = sampleRate, mediaTimestampUs = 0L)

        val engine = RealtimeFftEngine(ring, Dispatchers.Default, frameIntervalMs = 8L)
        val provider = AudioProviderConfig(index = 1, fftSize = 1024, sampleOutCount = 48)
        engine.start(
            providersProvider = { listOf(provider) },
            isPlayingProvider = { true },
            freezeOnPauseProvider = { false }
        )

        var snap = engine.primarySnapshotFlow.value
        var attempts = 0
        while ((snap == null || snap.rms <= 0.01f) && attempts < 40) {
            delay(15L)
            snap = engine.primarySnapshotFlow.value
            attempts++
        }

        assertNotNull(snap)
        val active = snap!!
        assertEquals(48, active.bars.size)
        assertTrue(active.rms > 0.1f)
        assertTrue(active.bass > active.treble)

        // Feed 32-bit float PCM high-frequency tone and verify generation reset + energy shift
        val pcmFloat = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frames) {
            pcmFloat.putFloat((0.7 * sin(2.0 * PI * 8000.0 * i / sampleRate)).toFloat())
        }
        pcmFloat.flip()
        ring.writePcmFloat(pcmFloat, channels = 1, sampleRateHz = sampleRate, mediaTimestampUs = 40_000L)

        delay(80L)
        val updated = engine.primarySnapshotFlow.value!!
        assertTrue(updated.treble > 0.05f)
        engine.close()
    }
}
