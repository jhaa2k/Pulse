package com.vexo.studio.engine.gl
import kotlin.math.pow
object MotionMath {
    fun persistence(value:Float,deltaSec:Float):Float =
        (value.takeIf{it.isFinite()}?.coerceIn(0f,.95f)?:0f)
            .pow((deltaSec.takeIf{it.isFinite()}?.coerceIn(.001f,.25f)?:1f/60f)*60f)
}
