package com.vexo.studio

import com.vexo.studio.data.AveeJsonCodec
import com.vexo.studio.data.NativeVexCodec
import com.vexo.studio.engine.gl.VexoShaders
import com.vexo.studio.model.*
import org.junit.Assert.*
import org.junit.Test

class Phase66EffectCompatibilityTest {
    @Test fun rgbSeparateBlendsAndChannelAlphaRoundTrip(){
        val p=AveeJsonCodec.importFromText("""{"compositions":[{"elements":[{"objType":"RgbSplitEffect","blendMode":{"v":"PreMulAlpha"},"blendModeContent":{"v":"Add"},"splitColor0":{"v":16711680},"showUnblurredContentUnder":{"v":1}}]}]}""")
        fun cfg(p:VexoProject)=p.compositions[0].layers[0].config as LayerConfig.RgbSplitEffect
        val c=cfg(p)
        assertEquals(BlendMode.PRE_MUL_ALPHA,c.effectBlendMode)
        assertEquals(BlendMode.ADD,c.blendModeContent)
        assertEquals(0,c.splitColor0Argb ushr 24)
        assertTrue(c.showUnblurredContentUnder)
        assertEquals(c,cfg(NativeVexCodec.decode(NativeVexCodec.encode(p))))
        assertEquals(c,cfg(AveeJsonCodec.importFromText(AveeJsonCodec.exportAveeScene(p))))
    }
    @Test fun motionAndMirrorExtensionsSurviveLegacyEdits(){
        val p=AveeJsonCodec.importFromText("""{"compositions":[{"elements":[{"objType":"MotionBlurEffect","manualRotationDeg":{"v":12},"manualZoom":{"v":0.25},"includeRotationAndZoom":{"v":1}},{"objType":"MirrorEffect","vexoRepeatCount":{"v":5}}]}]}""")
        val restored=AveeJsonCodec.importFromText(AveeJsonCodec.exportAveeScene(p))
        assertEquals(p.compositions[0].layers.map{it.config},restored.compositions[0].layers.map{it.config})
        assertTrue((restored.compositions[0].layers[0].config as LayerConfig.MotionBlurEffect).includeRotationAndZoom)
        assertEquals(5,(restored.compositions[0].layers[1].config as LayerConfig.MirrorEffect).repeatCount)
    }
    @Test fun shaderContractAppliesTintCoverageWithoutAttenuatingCleanMix(){
        val s=VexoShaders.RGB_SPLIT_FRAGMENT
        assertTrue(s.contains("uniform vec4 uColor0"))
        assertTrue(s.contains("r*=uColor0.a"))
        assertTrue(s.contains("gc=g*uColor1.a"))
        assertTrue(s.contains("b*=uColor2.a"))
        assertTrue(s.contains("max(r.a,max(gc.a,b.a))"))
        assertTrue(s.contains("mix(g,vec4(rgb,a)"))
    }
}
