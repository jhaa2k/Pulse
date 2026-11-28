package com.vexo.studio
import com.vexo.studio.engine.scene.*
import com.vexo.studio.model.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*
class Phase47EdgeQualityTest{
    @Test fun straightStrokeAndCornerHaveSharedOffset(){
        val a=WaveStrokeJoin.offset(0f,0f,1f,0f,2f,0f,2f)
        assertEquals(0f,a.first,.001f);assertEquals(-2f,a.second,.001f)
        val corner=WaveStrokeJoin.offset(0f,0f,1f,0f,1f,1f,2f)
        assertEquals(2f,corner.first,.001f);assertEquals(-2f,corner.second,.001f)
    }
    @Test fun acuteAndDegenerateJoinsStayBounded(){
        for(p in listOf(0f to 0f,.001f to .01f,1f to 0f,Float.NaN to 0f)){
            val v=WaveStrokeJoin.offset(0f,0f,1f,0f,p.first,p.second,2f)
            assertTrue(v.first.isFinite()&&v.second.isFinite())
            assertTrue(hypot(v.first,v.second)<=4.001f)
        }
    }
    @Test fun onlyVisibleGeometryTriggersOffscreenResolve(){
        val bar=LayerNode(LayerType.BARS,LayerCommon(),LayerConfig.Bars())
        assertTrue(OffscreenEdges.hasGeometry(CompositionNode(index=1,layers=listOf(bar))))
        assertFalse(OffscreenEdges.hasGeometry(CompositionNode(index=1,layers=listOf(bar.copy(common=bar.common.copy(visible=false))))))
        assertFalse(OffscreenEdges.hasGeometry(CompositionNode(index=1,layers=emptyList())))
    }
}
