package com.vexo.studio
import com.vexo.studio.model.*
import com.vexo.studio.engine.scene.SpectrumStudioMesh
import org.junit.Assert.*
import org.junit.Test
class SpectrumStudioMeshTest{
    @Test fun everyStyleAndPathProducesFiniteTriangles(){
        val bars=FloatArray(24){.04f+it*.012f}
        for(style in CreativeSpectrumStyle.entries)for(path in CreativeSpectrumPath.entries){
            var vertices=0
            SpectrumStudioMesh.emit(LayerConfig.Bars(creativeStyle=style,creativePath=path,showPeaks=false),bars,null,720,1280){x,y,_->assertTrue(x.isFinite()&&y.isFinite());vertices++}
            assertTrue("$style / $path",vertices>0&&vertices%3==0)
        }
    }
    @Test fun stylesHaveDifferentGeometry(){
        val bars=FloatArray(16){.15f+it*.01f}
        val fingerprints=CreativeSpectrumStyle.entries.map{style->
            val vertices=mutableListOf<Float>()
            SpectrumStudioMesh.emit(LayerConfig.Bars(creativeStyle=style,showPeaks=false,mirror=false),bars,null,1280,720){x,y,_->vertices.add(x);vertices.add(y)}
            vertices.hashCode()
        }.toSet()
        assertTrue(fingerprints.size>=24)
    }
    @Test fun fillLineAndPolygonAreDifferent(){
        val c=LayerConfig.Bars(creativeStyle=CreativeSpectrumStyle.FILL,creativePath=CreativeSpectrumPath.SQUARE,showPeaks=false)
        fun count(cfg:LayerConfig.Bars):Int{var n=0;SpectrumStudioMesh.emit(cfg,FloatArray(16){.2f},null,100,100){_,_,_->n++};return n}
        assertTrue(count(c.copy(mirror=false))>count(c.copy(mirror=false,creativeStyle=CreativeSpectrumStyle.LINE)))
        assertTrue(count(c)>count(c.copy(pathGap=.2f)))
    }
}
