package com.vexo.studio.engine.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.Closeable
import kotlin.math.hypot

data class SessionAudioFrame(val waveform: FloatArray, val spectrum: FloatArray, val sampleRateHz: Int)

object VisualizerFftDecoder {
    fun waveform(bytes: ByteArray): FloatArray =
        FloatArray(bytes.size) { ((bytes[it].toInt() and 255) - 128) / 128f }

    /** Android's packed FFT: DC, Nyquist, then complex signed byte pairs. */
    fun spectrum(bytes: ByteArray): FloatArray {
        if (bytes.size < 2 || bytes.size % 2 != 0) return FloatArray(0)
        return FloatArray(bytes.size / 2 + 1) { k ->
            when (k) {
                0 -> kotlin.math.abs(bytes[0].toInt()) / 128f
                bytes.size / 2 -> kotlin.math.abs(bytes[1].toInt()) / 128f
                else -> (hypot(bytes[k*2].toDouble(), bytes[k*2+1].toDouble()) / 181.02).toFloat()
            }.coerceIn(0f,1f)
        }
    }
}

/**
 * Optional Android Visualizer capture, restricted to the player's own session.
 * PCM tap remains the authoritative analyzer (including offline export).
 * Call attach only after the user grants RECORD_AUDIO.
 */
class SessionVisualizer(private val context: Context) : Closeable {
    private val lock = Any()
    private var visualizer: Visualizer? = null
    private var generation = 0
    private var wave = FloatArray(0)
    private var bins = FloatArray(0)
    private val _frames = MutableStateFlow<SessionAudioFrame?>(null)
    val frames: StateFlow<SessionAudioFrame?> = _frames.asStateFlow()

    fun attach(sessionId: Int): Boolean = synchronized(lock) {
        releaseLocked()
        if (sessionId <= 0 || ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) return@synchronized false
        val gen = generation
        runCatching {
            val v = Visualizer(sessionId)
            visualizer = v
            val range = Visualizer.getCaptureSizeRange()
            check(v.setCaptureSize(1024.coerceIn(range[0], range[1])) == Visualizer.SUCCESS)
            val status = v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(effect: Visualizer, waveform: ByteArray, samplingRate: Int) {
                    synchronized(lock) {
                        if (gen != generation) return
                        wave = VisualizerFftDecoder.waveform(waveform)
                        _frames.value = SessionAudioFrame(wave, bins, samplingRate / 1000)
                    }
                }
                override fun onFftDataCapture(effect: Visualizer, fft: ByteArray, samplingRate: Int) {
                    synchronized(lock) {
                        if (gen != generation) return
                        bins = VisualizerFftDecoder.spectrum(fft)
                        _frames.value = SessionAudioFrame(wave, bins, samplingRate / 1000)
                    }
                }
            }, minOf(Visualizer.getMaxCaptureRate(), 30_000), true, true)
            check(status == Visualizer.SUCCESS)
            check(v.setEnabled(true) == Visualizer.SUCCESS)
            true
        }.getOrElse { releaseLocked(); false }
    }
    private fun releaseLocked() {
        generation++
        val old = visualizer
        visualizer = null
        runCatching { old?.setEnabled(false) }
        runCatching { old?.release() }
        wave = FloatArray(0); bins = FloatArray(0); _frames.value = null
    }
    override fun close() { synchronized(lock) { releaseLocked() } }
}
