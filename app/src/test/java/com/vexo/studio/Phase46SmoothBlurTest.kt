package com.vexo.studio
import com.vexo.studio.engine.scene.SmoothBlurPlan
import com.vexo.studio.model.BlurQuality
import org.junit.Assert.*
import org.junit.Test
class Phase46SmoothBlurTest{
    @Test fun largeBlurUsesProgressiveReductionAndDenseTaps(){
        val p=SmoothBlurPlan.create(3840,2160,3840,2160,160f,1f,BlurQuality.BALANCED)
        assertTrue(p.reductions.size>1)
        assertEquals(1920 to 1080,p.reductions.first())
        assertTrue(p.sigmaX<=3.75f);assertTrue(p.sigmaY<=3.75f)
        assertEquals(p.width to p.height,p.reductions.last())
    }
    @Test fun smallBlurKeepsFullResolution(){
        val p=SmoothBlurPlan.create(1280,720,1280,720,1f,1f,BlurQuality.BALANCED)
        assertTrue(p.reductions.isEmpty());assertEquals(1f,p.sigmaX,.001f)
    }
    @Test fun directionalBlurAndQualityModes(){
        val b=SmoothBlurPlan.create(1920,1080,1920,1080,24f,0f,BlurQuality.BALANCED)
        val h=SmoothBlurPlan.create(1920,1080,1920,1080,24f,0f,BlurQuality.HIGH)
        assertEquals(0f,b.sigmaY,.001f);assertEquals(2,h.rounds);assertTrue(h.width>=b.width)
    }
}
