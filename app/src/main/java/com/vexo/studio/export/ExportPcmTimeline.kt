package com.vexo.studio.export

import kotlin.math.roundToLong

/** Map decoder timestamps onto a continuous, trim-relative PCM sample grid. */
object ExportPcmTimeline {
    data class Placement(val skipFrames:Int,val copyFrames:Int,val gapFrames:Long)
    fun durationFrames(durationUs:Long,sampleRate:Int):Long =
        (durationUs.coerceAtLeast(0L).toDouble()*sampleRate/1_000_000.0).roundToLong()
    fun place(ptsUs:Long,startUs:Long,sampleRate:Int,bufferFrames:Int,writtenFrames:Long,limitFrames:Long):Placement{
        if(bufferFrames<=0||writtenFrames>=limitFrames)return Placement(0,0,0)
        var first=((ptsUs.toDouble()-startUs.toDouble())*sampleRate/1_000_000.0).roundToLong()
        // Timestamp quantization may be off by one sample at contiguous buffer edges.
        if(writtenFrames>0&&kotlin.math.abs(first.toDouble()-writtenFrames.toDouble())<=1.0)first=writtenFrames
        val skip=(writtenFrames.toDouble()-first.toDouble()).coerceIn(0.0,bufferFrames.toDouble()).toInt()
        val copyStart=maxOf(writtenFrames,first+skip)
        if(copyStart>=limitFrames)return Placement(skip,0,0)
        val copy=minOf((bufferFrames-skip).toLong(),limitFrames-copyStart).toInt()
        return if(copy<=0)Placement(skip,0,0) else Placement(skip,copy,copyStart-writtenFrames)
    }
}
