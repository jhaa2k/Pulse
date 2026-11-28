package com.vexo.studio.engine.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/** Time-normalized envelope and signed waveform helpers shared by live and offline providers. */
object SpectrumDynamics {
    private const val FRAME_MS=16.6667f
    fun referenceCoefficient(timeMs:Float):Float {
        val t=timeMs.takeIf{it.isFinite()}?.coerceAtLeast(0f)?:75f
        return if(t<=.5f)1f else 1f-exp(-FRAME_MS/t)
    }

    /** Combine the existing pre-smoothing and attack/release at a reference 60 Hz,
     * then convert the combined retention to the actual elapsed time. */
    fun responseCoefficient(reference:Float,preRetention:Float,dtMs:Float):Float {
        val gain=reference.coerceIn(0f,1f)*(1f-preRetention.coerceIn(0f,.8f))
        return 1f-(1f-gain).pow(dtMs.coerceAtLeast(0f)/FRAME_MS)
    }

    fun decayPeak(previous:Float,level:Float,elapsedMs:Float,decay:Float):Float =
        maxOf(level,previous*decay.coerceIn(.5f,.995f).pow(elapsedMs.coerceAtLeast(0f)/FRAME_MS))

    /** Downsample by signed absolute peak per bucket, so short impulses are not
     * missed. Upsample by linear interpolation without duplicating entire steps. */
    fun resampleWaveform(input:FloatArray,output:FloatArray){
        if(output.isEmpty())return
        if(input.isEmpty()){output.fill(0f);return}
        if(input.size>=output.size){
            for(i in output.indices){
                val start=(i.toLong()*input.size/output.size).toInt()
                val end=((i+1L)*input.size/output.size).toInt()
                var peak=0f
                for(j in start until end){
                    val value=input[j]
                    if(value.isFinite()&&abs(value)>abs(peak))peak=value
                }
                output[i]=peak
            }
        }else{
            for(i in output.indices){
                val pos=i.toDouble()*(input.size-1)/(output.size-1)
                val a=pos.toInt()
                val b=(a+1).coerceAtMost(input.lastIndex)
                val x=input[a].takeIf{it.isFinite()}?:0f
                val y=input[b].takeIf{it.isFinite()}?:0f
                output[i]=x+(y-x)*(pos-a).toFloat()
            }
        }
    }
}
