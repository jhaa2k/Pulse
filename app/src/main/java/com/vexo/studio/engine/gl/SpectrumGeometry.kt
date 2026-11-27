package com.vexo.studio.engine.gl

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Geometry shared by the native preview and encoder renderer. */
object SpectrumGeometry {
    /** Exact integer mapping for unshifted samples; avoid rounding into the previous bin. */
    fun sampleIndex(i:Int,count:Int,inputCount:Int,repeats:Int,offset:Float):Int{
        if(count<=0||inputCount<=0)return 0
        val r=repeats.coerceIn(1,8)
        if(offset==0f||!offset.isFinite())return (((i.toLong()*r)%count+count)%count*inputCount/count).toInt()
        val phase=i.toDouble()/count*r+offset.coerceIn(-1f,1f)
        return ((phase-kotlin.math.floor(phase))*inputCount).toInt().coerceIn(0,inputCount-1)
    }

    fun resample(input: FloatArray, requested: Int): FloatArray {
        if (requested <= 0 || requested == input.size || input.isEmpty()) return input
        val n = requested.coerceIn(1, 512)
        return FloatArray(n) { i ->
            val p = if (n == 1) 0f else i.toFloat() * (input.size - 1) / (n - 1)
            val a = p.toInt()
            val b = (a + 1).coerceAtMost(input.lastIndex)
            input[a] + (input[b] - input[a]) * (p - a)
        }
    }

    // Error-bounded curves: maximum 48 segments per quarter, at most 588 fan vertices.
    const val MAX_ROUNDED_VERTICES = 4 * 49 * 3
    fun quarterSegments(radius:Float):Int {
        val r=radius.coerceAtLeast(.001f)
        val step=2.0*kotlin.math.acos((1.0-.10/r).coerceIn(-1.0,1.0))
        return kotlin.math.ceil((PI/2.0)/step.coerceAtLeast(.001)).toInt().coerceIn(12,48)
    }
    fun roundedContour(halfWidth:Float,halfHeight:Float,radius:Float):List<Pair<Float,Float>> {
        val hw=halfWidth.coerceAtLeast(0f);val hh=halfHeight.coerceAtLeast(0f)
        val r=radius.coerceIn(0f,minOf(hw,hh))
        if(r<=.0001f)return listOf(hw to hh,-hw to hh,-hw to -hh,hw to -hh)
        val n=quarterSegments(r)
        val centers=listOf((hw-r) to (hh-r),(-hw+r) to (hh-r),(-hw+r) to (-hh+r),(hw-r) to (-hh+r))
        val points=ArrayList<Pair<Float,Float>>(4*(n+1))
        for(k in 0..3)for(j in 0..n){
            val a=(k+j.toDouble()/n)*PI/2.0
            val q=(centers[k].first+r*cos(a).toFloat()) to (centers[k].second+r*sin(a).toFloat())
            val prev=points.lastOrNull()
            if(prev==null||kotlin.math.abs(prev.first-q.first)+kotlin.math.abs(prev.second-q.second)>.0001f)points.add(q)
        }
        val first=points.first();val last=points.last()
        if(kotlin.math.abs(first.first-last.first)+kotlin.math.abs(first.second-last.second)<.0001f)points.removeAt(points.lastIndex)
        return points
    }
    /** Convex rounded rectangle fan, without overlapping circle/rectangle fills. */
    fun roundedQuad(x0: Float,y0: Float,x1: Float,y1: Float,
                    x2: Float,y2: Float,x3: Float,y3: Float,roundness: Float): FloatArray {
        val cx=(x0+x1+x2+x3)*.25f;val cy=(y0+y1+y2+y3)*.25f
        val dx=(x1+x3-x0-x2)*.5f;val dy=(y1+y3-y0-y2)*.5f
        val ex=(x2+x3-x0-x1)*.5f;val ey=(y2+y3-y0-y1)*.5f
        val width=sqrt(dx*dx+dy*dy);val height=sqrt(ex*ex+ey*ey)
        if(width<=.0001f||height<=.0001f)return FloatArray(0)
        val ux=dx/width;val uy=dy/width;val vx=ex/height;val vy=ey/height
        val hw=width*.5f;val hh=height*.5f
        val points=roundedContour(hw,hh,minOf(hw,hh)*roundness.coerceIn(0f,1f))
        val out=FloatArray(points.size*6)
        fun put(i:Int,p:Pair<Float,Float>){out[i]=cx+ux*p.first+vx*p.second;out[i+1]=cy+uy*p.first+vy*p.second}
        for(i in points.indices){val k=i*6;out[k]=cx;out[k+1]=cy;put(k+2,points[i]);put(k+4,points[(i+1)%points.size])}
        return out
    }
}
