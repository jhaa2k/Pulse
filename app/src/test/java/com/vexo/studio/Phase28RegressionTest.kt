package com.vexo.studio

import com.vexo.studio.data.AveeJsonCodec
import com.vexo.studio.model.LayerConfig
import com.vexo.studio.engine.scene.SpectrumStudioMesh
import org.junit.Assert.*
import org.junit.Test

class Phase28RegressionTest {
    @Test fun wrappedVectorAndGlowPropertiesImport() {
        val p = AveeJsonCodec.importFromText("""{"compositions":[{"elements":[{"objType":"Bars","Position":{"v":[0.25,0.7]},"scale":{"v":"0.3   0.9"},"alignmentPosition":{"v":{"x":0.1,"y":0.8}}},{"objType":"BlurEffect","1layerScale":{"v":1.1},"2layerScale":{"v":1.2}}]}]}""")
        val common=p.compositions[0].layers[0].common
        assertEquals(.25f,common.posX,.001f);assertEquals(.7f,common.posY,.001f)
        assertEquals(.3f,common.scaleX,.001f);assertEquals(.9f,common.scaleY,.001f)
        assertEquals(.1f,common.alignmentX,.001f);assertEquals(.8f,common.alignmentY,.001f)
        val cfg=p.compositions[0].layers[1].config as LayerConfig.BlurEffect
        assertEquals(1.2f,cfg.layer2Scale,.001f)
        val reopened=AveeJsonCodec.importFromText(AveeJsonCodec.exportAveeScene(p))
        assertEquals(1.1f,(reopened.compositions[0].layers[1].config as LayerConfig.BlurEffect).layer1Scale,.001f)
    }
    @Test fun sampleRepeatAndBaselineChangeGeometry() {
        val cfg=LayerConfig.Bars(barCount=8)
        val audio=FloatArray(8){it/8f}
        fun mesh(c:LayerConfig.Bars):List<Float>{ val out=mutableListOf<Float>();SpectrumStudioMesh.emit(c,audio,null,640,360){x,y,_->out.add(x);out.add(y)};return out }
        assertNotEquals(mesh(cfg),mesh(cfg.copy(sampleRepeats=2)))
        assertNotEquals(mesh(cfg),mesh(cfg.copy(baselineOffset=.3f)))
        assertNotEquals(mesh(cfg),mesh(cfg.copy(amplitudeCurve=2f)))
    }
}
