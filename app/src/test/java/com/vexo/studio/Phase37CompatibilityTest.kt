package com.vexo.studio
import com.vexo.studio.data.*
import com.vexo.studio.model.*
import com.vexo.studio.engine.scene.SceneGraph
import org.junit.Assert.*
import org.junit.Test
class Phase37CompatibilityTest {
    @Test fun extensionlessImagesAndVectorGlow(){
        assertTrue(LegacyAssetReferences.isNumericAsset("1000022703"))
        assertEquals("png",LegacyAssetReferences.imageExtension(byteArrayOf(137.toByte(),80,78,71,13,10,26,10)))
        assertEquals("jpg",LegacyAssetReferences.imageExtension(byteArrayOf(255.toByte(),216.toByte(),255.toByte())))
        assertNull(LegacyAssetReferences.imageExtension(byteArrayOf(1,2,3)))
        val p=AveeJsonCodec.importFromText("""{"compositions":[{"elements":[{"objType":"BlurEffect","1layerScale":{"v":"1.000000 1.000000"},"2layerScale":{"v":"0.000000 0.000000"},"3layerScale":{"v":"1.2 1.4"}}]}]}""")
        val b=p.compositions.first().layers.first().config as LayerConfig.BlurEffect
        assertEquals(1f,b.layer1Scale,.001f);assertEquals(0f,b.layer2Scale,.001f);assertEquals(1.3f,b.layer3Scale,.001f)
    }
    @Test fun creativeCatalogUsesDistinctGeometryAndValidCompositionGraphs(){
        val all=StudioSceneCatalog.all()
        assertEquals(64,all.size)
        assertEquals(64,all.map{it.name}.distinct().size)
        val signatures=all.map{p->p.compositions[1].layers.map{val b=it.config as LayerConfig.Bars;listOf(b.creativePath,b.creativeStyle,b.barCount,it.common.posX,it.common.posY,it.common.rotationDeg)}}
        assertEquals(64,signatures.distinct().size)
        assertTrue(all.all{!SceneGraph.resolve(it).hasCycle&&SceneGraph.missingReferences(it).isEmpty()})
        assertTrue(all.all{it.audioProviders.size==2})
    }
}
