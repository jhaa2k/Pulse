package com.vexo.studio.engine.scene

/**
 * Renderer-time history. Legacy integer delay values are interpreted as 60 Hz
 * reference frames, independent of actual render/export FPS. Interpolation
 * retains signed waveform values; history resets on source or shape changes.
 */
class SpectrumReactionHistory {
    private data class Frame(val time:Double,val values:FloatArray)
    private val frames=ArrayDeque<Frame>()
    private var time=0.0
    private var previousSignature=""
    private var previousSize=-1
    fun sample(values:FloatArray,delayFrames:Int,dtSec:Float,signature:String):FloatArray{
        val delay=delayFrames.coerceIn(0,9)
        if(signature!=previousSignature||values.size!=previousSize||delay==0){
            frames.clear();time=0.0;previousSignature=signature;previousSize=values.size
        }
        if(delay==0||values.isEmpty())return values
        if(frames.isNotEmpty())time+=(dtSec.takeIf{it.isFinite()}?.coerceIn(.001f,.25f)?:1f/60f).toDouble()
        frames.addLast(Frame(time,values.copyOf()))
        // Retain the maximum supported delay, so changing the slider can
        // use existing history rather than abruptly restarting the response.
        val oldest=time-9.0/60.0
        while(frames.size>2&&frames[1].time<=oldest)frames.removeFirst()
        while(frames.size>192)frames.removeFirst()
        val target=time-delay/60.0
        val a=frames.first()
        if(target<=a.time)return a.values
        for(i in 1 until frames.size){
            val next=frames[i]
            if(next.time>=target){
                val prev=frames[i-1]
                val f=((target-prev.time)/(next.time-prev.time)).coerceIn(0.0,1.0).toFloat()
                return FloatArray(values.size){j->prev.values[j]+(next.values[j]-prev.values[j])*f}
            }
        }
        return frames.last().values
    }
    companion object{
        fun delayFrames(reaction:Int,accumulated:Int):Int=
            (reaction.toLong()+accumulated.toLong()).coerceIn(0L,9L).toInt()
    }
}
