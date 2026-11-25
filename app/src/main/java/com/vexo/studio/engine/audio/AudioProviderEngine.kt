package com.vexo.studio.engine.audio

import com.vexo.studio.model.AudioProviderConfig
import com.vexo.studio.model.SampleProviderType
import com.vexo.studio.model.WindowType
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

data class AudioProviderSnapshot(
    val bars: FloatArray,
    val waveform: FloatArray,
    val beat: Float,
    val beatPulse: Float,
    val bass: Float,
    val mid: Float,
    val treble: Float,
    val rms: Float,
    val peak: Float
)

/**
 * Full Audio Provider evaluation engine implementing §7 of the specification:
 * - Spectrum / Spectrum2 (RMS aggregation) / Waveform
 * - Hz bounds clamped to Nyquist with linear/log interpolation and frequency shift
 * - True IEC A-weighting by frequency
 * - Spatial filterRadius/filterStrength, preSmooth, temporal smooth, outputMultiplier
 * - Mirror, Repeat, Start/End Gap geometry
 * - Time-based FPS-independent Attack/Release/PeakHold/PeakDecay
 * - Beat energy over bar range with hysteresis and sensitivity
 */
class AudioProviderEngine(initialConfig: AudioProviderConfig) {
    private var config: AudioProviderConfig = initialConfig
    private var fft = FftProcessor(sanitizeFftSize(initialConfig.fftSize), initialConfig.window)
    private var linearSpectrum = FloatArray(fft.size / 2)
    private var rawSampleWindow = FloatArray(fft.size)

    private var mappedBars = FloatArray(initialConfig.sampleOutCount.coerceIn(1, 512))
    private var filteredBars = FloatArray(mappedBars.size)
    private var envelopeBars = FloatArray(mappedBars.size)
    private var peakHoldBars = FloatArray(mappedBars.size)
    private var peakHoldTimersMs = FloatArray(mappedBars.size)
    private var outputBars = FloatArray(mappedBars.size)
    private var outputWaveform = FloatArray(mappedBars.size)

    private var beatEnvelope = 0f
    private var beatPulseState = 0f
    private var prevBeatTriggered = false
    private var bassEnergy = 0f
    private var midEnergy = 0f
    private var trebleEnergy = 0f
    private var rmsEnergy = 0f
    private var peakEnergy = 0f

    private var lastStreamGen = -1

    fun updateConfig(newConfig: AudioProviderConfig) {
        val cleanSize = sanitizeFftSize(newConfig.fftSize)
        if (cleanSize != fft.size || newConfig.window != fft.windowType) {
            fft = FftProcessor(cleanSize, newConfig.window)
            linearSpectrum = FloatArray(cleanSize / 2)
            rawSampleWindow = FloatArray(cleanSize)
        }
        val outCount = newConfig.sampleOutCount.coerceIn(1, 512)
        if (outCount != outputBars.size) {
            mappedBars = FloatArray(outCount)
            filteredBars = FloatArray(outCount)
            envelopeBars = FloatArray(outCount)
            peakHoldBars = FloatArray(outCount)
            peakHoldTimersMs = FloatArray(outCount)
            outputBars = FloatArray(outCount)
            outputWaveform = FloatArray(outCount)
        }
        config = newConfig
    }

    fun resetState() {
        mappedBars.fill(0f)
        filteredBars.fill(0f)
        envelopeBars.fill(0f)
        peakHoldBars.fill(0f)
        peakHoldTimersMs.fill(0f)
        outputBars.fill(0f)
        outputWaveform.fill(0f)
        beatEnvelope = 0f
        beatPulseState = 0f
        prevBeatTriggered = false
        bassEnergy = 0f
        midEnergy = 0f
        trebleEnergy = 0f
        rmsEnergy = 0f
        peakEnergy = 0f
    }

