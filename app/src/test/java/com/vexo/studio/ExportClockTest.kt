package com.vexo.studio

import com.vexo.studio.export.ExportClock
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportClockTest {
    @Test fun exactOneHourAt60Fps() {
        assertEquals(3_600_000_000_000L, ExportClock.frameTimeNs(216_000L, 60))
        assertEquals(172_800_000L, ExportClock.sampleAtFrame(216_000L, 48_000, 60))
        assertEquals(3_600_000_000L, ExportClock.sampleTimeUs(172_800_000L, 48_000))
    }
    @Test fun frameCountRoundsUpAndNeverZero() {
        assertEquals(31, ExportClock.frameCount(1_000_001, 30))
        assertEquals(30, ExportClock.frameCount(1_000_000, 30))
        assertEquals(1, ExportClock.frameCount(1, 60))
    }
    @Test fun stereoSampleTimeUsesFramesNotBytes() {
        assertEquals(1_000_000L, ExportClock.sampleTimeUs(44_100, 44_100))
        assertEquals(1470L, ExportClock.sampleAtFrame(1, 44_100, 30))
    }
}
