package com.vexo.studio
import com.vexo.studio.engine.audio.*
import com.vexo.studio.model.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*
class Phase48AudioTest{
    @Test fun transientRejectsSteadyEnergyAndRearmsForNextHit(){
        val d=TransientBeatDetector()
        assertFalse(d.update(0f,16f,1f,100))
        assertTrue(d.update(.8f,16f,1f,100))
        repeat(12){assertFalse(d.update(.8f,16f,1f,100))}
        repeat(8){assertFalse(d.update(.05f,16f,1f,100))}
        assertTrue(d.update(.9f,16f,1f,100))
    }
    @Test fun narrowToneIsNotDilutedAcrossWideBand(){
        val bins=FloatArray(512);bins[3]=.7f
        assertTrue(AudioDynamics.bandEnergy(bins,40f,20f,250f,1f)>.5f)
        assertEquals(0f,AudioDynamics.bandEnergy(bins,40f,4000f,16000f,1f),.001f)
    }
    @Test fun enhancedEngineSeparatesBassAndTreble(){
        val engine=AudioProviderEngine(AudioProviderConfig(fftSize=2048,enhancedBands=true,adaptiveBeat=true,transientBeat=true))
        val samples=FloatArray(2048){(.7*sin(2*PI*110*it/44100)).toFloat()}
        var a=engine.process(samples,44100,16f,1,true,false)
        repeat(8){a=engine.process(samples,44100,16f,1,true,false)}
        assertTrue(a.bass>a.treble+.2f)
        assertTrue(a.bass in 0f..1f)
    }
}
