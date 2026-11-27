package com.vexo.studio.engine.audio
import kotlin.math.*

/** Fresh analysis windows only. A short fast envelope detects rises relative to
 * a slower baseline; an absolute floor rejects very quiet noise. */
class TransientBeatDetector{
    private var baseline=0f
    private var prior=0f
    private var remaining=0f
    private var armed=true
    fun reset(){baseline=0f;prior=0f;remaining=0f;armed=true}
    fun update(energy:Float,dtMs:Float,sensitivity:Float,cooldownMs:Int):Boolean{
        val value=energy.takeIf{it.isFinite()}?.coerceIn(0f,64f)?:0f
        val dt=dtMs.takeIf{it.isFinite()}?.coerceIn(1f,250f)?:16.67f
        remaining=(remaining-dt).coerceAtLeast(0f)
        val sens=sensitivity.takeIf{it.isFinite()}?.coerceIn(.1f,4f)?:1f
        val delta=value-prior
        val floor=maxOf(.025f,baseline*.12f)/sens
        val score=(value-baseline)/maxOf(.04f,baseline*.5f)*sens
        if(delta<=0f||score<.35f)armed=true
        val fire=armed&&remaining<=0f&&value>.035f&&delta>floor&&score>.65f
        if(fire){armed=false;remaining=cooldownMs.coerceIn(40,300).toFloat()}
        val time=if(value>baseline)500f else 180f
        baseline+=(value-baseline)*(1f-exp(-dt/time))
        prior=value
        return fire
    }
}
object AudioDynamics{
    /** Blend peak and RMS amplitudes instead of diluting a narrow tone by the
     * entire bandwidth. Uses a normalized compressive mapping, not a loudness meter. */
    fun bandEnergy(bins:FloatArray,hzPerBin:Float,lo:Float,hi:Float,gain:Float):Float{
        if(bins.size<2||!hzPerBin.isFinite()||hzPerBin<=0f||lo>=bins.size*hzPerBin)return 0f
        val start=ceil(lo/hzPerBin).toInt().coerceIn(1,bins.lastIndex)
        val end=floor(hi/hzPerBin).toInt().coerceIn(start,bins.lastIndex)
        var peak=0f;var sum=0.0
        for(i in start..end){
            val v=bins[i].takeIf{it.isFinite()}?.coerceIn(0f,2f)?:0f
            peak=maxOf(peak,v);sum+=v.toDouble()*v
        }
        val rms=sqrt(sum/(end-start+1)).toFloat()
        val g=gain.takeIf{it.isFinite()}?.coerceIn(.25f,3f)?:1f
        return (ln(1f+(peak*.6f+rms*.4f)*12f*g)/ln(13f)).coerceIn(0f,1f)
    }
}
