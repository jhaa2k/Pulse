package com.vexo.studio

import com.vexo.studio.engine.gl.SpectrumGeometry
import com.vexo.studio.engine.scene.SpectrumStudioMesh
import com.vexo.studio.model.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class Phase32RoundedGeometryTest{
    @Test fun largePillHasSmoothArcAndStaysWithinBounds(){
        val p=SpectrumGeometry.roundedContour(72f,196f,72f)
        assertTrue(p.size>48)
        assertTrue(p.all{abs(it.first)<=72.001f&&abs(it.second)<=196.001f})
        assertTrue(p.maxOf{it.second}>195.99f)
        for(i in p.indices){
            val a=p[i];val b=p[(i+1)%p.size];val c=p[(i+2)%p.size]
            val cross=(b.first-a.first)*(c.second-b.second)-(b.second-a.second)*(c.first-b.first)
            assertTrue("Nonconvex contour",cross>=-.05f)
        }
    }
    @Test fun shortPillBecomesACircleWithNoSquaredCorners(){
        val p=SpectrumGeometry.roundedContour(32f,32f,32f)
        assertTrue(p.size>=48)
        assertTrue(p.all{abs(sqrt(it.first*it.first+it.second*it.second)-32f)<.001f})
        assertTrue(p.zipWithNext().all{(a,b)->a!=b})
    }
    @Test fun roundFanFitsAllocatedCapacity(){
        val v=SpectrumGeometry.roundedQuad(-800f,-1200f,800f,-1200f,-800f,1200f,800f,1200f,1f)
        assertTrue(v.size/2<=SpectrumGeometry.MAX_ROUNDED_VERTICES)
        assertEquals(0,v.size%6);assertTrue(v.all{it.isFinite()})
        assertTrue(v.size>96)
    }
    @Test fun outlinedCapsuleRemainsFinite(){
        val cfg=LayerConfig.Bars(creativeStyle=CreativeSpectrumStyle.CAPSULE,barStrokeWidth=3f,barFillOpacity=0f,barStrokeColorArgb=0xFF112233.toInt(),mirror=true)
        var n=0
        SpectrumStudioMesh.emit(cfg,floatArrayOf(.7f,.4f,.9f),null,1536,832){x,y,c->
            assertTrue(x.isFinite()&&y.isFinite());assertEquals(cfg.barStrokeColorArgb,c);n++
        }
        assertTrue(n>100);assertEquals(0,n%3)
    }
}
