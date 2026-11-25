package com.vexo.studio

import com.vexo.studio.data.AveeJsonCodec
import com.vexo.studio.model.LayerConfig
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import org.junit.Test
import org.junit.Assert.*

class VizImportRegressionTest {
    @Test fun rootZipKeepsCompositionReferencesAndUnknownElements() {
        val json = """{"objType":"Root","ver":"10","compositions":[{"objType":"Composition","elements":[{"objType":"AudioProvider","sampleOutCount":{"v":174}},{"objType":"BlurEffect","sourceCompositionIndex":{"v":2}},{"objType":"AppLogo","visible":{"v":0}}]},{"objType":"Composition","elements":[{"objType":"Bars","heightScale":{"v":3},"Segment1":{"v":"Bars","barWidth":{"v":0.6}}}]},{"objType":"Composition","elements":[{"objType":"Image","customImage":{"v":"composition:1"},"generatedAlbumArtHint":{"v":7}}]}]}"""
        val zipped = ByteArrayOutputStream()
        ZipOutputStream(zipped).use { it.putNextEntry(ZipEntry("scene.json")); it.write(json.toByteArray()); it.closeEntry() }
        val project = AveeJsonCodec.importAuto(ByteArrayInputStream(zipped.toByteArray()), "Fixture")
        assertEquals(3, project.compositions.size)
        assertEquals(174, project.audioProviders.single().sampleOutCount)
        assertEquals(2, (project.compositions[0].layers.single().config as LayerConfig.BlurEffect).sourceCompositionIndex)
        assertEquals(1, (project.compositions[2].layers.single().config as LayerConfig.Image).sourceCompositionIndex)
        assertEquals(1, project.compositions[0].preservedElements.size)
        val edited = project.copy(compositions = project.compositions.map { c -> if(c.index == 1) c.copy(layers = c.layers.map { l -> l.copy(config = (l.config as LayerConfig.Bars).copy(barWidth = 0.9f)) }) else c })
        val saved = ByteArrayOutputStream()
        AveeJsonCodec.exportViz(edited, saved)
        val reopened = AveeJsonCodec.importAuto(ByteArrayInputStream(saved.toByteArray()), "Reopened")
        assertEquals(3, reopened.compositions.size)
        assertEquals(0.9f, (reopened.compositions[1].layers.single().config as LayerConfig.Bars).barWidth, 0.001f)
        assertEquals(1, reopened.compositions[0].preservedElements.size)
        assertTrue(AveeJsonCodec.exportAveeScene(reopened).contains("generatedAlbumArtHint"))
        val native = AveeJsonCodec.importFromText(AveeJsonCodec.exportProjectAsAveeJson(edited))
        assertEquals(3, native.compositions.size)
        assertEquals(1, native.compositions[0].preservedElements.size)
    }
}
