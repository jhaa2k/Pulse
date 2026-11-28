package com.vexo.studio.engine.gl

import com.vexo.studio.model.MeasureWhat

/** Deterministic track-relative GIF clock. Time measures express phase in seconds. */
object ImageAnimationClock {
    fun position(trackMs:Long,speed:Float,measure:Float,kind:MeasureWhat):Long {
        val s=speed.takeIf{it.isFinite()}?.coerceIn(0f,8f)?:1f
        val m=measure.takeIf{it.isFinite()}?:0f
        val base=trackMs.coerceAtLeast(0L).toDouble()*s
        val phase=when(kind){
            MeasureWhat.NOTHING->base
            MeasureWhat.TOTAL_TIME,MeasureWhat.TOTAL_TIME_AND_BEAT,MeasureWhat.TOTAL_TIME_WHEN_PLAYING->m.toDouble()*1000*s
            else->base+m.coerceIn(-10f,10f)*1000.0
        }
        return phase.coerceAtLeast(0.0).toLong()
    }
    fun loopTime(position:Long,duration:Int):Int=(position.coerceAtLeast(0L)%duration.coerceAtLeast(1)).toInt()
    fun gifSize(header:ByteArray):Pair<Int,Int>?{
        if(header.size<10)return null
        val magic=String(header,0,6,Charsets.US_ASCII)
        if(magic!="GIF87a"&&magic!="GIF89a")return null
        fun b(i:Int)=header[i].toInt() and 255
        val w=b(6)+(b(7) shl 8);val h=b(8)+(b(9) shl 8)
        return if(w in 1..2048&&h in 1..2048&&w.toLong()*h<=4_194_304L)w to h else null
    }
}
