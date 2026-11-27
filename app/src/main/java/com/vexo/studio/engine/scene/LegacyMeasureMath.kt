package com.vexo.studio.engine.scene

import com.vexo.studio.model.MeasureConfig
import com.vexo.studio.model.MeasureWhat
import kotlin.math.cos
import kotlin.math.sin

/** Compatibility approximation for structured Avee measures whose metadata labels
 * A as Amount (or Speed) and B as Speed (or Beat Amount), not X and Y gains.
 * Native measures retain their original semantics. */
object LegacyMeasureMath {
    val cyclicColorSources=setOf(MeasureWhat.TOTAL_TIME,MeasureWhat.TOTAL_TIME_BACKWARD,
        MeasureWhat.TOTAL_TIME_WHEN_PLAYING,MeasureWhat.TOTAL_TIME_AND_BEAT,MeasureWhat.TRACK_POSITION)

    fun evaluate(m:MeasureConfig,time:Float,beat:Float):EvaluatedPair? {
        val a=m.amountA.takeIf{it.isFinite()}?:0f
        val b=m.amountB.takeIf{it.isFinite()}?:0f
        val t=time.takeIf{it.isFinite()}?:0f
        val energy=beat.takeIf{it.isFinite()}?.coerceIn(0f,1.5f)?:0f
        if(m.measureWhat==MeasureWhat.TOTAL_TIME_AND_BEAT){
            val value=(t*a+energy*b).takeIf{it.isFinite()}?:0f
            return EvaluatedPair(value,value)
        }
        val type=m.measureWhat
        val rotation=type in setOf(MeasureWhat.BEAT_CAM_SHAKE_ROT_MORE,MeasureWhat.BEAT_CAM_SHAKE_ROT_LESS,
            MeasureWhat.CONSTANT_SHAKE_ROT_MORE,MeasureWhat.CONSTANT_SHAKE_ROT_LESS)
        val gain=when(type){
            MeasureWhat.BEAT_RANDOM_SHAKE -> energy
            MeasureWhat.BEAT_CAM_SHAKE_MORE -> energy*1.65f
            MeasureWhat.BEAT_CAM_SHAKE_LESS -> energy*.5f
            MeasureWhat.BEAT_CAM_SHAKE_ROT_MORE -> energy*1.2f
            MeasureWhat.BEAT_CAM_SHAKE_ROT_LESS -> energy*.4f
            MeasureWhat.CONSTANT_SHAKE -> 1f
            MeasureWhat.CONSTANT_SHAKE_MORE -> 1.6f
            MeasureWhat.CONSTANT_SHAKE_ROT_MORE -> 1f
            MeasureWhat.CONSTANT_SHAKE_ROT_LESS -> .3f
            else -> return null
        }
        if(gain==0f||a==0f)return EvaluatedPair(0f,0f)
        val phase=t.toDouble()*17.3*b.coerceIn(-100f,100f)+m.seed*.137
        val x=(sin(phase)*.6+cos(phase*1.73)*.4).toFloat()
        val y=(cos(phase*1.19)*.6+sin(phase*2.11)*.4).toFloat()
        // Bound extreme imported amounts before moving layers off-canvas.
        val amplitude=if(rotation)a.coerceIn(-10f,10f)*gain else a.coerceIn(-25f,25f)*.01f*gain
        return EvaluatedPair(x*amplitude,(if(rotation)x else y)*amplitude)
    }
}
