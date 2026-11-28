package com.vexo.studio

import com.vexo.studio.export.ExportEncoderPolicy
import org.junit.Assert.*
import org.junit.Test

class HighQualityExportPolicyTest{
    @Test fun supportedFpsSelectionNeverInventsRates(){
        assertEquals(60,ExportEncoderPolicy.preferredRate(listOf(24,30,60),60))
        assertEquals(50,ExportEncoderPolicy.preferredRate(listOf(24,30,50),60))
        assertEquals(30,ExportEncoderPolicy.preferredRate(listOf(24,30),60))
        assertNull(ExportEncoderPolicy.preferredRate(emptyList(),60))
    }
    @Test fun heavySurfaceAvoidsMultisampleAllocation(){
        assertEquals(listOf(0),ExportEncoderPolicy.surfaceSamples(true,3840,2160,60))
        assertEquals(listOf(0),ExportEncoderPolicy.surfaceSamples(true,2560,1440,30))
        assertEquals(listOf(0),ExportEncoderPolicy.surfaceSamples(true,1920,1080,50))
        assertEquals(listOf(4,2,0),ExportEncoderPolicy.surfaceSamples(true,1920,1080,30))
    }
    @Test fun highResolutionDoesNotChangeRequestedGeometry(){
        assertEquals(2560 to 1440,ExportEncoderPolicy.dimensions(16,9,1440))
        assertEquals(3840 to 2160,ExportEncoderPolicy.dimensions(16,9,2160))
        assertEquals(2160 to 3840,ExportEncoderPolicy.dimensions(9,16,2160))
    }
}
