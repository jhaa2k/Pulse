package com.vexo.studio
import com.vexo.studio.engine.scene.*
import com.vexo.studio.model.*
import org.junit.Assert.*
import org.junit.Test

class Phase43CompositionSlotsTest{
    private fun node(index:Int,vararg deps:Int)=CompositionNode(index=index,layers=deps.map{LayerNode(LayerType.BLUR_EFFECT,LayerCommon(),LayerConfig.BlurEffect(sourceCompositionIndex=it))})
    private fun plan(vararg nodes:CompositionNode):CompositionSlotPlan{
        val p=VexoProject(compositions=nodes.toList())
        return CompositionSlotPlan.create(SceneGraph.resolve(p).order,SceneGraph.reachable(p,0),0)
    }
    @Test fun linearChainNeedsOnlyTwoOffscreenTargets(){
        val nodes=(0..22).map{if(it==22)node(it)else node(it,it+1)}
        val p=plan(*nodes.toTypedArray())
        assertEquals(22,p.slotByComposition.size)
        assertEquals(2,p.slotCount)
        for(i in 1..21)assertNotEquals(p.slotByComposition[i],p.slotByComposition[i+1])
    }
    @Test fun sharedDependenciesRemainLiveThroughLastConsumer(){
        val p=plan(node(0,1,2),node(1,3),node(2,3),node(3))
        assertEquals(3,p.slotCount)
        assertEquals(3,p.slotByComposition.values.toSet().size)
    }
    @Test fun rootOnlyAndUnreachableDraftsDoNotAllocateSlots(){
        assertEquals(0,plan(node(0),node(1,2),node(2)).slotCount)
    }
}
