package com.vexo.studio

import com.vexo.studio.engine.scene.*
import com.vexo.studio.model.*
import org.junit.Assert.*
import org.junit.Test

class Phase57ParticleDynamicsTest {
    @Test fun rateChangesDoNotRetroactivelyCreateParticles(){
        val clock=ParticleEmissionClock()
        assertEquals(0,clock.advance(.09f,.1f))
        assertEquals(1,clock.advance(.001f,.01f))
        assertEquals(0,clock.advance(.001f,.01f))
        clock.reset()
        assertEquals(0,clock.advance(.001f,.01f))
    }
    @Test fun emissionIntegratesConsistentlyAcrossSubsteps(){
        val a=ParticleEmissionClock();val b=ParticleEmissionClock()
        var count=0
        repeat(8){count+=a.advance(.0078125f,.015625f)}
        assertEquals(b.advance(.0625f,.015625f),count)
    }
    @Test fun trailLengthFollowsMotionAndCanvasAspect(){
        assertEquals(1f,ParticleTrailMath.stretch(1f,0f,0f,1920,1080),.0001f)
        assertEquals(5f,ParticleTrailMath.stretch(1f,0f,.18f,1920,1080),.0001f)
        assertEquals(5f,ParticleTrailMath.stretch(1f,.18f,0f,1080,1920),.0001f)
        assertEquals(1f,ParticleTrailMath.stretch(0f,1f,1f,1920,1080),.0001f)
    }
    @Test fun projectedVelocityRespectsSpeedOverride(){
        val c=LayerConfig.Particles(countLimit=64,spawnTimeSec=.004f,perspectiveDepth=0f,
            speed=100f,speedRandom=0f,vectorAngleDeg=0f,directionSpread=0f)
        fun sample(speed:Float):ParticleSimulator {
            val s=ParticleSimulator(64)
            repeat(4){s.step(c,null,1f/30f,it/30f,it/30f,true,speedOverride=speed)}
            assertTrue(s.activeCount>0)
            return s
        }
        assertTrue(sample(0f).outVx[0]>.1f)
        assertEquals(0f,sample(-.4f).outVx[0],.0001f)
        assertTrue(sample(-.8f).outVx[0]<-.1f)
    }
}
