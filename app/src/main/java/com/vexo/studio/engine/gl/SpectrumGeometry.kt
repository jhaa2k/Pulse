package com.vexo.studio.engine.gl

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Geometry shared by the native preview and encoder renderer. */
object SpectrumGeometry {
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

    /** 16-triangle rounded rectangle; coordinates in canvas pixels. */
    fun roundedQuad(x0: Float,y0: Float,x1: Float,y1: Float,
                    x2: Float,y2: Float,x3: Float,y3: Float,roundness: Float): FloatArray {
        val cx = (x0+x1+x2+x3)*.25f
        val cy = (y0+y1+y2+y3)*.25f
        val dx = (x1+x3-x0-x2)*.5f
        val dy = (y1+y3-y0-y2)*.5f
        val ex = (x2+x3-x0-x1)*.5f
        val ey = (y2+y3-y0-y1)*.5f
        val width = sqrt(dx*dx+dy*dy).coerceAtLeast(.001f)
        val height = sqrt(ex*ex+ey*ey).coerceAtLeast(.001f)
        val ux=dx/width; val uy=dy/width
        val vx=ex/height; val vy=ey/height
        val hw=width*.5f; val hh=height*.5f
        val r=minOf(hw,hh)*roundness.coerceIn(0f,1f)
        val points=FloatArray(32)
        for (corner in 0..3) {
            val centerX = if(corner==0||corner==3) hw-r else -hw+r
            val centerY = if(corner<2) hh-r else -hh+r
            for(j in 0..3) {
                val a=(corner*90.0+j*30.0)*PI/180.0
                val px=centerX+r*cos(a).toFloat()
                val py=centerY+r*sin(a).toFloat()
                val k=(corner*4+j)*2
                points[k]=cx+ux*px+vx*py
                points[k+1]=cy+uy*px+vy*py
            }
        }
        val out=FloatArray(96)
        for(i in 0..15) {
            val j=(i+1)%16
            val k=i*6
            out[k]=cx;out[k+1]=cy
            out[k+2]=points[2*i];out[k+3]=points[2*i+1]
            out[k+4]=points[2*j];out[k+5]=points[2*j+1]
        }
        return out
    }
}
