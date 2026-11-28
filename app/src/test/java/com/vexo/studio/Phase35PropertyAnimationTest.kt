package com.vexo.studio
import com.vexo.studio.engine.scene.LayerPropertyAnimation
import com.vexo.studio.engine.scene.LayerPresets
import com.vexo.studio.model.*
import org.junit.Assert.*
import org.junit.Test

class Phase35PropertyAnimationTest{
    @Test fun spectrumInterpolationIsBoundedAndDoesNotMutateBase(){
        val cfg=LayerConfig.Bars(barStrokeWidth=1f)
        val layer=LayerNode(LayerType.BARS,LayerCommon(),cfg)
        val key=LayerPropertyAnimation.trackKey("bars.stroke")
        val t=KeyframeTrack(layer.common.id,key,listOf(Keyframe(0,0f),Keyframe(1000,12f)))
        val animated=LayerPropertyAnimation.evaluate(layer,listOf(t),500) as LayerConfig.Bars
        assertEquals(6f,animated.barStrokeWidth,.001f)
        assertEquals(1f,cfg.barStrokeWidth,.001f)
        assertEquals(cfg,LayerPropertyAnimation.evaluate(layer,emptyList(),500))
        val saved=checkNotNull(LayerPresets.capture("Animated",layer,listOf(t)))
        assertEquals(key,saved.tracks.single().propertyKey)
        assertEquals(24f,LayerPropertyAnimation.clamp("bars.stroke",100f),.001f)
    }
    @Test fun blurAndMotionHaveIndependentTracks(){
        val blur=LayerNode(LayerType.BLUR_EFFECT,LayerCommon(),LayerConfig.BlurEffect())
        val t=KeyframeTrack(blur.common.id,LayerPropertyAnimation.trackKey("blur.glow2"),listOf(Keyframe(0,0f),Keyframe(1000,2f)))
        assertEquals(1f,(LayerPropertyAnimation.evaluate(blur,listOf(t),500) as LayerConfig.BlurEffect).layer2Scale,.001f)
        val motion=LayerNode(LayerType.MOTION_BLUR_EFFECT,LayerCommon(),LayerConfig.MotionBlurEffect())
        assertEquals(motion.config,LayerPropertyAnimation.evaluate(motion,listOf(t),500))
    }
    @Test fun frameSnappingUsesExportFps(){
        assertEquals(42L,LayerPropertyAnimation.snap(40,24))
        assertEquals(33L,LayerPropertyAnimation.snap(30,30))
        assertEquals(1000L,LayerPropertyAnimation.snap(1000,60))
        assertEquals(0L,LayerPropertyAnimation.snap(-100,30))
    }
}
