package com.vexo.studio
import com.vexo.studio.engine.gl.SpectrumFilters
import com.vexo.studio.engine.audio.AudioProviderEngine
import com.vexo.studio.model.AudioProviderConfig
import com.vexo.studio.model.SampleProviderType
import org.junit.Assert.*
import org.junit.Test
class Phase6RegressionTest {
    @Test fun waveformIsNotDiscardedBySpatialFilter() {
        val engine=AudioProviderEngine(AudioProviderConfig(sampleProvider=SampleProviderType.WAVEFORM,fftSize=256,preSmooth=0f))
        val snap=engine.process(FloatArray(256){.5f},44100,16f,1,true,false)
        assertTrue(snap.bars.any{it>0f})
    }
    @Test fun disabledProviderClearsEvenWhenFrozen() {
        val engine=AudioProviderEngine(AudioProviderConfig(sampleProvider=SampleProviderType.WAVEFORM))
        engine.process(FloatArray(1024){.5f},44100,16f,1,true,false)
        engine.updateConfig(AudioProviderConfig(enabled=false))
        assertTrue(engine.process(null,44100,16f,1,false,true).bars.all{it==0f})
    }
    @Test fun smoothingPreservesConstantAndReducesSpike() {
        assertArrayEquals(floatArrayOf(2f,2f,2f),SpectrumFilters.smooth(floatArrayOf(2f,2f,2f),2),.001f)
        assertTrue(SpectrumFilters.smooth(floatArrayOf(0f,1f,0f),1)[1]<1f)
    }
}
