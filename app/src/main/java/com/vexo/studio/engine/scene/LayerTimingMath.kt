package com.vexo.studio.engine.scene
import com.vexo.studio.model.LayerTiming
object LayerTimingMath{
    fun opacity(t:LayerTiming,position:Long,duration:Long):Float{
        val start=t.startMs.coerceAtLeast(0L)
        val end=if(t.endMs>0)t.endMs else duration.takeIf{it>0}?:Long.MAX_VALUE
        if(position<start||position>end||end<=start)return 0f
        val fadeIn=if(t.fadeInMs>0)((position-start).toDouble()/t.fadeInMs).toFloat().coerceIn(0f,1f) else 1f
        val fadeOut=if(t.fadeOutMs>0&&end!=Long.MAX_VALUE)((end-position).toDouble()/t.fadeOutMs).toFloat().coerceIn(0f,1f) else 1f
        return minOf(fadeIn,fadeOut)
    }
}
