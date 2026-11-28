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
            CreativeSpectrumPath.CIRCLE -> Point(cos(a)*r,sin(a)*r)
            CreativeSpectrumPath.OVAL -> Point(cos(a)*r,sin(a)*r*(.35f+.65f*z))
            CreativeSpectrumPath.TRIANGLE -> polygon(u,gap,angle,r,3,false,z)
            CreativeSpectrumPath.SQUARE -> polygon(u,gap,angle,r,4,false,z)
            CreativeSpectrumPath.HEXAGON -> polygon(u,gap,angle,r,6,false,z)
            CreativeSpectrumPath.STAR -> polygon(u,gap,angle,r,5,true,z)
            CreativeSpectrumPath.FLOWER -> {val d=r*(.65f+.35f*cos(a*5f));Point(cos(a)*d,sin(a)*d)}
            CreativeSpectrumPath.FIGURE_EIGHT -> Point(r*sin(a),r*sin(2*a)*.5f)
            CreativeSpectrumPath.BUTTERFLY -> {val d=r*(.55f+.32f*cos(2*a)+.13f*sin(5*a));Point(sin(a)*d,cos(a)*d)}
            CreativeSpectrumPath.LISSAJOUS -> Point(r*sin(3*a+PI.toFloat()*.5f),r*sin(2*a))
            CreativeSpectrumPath.DIAGONAL -> Point((u-.5f)*.8f,(u-.5f)*z*.8f)
            CreativeSpectrumPath.ZIGZAG -> {val f=(u*turns.coerceIn(1f,5f))%1f;Point((u-.5f)*.86f,(1f-4f*abs(f-.5f))*z*.35f)}
        }
    }
    private fun polygon(u:Float,gap:Float,angle:Float,r:Float,sides:Int,star:Boolean,spread:Float):Point{
        val n=if(star)sides*2 else sides
        val q=u*n*(1f-gap.coerceIn(0f,.9f))
        val i=floor(q).toInt();val t=q-i
        fun vertex(j:Int):Point{
            val a=angle*PI.toFloat()/180f+j*2f*PI.toFloat()/n-PI.toFloat()*.5f
            val d=if(star&&j%2!=0)r*(.25f+.5f*spread) else r
            return Point(cos(a)*d,sin(a)*d)
        }
        val p=vertex(i);val b=vertex(i+1)
        return Point(p.x+(b.x-p.x)*t,p.y+(b.y-p.y)*t)
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