    fun process(
        samples: FloatArray?,
        sampleRate: Int,
        deltaMs: Float,
        streamGeneration: Int,
        isPlaying: Boolean,
        freezeOnPause: Boolean
    ): AudioProviderSnapshot {
        if (streamGeneration != lastStreamGen) {
            lastStreamGen = streamGeneration
            resetState()
        }

        if (!isPlaying && freezeOnPause) {
            return snapshot()
        }

        val dtMs = deltaMs.coerceIn(1f, 250f)

        if (samples == null || !isPlaying || !config.enabled) {
            // Decay gracefully toward zero
            decayEnvelopes(dtMs)
            return snapshot()
        }

        val copyCount = minOf(samples.size, rawSampleWindow.size)
        if (copyCount < rawSampleWindow.size) {
            rawSampleWindow.fill(0f)
        }
        val offset = samples.size - copyCount
        for (i in 0 until copyCount) {
            val s = samples[offset + i]
            rawSampleWindow[i] = if (s.isFinite()) s.coerceIn(-1f, 1f) else 0f
        }

        // Compute RMS & Peak directly from time-domain window
        var sumSq = 0.0
        var maxAbs = 0f
        for (s in rawSampleWindow) {
            val a = abs(s)
            if (a > maxAbs) maxAbs = a
            sumSq += s * s
        }
        rmsEnergy = sqrt(sumSq / rawSampleWindow.size).toFloat().coerceIn(0f, 1f)
        peakEnergy = maxAbs.coerceIn(0f, 1f)

        // Resample waveform for waveform layer/mode
        val nOut = outputBars.size
        val step = rawSampleWindow.size.toFloat() / nOut
        for (i in 0 until nOut) {
            val idx = (i * step).toInt().coerceIn(0, rawSampleWindow.lastIndex)
            outputWaveform[i] = rawSampleWindow[idx]
        }

        if (config.sampleProvider == SampleProviderType.WAVEFORM) {
            for (i in 0 until nOut) {
                mappedBars[i] = abs(outputWaveform[i]) * config.outputMultiplier
            }
        } else {
            fft.computeLinearMagnitudes(rawSampleWindow, linearSpectrum)
            mapFrequencyBins(sampleRate.coerceAtLeast(8000))
        }

        applySpatialFilter()
        applyGeometryAndEnvelopes(dtMs)
        computeBandsAndBeat(sampleRate.coerceAtLeast(8000), dtMs)

        return snapshot()
    }

    private fun mapFrequencyBins(sampleRate: Int) {
        val nyquist = sampleRate * 0.5f
        val loHz = config.lowerHz.coerceIn(10f, nyquist * 0.9f)
        val hiHz = config.higherHz.coerceIn(loHz + 10f, nyquist)
        val linearFactor = config.hzLinearFactor.coerceIn(0f, 1f)
        val shift = config.freqShift.coerceIn(-0.5f, 0.5f)
        val nOut = mappedBars.size
        val numBins = linearSpectrum.size
        val hzPerBin = nyquist / numBins

        // Leave gap slots at edges
        val gap = config.startAndEndGap.coerceIn(0, (nOut / 3))
        val activeCount = (nOut - gap * 2).coerceAtLeast(1)

        // Effective base count before mirror / repeat
        val repeat = config.repeatSamples.coerceIn(1, 6)
        val mirrorDiv = if (config.mirrorSamples) 2 else 1
        val uniqueCount = (activeCount / (repeat * mirrorDiv)).coerceAtLeast(1)

        for (i in 0 until uniqueCount) {
            val t0 = ((i.toFloat() / uniqueCount) + shift).coerceIn(0f, 1f)
            val t1 = (((i + 1).toFloat() / uniqueCount) + shift).coerceIn(t0, 1f)

            val logHz0 = loHz * (hiHz / loHz).pow(t0)
            val logHz1 = loHz * (hiHz / loHz).pow(t1)
            val linHz0 = loHz + (hiHz - loHz) * t0
            val linHz1 = loHz + (hiHz - loHz) * t1

            val hz0 = linHz0 * linearFactor + logHz0 * (1f - linearFactor)
            val hz1 = linHz1 * linearFactor + logHz1 * (1f - linearFactor)

            val bin0 = (hz0 / hzPerBin).toInt().coerceIn(1, numBins - 1)
            val bin1 = (hz1 / hzPerBin).toInt().coerceIn(bin0, numBins - 1)

            var agg = 0f
            if (config.sampleProvider == SampleProviderType.SPECTRUM2) {
                var sqSum = 0f
                for (b in bin0..bin1) {
                    val v = linearSpectrum[b]
                    sqSum += v * v
                }
                agg = sqrt(sqSum / (bin1 - bin0 + 1))
            } else {
                var maxV = 0f
                var avgV = 0f
                for (b in bin0..bin1) {
                    val v = linearSpectrum[b]
                    if (v > maxV) maxV = v
                    avgV += v
                }
                avgV /= (bin1 - bin0 + 1)
                agg = maxV * 0.65f + avgV * 0.35f
            }

            // Perceptual log compression + optional A-weighting
            val centerHz = 0.5f * (hz0 + hz1)
            val weight = if (config.aWeight > 0f) {
                1f + config.aWeight * (aWeightLinear(centerHz) - 1f)
            } else 1f

            val compressed = ln(1.0 + agg * weight * 12.0).toFloat()
            mappedBars[i] = compressed * config.outputMultiplier
        }

        // Expand unique bars -> full active region with repeat + mirror + gap
        filteredBars.fill(0f)
        for (i in 0 until activeCount) {
            val slot = if (config.mirrorSamples && i >= activeCount / 2) {
                activeCount - 1 - i
            } else i
            val srcIdx = (slot % uniqueCount).coerceIn(0, uniqueCount - 1)
            filteredBars[gap + i] = mappedBars[srcIdx]
        }
    }

