package com.vexo.studio
import com.vexo.studio.engine.scene.SceneGraph
import com.vexo.studio.model.*
import org.junit.Assert.*
import org.junit.Test
class Phase16GraphTest{
 private fun reference(index:Int)=LayerNode(LayerType.IMAGE,LayerCommon(),LayerConfig.Image(sourceType=ImageSourceType.COMPOSITION,sourceCompositionIndex=index))
 @Test fun rendersOnlyReachableCompositions(){
    val p=VexoProject(compositions=listOf(CompositionNode(index=0,layers=listOf(reference(2))),CompositionNode(index=1),CompositionNode(index=2,layers=listOf(reference(3))),CompositionNode(index=3)))
    assertEquals(setOf(0,2,3),SceneGraph.reachable(p,0))
    assertEquals(setOf(1),SceneGraph.reachable(p,1))
    assertEquals(setOf(2,3),SceneGraph.reachable(p,2))
 }
 @Test fun hiddenReferenceNotRendered(){
    val l=reference(1)
    val p=VexoProject(compositions=listOf(CompositionNode(index=0,layers=listOf(l.copy(common=l.common.copy(visible=false)))),CompositionNode(index=1)))
    assertEquals(setOf(0),SceneGraph.reachable(p,0))
 }
 @Test fun cyclicClosureTerminates(){
    val p=VexoProject(compositions=listOf(CompositionNode(index=0,layers=listOf(reference(1))),CompositionNode(index=1,layers=listOf(reference(0)))))
    assertEquals(setOf(0,1),SceneGraph.reachable(p,0));assertTrue(SceneGraph.resolve(p).hasCycle)
 }
}
