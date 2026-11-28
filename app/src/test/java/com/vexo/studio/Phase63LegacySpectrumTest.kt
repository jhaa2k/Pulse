package com.vexo.studio

import com.vexo.studio.data.AveeJsonCodec
import com.vexo.studio.engine.scene.LegacySpectrumMath
import com.vexo.studio.model.LayerConfig
import org.junit.Assert.*
import org.junit.Test

class Phase63LegacySpectrumTest {
    @Test fun importedSpectrumDoesNotInventPeakMarkersOrOverrideProviderCount(){
        val p=AveeJsonCodec.importFromText("""{"compositions":[{"elements":[{"objType":"AudioProvider","sampleOutCount":{"v":300}},{"objType":"Bars","ShapePath":{"v":"Circle"},"Segment1":{"v":"Bars"}}]}]}""")
        val c=p.compositions[0].layers[0].config as LayerConfig.Bars
        assertEquals(300,p.audioProviders[0].sampleOutCount)
        assertEquals(0,c.barCount)
        assertFalse(c.showPeaks)
    }
    @Test fun segmentDirectionAndNativePeakOptionsRoundTrip(){
        val p=AveeJsonCodec.importFromText("""{"compositions":[{"elements":[{"objType":"Bars","Segment1":{"v":"Bars","flipEveryOther":{"v":1}},"Segment2":{"v":"Line","flipEveryOther":{"v":0}},"vexoShowPeaks":{"v":1},"vexoBarCount":{"v":174}}]}]}""")
        val c=p.compositions[0].layers[0].config as LayerConfig.Bars
        assertTrue(c.alternatingDirection)
        assertEquals(false,c.segment2AlternatingDirection)
        assertTrue(c.showPeaks)
        assertEquals(174,c.barCount)
        val restored=AveeJsonCodec.importFromText(AveeJsonCodec.exportAveeScene(p))
        assertEquals(c,restored.compositions[0].layers[0].config)
    }
    @Test fun circularGapReducesSlotWidthProportionally(){
        val full=LegacySpectrumMath.arcStep(300,0f)
        assertEquals(full*.75f,LegacySpectrumMath.arcStep(300,.25f),.000001f)
        assertEquals(full,LegacySpectrumMath.arcStep(300,Float.NaN),.000001f)
    }
    @Test fun heightClampsPreserveSignAndZeroGain(){
        assertEquals(-.5f,LegacySpectrumMath.signedAmplitude(.8f,-2f,.1f,.5f),.0001f)
        assertEquals(.1f,LegacySpectrumMath.signedAmplitude(0f,2f,.1f,.5f),.0001f)
        assertEquals(0f,LegacySpectrumMath.signedAmplitude(1f,0f,.1f,.5f),0f)
        assertEquals(.5f,LegacySpectrumMath.signedAmplitude(1f,2f,.5f,.1f),.0001f)
    }
}
