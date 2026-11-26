package com.vexo.studio.engine.scene

import com.vexo.studio.model.*
import kotlin.math.*

object CreativeGeometry {
    data class Point(val x:Float,val y:Float)
    fun path(kind:CreativeSpectrumPath, t:Float, turns:Float, spread:Float, radius:Float, gap:Float, angle:Float):Point {
        val u=t.coerceIn(0f,1f)
        val a=(u*2f*PI.toFloat()*(1f-gap.coerceIn(0f,.9f))+angle*PI.toFloat()/180f)
        val r=radius.coerceIn(.01f,.8f)
        val z=spread.coerceIn(0f,1f)
        return when(kind){
            CreativeSpectrumPath.ARC -> Point(cos(a*0.75f)*r,sin(a*0.75f)*r)
            CreativeSpectrumPath.SPIRAL -> {val b=a*turns.coerceIn(.25f,5f);val d=r*(.12f+.88f*u);Point(cos(b)*d,sin(b)*d)}
            CreativeSpectrumPath.HEART -> Point(r*sin(a).pow(3),-r*(13*cos(a)-5*cos(2*a)-2*cos(3*a)-cos(4*a))/17f)
            CreativeSpectrumPath.SINE -> Point((u-.5f)*.86f,sin(a*turns.coerceIn(.25f,5f))*z*.45f)
            CreativeSpectrumPath.LEGACY -> Point((u-.5f)*.86f,0f)
        }
    }
    fun emitter(kind:ParticleEmitter,u:Float,v:Float,cx:Float,cy:Float,w:Float,h:Float):Point {
        val a=u*2f*PI.toFloat()
        val r=if(kind==ParticleEmitter.DISC)sqrt(v.coerceIn(0f,1f)) else 1f
        val (x,y)=when(kind){
            ParticleEmitter.POINT->0f to 0f
            ParticleEmitter.LINE->(u-.5f) to 0f
            ParticleEmitter.RING,ParticleEmitter.DISC->(cos(a)*r*.5f) to (sin(a)*r*.5f)
            else->(u-.5f) to (v-.5f)
        }
        return Point(cx+x*w,cy+y*h)
    }
    fun animationProgress(timeMs:Long,delay:Float,duration:Float,loop:Boolean):Float{
        val elapsed=timeMs/1000.0-delay
        if(elapsed<=0)return 0f
        val d=duration.coerceAtLeast(.05f)
        return if(loop)(elapsed%d/d).toFloat() else (elapsed/d).toFloat().coerceIn(0f,1f)
    }
}
