package com.vexo.studio
import com.vexo.studio.model.AudioTrackItem
import com.vexo.studio.playback.QueueResumeMath
import org.junit.Assert.*
import org.junit.Test
class QueueResumeTest {
 @Test fun selectedIdSurvivesReorder(){
   val a=AudioTrackItem(id="a",uri="file:a",title="A",durationMs=1000)
   val b=AudioTrackItem(id="b",uri="file:b",title="B",durationMs=2000)
   val r=QueueResumeMath.restore(listOf(b,a),"a",1500)
   assertEquals(1,r.index);assertEquals(1000L,r.positionMs)
 }
 @Test fun missingSelectionResetsPosition(){
   val a=AudioTrackItem(id="a",uri="file:a",title="A")
   assertEquals(0L,QueueResumeMath.restore(listOf(a),"gone",500).positionMs)
   assertEquals(-1,QueueResumeMath.restore(emptyList(),"a",500).index)
 }
}
