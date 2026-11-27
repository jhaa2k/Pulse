package com.vexo.studio
import com.vexo.studio.engine.scene.*
import com.vexo.studio.model.*
import org.junit.Assert.*
import org.junit.Test
class Phase49GalleryTest{
    @Test fun catalogFiltersAndSamplesAreUseful(){
        val projects=StudioSceneCatalog.all()
        assertEquals(64,projects.size)
        assertTrue(projects.all{PresetPreviewData.matches(it,"",PresetCategory.ALL)})
        assertTrue(projects.any{PresetPreviewData.matches(it,"",PresetCategory.MOTION)})
        assertTrue(projects.any{PresetPreviewData.matches(it,"",PresetCategory.PARTICLES)})
        assertFalse(PresetPreviewData.matches(projects.first(),"zzzz-no-match",PresetCategory.ALL))
        val a=PresetPreviewData.sample(1,96,.1f)
        val b=PresetPreviewData.sample(1,96,.3f)
        assertEquals(96,a.bars.size)
        assertFalse(a.bars.contentEquals(b.bars))
        assertTrue(a.bars.all{it.isFinite()&&it>=0f})
    }
}
