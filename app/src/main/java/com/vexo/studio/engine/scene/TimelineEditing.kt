package com.vexo.studio.engine.scene
import com.vexo.studio.model.LayerTiming
enum class TimelineDragMode { MOVE, TRIM_START, TRIM_END }
object TimelineEditing{
    fun editSnapped(t:LayerTiming,delta:Long,duration:Long,mode:TimelineDragMode,fps:Int):LayerTiming{
        val anchor=if(mode==TimelineDragMode.TRIM_END)t.endMs.takeIf{it>0}?:duration else t.startMs
        val target=LayerPropertyAnimation.snap((anchor+delta).coerceIn(0L,duration.coerceAtLeast(0L)),fps)
        return edit(t,target-anchor,duration,mode)
    }
    fun edit(t:LayerTiming,delta:Long,duration:Long,mode:TimelineDragMode):LayerTiming{
        val total=duration.coerceAtLeast(1)
        val minLen=minOf(100L,total)
        val start=t.startMs.coerceIn(0,total-minLen)
        val end=(if(t.endMs>0)t.endMs else total).coerceIn(start+minLen,total)
        val (a,b)=when(mode){
            TimelineDragMode.MOVE->{val d=delta.coerceIn(-start,total-end);start+d to end+d}
            TimelineDragMode.TRIM_START->(start+delta).coerceIn(0,end-minLen) to end
            TimelineDragMode.TRIM_END->start to (end+delta).coerceIn(start+minLen,total)
        }
        return t.copy(startMs=a,endMs=if(t.endMs==0L&&b==total&&mode!=TimelineDragMode.TRIM_END)0L else b)
    }
}
