package com.vexo.studio.engine.scene

import kotlin.math.roundToLong

/** All timeline rows use the same viewport, without allocating a very wide canvas. */
data class TimelineWindow(val start:Long,val end:Long){
    val span:Long get()=(end-start).coerceAtLeast(1L)
    fun fraction(time:Long):Float=((time-start).toDouble()/span).toFloat()
    fun timeAt(fraction:Float):Long=start+(span*fraction.coerceIn(0f,1f).toDouble()).roundToLong()
    companion object{
        fun create(total:Long,zoom:Float,scroll:Float):TimelineWindow{
            val t=total.coerceAtLeast(1L)
            val span=(t/(zoom.takeIf{it.isFinite()}?:1f).coerceIn(1f,16f).toDouble()).roundToLong().coerceIn(1L,t)
            val start=((t-span)*(scroll.takeIf{it.isFinite()}?:0f).coerceIn(0f,1f).toDouble()).roundToLong()
            return TimelineWindow(start,start+span)
        }
        fun centerFraction(total:Long,zoom:Float,time:Long):Float{
            val t=total.coerceAtLeast(1L)
            val span=create(t,zoom,0f).span
            return if(t==span)0f else ((time-span/2L).toDouble()/(t-span)).toFloat().coerceIn(0f,1f)
        }
    }
}
