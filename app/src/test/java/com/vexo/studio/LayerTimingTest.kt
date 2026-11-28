package com.vexo.studio
import com.vexo.studio.model.*
import com.vexo.studio.engine.scene.LayerTimingMath
import org.junit.Assert.*
import org.junit.Test
class LayerTimingTest{
 @Test fun timingAndFades(){
   val t=LayerTiming(1000,5000,1000,1000)
   assertEquals(0f,LayerTimingMath.opacity(t,500,8000),.001f)
   assertEquals(.5f,LayerTimingMath.opacity(t,1500,8000),.001f)
   assertEquals(1f,LayerTimingMath.opacity(t,3000,8000),.001f)
   assertEquals(.5f,LayerTimingMath.opacity(t,4500,8000),.001f)
   assertEquals(0f,LayerTimingMath.opacity(t,5001,8000),.001f)
 }
 @Test fun defaultAndTrackEnd(){
   assertEquals(1f,LayerTimingMath.opacity(LayerTiming(),0,0),.001f)
   assertEquals(.5f,LayerTimingMath.opacity(LayerTiming(fadeOutMs=1000),9500,10000),.001f)
 }
}
