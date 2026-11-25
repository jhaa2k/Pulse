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
        val json = """{"objType":"Root","ver":"10","compositions":[{"objType":"Composition","elements":[{"objType":"AudioProvider","sampleOutCount":{"v":174}},{"objType":"BlurEffect","sourceCompositionIndex":{"v":2}},{"objType":"AppLogo","visible":{"v":0}},{"objType":"FutureElement","value":{"v":17}}]},{"objType":"Composition","elements":[{"objType":"Bars","heightScale":{"v":3},"Segment1":{"v":"Bars","barWidth":{"v":0.6}}}]},{"objType":"Composition","elements":[{"objType":"Image","customImage":{"v":"composition:1"},"generatedAlbumArtHint":{"v":7}}]}]}"""
        val zipped = ByteArrayOutputStream()
        ZipOutputStream(zipped).use { it.putNextEntry(ZipEntry("scene.json")); it.write(json.toByteArray()); it.closeEntry() }
        val project = AveeJsonCodec.importAuto(ByteArrayInputStream(zipped.toByteArray()), "Fixture")
        assertEquals(3, project.compositions.size)
        assertEquals(174, project.audioProviders.single().sampleOutCount)
        assertEquals(2, (project.compositions[0].layers.first().config as LayerConfig.BlurEffect).sourceCompositionIndex)
        assertEquals(1, (project.compositions[2].layers.single().config as LayerConfig.Image).sourceCompositionIndex)
        assertEquals(1, project.compositions[0].preservedElements.size)
        assertEquals("AppLogo", project.compositions[0].layers[1].common.name)
        assertFalse(project.compositions[0].layers[1].common.visible)
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
    @Test fun nestedBarsPropertiesSurviveVizRoundTrip() {
        val scene = """{"objType":"Root","compositions":[{"elements":[{"objType":"Bars","heightScale":{"v":-8.5},"ShapePath":{"v":"Circle","radius":{"v":2},"gap":{"v":0.3}},"Segment1":{"v":"Bars"},"Segment2":{"v":"SharpBars","barWidth":{"v":0.42},"barHeightMultiplier":{"v":-1},"mirror":{"v":1}}}]}]}"""
        val project = AveeJsonCodec.importFromText(scene)
        val cfg = project.compositions.single().layers.single().config as LayerConfig.Bars
        assertEquals(0.56f, cfg.circleRadius, 0.001f)
        assertEquals(0.3f, cfg.pathGap, 0.001f)
        assertEquals(0.42f, cfg.segment2Width, 0.001f)
        assertEquals(-8.5f, cfg.heightScale, 0.001f)
        assertTrue(cfg.segment2Mirror)
        val restored = AveeJsonCodec.importFromText(AveeJsonCodec.exportAveeScene(project))
        val result = restored.compositions.single().layers.single().config as LayerConfig.Bars
        assertEquals(cfg, result)
    }
    @Test fun maskDependencyAndNestedTriangleArePreserved() {
        val scene = """{"objType":"Root","compositions":[{"elements":[{"objType":"BlurEffect","sourceCompositionIndex":{"v":3}}]},{"elements":[{"objType":"Bars","ShapePath":{"v":"SidedPolygon","sides":{"v":3},"radius":{"v":1.5}},"Segment1":{"v":"Line"},"Segment2":{"v":"Line","barHeightMultiplier":{"v":-0.4}}}]},{"elements":[{"objType":"Image","customImage":{"v":"internalres:rainbow128"}}]},{"elements":[{"objType":"Image","customImage":{"v":"composition:2"},"MaskImage":{"v":"composition:1"},"maskMode":{"v":"TransparencyAndBlacks"}}]}]}"""
        val project = AveeJsonCodec.importFromText(scene)
        val bars = project.compositions[1].layers.single().config as LayerConfig.Bars
        assertEquals(3, bars.polygonSides)
        assertEquals(-0.4f, bars.segment2HeightMultiplier, 0.001f)
        val order = com.vexo.studio.engine.scene.SceneGraph.resolve(project)
        assertFalse(order.hasCycle)
        val indices = order.order.map { it.index }
        assertTrue(indices.indexOf(1) < indices.indexOf(3))
        assertTrue(indices.indexOf(2) < indices.indexOf(3))
        val restored = AveeJsonCodec.importFromText(AveeJsonCodec.exportAveeScene(project))
        val image = restored.compositions[3].layers.single().config as LayerConfig.Image
        assertEquals("composition:1", image.maskImage)
        assertEquals(com.vexo.studio.model.MaskMode.TRANSPARENCY_AND_BLACKS, image.maskMode)
        assertEquals(3, (restored.compositions[1].layers.single().config as LayerConfig.Bars).polygonSides)
    }
}
