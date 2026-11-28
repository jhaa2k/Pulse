package com.vexo.studio

import com.vexo.studio.data.AveeJsonCodec
import com.vexo.studio.data.NativeVexCodec
import com.vexo.studio.engine.scene.LegacySpectrumMath
import com.vexo.studio.model.LayerConfig
import org.junit.Assert.*
import org.junit.Test

class Phase77ShapeWidthTest{
    @Test fun independentSegmentWidthFlagsRoundTrip(){
        val p=AveeJsonCodec.importFromText("""{"compositions":[{"elements":[{"objType":"Bars","Segment1":{"v":"Bars","barWidthAffectedByShape":{"v":1}},"Segment2":{"v":"Line","barWidthAffectedByShape":{"v":0}}}]}]}""")
        val b=p.compositions[0].layers[0].config as LayerConfig.Bars
        assertEquals(true,b.barWidthAffectedByShape)
        assertEquals(false,b.segment2WidthAffectedByShape)
        assertEquals(b,AveeJsonCodec.importFromText(AveeJsonCodec.exportAveeScene(p)).compositions[0].layers[0].config)
        assertEquals(p,NativeVexCodec.decode(NativeVexCodec.encode(p)))
    }
    @Test fun absentFlagPreservesNativeWidth(){
        assertEquals(12f,LegacySpectrumMath.slotWidth(12f,720f,100,null),0f)
        assertEquals(12f,LegacySpectrumMath.slotWidth(12f,720f,100,true),0f)
        assertEquals(7.2f,LegacySpectrumMath.slotWidth(12f,720f,100,false),.0001f)
    }
    @Test fun polygonEdgeSpacingChangesNearCorner(){
        val step=LegacySpectrumMath.arcStep(100,0f)
        val edge=LegacySpectrumMath.polygonSpacing(100f,kotlin.math.PI.toFloat()/4f,4,step)
        val corner=LegacySpectrumMath.polygonSpacing(100f,0f,4,step)
        assertTrue(corner>edge)
        assertTrue(edge.isFinite())
    }
}
