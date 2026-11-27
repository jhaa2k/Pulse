package com.vexo.studio

import com.vexo.studio.data.AveeJsonCodec
import com.vexo.studio.data.NativeVexCodec
import com.vexo.studio.engine.scene.LegacyGlowMath
import com.vexo.studio.model.LayerConfig
import org.junit.Assert.*
import org.junit.Test

class Phase65LegacyGlowTest {
    @Test fun anisotropicScalesSurviveBothFormats(){
        val p=AveeJsonCodec.importFromText("""{"compositions":[{"elements":[{"objType":"BlurEffect","1layerScale":{"v":"1.2 0.8"},"2layerScale":{"v":"0.25 0.75"},"3layerScale":{"v":"0 0"}}]}]}""")
        fun cfg(project:com.vexo.studio.model.VexoProject)=project.compositions[0].layers[0].config as LayerConfig.BlurEffect
        val c=cfg(p)
        val (x,y)=LegacyGlowMath.axes(c.layer1Scale,c.layer1AxisRatio)
        assertEquals(1.2f,x,.00001f)
        assertEquals(.8f,y,.00001f)
        assertEquals(c,cfg(NativeVexCodec.decode(NativeVexCodec.encode(p))))
        val restored=cfg(AveeJsonCodec.importFromText(AveeJsonCodec.exportAveeScene(p)))
        assertEquals(c.layer1AxisRatio,restored.layer1AxisRatio,.00001f)
        assertEquals(c.layer2AxisRatio,restored.layer2AxisRatio,.00001f)
        assertEquals(0f,restored.layer3Scale,0f)
    }
    @Test fun scalarEditMaintainsAspect(){
        val (x,y)=LegacyGlowMath.axes(2f,LegacyGlowMath.axisRatio(1.2f,.8f))
        assertEquals(2.4f,x,.00001f);assertEquals(1.6f,y,.00001f)
    }
    @Test fun nativeUniformScaleAndInvalidValues(){
        assertEquals(1.5f to 1.5f,LegacyGlowMath.axes(1.5f,1f))
        assertEquals(0f to 0f,LegacyGlowMath.axes(Float.NaN,Float.NaN))
        assertEquals(1f,LegacyGlowMath.axisRatio(0f,0f),0f)
    }
}
