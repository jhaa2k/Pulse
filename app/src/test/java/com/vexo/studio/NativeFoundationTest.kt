package com.vexo.studio

import com.vexo.studio.engine.audio.VisualizerFftDecoder
import com.vexo.studio.engine.gl.SpectrumGeometry
import com.vexo.studio.engine.scene.MeasureEvaluator
import com.vexo.studio.engine.audio.AudioProviderSnapshot
import com.vexo.studio.model.MeasureConfig
import com.vexo.studio.model.MeasureWhat
import org.junit.Assert.*
import org.junit.Test

class NativeFoundationTest {
    @Test fun visualizerPackedFftAndUnsignedWaveform() {
        val raw=byteArrayOf(64,32,3,4,0,0,0,0)
        val bins=VisualizerFftDecoder.spectrum(raw)
        assertEquals(5,bins.size)
        assertEquals(.5f,bins[0],.001f)
        assertEquals(.25f,bins.last(),.001f)
        assertTrue(bins[1]>0f)
        assertEquals(0f,VisualizerFftDecoder.waveform(byteArrayOf(-128))[0],.001f)
    }
    @Test fun countAndRoundedGeometry() {
        assertArrayEquals(floatArrayOf(0f,.5f,1f),SpectrumGeometry.resample(floatArrayOf(0f,1f),3),.0001f)
        val mesh=SpectrumGeometry.roundedQuad(0f,0f,10f,0f,0f,40f,10f,40f,1f)
        assertEquals(96,mesh.size)
        assertTrue(mesh.all { it.isFinite() })
        assertTrue(mesh.indices.filter{it%2==0}.all{mesh[it] in -.001f..10.001f})
    }
    @Test fun beatGateRejectsQuietAudio() {
        val snap=AudioProviderSnapshot(floatArrayOf(),floatArrayOf(),.2f,0f,.2f,0f,0f,0f,0f)
        val result=MeasureEvaluator.evaluatePair(MeasureConfig(MeasureWhat.BASS,1f,1f,triggerThreshold=.5f),snap,0f,0f,0f,true)
        assertEquals(0f,result.x,.001f)
    }
}
