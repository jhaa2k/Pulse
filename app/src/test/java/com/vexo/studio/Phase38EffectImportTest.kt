package com.vexo.studio
import com.vexo.studio.data.AveeJsonCodec
import com.vexo.studio.model.*
import org.junit.Assert.*
import org.junit.Test
class Phase38EffectImportTest {
    @Test fun motionMeasureAndEffectBlendRemainSeparate(){
        val p=AveeJsonCodec.importFromText("""{"compositions":[{"elements":[{"objType":"MotionBlurEffect","blendMode":{"v":"PreMulAlpha"},"blendModeContent":{"v":"Add"},"blurAmountMultiplier":{"v":"Beat 0.35 1.0"}},{"objType":"BlurEffect","blendMode":{"v":"Add"},"blendModeContent":{"v":"PreMulAlpha"}},{"objType":"Bars","colorBlendOffset":{"v":"Beat 0.25 1.0"},"Segment1":{"v":"Bars","flipEveryOther":{"v":1}}}]}]}""")
        val layers=p.compositions.first().layers
        val m=layers[0].config as LayerConfig.MotionBlurEffect
        assertEquals(0f,m.blurAmountMultiplier,.001f)
        assertEquals(MeasureWhat.BEAT,m.blurAmountMeasure.measureWhat)
        assertEquals(.35f,m.blurAmountMeasure.amountA,.001f)
        assertEquals(BlendMode.PRE_MUL_ALPHA,m.effectBlendMode)
        assertEquals(BlendMode.ADD,m.blendModeContent)
        val b=layers[1].config as LayerConfig.BlurEffect
        assertEquals(BlendMode.ADD,b.glowBlendMode)
        assertEquals(BlendMode.PRE_MUL_ALPHA,b.blendModeContent)
        val s=layers[2].config as LayerConfig.Bars
        assertEquals(MeasureWhat.BEAT,s.colorBlendMeasure.measureWhat)
        assertTrue(s.alternatingDirection)
    }
}
