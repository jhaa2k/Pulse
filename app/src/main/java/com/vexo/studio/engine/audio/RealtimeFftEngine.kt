package com.vexo.studio.engine.audio

import com.vexo.studio.model.AudioProviderConfig
import com.vexo.studio.model.ChannelMode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

/**
 * Coroutine-driven Radix-2 FFT audio analysis engine that consumes PCM streams
 * tapped from Media3 ExoPlayer via [PcmRingBuffer] and emits real-time
 * spectrum, waveform, frequency bands, and beat snapshots.
 */
class RealtimeFftEngine(
    private val ringBuffer: PcmRingBuffer,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val frameIntervalMs: Long = 16L
) : Closeable {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val engines = ConcurrentHashMap<Int, AudioProviderEngine>()
    private val reusableSampleWindow = FloatArray(8192)
    private val leftWindow=FloatArray(8192)
    private val rightWindow=FloatArray(8192)

    private val _snapshotsFlow = MutableStateFlow<Map<Int, AudioProviderSnapshot>>(emptyMap())
    val snapshotsFlow: StateFlow<Map<Int, AudioProviderSnapshot>> = _snapshotsFlow.asStateFlow()

    private val _primarySnapshotFlow = MutableStateFlow<AudioProviderSnapshot?>(null)
    val primarySnapshotFlow: StateFlow<AudioProviderSnapshot?> = _primarySnapshotFlow.asStateFlow()

    private var job: Job? = null

    fun start(
        providersProvider: () -> List<AudioProviderConfig>,
        isPlayingProvider: () -> Boolean,
        freezeOnPauseProvider: () -> Boolean,
        onFrameProcessed: ((Map<Int, AudioProviderSnapshot>, Float) -> Unit)? = null
    ) {
        if (job?.isActive == true) return
        job = scope.launch {
            var lastTickNs = System.nanoTime()
            while (isActive) {
                val nowNs = System.nanoTime()
                val deltaSec = ((nowNs - lastTickNs) / 1_000_000_000f).coerceIn(0.004f, 0.05f)
                lastTickNs = nowNs

                val snapshots = processStep(
                    providers = providersProvider(),
                    deltaMs = deltaSec * 1000f,
                    isPlaying = isPlayingProvider(),
                    freezeOnPause = freezeOnPauseProvider()
                )
                onFrameProcessed?.invoke(snapshots, deltaSec)
                delay(frameIntervalMs)
            }
        }
    }

    /**
     * Evaluates a single FFT analysis frame across all configured [AudioProviderConfig]s
     * without allocating per-frame PCM scratch arrays.
     */
    fun processStep(
        providers: List<AudioProviderConfig>,
        deltaMs: Float,
        isPlaying: Boolean,
        freezeOnPause: Boolean
    ): Map<Int, AudioProviderSnapshot> {
        val gen = ringBuffer.currentGeneration()
        val maxFftSize = providers.maxOfOrNull {
            AudioProviderEngine.sanitizeFftSize(it.fftSize)
        } ?: 1024
        val windowLen = maxFftSize.coerceIn(256, reusableSampleWindow.size)
        val hasFreshSamples=ringBuffer.readStereoWindows(
            reusableSampleWindow.takeIf{providers.any{it.channelMode==ChannelMode.MIX}},
            leftWindow.takeIf{providers.any{it.channelMode==ChannelMode.LEFT}},
            rightWindow.takeIf{providers.any{it.channelMode==ChannelMode.RIGHT}},
            gen,windowLen)

        val out = HashMap<Int, AudioProviderSnapshot>(providers.size)
        for (prov in providers) {
            val engine = engines.getOrPut(prov.index) { AudioProviderEngine(prov) }
            engine.updateConfig(prov)
            out[prov.index] = engine.process(
                samples = if (hasFreshSamples) when(prov.channelMode){ChannelMode.LEFT->leftWindow;ChannelMode.RIGHT->rightWindow;ChannelMode.MIX->reusableSampleWindow} else null,
                sampleRate = ringBuffer.sampleRate,
                deltaMs = deltaMs,
                streamGeneration = gen,
                isPlaying = isPlaying,
                freezeOnPause = freezeOnPause,
                sampleLength = if (hasFreshSamples) windowLen else 0
            )
        }

        // Prune engines whose providers were removed
        val activeIndices = providers.mapTo(HashSet(providers.size)) { it.index }
        engines.keys.retainAll(activeIndices)

        _snapshotsFlow.value = out
        _primarySnapshotFlow.value = out[1] ?: out.values.firstOrNull()
        return out
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    override fun close() {
        stop()
        scope.cancel()
    }
}
