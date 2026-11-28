package com.vexo.studio

import com.vexo.studio.data.AveeJsonCodec
import com.vexo.studio.data.NativeVexCodec
import com.vexo.studio.engine.scene.LegacySpriteProfile
import com.vexo.studio.engine.scene.ParticleSimulator
import com.vexo.studio.model.LayerConfig
import org.junit.Assert.*
import org.junit.Test

class Phase61LegacyParticleTest {
    @Test fun invertedMaskIsOppositeOfNormalSprite(){
        assertEquals(0f,LegacySpriteProfile.alpha("internalres:particle_blur_inv",0f),0f)
        assertEquals(1f,LegacySpriteProfile.alpha("internalres:particle_blur_inv",1f),0f)
        assertEquals(1f,LegacySpriteProfile.alpha("internalres:particle_circle_blur4",0f),0f)
        assertEquals(0f,LegacySpriteProfile.alpha("internalres:particle_circle_blur4",1f),.0001f)
    }
    @Test fun sharpAndSoftSpritesAreDistinct(){
        val sharp=LegacySpriteProfile.alpha("internalres:particle_circle_w_a_64",.5f)
        val soft=LegacySpriteProfile.alpha("internalres:particle_blur01",.5f)
        val softer=LegacySpriteProfile.alpha("internalres:particle_blur01_more",.5f)
        assertTrue(sharp>soft&&soft>softer)
    }
    @Test fun importedParticleParametersSurviveNativeAndAveeRoundTrips(){
        val p=AveeJsonCodec.importFromText("""{"compositions":[{"elements":[{"objType":"Particles","sideSineWaveFreqRandom":{"v":-0.5},"sideSineWaveMagRandom":{"v":4},"sizeAspectRatio":{"v":2},"mirrorX":{"v":1},"fadeInSize":{"v":7}}]}]}""")
        fun cfg(project:com.vexo.studio.model.VexoProject)=project.compositions[0].layers[0].config as LayerConfig.Particles
        val c=cfg(p)
        assertEquals(-.5f,c.sideSineWaveFreqRandom,0f)
        assertEquals(4f,c.sideSineWaveMagRandom,0f)
        assertEquals(2f,c.sizeAspectRatio,0f)
        assertTrue(c.mirrorX)
        assertEquals(7f,c.fadeInSize,0f)
        assertEquals(c,cfg(NativeVexCodec.decode(NativeVexCodec.encode(p))))
        val a=cfg(AveeJsonCodec.importFromText(AveeJsonCodec.exportAveeScene(p)))
        assertEquals(c.sideSineWaveMagRandom,a.sideSineWaveMagRandom,0f)
        assertEquals(c.sizeAspectRatio,a.sizeAspectRatio,0f)
    }
    @Test fun randomizedMotionIsDeterministic(){
        val cfg=LayerConfig.Particles(countLimit=64,spawnTimeSec=.01f,perspectiveDepth=0f,
            sideSineWaveFreqRandom=-.5f,sideSineWaveMagRandom=4f)
        val a=ParticleSimulator(64);val b=ParticleSimulator(64)
        repeat(20){a.step(cfg,null,1f/30f,it/30f,it/30f,true);b.step(cfg,null,1f/30f,it/30f,it/30f,true)}
        assertTrue(a.activeCount>0)
        assertEquals(a.activeCount,b.activeCount)
        assertArrayEquals(a.outX.copyOf(a.activeCount),b.outX.copyOf(b.activeCount),.00001f)
    }
}
