package com.vexo.studio.export

import com.vexo.studio.model.ExportSettings
import kotlin.math.roundToInt

object ExportEncoderPolicy {
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
