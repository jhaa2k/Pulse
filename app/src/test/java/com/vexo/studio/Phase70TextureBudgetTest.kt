package com.vexo.studio

import com.vexo.studio.engine.gl.TextureCacheBudget
import com.vexo.studio.engine.gl.TextureCacheBudget.Entry
import org.junit.Assert.*
import org.junit.Test

class Phase70TextureBudgetTest{
    @Test fun textLimitDoesNotEvictImagesOrNewestLabel(){
        val entries=listOf(Entry("file://cover",32),Entry("txt_1",1),Entry("file://background",32),Entry("txt_2",1),Entry("txt_3",1))
        assertEquals(setOf("txt_1","txt_2"),TextureCacheBudget.textEvictions(entries,1))
    }
    @Test fun totalBudgetPrioritizesTransientText(){
        val entries=listOf(Entry("file://cover",10),Entry("txt_old",2),Entry("txt_new",2),Entry("internalres:white",1))
        assertEquals(setOf("txt_old"),TextureCacheBudget.evictions(entries,13,48))
        assertEquals(setOf("txt_old","txt_new","file://cover"),TextureCacheBudget.evictions(entries,1,48))
    }
    @Test fun pinnedTexturesAreRetainedUnderPressure(){
        val entries=listOf(Entry("internalres:white",1),Entry("internalres:vignette80",1),Entry("internalres:particle_circle_blur4",1),Entry("file://cover",8))
        assertEquals(setOf("file://cover"),TextureCacheBudget.evictions(entries,0,0))
    }
    @Test fun entryCountLimitAndEmptyCache(){
        assertEquals(setOf("file://a"),TextureCacheBudget.evictions(listOf(Entry("file://a",1),Entry("file://b",1)),100,1))
        assertTrue(TextureCacheBudget.evictions(emptyList(),0,0).isEmpty())
    }
}
