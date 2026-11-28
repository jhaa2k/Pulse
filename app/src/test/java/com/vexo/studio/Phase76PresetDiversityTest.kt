package com.vexo.studio

import com.vexo.studio.model.*
import com.vexo.studio.engine.scene.*
import com.vexo.studio.data.NativeVexCodec
import org.junit.Assert.*
import org.junit.Test

class Phase76PresetDiversityTest {
    @Test fun all64HaveDifferentSpectrumStructureAndValidSceneGraph(){
        val all=StudioSceneCatalog.all()
        assertEquals(64,all.size)
        assertEquals(64,all.map(PresetDiversity::spectrumSignature).distinct().size)
        all.forEach{p->
            assertFalse(p.name,SceneGraph.resolve(p).hasCycle)
            assertTrue(p.name,SceneGraph.missingReferences(p).isEmpty())
            assertTrue(p.name,PresetDiversity.spectrumSignature(p).isNotEmpty())
            assertEquals(p,NativeVexCodec.decode(NativeVexCodec.encode(p)))
        }
    }
    @Test fun signatureIgnoresRecoloring(){
        val p=StudioSceneCatalog.all().first()
        val q=p.copy(name="recolored",compositions=p.compositions.map{comp->comp.copy(layers=comp.layers.map{l->
            val b=l.config as? LayerConfig.Bars
            if(b==null)l else l.copy(config=b.copy(colorFromArgb=0xFF000000.toInt(),colorToArgb=-1))
        })})
        assertEquals(PresetDiversity.spectrumSignature(p),PresetDiversity.spectrumSignature(q))
    }
    @Test fun stageAndMotionAreVaried(){
        val all=StudioSceneCatalog.all()
        val configs=all.flatMap{it.compositions}.flatMap{it.layers}.map{it.config}
        assertTrue(configs.filterIsInstance<LayerConfig.Image>().map{it.creative.pattern}.distinct().size>=5)
        assertTrue(configs.filterIsInstance<LayerConfig.Text>().map{it.animation}.distinct().size>=5)
        assertTrue(all.map{it.audioProviders.first().attackMs to it.audioProviders.first().releaseMs}.distinct().size>=7)
        assertTrue(all.any{PresetDiversity.spectrumSignature(it).size==3})
    }
}
