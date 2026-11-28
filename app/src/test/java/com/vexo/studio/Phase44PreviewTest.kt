package com.vexo.studio
import com.vexo.studio.engine.gl.AdaptivePreviewController
import com.vexo.studio.engine.gl.TextureDecodePolicy
import org.junit.Assert.*
import org.junit.Test
class Phase44PreviewTest{
    @Test fun sustainedLoadReducesScaleAndStablePlaybackRecovers(){
        val c=AdaptivePreviewController(1f,30)
        repeat(2){assertEquals(1f,c.observe(15f,40f),.001f)}
        assertEquals(.85f,c.observe(15f,40f),.001f)
        repeat(4){c.observe(30f,5f)}
        repeat(9){assertEquals(.85f,c.observe(30f,5f),.001f)}
        assertEquals(1f,c.observe(30f,5f),.001f)
    }
    @Test fun obeysUserCeilingAndMinimum(){
        val c=AdaptivePreviewController(.75f,60)
        repeat(100){c.observe(10f,50f)}
        assertEquals(.5f,c.scale,.001f)
        repeat(100){c.observe(60f,2f)}
        assertEquals(.75f,c.scale,.001f)
    }
    @Test fun downsamplingRespectsGpuBound(){
        assertEquals(4,TextureDecodePolicy.sampleSize(6000,3000,2048))
        assertEquals(8,TextureDecodePolicy.sampleSize(4097,100,1024))
        assertEquals(1,TextureDecodePolicy.sampleSize(100,100,1024))
    }
}
