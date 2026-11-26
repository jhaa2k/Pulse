package com.vexo.studio.engine.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Bounded PCM ring buffer with coherent stereo windows and synchronized format resets.
 * Supports pass-through PCM capture without blocking playback and a race-safe
 * stream generation protocol for seek, pause, track switches, and format resets.
 */
class PcmRingBuffer(capacitySamples: Int = 65536) {
    val capacity: Int = nextPowerOfTwo(capacitySamples.coerceAtLeast(1024))
    private val mask: Int = capacity - 1
    private val ring = FloatArray(capacity)
    private val mixedRing = FloatArray(capacity)
    private val leftRing = FloatArray(capacity)
    private val rightRing = FloatArray(capacity)

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

    @Synchronized
    fun resetGeneration(newSampleRate: Int = sampleRate, newChannelCount: Int = channelCount): Int {
        val nextGen = generation.incrementAndGet()
        sampleRate = newSampleRate.coerceIn(8000, 192000)
        channelCount = newChannelCount.coerceIn(1, 8)
        writeIndex.set(0L)
        lastReadIndex = 0L
        latestMediaTimestampUs = 0L
        ring.fill(0f); mixedRing.fill(0f); leftRing.fill(0f); rightRing.fill(0f)
        return nextGen
    }

    /**
     * Non-blocking producer write from ExoPlayer's AudioProcessor or offline decoder.
     * Supports 16-bit PCM and 32-bit float PCM interleaved frames, preserving original bytes
     * while downmixing safely and sanitizing non-finite values.
     */
    @Synchronized
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
            val base=dup.position()+f*channels*2
            val left=dup.getShort(base)/32768f
            val right=dup.getShort(base+(if(channels>1)2 else 0))/32768f
            var sum=0f
            for(c in 0 until channels)sum+=dup.getShort(base+c*2)/32768f
            store(pos,left,right,sum/channels,channelSelect)
            pos++
        }
        latestMediaTimestampUs = mediaTimestampUs
        writeIndex.lazySet(pos)
    }

    @Synchronized
    fun writePcmFloat(
        buffer: ByteBuffer,
        channels: Int,
        sampleRateHz: Int,
        mediaTimestampUs: Long,
        channelSelect: Int = 0
    ) {
        if (channels <= 0) return
        if (sampleRateHz != sampleRate || channels != channelCount) {
            resetGeneration(sampleRateHz, channels)
        }
        val dup = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val totalFloats = dup.remaining() / 4
        val frames = totalFloats / channels
        if (frames <= 0) return

        var pos = writeIndex.get()
        if (frames > capacity) {
            droppedSamples.addAndGet((frames - capacity).toLong())
        }

        val basePos = dup.position()
        for (f in 0 until frames) {
            val base=basePos+f*channels*4
            val left=clean(dup.getFloat(base))
            val right=clean(dup.getFloat(base+(if(channels>1)4 else 0)))
            var sum=0f
            for(c in 0 until channels)sum+=clean(dup.getFloat(base+c*4))
            store(pos,left,right,sum/channels,channelSelect)
            pos++
        }
        latestMediaTimestampUs = mediaTimestampUs
        writeIndex.lazySet(pos)
    }

    @Synchronized
    fun writeFloatSamples(
        samples: FloatArray,
        sampleRateHz: Int = sampleRate,
        channels: Int = 1,
        mediaTimestampUs: Long = 0L
    ) {
        if (samples.isEmpty() || channels<=0) return
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
            store(pos, samples[f*channels],samples[f*channels+if(channels>1)1 else 0],sum/channels,0)
            pos++
        }
        latestMediaTimestampUs = mediaTimestampUs
        writeIndex.lazySet(pos)
    }

    /**
     * Reads the latest [sampleCount] mono samples into [dest] if generation matches and enough samples exist.
     * Detects ring wraparound drops cleanly without blocking.
     */
    @Synchronized
    fun readLatestWindow(
        dest: FloatArray,
        expectedGeneration: Int,
        sampleCount: Int = dest.size
    ): Boolean {
        val count = sampleCount.coerceIn(0, dest.size)
        if (count == 0) return false
        if (generation.get() != expectedGeneration) return false
        val currentWrite = writeIndex.get()
        if (currentWrite <= 0L) {
            dest.fill(0f, 0, count)
            return false
        }
        if (currentWrite - lastReadIndex > capacity) {
            droppedSamples.addAndGet((currentWrite - lastReadIndex - capacity))
        }
        val available = minOf(currentWrite, capacity.toLong()).toInt()
        val take = minOf(available, count)
        val start = currentWrite - take
        val pad = count - take
        for (i in 0 until pad) {
            dest[i] = 0f
        }
        for (i in 0 until take) {
            dest[pad + i] = ring[((start + i) and mask.toLong()).toInt()]
        }
        lastReadIndex = currentWrite
        return generation.get() == expectedGeneration
    }

    private fun clean(value:Float)=if(value.isFinite())value.coerceIn(-1f,1f) else 0f

    private fun store(pos:Long,left:Float,right:Float,mix:Float,select:Int){
        val i=(pos and mask.toLong()).toInt()
        leftRing[i]=clean(left);rightRing[i]=clean(right);mixedRing[i]=clean(mix)
        ring[i]=when(select){1->leftRing[i];2->rightRing[i];else->mixedRing[i]}
    }

    /** All requested channels share the same ending sample and generation. */
    @Synchronized
    fun readStereoWindows(mix:FloatArray?,left:FloatArray?,right:FloatArray?,expectedGeneration:Int,count:Int):Boolean{
        if(generation.get()!=expectedGeneration)return false
        val end=writeIndex.get()
        if(end<=0L)return false
        val take=minOf(end,capacity.toLong(),count.toLong()).toInt().coerceAtLeast(0)
        fun copy(src:FloatArray,dst:FloatArray?){
            if(dst==null)return
            require(count in 0..dst.size)
            val pad=count-take
            dst.fill(0f,0,pad)
            for(i in 0 until take)dst[pad+i]=src[((end-take+i) and mask.toLong()).toInt()]
        }
        copy(mixedRing,mix);copy(leftRing,left);copy(rightRing,right)
        lastReadIndex=end
        return true
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
