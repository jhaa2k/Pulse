package com.vexo.studio
import com.vexo.studio.engine.gl.MotionMath
import com.vexo.studio.playback.PlaybackTransition
import org.junit.Assert.*
import org.junit.Test
class Phase7RegressionTest {
 @Test fun trailDecayIndependentOfFps(){
    val a=MotionMath.persistence(.65f,1f/30f)
    val b=MotionMath.persistence(.65f,1f/60f)
    assertEquals(a,b*b,.0001f)
 }
 @Test fun transitionEnvelope(){
    assertEquals(.5f,PlaybackTransition.gain(2000,1000,5000,true,true),.001f)
    assertEquals(.25f,PlaybackTransition.gain(2000,10000,500,true,true),.001f)
    assertEquals(1f,PlaybackTransition.gain(0,0,0,true,true),.001f)
    assertEquals(1f,PlaybackTransition.gain(2000,0,5000,false,true),.001f)
 }
}
