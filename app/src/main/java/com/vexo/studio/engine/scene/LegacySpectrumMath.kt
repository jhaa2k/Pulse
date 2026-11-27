package com.vexo.studio.engine.scene

import kotlin.math.PI
import kotlin.math.abs

object LegacySpectrumMath {
    /** Slot angle and width must use the same active arc. */
    fun arcStep(count:Int,gap:Float):Float {
        val safe=gap.takeIf{it.isFinite()}?.coerceIn(0f,.9f)?:0f
        return 2f*PI.toFloat()*(1f-safe)/count.coerceAtLeast(1)
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
