package com.vexo.studio.engine.gl
import kotlin.math.pow
object MotionMath {
    fun persistence(value:Float,deltaSec:Float):Float =
        value.coerceIn(0f,.95f).pow(deltaSec.coerceIn(.001f,.1f)*60f)
}
