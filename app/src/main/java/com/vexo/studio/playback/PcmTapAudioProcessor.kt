package com.vexo.studio.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.vexo.studio.engine.audio.PcmRingBuffer
import java.nio.ByteBuffer

/**
 * Pass-through Media3 AudioProcessor tapping PCM frames into [PcmRingBuffer]
 * without blocking playback or mutating audio samples.
 */
@UnstableApi
class PcmTapAudioProcessor(
    private val ringBuffer: PcmRingBuffer
) : BaseAudioProcessor() {

    @Volatile
    var channelSelectMode: Int = 0 // 0=mix, 1=left, 2=right

    @Volatile
    var playbackClockUsProvider: () -> Long = { 0L }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            ringBuffer.resetGeneration(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
            return inputAudioFormat
        }
        return AudioProcessor.AudioFormat.NOT_SET
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining <= 0) return

        ringBuffer.writePcm16(
            buffer = inputBuffer,
            channels = inputAudioFormat.channelCount,
            sampleRateHz = inputAudioFormat.sampleRate,
            mediaTimestampUs = playbackClockUsProvider(),
            channelSelect = channelSelectMode
        )

        val out = replaceOutputBuffer(remaining)
        out.put(inputBuffer)
        out.flip()
    }

    override fun onFlush() {
        ringBuffer.resetGeneration(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
    }

    override fun onReset() {
        ringBuffer.resetGeneration()
    }
}
