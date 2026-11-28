package com.vexo.studio.engine.scene

import kotlin.math.PI
import kotlin.math.abs

object LegacySpectrumMath {
    /** Slot angle and width must use the same active arc. */
    fun arcStep(count:Int,gap:Float):Float {
        val safe=gap.takeIf{it.isFinite()}?.coerceIn(0f,.9f)?:0f
        return 2f*PI.toFloat()*(1f-safe)/count.coerceAtLeast(1)
    }
    /** Explicit false uses a stable reference width; null keeps the native path behavior. */
    fun slotWidth(pathSpacing:Float,referenceDimension:Float,count:Int,affected:Boolean?):Float{
        val reference=(referenceDimension.takeIf{it.isFinite()}?.coerceAtLeast(0f)?:0f)/count.coerceAtLeast(1)
        return if(affected==false)reference else pathSpacing.takeIf{it.isFinite()}?.coerceAtLeast(0f)?:reference
    }
    /** Local polygon edge arc-length derivative (apothem / cos² angle). */
    fun polygonSpacing(radius:Float,angle:Float,sides:Int,step:Float):Float{
        val sector=2f*PI.toFloat()/sides.coerceIn(3,24)
        val local=((angle%sector)+sector)%sector-sector*.5f
        val cos=kotlin.math.cos(local).coerceAtLeast(.2f)
        return radius.coerceAtLeast(0f)*kotlin.math.cos(sector*.5f)/(cos*cos)*step
    }
    /** A line and its neighboring bar segment use identical signed height limits. */
    fun signedAmplitude(sample:Float,gain:Float,min:Float,max:Float):Float {
        val g=gain.takeIf{it.isFinite()}?:0f
        if(g==0f)return 0f
        val a=abs(min.takeIf{it.isFinite()}?:0f)
        val b=abs(max.takeIf{it.isFinite()}?:1f)
        val level=sample.takeIf{it.isFinite()}?.coerceAtLeast(0f)?:0f
        return (level*abs(g)).coerceIn(minOf(a,b),maxOf(a,b))*(if(g<0f)-1f else 1f)
    }
}
