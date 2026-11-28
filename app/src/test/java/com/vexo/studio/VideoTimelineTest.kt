package com.vexo.studio
import com.vexo.studio.engine.gl.VideoTimeline
import org.junit.Assert.assertEquals
import org.junit.Test
class VideoTimelineTest {
    @Test fun loopsAndQuantizes() {
        assertEquals(500L,VideoTimeline.sampleTimeMs(2500,2000,1f,0,true,12))
        assertEquals(0L,VideoTimeline.sampleTimeMs(2000,2000,1f,0,true,12))
        assertEquals(1916L,VideoTimeline.sampleTimeMs(3000,2000,1f,0,false,12))
    }
    @Test fun speedAndOffset() {
        assertEquals(1500L,VideoTimeline.sampleTimeMs(500,3000,2f,500,true,12))
    }
}
