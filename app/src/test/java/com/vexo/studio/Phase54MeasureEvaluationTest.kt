package com.vexo.studio

import com.vexo.studio.engine.scene.EvaluatedPair
import com.vexo.studio.engine.scene.MeasureEvaluator
import com.vexo.studio.model.MeasureConfig
import com.vexo.studio.model.MeasureWhat
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sin
import kotlin.math.cos

class Phase54MeasureEvaluationTest {
    private fun sample(type: MeasureWhat, time: Float = 2f, keyframe: Float? = null) =
        MeasureEvaluator.evaluatePair(MeasureConfig(measureWhat = type, amountA = 2f, amountB = 3f, seed = 7),
            null, time, 1.5f, .25f, true, keyframe)

    @Test fun timelineAndKeyframeKeepIndependentAxisAmounts() {
        assertEquals(EvaluatedPair(4f, 6f), sample(MeasureWhat.TOTAL_TIME))
        assertEquals(EvaluatedPair(-4f, -6f), sample(MeasureWhat.TOTAL_TIME_BACKWARD))
        assertEquals(EvaluatedPair(3f, 4.5f), sample(MeasureWhat.TOTAL_TIME_WHEN_PLAYING))
        assertEquals(EvaluatedPair(.5f, .75f), sample(MeasureWhat.TRACK_POSITION))
        assertEquals(EvaluatedPair(1f, 1.5f), sample(MeasureWhat.KEYFRAME, keyframe = .5f))
    }

    @Test fun absentAudioProducesNoReactiveShakeOrBandSignal() {
        val types = listOf(MeasureWhat.BEAT, MeasureWhat.BASS, MeasureWhat.MID, MeasureWhat.TREBLE,
            MeasureWhat.RMS, MeasureWhat.PEAK, MeasureWhat.BEAT_TRIGGER_ANIM,
            MeasureWhat.BEAT_RANDOM_SHAKE, MeasureWhat.BEAT_CAM_SHAKE_MORE,
            MeasureWhat.BEAT_CAM_SHAKE_LESS, MeasureWhat.BEAT_CAM_SHAKE_ROT_MORE,
            MeasureWhat.BEAT_CAM_SHAKE_ROT_LESS)
        types.forEach { assertEquals(it.name, EvaluatedPair(0f, 0f), sample(it)) }
    }

    @Test fun constantShakeMatchesReferenceAndRotationUsesSameAxisSignal() {
        val phase = 2f * 17.3f + 7 * .137f
        val x = sin(phase) * .6f + cos(phase * 1.73f) * .4f
        val y = cos(phase * 1.19f) * .6f + sin(phase * 2.11f) * .4f
        val result = sample(MeasureWhat.CONSTANT_SHAKE)
        assertEquals(x * 2f, result.x, .00001f)
        assertEquals(y * 3f, result.y, .00001f)
        val rot = sample(MeasureWhat.CONSTANT_SHAKE_ROT_MORE)
        assertEquals(x * 10f * 2f, rot.x, .00001f)
        assertEquals(x * 10f * 3f, rot.y, .00001f)
    }

    @Test fun scalarAverageDoesNotOverflowAndInvalidValuesAreSanitized() {
        assertEquals(Float.MAX_VALUE, EvaluatedPair(Float.MAX_VALUE, Float.MAX_VALUE).mean(), 0f)
        assertEquals(0f, EvaluatedPair(Float.MAX_VALUE, -Float.MAX_VALUE).mean(), 0f)
        assertEquals(0f, EvaluatedPair(Float.NaN, 1f).mean(), 0f)
        assertEquals(EvaluatedPair(0f, 0f), sample(MeasureWhat.TOTAL_TIME, Float.NaN))
    }
}
