package com.vexo.studio.engine.scene
import com.vexo.studio.engine.audio.AudioProviderSnapshot
import com.vexo.studio.model.ParticleBurstSource

/** A hysteretic rising-edge gate, with a configurable minimum interval between bursts. */
class ParticleBurstGate {
    private var armed=true
    private var remaining=0f
    fun reset(){armed=true;remaining=0f}
    fun update(value:Float,threshold:Float,cooldownMs:Int,dt:Float,playing:Boolean):Boolean{
        if(!playing){reset();return false}
        remaining=(remaining-dt.coerceAtLeast(0f)).coerceAtLeast(0f)
        val level=threshold.coerceIn(.05f,1f)
        if(value<level*.65f)armed=true
        if(armed&&value>=level&&remaining<=0f){
            armed=false
            remaining=cooldownMs.coerceIn(0,1000)/1000f
            return true
        }
        return false
    }
    companion object{
        fun signal(source:ParticleBurstSource,s:AudioProviderSnapshot?):Float{
            if(s==null)return 0f
            val v=when(source){
                ParticleBurstSource.BEAT_ENERGY->s.beat
                ParticleBurstSource.BEAT_PULSE->s.beatPulse
                ParticleBurstSource.BASS->s.bass
                ParticleBurstSource.MID->s.mid
                ParticleBurstSource.TREBLE->s.treble
                ParticleBurstSource.RMS->s.rms
                ParticleBurstSource.PEAK->s.peak
            }
            return v.takeIf{it.isFinite()}?.coerceIn(0f,1f)?:0f
        }
    }
}
