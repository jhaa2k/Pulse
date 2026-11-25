package com.vexo.studio.engine.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Lock-free bounded single-producer single-consumer (SPSC) PCM ring buffer.
 * Supports pass-through PCM capture without blocking playback and a race-safe
 * stream generation protocol for seek, pause, track switches, and format resets.
 */
class PcmRingBuffer(capacitySamples: Int = 65536) {
    val capacity: Int = nextPowerOfTwo(capacitySamples.coerceAtLeast(1024))
    private val mask: Int = capacity - 1
    private val ring = FloatArray(capacity)

    private val writeIndex = AtomicLong(0L)
    private val generation = AtomicInteger(1)
    private val droppedSamples = AtomicLong(0L)

    @Volatile
    var sampleRate: Int = 44100
        private set

    @Volatile
    var channelCount: Int = 2
        private set

    @Volatile
    var latestMediaTimestampUs: Long = 0L
        private set

    @Volatile
    var lastReadIndex: Long = 0L
        private set

    fun currentGeneration(): Int = generation.get()

    fun totalWrittenFrames(): Long = writeIndex.get()

    fun totalDroppedSamples(): Long = droppedSamples.get()

    fun resetGeneration(newSampleRate: Int = sampleRate, newChannelCount: Int = channelCount): Int {
        val nextGen = generation.incrementAndGet()
        sampleRate = newSampleRate.coerceIn(8000, 192000)
        channelCount = newChannelCount.coerceIn(1, 8)
        writeIndex.set(0L)
        lastReadIndex = 0L
        latestMediaTimestampUs = 0L
        ring.fill(0f)
        return nextGen
    }

    /**
     * Non-blocking producer write from ExoPlayer's AudioProcessor or offline decoder.
     * Supports 16-bit PCM and 32-bit float PCM interleaved frames, preserving original bytes
     * while downmixing safely and sanitizing non-finite values.
     */
    fun writePcm16(
        buffer: ByteBuffer,
        channels: Int,
        sampleRateHz: Int,
        mediaTimestampUs: Long,
        channelSelect: Int = 0 // 0=mix, 1=left, 2=right
    ) {
        if (channels <= 0) return
        if (sampleRateHz != sampleRate || channels != channelCount) {
            resetGeneration(sampleRateHz, channels)
        }
        val dup = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val totalShorts = dup.remaining() / 2
        val frames = totalShorts / channels
        if (frames <= 0) return

        var pos = writeIndex.get()
        if (frames > capacity) {
            droppedSamples.addAndGet((frames - capacity).toLong())
        }

        for (f in 0 until frames) {
            val sample = when (channelSelect) {
                1 -> dup.getShort(dup.position() + (f * channels) * 2) / 32768f
                2 -> {
                    val ch = if (channels > 1) 1 else 0
                    dup.getShort(dup.position() + (f * channels + ch) * 2) / 32768f
                }
                else -> {
                    var sum = 0f
                    for (c in 0 until channels) {
                        sum += dup.getShort(dup.position() + (f * channels + c) * 2) / 32768f
                    }
                    sum / channels
                }
            }
            val safe = if (sample.isFinite()) sample.coerceIn(-1f, 1f) else 0f
            ring[(pos and mask.toLong()).toInt()] = safe
            pos++
        }
        latestMediaTimestampUs = mediaTimestampUs
        writeIndex.lazySet(pos)
    }

    fun writeFloatSamples(
        samples: FloatArray,
        sampleRateHz: Int = sampleRate,
        channels: Int = 1,
        mediaTimestampUs: Long = 0L
    ) {
        if (samples.isEmpty()) return
        if (sampleRateHz != sampleRate || channels != channelCount) {
            resetGeneration(sampleRateHz, channels)
        }
        val frames = samples.size / channels.coerceAtLeast(1)
        var pos = writeIndex.get()
        for (f in 0 until frames) {
            var sum = 0f
            for (c in 0 until channels) {
                val s = samples[f * channels + c]
                sum += if (s.isFinite()) s else 0f
            }
            ring[(pos and mask.toLong()).toInt()] = (sum / channels).coerceIn(-1f, 1f)
            pos++
        }
        latestMediaTimestampUs = mediaTimestampUs
        writeIndex.lazySet(pos)
    }

    /**
     * Reads the latest [dest.size] mono samples if generation matches and enough samples exist.
     * Detects ring wraparound drops cleanly without blocking.
     */
    fun readLatestWindow(dest: FloatArray, expectedGeneration: Int): Boolean {
        if (generation.get() != expectedGeneration) return false
        val currentWrite = writeIndex.get()
        if (currentWrite <= 0L) {
            dest.fill(0f)
            return false
        }
        if (currentWrite - lastReadIndex > capacity) {
            droppedSamples.addAndGet((currentWrite - lastReadIndex - capacity))
        }
        val available = minOf(currentWrite, capacity.toLong()).toInt()
        val count = dest.size
        val start = currentWrite - minOf(available, count)
        val pad = count - minOf(available, count)
        for (i in 0 until pad) {
            dest[i] = 0f
        }
        for (i in 0 until minOf(available, count)) {
            dest[pad + i] = ring[((start + i) and mask.toLong()).toInt()]
        }
        lastReadIndex = currentWrite
        return generation.get() == expectedGeneration
    }

    private fun nextPowerOfTwo(n: Int): Int {
        var v = n - 1
        v = v or (v shr 1)
        v = v or (v shr 2)
        v = v or (v shr 4)
        v = v or (v shr 8)
        v = v or (v shr 16)
        return v + 1
    }
}
