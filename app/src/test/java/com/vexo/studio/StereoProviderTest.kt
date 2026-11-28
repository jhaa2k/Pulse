package com.vexo.studio
import com.vexo.studio.engine.audio.*
import com.vexo.studio.model.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
class StereoProviderTest{
 @Test fun stereoWindowsShareTimestampAndPreserveChannels(){
    val ring=PcmRingBuffer(1024)
    val b=ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
    b.putShort(16384.toShort()).putShort(0.toShort()).putShort(8192.toShort()).putShort((-8192).toShort()).flip()
    ring.writePcm16(b,2,44100,0)
    val l=FloatArray(2);val r=FloatArray(2);val m=FloatArray(2)
    assertTrue(ring.readStereoWindows(m,l,r,ring.currentGeneration(),2))
    assertArrayEquals(floatArrayOf(.5f,.25f),l,.0001f)
    assertArrayEquals(floatArrayOf(0f,-.25f),r,.0001f)
    assertArrayEquals(floatArrayOf(.25f,0f),m,.0001f)
 }
 @Test fun providersAnalyzeDifferentChannelsInSameFrame(){
    val ring=PcmRingBuffer(1024)
    ring.writeFloatSamples(FloatArray(512){if(it%2==0).5f else 0f},44100,2)
    val e=RealtimeFftEngine(ring)
    try{
        val p=AudioProviderConfig(index=1,fftSize=256,channelMode=ChannelMode.LEFT)
        val frames=e.processStep(listOf(p,p.copy(index=2,channelMode=ChannelMode.RIGHT)),16f,true,false)
        assertEquals(.5f,frames.getValue(1).rms,.001f)
        assertEquals(0f,frames.getValue(2).rms,.001f)
    }finally{e.close()}
 }
}
