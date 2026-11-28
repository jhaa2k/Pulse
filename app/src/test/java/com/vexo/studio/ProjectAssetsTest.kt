package com.vexo.studio
import com.vexo.studio.data.ProjectAssets
import com.vexo.studio.model.*
import org.junit.Assert.*
import org.junit.Test
class ProjectAssetsTest{
    @Test fun sharedUrisAreGroupedAndInternalReferencesIgnored(){
        val uri="content://test/image"
        val layers=listOf(
            LayerNode(LayerType.IMAGE,LayerCommon(name="Image"),LayerConfig.Image(sourceType=ImageSourceType.IMPORTED,customUri=uri,maskImage=uri)),
            LayerNode(LayerType.PARTICLES,LayerCommon(name="Particle"),LayerConfig.Particles(customImage="internalres:particle_circle_blur4")),
            LayerNode(LayerType.TEXT,LayerCommon(name="Font"),LayerConfig.Text(fontAssetUri="file:///font.ttf")))
        val assets=ProjectAssets.collect(VexoProject(audioUri="content://test/audio",compositions=listOf(CompositionNode(index=0,layers=layers))))
        assertEquals(3,assets.size)
        val a=assets.single{it.uri==uri}
        assertEquals(2,a.uses.size)
        assertEquals(setOf("Image","Image mask"),a.uses.map{it.role}.toSet())
    }
}
