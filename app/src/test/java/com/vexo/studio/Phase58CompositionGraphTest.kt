package com.vexo.studio

import com.vexo.studio.engine.scene.SceneGraph
import com.vexo.studio.model.*
import org.junit.Assert.*
import org.junit.Test

class Phase58CompositionGraphTest {
    private fun node(index:Int,vararg deps:Int)=CompositionNode(index=index,
        layers=deps.map{LayerNode(LayerType.BLUR_EFFECT,LayerCommon(),LayerConfig.BlurEffect(sourceCompositionIndex=it))})

    @Test fun unrelatedDraftCycleDoesNotBlockFinalOutput(){
        val p=VexoProject(compositions=listOf(node(0,1),node(1),node(2,3),node(3,2)))
        val output=SceneGraph.resolveOutput(p,0)
        assertFalse(output.hasCycle)
        assertEquals(listOf(1,0),output.order.map{it.index})
        assertTrue(SceneGraph.resolve(p).hasCycle)
        assertTrue(SceneGraph.resolveOutput(p,2).hasCycle)
    }
    @Test fun visibleSelfReferenceIsDetectedButHiddenIsIgnored(){
        val self=node(0,0)
        assertTrue(SceneGraph.resolveOutput(VexoProject(compositions=listOf(self)),0).hasCycle)
        val hidden=self.copy(layers=self.layers.map{it.copy(common=it.common.copy(visible=false))})
        assertFalse(SceneGraph.resolveOutput(VexoProject(compositions=listOf(hidden)),0).hasCycle)
    }
    @Test fun deepImportedGraphDoesNotRequireRecursiveStack(){
        val nodes=(0..3000).map{if(it==3000)node(it)else node(it,it+1)}
        val output=SceneGraph.resolveOutput(VexoProject(compositions=nodes),0)
        assertFalse(output.hasCycle)
        assertEquals(3001,output.order.size)
        assertEquals(3000,output.order.first().index)
        assertEquals(0,output.order.last().index)
    }
    @Test fun missingRootAndSourceAreNotSilentlySubstituted(){
        val p=VexoProject(compositions=listOf(node(2,9)))
        assertTrue(SceneGraph.resolveOutput(p,0).order.isEmpty())
        assertEquals(listOf(2),SceneGraph.resolveOutput(p,2).order.map{it.index})
        assertEquals(9,SceneGraph.missingReferences(p,2).single().targetIndex)
    }
}
