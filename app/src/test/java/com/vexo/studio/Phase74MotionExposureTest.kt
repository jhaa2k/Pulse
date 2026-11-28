package com.vexo.studio

import com.vexo.studio.engine.scene.MotionPose
import com.vexo.studio.engine.scene.MotionSampling
import com.vexo.studio.engine.gl.MotionMath
import com.vexo.studio.model.LayerConfig
import com.vexo.studio.model.MotionSource
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.exp

class Phase74MotionExposureTest {
    @Test fun relativeMotionHasSameExposureAtEveryFps(){
        val c=LayerConfig.MotionBlurEffect(blurAmountMultiplier=1f,includeRotationAndZoom=true)
        val prev=MotionPose(.5f,.5f,0f,1f,1f)
        val reference=MotionSampling.evaluate(c,MotionPose(.51f,.505f,1f,exp(.01f),exp(.01f)),prev,1f/60f)
        for(fps in listOf(24,25,30,50,60,120)){
            val t=60f/fps
            val next=MotionSampling.evaluate(c,MotionPose(.5f+.01f*t,.5f+.005f*t,t,exp(.01f*t),exp(.01f*t)),prev,1f/fps)
            assertEquals(reference.x,next.x,.0001f)
            assertEquals(reference.y,next.y,.0001f)
            assertEquals(reference.radians,next.radians,.0001f)
            assertEquals(reference.zoom,next.zoom,.0001f)
        }
    }
    @Test fun manualAndAbsoluteMotionAreNotRescaled(){
        val prev=MotionPose(.4f,.4f,0f,1f,1f)
        val now=MotionPose(.55f,.6f,3f,1.1f,1.1f)
        for(c in listOf(LayerConfig.MotionBlurEffect(motionSource=MotionSource.MANUAL),LayerConfig.MotionBlurEffect(relativeMotionMode=false))){
            assertEquals(MotionSampling.evaluate(c,now,prev,1f/60f),MotionSampling.evaluate(c,now,prev,1f/24f))
        }
    }
    @Test fun persistenceIsTimeNormalizedAndFinite(){
        assertEquals(MotionMath.persistence(.7f,1f/30f),MotionMath.persistence(.7f,1f/60f)*MotionMath.persistence(.7f,1f/60f),.0001f)
        assertEquals(0f,MotionMath.persistence(Float.NaN,1f/30f),0f)
        assertTrue(MotionMath.persistence(.7f,Float.NaN).isFinite())
    }
}
