package com.vexo.studio.engine.scene

import kotlin.math.hypot

/** Fractional particle credits integrate emission rate rather than accumulated
 * seconds divided by the latest interval, avoiding false bursts on rate changes. */
class ParticleEmissionClock {
    private var credit=0.0
    fun reset(){credit=0.0}
    fun advance(dt:Float,interval:Float):Int {
        if(!dt.isFinite()||!interval.isFinite()||dt<=0f||interval<=0f)return 0
        credit+=dt.toDouble()/interval.toDouble()
        val whole=credit.toInt()
        credit-=whole
        return whole.coerceIn(0,24)
    }
}

/** Trails follow actual projected velocity and remain circular when stationary.
 * Normalize screen-space speed against the short canvas dimension. */
object ParticleTrailMath {
    fun stretch(amount:Float,vx:Float,vy:Float,w:Int,h:Int):Float {
        val a=amount.takeIf{it.isFinite()}?.coerceIn(0f,1f)?:0f
        if(a==0f||!vx.isFinite()||!vy.isFinite()||w<=0||h<=0)return 1f
        val speed=hypot(vx*w,vy*h)/minOf(w,h)
        return 1f+4f*a*(speed/.18f).coerceIn(0f,1f)
    }
}
