package com.vexo.studio.engine.scene

import com.vexo.studio.engine.audio.AudioProviderSnapshot
import com.vexo.studio.model.ProjectCamera
import com.vexo.studio.model.SpectrumReactiveSource
import kotlin.math.*

data class CameraPose(val zoom:Float,val offsetX:Float,val offsetY:Float,val rotationDeg:Float)

/** Pure media-time camera evaluator, independent of editor-only pan/zoom. */
object CinematicCamera {
    fun evaluate(c:ProjectCamera,a:AudioProviderSnapshot?,positionMs:Long,width:Int,height:Int):CameraPose{
        if(!c.enabled)return CameraPose(1f,0f,0f,0f)
        fun finite(v:Float,default:Float=0f)=v.takeIf{it.isFinite()}?:default
        val energy=finite(when(c.source){
            SpectrumReactiveSource.BEAT->a?.beatPulse?:0f
            SpectrumReactiveSource.BASS->a?.bass?:0f
            SpectrumReactiveSource.MID->a?.mid?:0f
            SpectrumReactiveSource.TREBLE->a?.treble?:0f
            SpectrumReactiveSource.RMS->a?.rms?:0f
            SpectrumReactiveSource.PEAK->a?.peak?:0f
        }).coerceIn(0f,1f)
        val time=positionMs.coerceAtLeast(0L)/1000.0
        val phase=time*finite(c.frequency,3f).coerceIn(.1f,12f)*2.0*PI+c.seed*.137
        val sx=(sin(phase)*.65+sin(phase*1.73)*.35).toFloat()
        val sy=(cos(phase*1.19)*.65+sin(phase*2.11)*.35).toFloat()
        val dim=minOf(width,height).coerceAtLeast(1).toFloat()
        val strength=finite(c.shake).coerceIn(0f,.15f)*energy
        val drift=finite(c.drift).coerceIn(0f,.12f)
        val x=dim*(strength*sx+drift*sin(time*.65).toFloat())
        val y=dim*(strength*sy+drift*cos(time*.53).toFloat())
        val rotation=finite(c.rollDeg).coerceIn(0f,20f)*energy*sx
        var zoom=(finite(c.baseZoom,1f).coerceIn(.5f,3f)+finite(c.pulseZoom).coerceIn(0f,1f)*energy).coerceIn(.5f,4f)
        if(c.autoOverscan){
            val w=width.coerceAtLeast(1).toFloat();val h=height.coerceAtLeast(1).toFloat()
            val theta=rotation*PI.toFloat()/180f
            val cs=abs(cos(theta));val sn=abs(sin(theta))
            val rw=cs*(w+2f*abs(x))+sn*(h+2f*abs(y))
            val rh=sn*(w+2f*abs(x))+cs*(h+2f*abs(y))
            zoom=maxOf(zoom,rw/w,rh/h)
        }
        return CameraPose(zoom,x,y,rotation)
    }
}
