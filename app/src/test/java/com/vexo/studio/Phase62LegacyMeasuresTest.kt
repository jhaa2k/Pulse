package com.vexo.studio

import com.vexo.studio.data.AveeJsonCodec
import com.vexo.studio.data.NativeVexCodec
import com.vexo.studio.engine.scene.*
import com.vexo.studio.model.*
import org.junit.Assert.*
import org.junit.Test

class Phase62LegacyMeasuresTest {
    private fun eval(m:MeasureConfig,t:Float=1f)=MeasureEvaluator.evaluatePair(m,null,t,t,.5f,true)
    @Test fun structuredShakeUsesSpeedRatherThanYAxisGain(){
        val m=MeasureConfig(MeasureWhat.CONSTANT_SHAKE_MORE,1.3f,.1f,legacyParameterSemantics=true)
        val slow=eval(m);val fast=eval(m.copy(amountB=2f))
        assertNotEquals(slow,fast)
        assertTrue(kotlin.math.abs(slow.x)<=.021f&&kotlin.math.abs(slow.y)<=.021f)
        val frozen=m.copy(amountB=0f)
        assertEquals(eval(frozen,1f),eval(frozen,2f))
    }
    @Test fun independentNativeAxisBehaviorIsUnchanged(){
        val m=MeasureConfig(MeasureWhat.TOTAL_TIME,2f,3f)
        assertEquals(EvaluatedPair(4f,6f),eval(m,2f))
        assertEquals(5f,MeasureEvaluator.scalarValue(m,eval(m,2f)),0f)
    }
    @Test fun legacyTimeAndBeatAndColorPhaseUseIndependentParameters(){
        val m=MeasureConfig(MeasureWhat.TOTAL_TIME_AND_BEAT,.5f,2f,legacyParameterSemantics=true)
        assertEquals(EvaluatedPair(2f,2f),LegacyMeasureMath.evaluate(m,2f,.5f))
        assertEquals(.25f,MeasureEvaluator.colorProgress(m,3.25f),.0001f)
        assertEquals(.75f,MeasureEvaluator.colorProgress(m,-.25f),.0001f)
        assertEquals(1f,MeasureEvaluator.colorProgress(m.copy(legacyParameterSemantics=false),3.25f),0f)
    }
    @Test fun legacyMetadataAndMeasureSettingsSurviveBothFormats(){
        val raw="""{"compositions":[{"elements":[{"objType":"Image","MeasurePos":{"t":"_child","measureWhat":{"v":"ConstantShakeMore"},"A":{"v":1.3,"hint":"Amount"},"B":{"v":0.1,"hint":"Speed"},"providerIndex":{"v":2},"smoothing":{"v":0.4},"seed":{"v":99},"triggerThreshold":{"v":0.2}}}]}]}"""
        val p=AveeJsonCodec.importFromText(raw)
        val m=p.compositions[0].layers[0].common.measurePos
        assertTrue(m.legacyParameterSemantics)
        assertEquals(2,m.providerIndex)
        assertEquals(99,m.seed)
        assertEquals(.4f,m.smoothing,.0001f)
        assertEquals(.2f,m.triggerThreshold,.0001f)
        fun get(p:VexoProject)=p.compositions[0].layers[0].common.measurePos
        assertEquals(m,get(NativeVexCodec.decode(NativeVexCodec.encode(p))))
        assertEquals(m,get(AveeJsonCodec.importFromText(AveeJsonCodec.exportAveeScene(p))))
    }
}
