package com.vexo.studio.engine.scene
import com.vexo.studio.model.MeasureConfig
import com.vexo.studio.model.MeasureWhat
import kotlin.math.exp

/** Local to each renderer; independent layer/property state, reset on seek and project changes. */
class ReactiveMeasureFilter {
    private data class Entry(val config:MeasureConfig,val pair:EvaluatedPair)
    private val history=mutableMapOf<Pair<String,String>,Entry>()
    private val continuous=setOf(MeasureWhat.BEAT,MeasureWhat.BASS,MeasureWhat.MID,MeasureWhat.TREBLE,MeasureWhat.RMS,MeasureWhat.PEAK)
    fun apply(id:String,key:String,c:MeasureConfig,raw:EvaluatedPair,deltaSec:Float):EvaluatedPair{
        val k=id to key
        val safe=EvaluatedPair(raw.x.takeIf{it.isFinite()}?:0f,raw.y.takeIf{it.isFinite()}?:0f)
        val smoothing=c.smoothing.takeIf{it.isFinite()}?.coerceIn(0f,1f)?:0f
        // Keep time, keyframes, shake and beat-trigger pulses immediate.
        if(c.measureWhat !in continuous||smoothing<=0f){history.remove(k);return safe}
        val prior=history[k]?.takeIf{it.config==c}?.pair?:safe
        val dt=deltaSec.takeIf{it.isFinite()}?.coerceIn(0f,.25f)?:0f
        val amount=1f-exp(-dt/(.01f+.29f*smoothing*smoothing))
        val pair=EvaluatedPair(prior.x+(safe.x-prior.x)*amount,prior.y+(safe.y-prior.y)*amount)
        history[k]=Entry(c,pair)
        return pair
    }
    fun retain(ids:Set<String>){history.keys.removeAll{it.first !in ids}}
    fun clear(){history.clear()}
}
