package com.vexo.studio
import com.vexo.studio.data.LegacyAssetReferences
import com.vexo.studio.engine.audio.AudioProviderEngine
import com.vexo.studio.model.AudioProviderConfig
import com.vexo.studio.model.SampleProviderType
import org.junit.Assert.*
import org.junit.Test
class Phase8RegressionTest{
 @Test fun archiveAssetResolution(){
    val names=setOf("assets/art.png","other/art.png","fonts/type.otf")
    assertEquals("assets/art.png",LegacyAssetReferences.match("local:assets/art.png",names))
    assertEquals("fonts/type.otf",LegacyAssetReferences.match("type.otf",names))
    assertNull(LegacyAssetReferences.match("art.png",names))
    assertNull(LegacyAssetReferences.match("../type.otf",names))
    assertNull(LegacyAssetReferences.match("content://type.otf",names))
    assertNull(LegacyAssetReferences.match("composition:1",names))
    assertEquals("assets/art.png",LegacyAssetReferences.rawValue("{\"v\":\"assets/art.png\"}"))
 }
 @Test fun peakSnapshotHoldsValue(){
    val engine=AudioProviderEngine(AudioProviderConfig(sampleProvider=SampleProviderType.WAVEFORM,fftSize=256,preSmooth=0f))
    val snap=engine.process(FloatArray(256){.5f},44100,16f,1,true,false)
    assertEquals(snap.bars.size,snap.peakBars.size)
    assertTrue(snap.peakBars.any{it>0f})
 }
}
