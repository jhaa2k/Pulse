package com.vexo.studio
import com.vexo.studio.engine.gl.*
import org.junit.Assert.*
import org.junit.Test
class Phase45RenderQualityTest{
    @Test fun exactIdentityAndRepeatBinMapping(){
        for(n in 1..512)for(i in 0 until n)assertEquals(i,SpectrumGeometry.sampleIndex(i,n,n,1,0f))
        assertEquals(0,SpectrumGeometry.sampleIndex(4,8,8,2,0f))
        assertEquals(4,SpectrumGeometry.sampleIndex(0,8,8,1,.5f))
        assertEquals(6,SpectrumGeometry.sampleIndex(0,8,8,1,-.25f))
    }
    @Test fun exportTextureLimitRespectsDeviceAndSelection(){
        assertEquals(4096,TextureDecodePolicy.limit(true,false,true,3840,8192))
        assertEquals(2048,TextureDecodePolicy.limit(true,false,false,3840,8192))
        assertEquals(2048,TextureDecodePolicy.limit(true,true,true,3840,8192))
        assertEquals(1024,TextureDecodePolicy.limit(false,true,true,3840,8192))
        assertEquals(1024,TextureDecodePolicy.limit(true,false,true,3840,1024))
    }
}
