package com.vexo.studio
import com.vexo.studio.model.*
import com.vexo.studio.engine.scene.*
import org.junit.Assert.*
import org.junit.Test
class Phase18MotionTest{
 @Test fun curveEndpoints(){
  InterpolationType.entries.forEach{m->assertEquals(0f,AnimationCurves.ease(m,0f),.001f);assertEquals(1f,AnimationCurves.ease(m,1f),.001f)}
  assertTrue(AnimationCurves.ease(InterpolationType.BACK_OUT,.6f)>1f)
  assertEquals(.125f,AnimationCurves.ease(InterpolationType.EASE_IN,.5f),.001f)
 }
 @Test fun presetAtPlayheadAndPreserveOtherTracks(){
  val c=LayerCommon()
  val old=KeyframeTrack(c.id,"transform.posX",listOf(Keyframe(0,.5f)))
  val fade=MotionPresets.builtin(MotionRecipe.FADE_IN,c,1000,2000,1f)
  val out=MotionPresets.replace(listOf(old),fade)
  assertTrue(old in out)
  assertEquals(0f,KeyframeEvaluator.evaluate(fade.first(),1000,1f),.001f)
  assertEquals(1f,KeyframeEvaluator.evaluate(fade.first(),3000,0f),.001f)
 }
 @Test fun savedPresetRetimes(){
  val c=LayerCommon()
  val p=MotionPresets.capture("test",listOf(KeyframeTrack(c.id,"transform.opacity",listOf(Keyframe(2000,0f),Keyframe(4000,1f)))))!!
  val target=LayerCommon()
  val out=MotionPresets.applySaved(p,target,500,1000).single()
  assertEquals(target.id,out.targetLayerId)
  assertEquals(1500L,out.keyframes.last().timeMs)
 }
}
