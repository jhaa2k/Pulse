package com.vexo.studio
import com.vexo.studio.engine.gl.FrameDiagnosticsCounter
import org.junit.Assert.*
import org.junit.Test
class FrameDiagnosticsTest{
 @Test fun averagesMeasuredFrameCallbacks(){
    val c=FrameDiagnosticsCounter()
    assertNull(c.frame(1L,5f))
    var latest:Pair<Float,Float>?=null
    for(i in 1..10){latest=c.frame(1L+i*50_000_000L,5f)}
    assertEquals(20f,latest!!.first,.001f)
    assertEquals(5f,latest!!.second,.001f)
 }
}
