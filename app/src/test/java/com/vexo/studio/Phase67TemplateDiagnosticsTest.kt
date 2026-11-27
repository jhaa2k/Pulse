package com.vexo.studio

import com.vexo.studio.data.*
import com.vexo.studio.model.*
import org.junit.Assert.*
import org.junit.Test

class Phase67TemplateDiagnosticsTest {
    private fun image(name:String,index:Int,visible:Boolean=true)=LayerNode(
        LayerType.IMAGE,LayerCommon(name=name,visible=visible),
        LayerConfig.Image(sourceType=ImageSourceType.COMPOSITION,sourceCompositionIndex=index))

    @Test fun reportsActiveAndDraftMissingReferencesWithoutChangingScene(){
        val p=VexoProject(compositions=listOf(
            CompositionNode(index=0,layers=listOf(image("Final reference",9))),
            CompositionNode(index=1,layers=listOf(image("Draft reference",8),image("Hidden reference",7,false)))))
        val r=TemplateDiagnostics.inspect(p)
        assertEquals(3,r.issues.count{it.code=="MISSING_COMPOSITION"})
        assertEquals(1,r.issues.count{it.active})
        assertEquals(2,r.issues.count{!it.active})
        assertEquals(3,p.compositions.sumOf{it.layers.size})
    }
    @Test fun missingProvidersAndUnsupportedElementsAreReported(){
        val p=VexoProject(compositions=listOf(CompositionNode(index=0,
            layers=listOf(LayerNode(LayerType.BARS,LayerCommon(name="Spectrum"),LayerConfig.Bars(audioProviderIndex=7))),
            preservedElements=mapOf(2 to """{"objType":"Unknown"}"""))))
        val r=TemplateDiagnostics.inspect(p)
        assertEquals(1,r.unsupportedElements)
        assertTrue(r.issues.any{it.code=="MISSING_PROVIDER"&&it.active})
        assertTrue(r.banner(p).contains("unsupported elements preserved"))
    }
    @Test fun activeCycleAndInvalidMaskAreReported(){
        val p=VexoProject(compositions=listOf(CompositionNode(index=0,layers=listOf(
            image("Self",0),LayerNode(LayerType.IMAGE,LayerCommon(),LayerConfig.Image(maskImage="composition:bad"))))))
        val r=TemplateDiagnostics.inspect(p)
        assertTrue(r.issues.any{it.code=="CYCLE"&&it.active})
        assertTrue(r.issues.any{it.code=="INVALID_REFERENCE"})
    }
    @Test fun plainLegacyFilesCannotSilentlyLoseBundledAssets(){
        val p=AveeJsonCodec.importFromText("""{"compositions":[{"elements":[{"objType":"Image","customImage":{"v":"local:123"}}]}]}""")
        val e=assertThrows(IllegalArgumentException::class.java){TemplateDiagnostics.requireNoUnpackedAssets(p)}
        assertTrue(e.message.orEmpty().contains("Missing bundled image"))
    }
    @Test fun importedStructureMustFitNativeSaveLimits(){
        val oversized=VexoProject(compositions=(0..64).map{CompositionNode(index=it)})
        assertThrows(IllegalArgumentException::class.java){NativeVexCodec.validateStructure(oversized)}
        val valid=VexoProject()
        assertEquals(valid,NativeVexCodec.validateStructure(valid))
    }
}
