package com.vexo.studio

import com.vexo.studio.export.ExportEncoderPolicy
import com.vexo.studio.model.AspectRatioPreset
import com.vexo.studio.model.VideoResolution
import org.junit.Assert.*
import org.junit.Test

class Phase78ExportGeometryTest{
    @Test fun nearestEvenDimensions(){
        assertEquals(854 to 480,ExportEncoderPolicy.dimensions(16,9,480))
        assertEquals(480 to 854,ExportEncoderPolicy.dimensions(9,16,480))
        assertEquals(1920 to 1080,ExportEncoderPolicy.dimensions(16,9,1080))
        assertEquals(3840 to 2160,ExportEncoderPolicy.dimensions(16,9,2160))
        assertEquals(1080 to 1350,ExportEncoderPolicy.dimensions(4,5,1080))
    }
    @Test fun allResolutionAndAspectCombinationsRetainShortSide(){
        for(r in VideoResolution.entries)for(a in AspectRatioPreset.entries){
            val (w,h)=ExportEncoderPolicy.dimensions(a.widthRatio,a.heightRatio,r.shortSide)
            assertEquals(r.shortSide,minOf(w,h))
            assertEquals(0,w%2);assertEquals(0,h%2)
            assertTrue(kotlin.math.abs(w.toDouble()/h-a.widthRatio.toDouble()/a.heightRatio)<.004)
        }
    }
    @Test fun eglFallbackPreferences(){
        assertEquals(listOf(4,2,0),ExportEncoderPolicy.samplePreferences(true))
        assertEquals(listOf(0),ExportEncoderPolicy.samplePreferences(false))
    }
}
