package com.vexo.studio
import com.vexo.studio.engine.scene.*
import com.vexo.studio.model.*
import org.junit.Assert.*
import org.junit.Test
class TransformAnimationTest{
 @Test fun interpolatesIndependentProperties(){
    val c=LayerCommon()
    val tracks=listOf(KeyframeTrack(c.id,"transform.posX",listOf(Keyframe(0,0f),Keyframe(1000,1f))),KeyframeTrack(c.id,"transform.opacity",listOf(Keyframe(0,1f),Keyframe(1000,0f))))
    val value=TransformAnimation.evaluate(c,tracks,250)
    assertEquals(.25f,value.posX,.001f);assertEquals(.75f,value.opacity,.001f);assertEquals(c.scaleX,value.scaleX,.001f)
 }
 @Test fun holdChangesAtExactKey(){
    val t=KeyframeTrack("x","test",listOf(Keyframe(0,0f,InterpolationType.HOLD),Keyframe(500,1f),Keyframe(1000,2f)))
    assertEquals(0f,KeyframeEvaluator.evaluate(t,499,0f),.001f)
    assertEquals(1f,KeyframeEvaluator.evaluate(t,500,0f),.001f)
 }
 @Test fun blurMaskDependencyIsIncluded(){
    assertTrue(3 in SceneGraph.referencesOf(LayerConfig.BlurEffect(maskImage="composition:3")))
 }
}
