package com.vexo.studio
import com.vexo.studio.engine.scene.*
import com.vexo.studio.model.*
import org.junit.Assert.*
import org.junit.Test

class Phase39MotionTest {
    @Test fun firstRelativeFrameHasNoMotionAndRotationWraps(){
        val c=LayerConfig.MotionBlurEffect(blurAmountMultiplier=1f,includeRotationAndZoom=true)
        val now=MotionPose(.5f,.5f,359f,1.2f,1.2f)
        assertEquals(MotionSample(0f,0f,0f,0f),MotionSampling.evaluate(c,now,null))
        val next=MotionSampling.evaluate(c,MotionPose(.55f,.5f,1f,1.4f,1.4f),now)
        assertEquals(.05f,next.x,.001f)
        assertEquals(2f*kotlin.math.PI.toFloat()/180f,next.radians,.001f)
        assertTrue(next.zoom>0)
        assertEquals(0f,MotionSampling.evaluate(c.copy(includeRotationAndZoom=false),now,MotionPose(.5f,.5f,1f,1f,1f)).zoom,.001f)
    }
    @Test fun reactiveSmoothingIsIndependentAndTimeBased(){
        val c=MeasureConfig(MeasureWhat.BEAT,1f,1f,smoothing=.8f)
        val f=ReactiveMeasureFilter()
        val zero=EvaluatedPair(0f,0f);val one=EvaluatedPair(1f,1f)
        f.apply("a","scale",c,zero,.02f)
        val x=f.apply("a","scale",c,one,.02f)
        assertTrue(x.x in .001f.. .99f)
        assertEquals(one,f.apply("b","scale",c,one,.02f))
        f.clear()
        assertEquals(one,f.apply("a","scale",c,one,.02f))
    }
    @Test fun playingClockDoesNotJumpToZeroWhenPaused(){
        val c=MeasureConfig(MeasureWhat.TOTAL_TIME_WHEN_PLAYING,1f,1f)
        assertEquals(EvaluatedPair(4f,4f),MeasureEvaluator.evaluatePair(c,null,9f,4f,.4f,false))
    }
}
