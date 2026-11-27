package com.vexo.studio

import com.vexo.studio.engine.scene.SmoothBlurPlan
import com.vexo.studio.model.BlurQuality
import org.junit.Assert.*
import org.junit.Test

class Phase55BlurReconstructionTest {
    @Test fun largeBlurReusesPyramidInAscendingSizeOrder() {
        val p=SmoothBlurPlan.create(3840,2160,3840,2160,160f,1f,BlurQuality.HIGH)
        val indices=p.reconstructionIndices(3840,2160,BlurQuality.HIGH)
        assertTrue(indices.isNotEmpty())
        assertEquals((p.reductions.lastIndex-1 downTo 0).toList(),indices)
        assertTrue(indices.zipWithNext().all{(a,b)->p.reductions[a].first<=p.reductions[b].first})
        assertFalse(indices.contains(p.reductions.lastIndex))
    }

    @Test fun outputBudgetCapsReconstruction() {
        val p=SmoothBlurPlan.create(3840,2160,3840,2160,160f,1f,BlurQuality.BALANCED)
        val indices=p.reconstructionIndices(960,540,BlurQuality.BALANCED)
        assertTrue(indices.isNotEmpty())
        assertTrue(indices.all{maxOf(p.reductions[it].first,p.reductions[it].second)<=960})
    }

    @Test fun fastAndRadialAndSmallBlurSkipReconstruction() {
        val p=SmoothBlurPlan.create(1920,1080,1920,1080,100f,1f,BlurQuality.BALANCED)
        assertTrue(p.reconstructionIndices(1920,1080,BlurQuality.FAST).isEmpty())
        assertTrue(p.reconstructionIndices(1920,1080,BlurQuality.HIGH,true).isEmpty())
        val small=SmoothBlurPlan.create(1280,720,1280,720,1f,1f,BlurQuality.HIGH)
        assertTrue(small.reconstructionIndices(1280,720,BlurQuality.HIGH).isEmpty())
    }
}