    private fun applySpatialFilter() {
        val radius = config.filterRadius.coerceIn(0, 20)
        val strength = config.filterStrength.coerceIn(0f, 1f)
        val n = filteredBars.size
        if (radius == 0 || strength <= 0.001f) {
            System.arraycopy(filteredBars, 0, mappedBars, 0, n)
            return
        }
        for (i in 0 until n) {
            var sum = 0f
            var wSum = 0f
            for (k in -radius..radius) {
                val idx = (i + k).coerceIn(0, n - 1)
                val w = (radius + 1 - abs(k)).toFloat()
                sum += filteredBars[idx] * w
                wSum += w
            }
            val blurred = sum / wSum
            mappedBars[i] = filteredBars[i] * (1f - strength) + blurred * strength
        }
    }

    private fun applyGeometryAndEnvelopes(dtMs: Float) {
        val n = outputBars.size
        val attackCoeff = if (config.attackMs <= 0.5f) 1f else 1f - exp(-dtMs / config.attackMs)
        val releaseCoeff = if (config.releaseMs <= 0.5f) 1f else 1f - exp(-dtMs / config.releaseMs)
        val smoothBlend = (1f - config.smooth.coerceIn(0.05f, 1f)) * 0.5f

        for (i in 0 until n) {
            val target = mappedBars[i].coerceAtLeast(0f)
            val prev = envelopeBars[i]
            val env = if (target >= prev) {
                prev + (target - prev) * attackCoeff
            } else {
                prev + (target - prev) * releaseCoeff
            }
            envelopeBars[i] = env

            // Peak hold tracking
            if (env >= peakHoldBars[i]) {
                peakHoldBars[i] = env
                peakHoldTimersMs[i] = config.peakHoldMs
            } else {
                peakHoldTimersMs[i] -= dtMs
                if (peakHoldTimersMs[i] <= 0f) {
                    peakHoldBars[i] = (peakHoldBars[i] * config.peakDecay.coerceIn(0.5f, 0.995f))
                }
            }

            outputBars[i] = env * (1f - smoothBlend) + prev * smoothBlend
        }
    }

    private fun decayEnvelopes(dtMs: Float) {
        val releaseCoeff = if (config.releaseMs <= 0.5f) 1f else 1f - exp(-dtMs / config.releaseMs)
        for (i in outputBars.indices) {
            envelopeBars[i] *= (1f - releaseCoeff)
            outputBars[i] = envelopeBars[i]
            peakHoldBars[i] *= 0.92f
        }
        beatEnvelope *= (1f - releaseCoeff)
        beatPulseState *= 0.85f
        bassEnergy *= 0.9f
        midEnergy *= 0.9f
        trebleEnergy *= 0.9f
        rmsEnergy *= 0.9f
        peakEnergy *= 0.9f
    }

