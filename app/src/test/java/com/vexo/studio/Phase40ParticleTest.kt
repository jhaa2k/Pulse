package com.vexo.studio
import com.vexo.studio.engine.scene.*
import com.vexo.studio.model.*
import org.junit.Assert.*
import org.junit.Test

class Phase40ParticleTest {
    @Test fun burstGateDoesNotSpamAndRearms(){
        val g=ParticleBurstGate()
        assertTrue(g.update(.9f,.6f,80,.01f,true))
        assertFalse(g.update(.9f,.6f,80,.10f,true))
        assertFalse(g.update(.2f,.6f,80,.01f,true))
        assertTrue(g.update(.9f,.6f,80,.01f,true))
        assertFalse(g.update(.2f,.6f,80,.01f,false))
    }
    @Test fun fixedStepsMatchAcrossFrameRates(){
        val c=LayerConfig.Particles(countLimit=64,spawnTimeSec=.025f,perspectiveDepth=0f,seed=123,turbulence=.5f,centerForce=.4f)
        val a=ParticleSimulator(64);val b=ParticleSimulator(64)
        repeat(30){a.step(c,null,1f/30f,(it+1)/30f,(it+1)/30f,true)}
        repeat(60){b.step(c,null,1f/60f,(it+1)/60f,(it+1)/60f,true)}
        assertTrue(a.activeCount>0)
        assertEquals(a.activeCount,b.activeCount)
        for(i in 0 until a.activeCount){
            assertEquals(a.outX[i],b.outX[i],.0001f);assertEquals(a.outY[i],b.outY[i],.0001f)
            assertTrue(a.outAlpha[i] in 0f..1f)
        }
    }
    @Test fun zeroSizeDoesNotGetForcedToVisibleMinimum(){
        val c=LayerConfig.Particles(countLimit=64,spawnTimeSec=.005f,particleScale=0f,perspectiveDepth=0f)
        val a=ParticleSimulator(64)
        repeat(10){a.step(c,null,1f/30f,it/30f,it/30f,true)}
        assertTrue(a.activeCount>0)
        assertTrue((0 until a.activeCount).all{a.outSize[it]==0f})
    }
}
