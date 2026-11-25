package com.vexo.studio.engine.audio

import com.vexo.studio.model.WindowType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Reusable Radix-2 Cooley-Tukey FFT processor with configurable windowing,
 * coherent gain correction, precomputed bit-reversal and twiddle tables,
 * and zero per-frame allocations.
 */
class FftProcessor(
    val size: Int = 1024,
    val windowType: WindowType = WindowType.HANN
) {
    init {
        require(size in listOf(256, 512, 1024, 2048, 4096, 8192)) {
            "FFT size must be a power of two between 256 and 8192 (got $size)"
        }
    }

    private val bits = Integer.numberOfTrailingZeros(size)
    private val bitReversed = IntArray(size) { i -> Integer.reverse(i) ushr (32 - bits) }
    private val window = DoubleArray(size)
    private val coherentGain: Double
    private val real = DoubleArray(size)
    private val imag = DoubleArray(size)
    private val cosTable = DoubleArray(size / 2)
    private val sinTable = DoubleArray(size / 2)

    init {
        var winSum = 0.0
        for (i in 0 until size) {
            val w = when (windowType) {
                WindowType.HANN -> 0.5 - 0.5 * cos(2.0 * PI * i / (size - 1))
                WindowType.HAMMING -> 0.54 - 0.46 * cos(2.0 * PI * i / (size - 1))
                WindowType.RECTANGULAR -> 1.0
            }
            window[i] = w
            winSum += w
        }
        coherentGain = if (winSum > 0.0) size / winSum else 1.0

        for (k in 0 until size / 2) {
            val angle = -2.0 * PI * k / size
            cosTable[k] = cos(angle)
            sinTable[k] = sin(angle)
        }
    }

    /**
     * Computes linear amplitude spectrum of length [size / 2] into [outMagnitudes].
     * Sanitizes non-finite input and removes DC bias before windowing.
     */
    fun computeLinearMagnitudes(input: FloatArray, outMagnitudes: FloatArray) {
        require(input.size >= size) { "Input must have at least $size samples" }
        require(outMagnitudes.size >= size / 2) { "Output must hold at least ${size / 2} bins" }

        // Remove DC component and check silence
        var dc = 0.0
        for (i in 0 until size) {
            val s = input[i]
            if (s.isFinite()) dc += s
        }
        dc /= size

        imag.fill(0.0)
        for (i in 0 until size) {
            val s = input[i]
            val clean = if (s.isFinite()) (s - dc).coerceIn(-1.0, 1.0) else 0.0
            real[bitReversed[i]] = clean * window[i] * coherentGain
        }

        var halfSize = 1
        var tableStep = size / 2
        while (halfSize < size) {
            val step = halfSize shl 1
            var base = 0
            while (base < size) {
                var tableIdx = 0
                for (j in 0 until halfSize) {
                    val wr = cosTable[tableIdx]
                    val wi = sinTable[tableIdx]
                    val even = base + j
                    val odd = even + halfSize
                    val tr = wr * real[odd] - wi * imag[odd]
                    val ti = wr * imag[odd] + wi * real[odd]
                    real[odd] = real[even] - tr
                    imag[odd] = imag[even] - ti
                    real[even] += tr
                    imag[even] += ti
                    tableIdx += tableStep
                }
                base += step
            }
            halfSize = step
            tableStep = tableStep shr 1
        }

        val scale = 2.0 / size
        val bins = size / 2
        for (i in 0 until bins) {
            val mag = hypot(real[i], imag[i]) * scale
            outMagnitudes[i] = if (mag.isFinite()) mag.toFloat() else 0f
        }
        // DC bin does not need double-sided scaling
        outMagnitudes[0] = (hypot(real[0], imag[0]) / size).toFloat()
    }
}
