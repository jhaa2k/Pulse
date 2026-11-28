package com.vexo.studio.ui.components

import kotlin.math.round

/** Shared numeric and gesture input policy. Never send NaN/Infinity to the renderer. */
object SliderValuePolicy {
    fun safe(value:Float,min:Float,max:Float,default:Float):Float =
        (value.takeIf{it.isFinite()}?:default.takeIf{it.isFinite()}?:min).coerceIn(min,max)
    fun parse(text:String):Float?=text.trim().replace(',','.').toFloatOrNull()?.takeIf{it.isFinite()}
    fun quantize(raw:Float,min:Float,max:Float,step:Float,default:Float):Float{
        val value=safe(raw,min,max,default)
        if(value==min||value==max||!step.isFinite()||step<=0f)return value
        val units=round((value.toDouble()-min.toDouble())/step.toDouble())
        return (min.toDouble()+units*step).toFloat().coerceIn(min,max)
    }
}
