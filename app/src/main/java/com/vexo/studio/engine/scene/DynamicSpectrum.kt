package com.vexo.studio.engine.scene
import com.vexo.studio.engine.audio.AudioProviderSnapshot
import com.vexo.studio.model.*
import kotlin.math.floor

/** Evaluated from media position, so seeking or repeated exports do not accumulate drift. */
object DynamicSpectrum {
    fun evaluate(c:LayerConfig.Bars,audio:AudioProviderSnapshot?,positionMs:Long):LayerConfig.Bars{
        val speed=c.gradientSpeed.takeIf{it.isFinite()}?.coerceIn(-2f,2f)?:0f
        val stroke=c.reactiveStroke.takeIf{it.isFinite()}?.coerceIn(0f,16f)?:0f
        val mix=c.reactiveColorMix.takeIf{it.isFinite()}?.coerceIn(0f,1f)?:0f
        if(speed==0f&&stroke==0f&&mix==0f)return c
        val value=when(c.reactiveSource){
            SpectrumReactiveSource.BEAT->audio?.beatPulse
            SpectrumReactiveSource.BASS->audio?.bass
            SpectrumReactiveSource.MID->audio?.mid
            SpectrumReactiveSource.TREBLE->audio?.treble
            SpectrumReactiveSource.RMS->audio?.rms
            SpectrumReactiveSource.PEAK->audio?.peak
        }?.takeIf{it.isFinite()}?.coerceIn(0f,1f)?:0f
        val phase=c.colorBlendOffset+positionMs.coerceAtLeast(0L)/1000.0*speed
        fun color(col:Int)=if(mix>0f&&value>0f)MeasureEvaluator.blendArgb(col,c.reactiveColorArgb,mix*value)else col
        return c.copy(
            colorBlendOffset=if(speed!=0f)(phase-floor(phase)).toFloat() else c.colorBlendOffset,
            gradientCycle=c.gradientCycle||speed!=0f,
            barStrokeWidth=(c.barStrokeWidth+stroke*value).coerceIn(0f,24f),
            segment2StrokeWidth=(c.segment2StrokeWidth+stroke*value).coerceIn(0f,24f),
            creativeStrokeWidth=(c.creativeStrokeWidth+stroke*value).coerceIn(.25f,24f),
            colorFromArgb=color(c.colorFromArgb),colorToArgb=color(c.colorToArgb),
            segment2ColorFrom=color(c.segment2ColorFrom),segment2ColorTo=color(c.segment2ColorTo),
            barStrokeColorArgb=color(c.barStrokeColorArgb),segment2StrokeColorArgb=color(c.segment2StrokeColorArgb)
        )
    }
}
