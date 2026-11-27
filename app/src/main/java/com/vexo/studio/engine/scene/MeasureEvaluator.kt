package com.vexo.studio.engine.scene

import com.vexo.studio.engine.audio.AudioProviderSnapshot
import com.vexo.studio.model.InterpolationType
import com.vexo.studio.model.KeyframeTrack
import com.vexo.studio.model.MeasureConfig
import com.vexo.studio.model.MeasureWhat
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class EvaluatedPair(val x: Float, val y: Float)

object KeyframeEvaluator {
    /** Callers without a compiled index may supply unordered imported tracks. */
    fun evaluate(track: KeyframeTrack?, timeMs: Long, defaultValue: Float): Float =
        PreparedKeyframeTrack.create(track).evaluate(timeMs,defaultValue)
}

/**
 * Deterministic frame evaluation for all §10 audio-reactive measures.
 * Reproduces identical motion at identical timestamps during live preview and offline export.
 */
object MeasureEvaluator {
    fun evaluatePair(
        measure: MeasureConfig,
        snapshot: AudioProviderSnapshot?,
        totalTimeSec: Float,
        playingTimeSec: Float,
        trackPositionNorm: Float,
        isPlaying: Boolean,
        keyframeValue: Float? = null
    ): EvaluatedPair {
        if (measure.measureWhat == MeasureWhat.NOTHING) return EvaluatedPair(0f, 0f)

        val threshold = measure.triggerThreshold.takeIf{it.isFinite()}?.coerceIn(0f,0.999f)?:0f
        fun gate(value: Float): Float = if (!value.isFinite()) 0f
            else if (threshold == 0f) value else ((value - threshold) / (1f - threshold)).coerceIn(0f, 1f)
        val beat = gate(snapshot?.beat ?: 0f)
        val beatPulse = gate(snapshot?.beatPulse ?: 0f)
        val bass = gate(snapshot?.bass ?: 0f)
        val mid = gate(snapshot?.mid ?: 0f)
        val treble = gate(snapshot?.treble ?: 0f)
        val rms = gate(snapshot?.rms ?: 0f)
        val peak = gate(snapshot?.peak ?: 0f)

        val shakePhase = totalTimeSec * 17.3f + measure.seed * 0.137f
        val shakeX = sin(shakePhase) * 0.6f + cos(shakePhase * 1.73f) * 0.4f
        val shakeY = cos(shakePhase * 1.19f) * 0.6f + sin(shakePhase * 2.11f) * 0.4f

        val (nx, ny) = when (measure.measureWhat) {
            MeasureWhat.NOTHING -> 0f to 0f
            MeasureWhat.BEAT -> beat to beat
            MeasureWhat.TOTAL_TIME -> totalTimeSec to totalTimeSec
            MeasureWhat.TOTAL_TIME_BACKWARD -> -totalTimeSec to -totalTimeSec
            MeasureWhat.TOTAL_TIME_WHEN_PLAYING -> playingTimeSec to playingTimeSec
            MeasureWhat.TOTAL_TIME_AND_BEAT -> (totalTimeSec + beat * 0.5f) to (totalTimeSec + beat * 0.5f)
            MeasureWhat.TRACK_POSITION -> trackPositionNorm to trackPositionNorm
            MeasureWhat.BEAT_RANDOM_SHAKE -> (shakeX * beat) to (shakeY * beat)
            MeasureWhat.BEAT_CAM_SHAKE_MORE -> (shakeX * beat * 1.65f) to (shakeY * beat * 1.65f)
            MeasureWhat.BEAT_CAM_SHAKE_LESS -> (shakeX * beat * 0.5f) to (shakeY * beat * 0.5f)
            MeasureWhat.BEAT_CAM_SHAKE_ROT_MORE -> (shakeX * beat * 12f) to (shakeX * beat * 12f)
            MeasureWhat.BEAT_CAM_SHAKE_ROT_LESS -> (shakeX * beat * 4f) to (shakeX * beat * 4f)
            MeasureWhat.CONSTANT_SHAKE -> shakeX to shakeY
            MeasureWhat.CONSTANT_SHAKE_MORE -> (shakeX * 1.6f) to (shakeY * 1.6f)
            MeasureWhat.CONSTANT_SHAKE_ROT_MORE -> (shakeX * 10f) to (shakeX * 10f)
            MeasureWhat.CONSTANT_SHAKE_ROT_LESS -> (shakeX * 3f) to (shakeX * 3f)
            MeasureWhat.BEAT_TRIGGER_ANIM -> beatPulse to beatPulse
            MeasureWhat.BASS -> bass to bass
            MeasureWhat.MID -> mid to mid
            MeasureWhat.TREBLE -> treble to treble
            MeasureWhat.RMS -> rms to rms
            MeasureWhat.PEAK -> peak to peak
            MeasureWhat.KEYFRAME -> (keyframeValue ?: 0f) to (keyframeValue ?: 0f)
        }

        return EvaluatedPair((nx*measure.amountA).takeIf{it.isFinite()}?:0f,(ny*measure.amountB).takeIf{it.isFinite()}?:0f)
    }

    fun evaluateScalar(
        measure: MeasureConfig,
        snapshot: AudioProviderSnapshot?,
        totalTimeSec: Float,
        playingTimeSec: Float,
        trackPositionNorm: Float,
        isPlaying: Boolean,
        keyframeValue: Float? = null
    ): Float {
        val pair = evaluatePair(
            measure = measure,
            snapshot = snapshot,
            totalTimeSec = totalTimeSec,
            playingTimeSec = playingTimeSec,
            trackPositionNorm = trackPositionNorm,
            isPlaying = isPlaying,
            keyframeValue = keyframeValue
        )
        return 0.5f * (pair.x + pair.y)
    }

    fun blendArgb(colorFrom: Int, colorTo: Int, t: Float): Int {
        val c = t.takeIf{it.isFinite()}?.coerceIn(0f,1f)?:0f
        val a0 = (colorFrom ushr 24) and 0xFF
        val r0 = (colorFrom ushr 16) and 0xFF
        val g0 = (colorFrom ushr 8) and 0xFF
        val b0 = colorFrom and 0xFF

        val a1 = (colorTo ushr 24) and 0xFF
        val r1 = (colorTo ushr 16) and 0xFF
        val g1 = (colorTo ushr 8) and 0xFF
        val b1 = colorTo and 0xFF

        val a = (a0 + (a1 - a0) * c).toInt().coerceIn(0, 255)
        val r = (r0 + (r1 - r0) * c).toInt().coerceIn(0, 255)
        val g = (g0 + (g1 - g0) * c).toInt().coerceIn(0, 255)
        val b = (b0 + (b1 - b0) * c).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
