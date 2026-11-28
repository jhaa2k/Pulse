package com.vexo.studio

import com.vexo.studio.data.AveeJsonCodec
import com.vexo.studio.data.LegacyAssetReferences
import com.vexo.studio.engine.gl.ImageAnimationClock
import com.vexo.studio.model.*
import org.junit.Assert.*
import org.junit.Test

class Phase73ImageAnimationTest{
    @Test fun gifHeaderAndBounds(){
        val h="GIF89a".toByteArray()+byteArrayOf(0,2,0,1)
        assertEquals("gif",LegacyAssetReferences.imageExtension(h))
        assertEquals(512 to 256,ImageAnimationClock.gifSize(h))
        assertNull(ImageAnimationClock.gifSize("GIF89a".toByteArray()+byteArrayOf(0,16,0,1)))
    }
    @Test fun clockLoopsAndSupportsTimeAndBeat(){
        assertEquals(250,ImageAnimationClock.loopTime(1250,1000))
        assertEquals(2000L,ImageAnimationClock.position(1000,2f,0f,MeasureWhat.NOTHING))
        assertEquals(750L,ImageAnimationClock.position(1000,1f,.75f,MeasureWhat.TOTAL_TIME_AND_BEAT))
        assertEquals(1250L,ImageAnimationClock.position(1000,1f,.25f,MeasureWhat.BEAT))
    }
    @Test fun legacyAnimationMeasureSurvivesExport(){
        val p=AveeJsonCodec.importFromText("""{"compositions":[{"elements":[{"objType":"Image","measureAnimationSpeed":{"t":"_child","measureWhat":{"v":"TotalTimeAndBeat"},"A":{"v":0.5},"B":{"v":0.5}}}]}]}""")
        fun image(project:VexoProject)=project.compositions[0].layers[0].config as LayerConfig.Image
        val c=image(p)
        assertEquals(MeasureWhat.TOTAL_TIME_AND_BEAT,c.animationSpeedMeasure.measureWhat)
        assertEquals(c.animationSpeedMeasure,image(AveeJsonCodec.importFromText(AveeJsonCodec.exportAveeScene(p))).animationSpeedMeasure)
    }
}
