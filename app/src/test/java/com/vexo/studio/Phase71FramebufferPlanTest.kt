package com.vexo.studio

import com.vexo.studio.engine.scene.SmoothBlurPlan
import com.vexo.studio.model.BlurQuality
import org.junit.Assert.*
import org.junit.Test

class Phase71FramebufferPlanTest {
    @Test fun radialRetainsOnlyItsRequiredTargets(){
        val p=SmoothBlurPlan.create(3840,2160,1920,1080,12f,1f,BlurQuality.HIGH,true)
        assertEquals(setOf("blur_scene_down_0","blur_scene_a"),p.scratchKeys("blur_scene",true))
    }
    @Test fun singleAxisDoesNotRetainUnusedSecondTarget(){
        val p=SmoothBlurPlan.create(1920,1080,1920,1080,2f,0f,BlurQuality.BALANCED)
        assertEquals(setOf("image_blur_main_a"),p.scratchKeys("image_blur_main",false))
    }
    @Test fun highQualityRepeatedSingleAxisNeedsPingPong(){
        val p=SmoothBlurPlan.create(1920,1080,1920,1080,16f,0f,BlurQuality.HIGH)
        val keys=p.scratchKeys("blur_scene",false)
        assertTrue("blur_scene_b" in keys)
        assertEquals(p.reductions.size+2,keys.size)
    }
    @Test fun broadBlurKeepsReconstructionLevels(){
        val p=SmoothBlurPlan.create(3840,2160,3840,2160,100f,1f,BlurQuality.HIGH)
        val keys=p.scratchKeys("blur_scene",false)
        assertTrue(p.reconstructionIndices(3840,2160,BlurQuality.HIGH).all{"blur_scene_down_$it" in keys})
        assertTrue("blur_scene_a" in keys&&"blur_scene_b" in keys)
    }
}
