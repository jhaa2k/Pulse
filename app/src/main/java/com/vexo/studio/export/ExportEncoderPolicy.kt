package com.vexo.studio.export

import com.vexo.studio.model.ExportSettings
import kotlin.math.roundToInt

object ExportEncoderPolicy {
    /** Nearest even pixel count, without truncating an already rounded dimension. */
    fun dimensions(wRatio:Int,hRatio:Int,shortSide:Int):Pair<Int,Int>{
        require(wRatio>0&&hRatio>0&&shortSide>=320)
        fun even(v:Double)=((v/2).roundToInt()*2).coerceAtLeast(320)
        return if(wRatio<=hRatio)even(shortSide.toDouble()) to even(shortSide.toDouble()*hRatio/wRatio)
            else even(shortSide.toDouble()*wRatio/hRatio) to even(shortSide.toDouble())
    }
    fun samplePreferences(smooth:Boolean):List<Int> = if(smooth)listOf(4,2,0)else listOf(0)
    /** Large surfaces and high-FPS exports avoid expensive 4x multisample buffers.
     * Shader effects and exact output dimensions/FPS are unchanged. */
    fun surfaceSamples(smooth:Boolean,width:Int,height:Int,fps:Int):List<Int> =
        if(!smooth||width.toLong()*height>1920L*1080||fps>=50)listOf(0)else samplePreferences(true)

    val frameRates=listOf(24,25,30,50,60)
    fun preferredRate(available:List<Int>,requested:Int):Int? =
        available.takeIf{requested in it}?.let{requested}
            ?:available.filter{it<=requested}.maxOrNull()?:available.minOrNull()

    /** Resolution/FPS-based starting target; not a content-complexity bitrate estimator. */
    fun targetBitrate(s:ExportSettings):Int {
        if(!s.adaptiveVideoBitrate)return s.videoBitrateMbps.coerceIn(2,80)*1_000_000
        val short=s.resolution.shortSide.toDouble()
        val ratio=s.aspectRatio.widthRatio.toDouble()/s.aspectRatio.heightRatio
        val pixels=short*short*maxOf(ratio,1/ratio)
        return (pixels*s.fps.coerceIn(24,60)*.19).roundToInt().coerceIn(2_000_000,80_000_000)
    }
    /** Only automatic mode may adjust the requested target. Never alter size or rate. */
    fun fitBitrate(target:Int,min:Int,max:Int,automatic:Boolean):Int? {
        if(min<=0||max<min)return null
        val low=maxOf(min,2_000_000)
        val high=minOf(max,80_000_000)
        if(low>high)return null
        return if(automatic)target.coerceIn(low,high)else target.takeIf{it in low..high}
    }
}
