package com.vexo.studio
import com.vexo.studio.engine.scene.SpectrumStudioMesh
import com.vexo.studio.engine.scene.SpectrumStyleLibrary
import com.vexo.studio.model.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sin

class Phase29SpectrumTest {
    private fun mesh(cfg:LayerConfig.Bars, values:FloatArray, width:Int=720,height:Int=1280):List<Triple<Float,Float,Int>> {
        val vertices=ArrayList<Triple<Float,Float,Int>>()
        SpectrumStudioMesh.emit(cfg,values,null,width,height){x,y,color->vertices.add(Triple(x,y,color))}
        return vertices
    }
    @Test fun allRecipesEmitFiniteTrianglesInBothOrientations() {
        for(recipe in SpectrumStyleLibrary.recipes) {
            val cfg=recipe.apply(LayerConfig.Bars())
            val samples=FloatArray(cfg.barCount){i->if(cfg.signedWaveform)sin(i*.3f)*.65f else .25f+.2f*sin(i*.3f)}
            for((w,h) in listOf(1280 to 720,720 to 1280,720 to 720)) {
                val m=mesh(cfg,samples,w,h)
                assertTrue(recipe.name,m.isNotEmpty())
                assertEquals(recipe.name,0,m.size%3)
                assertTrue(recipe.name,m.all{it.first.isFinite()&&it.second.isFinite()})
            }
        }
    }
    @Test fun legacyAlternateNestedNamesAndNumericMeasureImport() {
        val project=com.vexo.studio.data.AveeJsonCodec.importFromText("""{"compositions":[{"elements":[{"objType":"Bars","shapepath":{"v":"Circle","radius":{"v":2}},"segment1":{"v":"Line","barWidth":{"v":0.4}},"colorBlendOffset":{"v":0.25}}]}]}""")
        val cfg=project.compositions.first().layers.first().config as LayerConfig.Bars
        assertEquals(BarsSegmentType.LINE,cfg.segment1)
        assertEquals(BarsShapePath.CIRCLE,cfg.shapePath)
        assertEquals(.56f,cfg.circleRadius,.001f)
        assertEquals(.4f,cfg.barWidth,.001f)
        assertEquals(.25f,cfg.colorBlendOffset,.001f)
        assertEquals(0xFFFFFFFF.toInt(),com.vexo.studio.data.AveeJsonCodec.parseHsla4fToArgb("NaN 1 1 1"))
    }
    @Test fun signedWaveformCrossesBaselineAndSmoothCurvesHaveMoreVertices() {
        val c=LayerConfig.Bars(signedWaveform=true,creativeStyle=CreativeSpectrumStyle.LINE,mirror=false,minHeightScale=0f)
        val samples=floatArrayOf(-.6f,.5f,-.3f,.7f)
        val straight=mesh(c,samples)
        assertTrue(straight.any{it.second<640f})
        assertTrue(straight.any{it.second>640f})
        assertTrue(mesh(c.copy(curveSubdivisions=4),samples).size>straight.size)
        assertNotEquals(straight.map{it.third},mesh(c.copy(gradientRepeats=3,colorToArgb=0xFF00FF00.toInt()),samples).map{it.third})
    }
}
