package com.vexo.studio.engine.scene
import com.vexo.studio.model.InterpolationType
import kotlin.math.*
object AnimationCurves {
    fun ease(mode:InterpolationType,fraction:Float):Float {
        val t=fraction.coerceIn(0f,1f)
        if(t<=0f)return 0f
        if(t>=1f)return 1f
        return when(mode){
            InterpolationType.HOLD->0f
            InterpolationType.LINEAR->t
            InterpolationType.EASE_IN_OUT->t*t*(3f-2f*t)
            InterpolationType.EASE_IN->t*t*t
            InterpolationType.EASE_OUT->1f-(1f-t).pow(3)
            InterpolationType.SINE_IN_OUT->(.5f-.5f*cos(PI.toFloat()*t))
            InterpolationType.BACK_OUT->{val x=t-1f;1f+2.70158f*x*x*x+1.70158f*x*x}
            InterpolationType.BOUNCE_OUT->when{
                t<1f/2.75f->7.5625f*t*t
                t<2f/2.75f->{val x=t-1.5f/2.75f;7.5625f*x*x+.75f}
                t<2.5f/2.75f->{val x=t-2.25f/2.75f;7.5625f*x*x+.9375f}
                else->{val x=t-2.625f/2.75f;7.5625f*x*x+.984375f}
            }
        }
    }
}
