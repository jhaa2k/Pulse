package com.vexo.studio

import com.vexo.studio.data.AveeJsonCodec
import com.vexo.studio.data.NativeVexCodec
import com.vexo.studio.model.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class Phase60AveeCompatibilityTest {
    @Test fun interleavedProviderAndUnknownElementPositionsSurviveRoundTrip(){
        val raw="""{"compositions":[{"elements":[{"objType":"AudioProvider"},{"objType":"Image"},{"objType":"FutureElement"},{"objType":"AudioProvider"},{"objType":"Bars","audioProviderIndex":{"v":2}}]}]}"""
        val p=NativeVexCodec.decode(NativeVexCodec.encode(AveeJsonCodec.importFromText(raw)))
        assertEquals(mapOf(1 to 0,2 to 3),p.compositions[0].providerElementPositions)
        val exported=Json.parseToJsonElement(AveeJsonCodec.exportAveeScene(p)).jsonObject
        val elements=exported.getValue("compositions").jsonArray[0].jsonObject.getValue("elements").jsonArray
        assertEquals(listOf("AudioProvider","Image","FutureElement","AudioProvider","Bars"),elements.map{it.jsonObject.getValue("objType").jsonPrimitive.content})
    }
    @Test fun localNumericReferenceDoesNotBecomeAlbumArt(){
        val p=AveeJsonCodec.importFromText("""{"compositions":[{"elements":[{"objType":"Image","customImage":{"v":"local:1000013310"}}]}]}""")
        val image=p.compositions[0].layers[0].config as LayerConfig.Image
        assertEquals(ImageSourceType.IMPORTED,image.sourceType)
        assertEquals("local:1000013310",image.customUri)
        val reopened=AveeJsonCodec.importFromText(AveeJsonCodec.exportAveeScene(p))
        assertEquals(image.customUri,(reopened.compositions[0].layers[0].config as LayerConfig.Image).customUri)
    }
    @Test fun reactiveRgbMultiplierRetainsBothMeasureAmounts(){
        val p=AveeJsonCodec.importFromText("""{"compositions":[{"elements":[{"objType":"RgbSplitEffect","splitMultiplier":{"v":"Beat 1.05 6.0"},"splitAmount":{"v":"Beat 0.001 -0.001"}}]}]}""")
        val c=p.compositions[0].layers[0].config as LayerConfig.RgbSplitEffect
        assertEquals(0f,c.splitMultiplier,0f)
        assertEquals(MeasureWhat.BEAT,c.splitMultiplierMeasure.measureWhat)
        assertEquals(1.05f,c.splitMultiplierMeasure.amountA,.0001f)
        assertEquals(6f,c.splitMultiplierMeasure.amountB,.0001f)
        assertEquals(0f,c.splitAmountX,0f)
        assertEquals(-.001f,c.splitMeasure.amountB,.0001f)
        val reopened=AveeJsonCodec.importFromText(AveeJsonCodec.exportAveeScene(p))
        assertEquals(c,(reopened.compositions[0].layers[0].config as LayerConfig.RgbSplitEffect))
    }
    @Test fun constantRgbSplitPreservesIndependentAxes(){
        val p=AveeJsonCodec.importFromText("""{"compositions":[{"elements":[{"objType":"RgbSplitEffect","splitAmount":{"v":"Constant 0.5 -0.5"}}]}]}""")
        val c=p.compositions[0].layers[0].config as LayerConfig.RgbSplitEffect
        assertEquals(.0075f,c.splitAmountX,.0001f)
        assertEquals(-.0075f,c.splitAmountY,.0001f)
    }
}
