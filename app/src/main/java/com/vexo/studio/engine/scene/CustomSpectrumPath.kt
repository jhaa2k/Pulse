package com.vexo.studio.engine.scene

import com.vexo.studio.model.SpectrumPathPoint
import kotlin.math.sqrt

/** Bounded arc-length polyline: spectrum samples are evenly spaced along its length. */
class CustomSpectrumPath private constructor(
    private val points:List<SpectrumPathPoint>, private val cumulative:FloatArray
) {
    fun at(t:Float):SpectrumPathPoint {
        if(!t.isFinite()||t<=0f)return points.first()
        if(t>=1f)return points.last()
        val distance=t*cumulative.last()
        var i=1
        while(i<cumulative.lastIndex && cumulative[i]<distance)i++
        val a=points[i-1];val b=points[i]
        val fraction=((distance-cumulative[i-1])/(cumulative[i]-cumulative[i-1])).coerceIn(0f,1f)
        return SpectrumPathPoint(a.x+(b.x-a.x)*fraction,a.y+(b.y-a.y)*fraction)
    }
    companion object {
        fun create(input:List<SpectrumPathPoint>,closed:Boolean):CustomSpectrumPath? {
            val clean=ArrayList<SpectrumPathPoint>()
            for(p in input.take(32)){
                if(!p.x.isFinite()||!p.y.isFinite())continue
                val safe=SpectrumPathPoint(p.x.coerceIn(-.5f,.5f),p.y.coerceIn(-.5f,.5f))
                if(clean.lastOrNull()!=safe)clean.add(safe)
            }
            if(clean.size<2)return null
            if(closed&&clean.last()!=clean.first())clean.add(clean.first())
            val lengths=FloatArray(clean.size)
            for(i in 1 until clean.size){
                val dx=clean[i].x-clean[i-1].x;val dy=clean[i].y-clean[i-1].y
                lengths[i]=lengths[i-1]+sqrt(dx*dx+dy*dy)
                if(lengths[i]<=lengths[i-1])return null
            }
            return CustomSpectrumPath(clean,lengths)
        }
    }
}
