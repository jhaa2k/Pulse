package com.vexo.studio
import com.vexo.studio.engine.scene.*
import com.vexo.studio.model.*
import org.junit.Assert.*
import org.junit.Test
class Phase36TimelineTest{
    @Test fun zoomPanAndSeekShareOneTimeWindow(){
        val w=TimelineWindow.create(16000,4f,.5f)
        assertEquals(6000L,w.start);assertEquals(10000L,w.end)
        assertEquals(8000L,w.timeAt(.5f));assertEquals(.5f,w.fraction(8000),.001f)
        assertEquals(16000L,TimelineWindow.create(16000,16f,1f).end)
        val centered=TimelineWindow.create(16000,4f,TimelineWindow.centerFraction(16000,4f,12000))
        assertEquals(12000L,centered.timeAt(.5f))
    }
    @Test fun frameSnapPreservesMoveLength(){
        val timing=LayerTiming(startMs=1000,endMs=3000)
        val next=TimelineEditing.editSnapped(timing,45,10000,TimelineDragMode.MOVE,24)
        assertEquals(1042L,next.startMs)
        assertEquals(2000L,next.endMs-next.startMs)
    }
    @Test fun missingSourcesOnlyIncludeReachableVisibleLayers(){
        fun blur(name:String,target:Int,visible:Boolean=true)=LayerNode(LayerType.BLUR_EFFECT,LayerCommon(name=name,visible=visible),LayerConfig.BlurEffect(sourceCompositionIndex=target))
        val p=VexoProject(compositions=listOf(
            CompositionNode(index=0,layers=listOf(blur("Glow",1),blur("Hidden",55,false))),
            CompositionNode(index=1,layers=listOf(blur("Missing",99))),
            CompositionNode(index=2,layers=listOf(blur("Unused",88)))))
        val missing=SceneGraph.missingReferences(p)
        assertEquals(1,missing.size)
        assertEquals(99,missing.single().targetIndex)
        assertEquals("Missing",missing.single().layerName)
    }
}
