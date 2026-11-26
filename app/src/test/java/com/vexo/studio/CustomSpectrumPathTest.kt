package com.vexo.studio
import com.vexo.studio.engine.scene.CustomSpectrumPath
import com.vexo.studio.engine.scene.SpectrumStudioMesh
import com.vexo.studio.model.*
import com.vexo.studio.data.NativeVexCodec
import org.junit.Assert.*
import org.junit.Test

class CustomSpectrumPathTest {
    @Test fun unequalSegmentsAreSampledByDistance() {
        val p=checkNotNull(CustomSpectrumPath.create(listOf(SpectrumPathPoint(-.5f,0f),SpectrumPathPoint(0f,0f),SpectrumPathPoint(.25f,0f)),false))
        assertEquals(-.125f,p.at(.5f).x,.0001f)
        assertEquals(.25f,p.at(1f).x,.0001f)
    }
    @Test fun closedPathReturnsToFirstAndRejectsDuplicates() {
        val a=SpectrumPathPoint(-.4f,-.3f)
        val p=checkNotNull(CustomSpectrumPath.create(listOf(a,SpectrumPathPoint(.4f,-.3f),SpectrumPathPoint(0f,.4f)),true))
        assertEquals(p.at(0f),p.at(1f))
        assertNull(CustomSpectrumPath.create(listOf(a,a),false))
    }
    @Test fun meshAndNativeRoundTripKeepCustomPath() {
        val cfg=LayerConfig.Bars(customPathEnabled=true,customPathClosed=true,customPathPoints=listOf(
            SpectrumPathPoint(-.3f,-.3f),SpectrumPathPoint(.3f,-.3f),SpectrumPathPoint(0f,.3f)),pathStretchX=1.3f)
        val vertices=mutableListOf<Float>()
        SpectrumStudioMesh.emit(cfg,FloatArray(64){.2f},null,720,1280){x,y,_->vertices.add(x);vertices.add(y)}
        assertTrue(vertices.isNotEmpty()&&vertices.all{it.isFinite()})
        val project=VexoProject(compositions=listOf(CompositionNode(index=0,layers=listOf(LayerNode(LayerType.BARS,LayerCommon(),cfg)))))
        val reopened=NativeVexCodec.decode(NativeVexCodec.encode(project))
        assertEquals(cfg,reopened.compositions.first().layers.first().config)
    }
}
