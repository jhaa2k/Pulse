package com.vexo.studio.export

/** Integer clocks avoid accumulated rounding and floating-point sample drift. */
object ExportClock {
    fun frameTimeNs(frame: Long, fps: Int): Long = frame * 1_000_000_000L / fps
    fun sampleAtFrame(frame: Long, sampleRate: Int, fps: Int): Long = frame * sampleRate / fps
    fun sampleTimeUs(samples: Long, sampleRate: Int): Long = samples * 1_000_000L / sampleRate
    fun frameCount(durationUs: Long, fps: Int): Int =
        ((durationUs * fps + 999_999L) / 1_000_000L).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
}