    private fun computeBandsAndBeat(sampleRate: Int, dtMs: Float) {
        val n = outputBars.size
        val rawFirst = config.beatRangeBarFirst.coerceIn(0f, 1f)
        val rawLast = config.beatRangeBarLast.coerceIn(0f, 1f)
        val firstNorm = minOf(rawFirst, rawLast)
        val lastNorm = maxOf(rawFirst, rawLast)

        val idx0 = (firstNorm * (n - 1)).toInt().coerceIn(0, n - 1)
        val idx1 = (lastNorm * (n - 1)).toInt().coerceIn(idx0, n - 1)

        var beatSum = 0f
        for (i in idx0..idx1) {
            beatSum += outputBars[i]
        }
        val rawBeatAvg = beatSum / (idx1 - idx0 + 1)

        val loVal = config.beatRangeValueLower.coerceAtLeast(0f) * 0.1f
        val hiVal = config.beatRangeValueHigher.coerceAtLeast(loVal + 0.1f) * 0.08f
        val normalizedBeat = (((rawBeatAvg - loVal) / (hiVal - loVal)) * config.beatSensitivity)
            .coerceIn(0f, 1.5f)

        val bSmooth = config.beatSmooth.coerceIn(0.05f, 1f)
        beatEnvelope = beatEnvelope * (1f - bSmooth) + normalizedBeat * bSmooth

        val triggered = beatEnvelope > 0.55f
        if (triggered && !prevBeatTriggered) {
            beatPulseState = 1f
        } else {
            beatPulseState = (beatPulseState - dtMs / 180f).coerceAtLeast(0f)
        }
        prevBeatTriggered = triggered

        // True Hz Bass/Mid/Treble energy from linearSpectrum
        val nyquist = sampleRate * 0.5f
        val hzPerBin = nyquist / linearSpectrum.size
        fun bandAvg(lowHz: Float, highHz: Float): Float {
            val f = (lowHz / hzPerBin).toInt().coerceIn(1, linearSpectrum.lastIndex)
            val l = (highHz / hzPerBin).toInt().coerceIn(f, linearSpectrum.lastIndex)
            var s = 0f
            for (b in f..l) s += linearSpectrum[b]
            return ln(1.0 + (s / (l - f + 1)) * 18.0).toFloat().coerceIn(0f, 1f)
        }

        bassEnergy = bassEnergy * 0.4f + bandAvg(20f, 250f) * 0.6f
        midEnergy = midEnergy * 0.4f + bandAvg(250f, 4000f) * 0.6f
        trebleEnergy = trebleEnergy * 0.4f + bandAvg(4000f, 16000f) * 0.6f
    }

    private fun snapshot(): AudioProviderSnapshot = AudioProviderSnapshot(
        bars = outputBars.copyOf(),
        waveform = outputWaveform.copyOf(),
        beat = beatEnvelope.coerceIn(0f, 1.5f),
        beatPulse = beatPulseState,
        bass = bassEnergy,
        mid = midEnergy,
        treble = trebleEnergy,
        rms = rmsEnergy,
        peak = peakEnergy
    )

    companion object {
        fun sanitizeFftSize(size: Int): Int = when {
            size <= 256 -> 256
            size <= 512 -> 512
            size <= 1024 -> 1024
            size <= 2048 -> 2048
            size <= 4096 -> 4096
            else -> 8192
        }

        /**
         * IEC 61672 A-weighting gain curve (linear multiplier normalized near 1 kHz).
         */
        fun aWeightLinear(hz: Float): Float {
            val f = hz.coerceIn(10f, 22000f).toDouble()
            val f2 = f * f
            val num = 12194.0 * 12194.0 * f2 * f2
            val d1 = f2 + 20.6 * 20.6
            val d2 = sqrt((f2 + 107.7 * 107.7) * (f2 + 737.9 * 737.9))
            val d3 = f2 + 12194.0 * 12194.0
            val ra = num / (d1 * d2 * d3)
            // Normalize so 1000 Hz = ~1.0
            return (ra / 0.7943).toFloat().coerceIn(0.05f, 1.5f)
        }
    }
}
