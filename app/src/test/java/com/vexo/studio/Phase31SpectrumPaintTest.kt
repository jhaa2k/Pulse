package com.vexo.studio

import com.vexo.studio.engine.scene.SpectrumStudioMesh
import com.vexo.studio.model.*
import org.junit.Assert.*
import org.junit.Test

class Phase31SpectrumPaintTest {
    data class Vertex(val x:Float,val y:Float,val color:Int)
    private fun mesh(c:LayerConfig.Bars):List<Vertex>{
        val v=ArrayList<Vertex>()
        SpectrumStudioMesh.emit(c,floatArrayOf(.2f,.65f,.35f,.85f),null,1280,720){x,y,col->v.add(Vertex(x,y,col))}
        return v
    }
    @Test fun hollowBarUsesOnlyStrokeColor(){
        val red=0xFFFF0000.toInt()
        val base=LayerConfig.Bars(mirror=false,barFillOpacity=0f,barStrokeWidth=3f,barStrokeColorArgb=red)
        val outline=mesh(base)
        assertTrue(outline.isNotEmpty())
        assertTrue(outline.all{it.color==red&&it.x.isFinite()&&it.y.isFinite()})
        assertTrue(mesh(base.copy(barStrokeWidth=0f)).isEmpty())
        assertTrue(mesh(base.copy(barFillOpacity=1f)).size>outline.size)
    }
    @Test fun strokeSupportsFilledShapesAndHeightGradient(){
        for(style in listOf(CreativeSpectrumStyle.BARS,CreativeSpectrumStyle.CAPSULE,CreativeSpectrumStyle.DOTS,CreativeSpectrumStyle.TRIANGLE,CreativeSpectrumStyle.DIAMOND,CreativeSpectrumStyle.TAPERED)){
            val c=LayerConfig.Bars(creativeStyle=style,barStrokeWidth=2f,barStrokeColorArgb=0xFF123456.toInt(),barGradientMode=SpectrumGradientMode.HEIGHT)
            val v=mesh(c)
            assertTrue(style.name,v.any{it.color==c.barStrokeColorArgb})
            assertTrue(style.name,v.all{it.x.isFinite()&&it.y.isFinite()})
            assertEquals(0,v.size%3)
        }
    }
    @Test fun wavesHaveSmoothFillRibbonAndDashes(){
        for(style in listOf(CreativeSpectrumStyle.LINE,CreativeSpectrumStyle.FILL,CreativeSpectrumStyle.RIBBON,CreativeSpectrumStyle.DOUBLE_LINE)){
            val c=LayerConfig.Bars(creativeStyle=style,mirror=false)
            assertTrue(style.name,mesh(c.copy(curveSubdivisions=5)).size>mesh(c).size)
        }
        val c=LayerConfig.Bars(creativeStyle=CreativeSpectrumStyle.LINE,mirror=false,curveSubdivisions=4)
        assertNotEquals(mesh(c),mesh(c.copy(waveDashRatio=.4f)))
        assertNotEquals(mesh(c),mesh(c.copy(waveCurveTension=.9f)))
        val ribbon=c.copy(creativeStyle=CreativeSpectrumStyle.RIBBON)
        assertNotEquals(mesh(ribbon),mesh(ribbon.copy(waveRibbonWidth=3f)))
    }
}
