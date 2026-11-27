package com.vexo.studio

import com.vexo.studio.data.AveeJsonCodec
import com.vexo.studio.data.NativeVexCodec
import com.vexo.studio.engine.scene.LegacyImageMath
import com.vexo.studio.model.LayerConfig
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class Phase64LegacyImageTest {
    @Test fun argbAndHslaLegacyColorsRoundTrip(){
        val colors=listOf(0,0xFF000000.toInt(),-1,0xA4329BEE.toInt(),0xFFFE0001.toInt())
        for(c in colors){
            assertEquals(c,AveeJsonCodec.parseHsla4fToArgb(c.toString()))
            assertEquals(c,AveeJsonCodec.parseHsla4fToArgb(LegacyImageMath.argbToHsla(c)))
        }
    }
    @Test fun editedImageColorsAndBlurControlsUpdateOriginalFields(){
        val raw="""{"compositions":[{"elements":[{"objType":"Image","Color":{"v":"0 0 1 1","t":"chsla"},"ColorTo":{"v":-1,"t":"crgba"},"blurDivider":{"v":5},"blurredBorder":{"v":0}}]}]}"""
        val p=AveeJsonCodec.importFromText(raw)
        val comp=p.compositions.single();val layer=comp.layers.single()
        val c=(layer.config as LayerConfig.Image).copy(colorArgb=0xFFAA0077.toInt(),colorToArgb=0x60000000,blurDivider=10,blurredBorder=true,autoDetectColorKey=false)
        val edited=p.copy(compositions=listOf(comp.copy(layers=listOf(layer.copy(config=c)))))
        val out=AveeJsonCodec.exportAveeScene(edited)
        val e=Json.parseToJsonElement(out).jsonObject.getValue("compositions").jsonArray[0].jsonObject.getValue("elements").jsonArray[0].jsonObject
        assertEquals(c.colorArgb,AveeJsonCodec.parseHsla4fToArgb(e.getValue("Color").jsonObject.getValue("v").jsonPrimitive.content))
        assertEquals(c.colorToArgb,e.getValue("ColorTo").jsonObject.getValue("v").jsonPrimitive.int)
        assertEquals(c,AveeJsonCodec.importFromText(out).compositions[0].layers[0].config)
    }
    @Test fun legacyTrackTimeAndMetadataAreExpandedOnce(){
        assertEquals("1:05 / 2:10 — 50% — {Artist}",LegacyImageMath.resolveTrackText("{TimeCurrent} / {TimeLength} — {Progress} — {Title}","{Artist}","Test","",65000,130000))
    }
    @Test fun builtInTypefaceIdentifierSurvivesWithoutPretendingItIsAUri(){
        val p=AveeJsonCodec.importFromText("""{"compositions":[{"elements":[{"objType":"Text","typeFace":{"v":"internal_2"},"text":{"v":"{TimeCurrent}"}}]}]}""")
        fun cfg(p:com.vexo.studio.model.VexoProject)=p.compositions[0].layers[0].config as LayerConfig.Text
        assertEquals("internal_2",cfg(p).legacyTypeface)
        assertNull(cfg(p).fontAssetUri)
        assertEquals(cfg(p),cfg(NativeVexCodec.decode(NativeVexCodec.encode(p))))
        assertEquals(cfg(p),cfg(AveeJsonCodec.importFromText(AveeJsonCodec.exportAveeScene(p))))
    }
}
