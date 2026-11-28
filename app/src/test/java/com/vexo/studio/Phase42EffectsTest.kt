package com.vexo.studio
import com.vexo.studio.data.NativeVexCodec
import com.vexo.studio.engine.scene.LayerPropertyAnimation
import com.vexo.studio.model.*
import org.junit.Assert.*
import org.junit.Test
class Phase42EffectsTest{
    @Test fun nativeEffectSettingsRoundTrip(){
        val blur=LayerConfig.BlurEffect(filterMode=BlurFilterMode.RADIAL_SPIN,radialCenterX=.2f,radialCenterY=.7f,radialStrength=.3f,bloomThreshold=.4f,bloomKnee=.2f,bloomIntensity=2f)
        val rgb=LayerConfig.RgbSplitEffect(aberrationMode=AberrationMode.RADIAL,aberrationMix=.7f,aberrationCenterX=.3f)
        val p=VexoProject(compositions=listOf(CompositionNode(index=0,layers=listOf(LayerNode(LayerType.BLUR_EFFECT,LayerCommon(),blur),LayerNode(LayerType.RGB_SPLIT_EFFECT,LayerCommon(),rgb))),CompositionNode(index=1,layers=emptyList())))
        val restored=NativeVexCodec.decode(NativeVexCodec.encode(p))
        assertEquals(blur,restored.compositions.first().layers[0].config)
        assertEquals(rgb,restored.compositions.first().layers[1].config)
    }
    @Test fun bloomAndRadialTracksAreBounded(){
        val layer=LayerNode(LayerType.BLUR_EFFECT,LayerCommon(),LayerConfig.BlurEffect())
        assertTrue("blur.intensity" in LayerPropertyAnimation.keys(layer))
        assertEquals(4f,LayerPropertyAnimation.clamp("blur.intensity",10f),.001f)
        assertEquals(0f,LayerPropertyAnimation.clamp("blur.threshold",-1f),.001f)
        assertEquals(1f,LayerPropertyAnimation.clamp("blur.radial",2f),.001f)
        val t=KeyframeTrack(layer.common.id,LayerPropertyAnimation.trackKey("blur.intensity"),listOf(Keyframe(0,1f),Keyframe(1000,3f)))
        val result=LayerPropertyAnimation.evaluate(layer,listOf(t),500) as LayerConfig.BlurEffect
        assertEquals(2f,result.bloomIntensity,.001f)
    }
}
