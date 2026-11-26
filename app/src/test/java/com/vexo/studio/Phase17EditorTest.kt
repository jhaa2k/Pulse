package com.vexo.studio
import com.vexo.studio.model.*
import com.vexo.studio.engine.scene.*
import org.junit.Assert.*
import org.junit.Test
class Phase17EditorTest{
 @Test fun timelineClamp(){
  val t=LayerTiming(1000,3000)
  assertEquals(LayerTiming(3000,5000),TimelineEditing.edit(t,10000,5000,TimelineDragMode.MOVE))
  assertEquals(2900L,TimelineEditing.edit(t,5000,5000,TimelineDragMode.TRIM_START).startMs)
  assertEquals(1100L,TimelineEditing.edit(t,-5000,5000,TimelineDragMode.TRIM_END).endMs)
 }
 @Test fun deletePreservesLockedAndRemovesTracks(){
  val a=LayerNode(LayerType.TEXT,LayerCommon(),LayerConfig.Text())
  val b=a.copy(common=LayerCommon(locked=true))
  val p=VexoProject(compositions=listOf(CompositionNode(index=0,layers=listOf(a,b))),keyframes=listOf(KeyframeTrack(a.common.id,"transform.posX",listOf(Keyframe(0,0f)))))
  val next=BatchLayerEdits.apply(p,0,setOf(a.common.id,b.common.id),BatchLayerAction.DELETE)
  assertEquals(listOf(b),next.compositions.first().layers);assertTrue(next.keyframes.isEmpty())
 }
 @Test fun duplicateRemapsKeyframes(){
  val a=LayerNode(LayerType.TEXT,LayerCommon(),LayerConfig.Text())
  val p=VexoProject(compositions=listOf(CompositionNode(index=0,layers=listOf(a))),keyframes=listOf(KeyframeTrack(a.common.id,"transform.posX",listOf(Keyframe(0,0f)))))
  val next=BatchLayerEdits.apply(p,0,setOf(a.common.id),BatchLayerAction.DUPLICATE)
  val clone=next.compositions.first().layers.last()
  assertNotEquals(a.common.id,clone.common.id);assertTrue(next.keyframes.any{it.targetLayerId==clone.common.id})
 }
}
