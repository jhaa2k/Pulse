package com.vexo.studio
import com.vexo.studio.export.ExportFileName
import org.junit.Assert.*
import org.junit.Test

class ExportFileNameTest {
    @Test fun defaultUsesReadableDateTime(){
        assertTrue(Regex("VEXO_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}\\.mp4").matches(ExportFileName.defaultName(0L)))
    }
    @Test fun customNameDoesNotGetTimestamp(){
        assertEquals("My New Video.mp4",ExportFileName.normalize("My New Video"))
        assertEquals("My New Video.mp4",ExportFileName.normalize("My New Video.MP4"))
        assertEquals("ଓଡ଼ିଆ Music.mp4",ExportFileName.normalize("ଓଡ଼ିଆ Music"))
    }
    @Test fun pathAndEmptyNamesAreHandled(){
        assertFalse(ExportFileName.normalize("../clip").contains('/'))
        assertEquals(ExportFileName.defaultName(0L),ExportFileName.normalize("  .mp4",0L))
        assertTrue(ExportFileName.normalize("🎵".repeat(200)).toByteArray().size<=204)
    }
}
