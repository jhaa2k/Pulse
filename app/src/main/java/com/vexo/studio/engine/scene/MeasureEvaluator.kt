package com.vexo.studio.engine.scene

import com.vexo.studio.engine.audio.AudioProviderSnapshot
import com.vexo.studio.model.KeyframeTrack
import com.vexo.studio.model.MeasureConfig
import com.vexo.studio.model.MeasureWhat
import kotlin.math.cos
import kotlin.math.sin

data class EvaluatedPair(val x: Float, val y: Float) {
    fun mean(): Float = ((x.toDouble() + y.toDouble()) * 0.5).toFloat().takeIf { it.isFinite() } ?: 0f
}

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
        if(measure.legacyParameterSemantics){
            LegacyMeasureMath.evaluate(measure,totalTimeSec,gate(snapshot?.beat?:0f))?.let{return it}
        }
        // Evaluate only the signal selected by the measure. Ordinary beat, band,
        // and timeline controls need no trigonometry.
        fun same(value: Float) = value to value
        fun shake(gain: Float, rotation: Boolean = false): Pair<Float, Float> {
            if (gain == 0f) return same(0f)
            val phase = totalTimeSec * 17.3f + measure.seed * 0.137f
            val x = sin(phase) * 0.6f + cos(phase * 1.73f) * 0.4f
            if (rotation) return same(x * gain)
            val y = cos(phase * 1.19f) * 0.6f + sin(phase * 2.11f) * 0.4f
            return (x * gain) to (y * gain)
        }
        val (nx, ny) = when (measure.measureWhat) {
            MeasureWhat.NOTHING -> same(0f)
            MeasureWhat.BEAT -> same(gate(snapshot?.beat ?: 0f))
            MeasureWhat.TOTAL_TIME -> same(totalTimeSec)
            MeasureWhat.TOTAL_TIME_BACKWARD -> same(-totalTimeSec)
            MeasureWhat.TOTAL_TIME_WHEN_PLAYING -> same(playingTimeSec)
            MeasureWhat.TOTAL_TIME_AND_BEAT -> same(totalTimeSec + gate(snapshot?.beat ?: 0f) * 0.5f)
            MeasureWhat.TRACK_POSITION -> same(trackPositionNorm)
            MeasureWhat.BEAT_RANDOM_SHAKE -> shake(gate(snapshot?.beat ?: 0f))
            MeasureWhat.BEAT_CAM_SHAKE_MORE -> shake(gate(snapshot?.beat ?: 0f) * 1.65f)
            MeasureWhat.BEAT_CAM_SHAKE_LESS -> shake(gate(snapshot?.beat ?: 0f) * 0.5f)
            MeasureWhat.BEAT_CAM_SHAKE_ROT_MORE -> shake(gate(snapshot?.beat ?: 0f) * 12f, true)
            MeasureWhat.BEAT_CAM_SHAKE_ROT_LESS -> shake(gate(snapshot?.beat ?: 0f) * 4f, true)
            MeasureWhat.CONSTANT_SHAKE -> shake(1f)
            MeasureWhat.CONSTANT_SHAKE_MORE -> shake(1.6f)
            MeasureWhat.CONSTANT_SHAKE_ROT_MORE -> shake(10f, true)
            MeasureWhat.CONSTANT_SHAKE_ROT_LESS -> shake(3f, true)
            MeasureWhat.BEAT_TRIGGER_ANIM -> same(gate(snapshot?.beatPulse ?: 0f))
            MeasureWhat.BASS -> same(gate(snapshot?.bass ?: 0f))
            MeasureWhat.MID -> same(gate(snapshot?.mid ?: 0f))
            MeasureWhat.TREBLE -> same(gate(snapshot?.treble ?: 0f))
            MeasureWhat.RMS -> same(gate(snapshot?.rms ?: 0f))
            MeasureWhat.PEAK -> same(gate(snapshot?.peak ?: 0f))
            MeasureWhat.KEYFRAME -> same(keyframeValue ?: 0f)
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
        return scalarValue(measure,pair)
    }

    fun scalarValue(measure:MeasureConfig,pair:EvaluatedPair):Float =
        if(measure.legacyParameterSemantics)pair.x.takeIf{it.isFinite()}?:0f else pair.mean()

    fun colorProgress(measure:MeasureConfig,value:Float):Float {
        val v=value.takeIf{it.isFinite()}?:0f
        return if(measure.legacyParameterSemantics&&measure.measureWhat in LegacyMeasureMath.cyclicColorSources)
            (v.toDouble()-kotlin.math.floor(v.toDouble())).toFloat()
        else v.coerceIn(0f,1f)
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
