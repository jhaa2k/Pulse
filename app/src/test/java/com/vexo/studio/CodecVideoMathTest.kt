package com.vexo.studio
import com.vexo.studio.engine.gl.VideoDecodeMath
import com.vexo.studio.engine.gl.VideoTimeline
import org.junit.Assert.*
import org.junit.Test
class CodecVideoMathTest{
 @Test fun sequentialAndSeek(){
    assertTrue(VideoDecodeMath.shouldSeek(-1,0))
    assertFalse(VideoDecodeMath.shouldSeek(1000,34000))
    assertTrue(VideoDecodeMath.shouldSeek(34000,1000))
    assertTrue(VideoDecodeMath.shouldSeek(0,3000000))
 }
 @Test fun rotations(){
    assertArrayEquals(floatArrayOf(0f,1f,1f,1f,0f,0f,1f,0f),VideoDecodeMath.uv(0),.001f)
    assertArrayEquals(floatArrayOf(0f,0f,0f,1f,1f,0f,1f,1f),VideoDecodeMath.uv(90),.001f)
 }
 @Test fun sample60fps(){
    assertEquals(500L,VideoTimeline.sampleTimeMs(501,2000,1f,0,true,60))
 }
}
