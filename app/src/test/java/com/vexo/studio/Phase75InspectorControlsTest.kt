package com.vexo.studio

import com.vexo.studio.engine.scene.LegacyGlowMath
import com.vexo.studio.ui.screens.InspectorSearch
import org.junit.Assert.*
import org.junit.Test

class Phase75InspectorControlsTest{
    @Test fun editingOneGlowAxisPreservesTheOther(){
        val (scale,ratio)=LegacyGlowMath.editAxis(2f,1.5f,4f,true)
        val (x,y)=LegacyGlowMath.axes(scale,ratio)
        assertEquals(4f,x,.0001f)
        assertEquals(1f,y,.0001f)
        val (s,r)=LegacyGlowMath.editAxis(scale,ratio,.5f,false)
        val (nx,ny)=LegacyGlowMath.axes(s,r)
        assertEquals(4f,nx,.0001f)
        assertEquals(.5f,ny,.0001f)
    }
    @Test fun dormantGlowCanBeEditedAndReset(){
        val (s,r)=LegacyGlowMath.editAxis(0f,1f,2f,true)
        assertEquals(2f,LegacyGlowMath.axes(s,r).first,.0001f)
        assertEquals(0f,LegacyGlowMath.axes(s,r).second,.0001f)
        val (zero,balanced)=LegacyGlowMath.editAxis(s,r,0f,true)
        assertEquals(0f,zero,0f)
        assertEquals(1f,balanced,0f)
    }
    @Test fun addedControlsAreDiscoverable(){
        for((section,query) in listOf("Image animation" to "GIF Speed","Secondary segment" to "alternating direction","Motion & forces" to "frequency random","Size & fading" to "size aspect ratio","Blur parameters" to "glow 2 scale y","Channel offsets" to "rgb multiplier audio binding")){
            assertTrue("$section / $query",InspectorSearch.matches(section,query))
        }
    }
}
