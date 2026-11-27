package com.vexo.studio

import com.vexo.studio.engine.audio.*
import com.vexo.studio.model.*
import org.junit.Assert.*
import org.junit.Test

class Phase56SpectrumDynamicsTest {
    @Test fun waveformPreservesSignedTransientBetweenOldSamplePositions(){
        val input=floatArrayOf(0f,-.9f,.2f,0f,0f,.8f,0f,0f)
        val output=FloatArray(2)
        SpectrumDynamics.resampleWaveform(input,output)
        assertArrayEquals(floatArrayOf(-.9f,.8f),output,.0001f)
    }
    @Test fun waveformUpsamplingInterpolatesAndHandlesInvalidSamples(){
        val output=FloatArray(5)
        SpectrumDynamics.resampleWaveform(floatArrayOf(-1f,1f),output)
        assertArrayEquals(floatArrayOf(-1f,-.5f,0f,.5f,1f),output,.0001f)
        SpectrumDynamics.resampleWaveform(floatArrayOf(Float.NaN),output)
        assertArrayEquals(FloatArray(5),output,.0001f)
    }
    @Test fun responseIsIndependentOfFrameSubdivision(){
        val reference=SpectrumDynamics.referenceCoefficient(8f)
        val full=SpectrumDynamics.responseCoefficient(reference,.4f,33.3334f)
        val half=SpectrumDynamics.responseCoefficient(reference,.4f,16.6667f)
        assertEquals(full,half+(1f-half)*half,.00001f)
    }
    @Test fun peakCannotFallBelowCurrentBar(){
        assertEquals(.8f,SpectrumDynamics.decayPeak(1f,.8f,100f,.5f),.0001f)
        assertEquals(1f,SpectrumDynamics.decayPeak(1f,.2f,0f,.5f),.0001f)
    }
    @Test fun providerPeaksStayAboveBarsDuringRelease(){
        val engine=AudioProviderEngine(AudioProviderConfig(fftSize=256,sampleOutCount=32,
            peakHoldMs=0f,peakDecay=.5f,releaseMs=200f))
        val signal=FloatArray(256){if(it%4<2).8f else -.8f}
        repeat(5){engine.process(signal,44100,16f,1,true,false)}
        repeat(10){
            val s=engine.process(FloatArray(256),44100,16f,1,true,false)
            assertTrue(s.bars.indices.all{i->s.peakBars[i]>=s.bars[i]})
        }
    }
}
