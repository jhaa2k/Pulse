package com.vexo.studio

import com.vexo.studio.export.ExportPcmTimeline
import com.vexo.studio.export.ExportPcmTimeline.Placement
import com.vexo.studio.export.ExportClock
import com.vexo.studio.model.VideoResolution
import org.junit.Assert.*
import org.junit.Test

class Phase68ExportTimelineTest {
    @Test fun trimsLeadingSamplesAndClipsFinalBuffer(){
        assertEquals(Placement(480,480,0),ExportPcmTimeline.place(0,10000,48000,960,0,480))
        assertEquals(Placement(0,80,0),ExportPcmTimeline.place(20000,0,48000,960,960,1040))
    }
    @Test fun preservesSilenceGapsAndDropsOverlaps(){
        assertEquals(Placement(0,480,480),ExportPcmTimeline.place(20000,0,48000,480,480,4800))
        assertEquals(Placement(240,240,0),ExportPcmTimeline.place(5000,0,48000,480,480,4800))
    }
    @Test fun ignoresOneSampleTimestampJitter(){
        assertEquals(Placement(0,480,0),ExportPcmTimeline.place(10021,0,48000,480,480,4800))
        assertEquals(Placement(0,480,0),ExportPcmTimeline.place(9979,0,48000,480,480,4800))
    }
    @Test fun outOfRangeBuffersDoNotPadAndNewFormatsUseIntegerClock(){
        assertEquals(Placement(0,0,0),ExportPcmTimeline.place(2000000,0,48000,480,0,48000))
        assertEquals(480,VideoResolution.SD_480.shortSide)
        assertEquals(50,ExportClock.frameCount(1_000_000,50))
        assertEquals(44100L,ExportPcmTimeline.durationFrames(1_000_000,44100))
    }
}
