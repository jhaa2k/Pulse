package com.vexo.studio
import com.vexo.studio.engine.scene.*
import com.vexo.studio.model.*
import org.junit.Assert.*
import org.junit.Test
class Phase51PropertyTest{
    @Test fun invalidTransformsCannotPoisonMatrix(){
        val c=LayerCommon(posX=Float.NaN,scaleX=Float.POSITIVE_INFINITY,rotationDeg=Float.NaN,opacity=Float.NaN)
        val v=TransformAnimation.evaluate(c,emptyList(),0)
        assertEquals(.5f,v.posX,.001f);assertEquals(1f,v.scaleX,.001f)
        assertEquals(0f,v.rotationDeg,.001f);assertEquals(1f,v.opacity,.001f)
    }
    @Test fun uniformScaleHidesInactiveTrackWithoutDeletingIt(){
        val l=LayerNode(LayerType.BARS,LayerCommon(scaleIsUniform=true,scaleX=2f,scaleY=.5f),LayerConfig.Bars())
        assertFalse("scaleY" in LayerPropertyAnimation.keys(l))
        assertEquals(2f,TransformAnimation.evaluate(l.common,emptyList(),0).scaleY,.001f)
        assertTrue("scaleY" in LayerPropertyAnimation.keys(l.copy(common=l.common.copy(scaleIsUniform=false))))
        assertEquals(-2f,LayerPropertyAnimation.clamp("bars.height",-2f),.001f)
    }
    @Test fun measureAndKeyframeInputsStayFinite(){
        val p=MeasureEvaluator.evaluatePair(MeasureConfig(measureWhat=MeasureWhat.TOTAL_TIME,amountA=Float.NaN,amountB=Float.POSITIVE_INFINITY),null,1f,1f,0f,true)
        assertEquals(EvaluatedPair(0f,0f),p)
        assertEquals(0f,LayerPropertyAnimation.clamp("blur.radius",Float.NaN),.001f)
    }
}
