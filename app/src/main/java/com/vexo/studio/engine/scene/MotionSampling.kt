package com.vexo.studio.engine.scene
import com.vexo.studio.model.LayerConfig
import com.vexo.studio.model.MotionSource
import kotlin.math.*

data class MotionPose(val x:Float,val y:Float,val degrees:Float,val scaleX:Float,val scaleY:Float)
data class MotionSample(val x:Float,val y:Float,val radians:Float,val zoom:Float)
object MotionSampling {
    /** Relative transform deltas describe motion over a 60 Hz reference exposure.
     * Manual displacement and absolute transforms are already frame-independent. */
    fun evaluate(c:LayerConfig.MotionBlurEffect,pose:MotionPose,previous:MotionPose?,deltaSec:Float=1f/60f):MotionSample{
        val gain=c.blurAmountMultiplier.coerceIn(0f,3f)
        val manual=c.motionSource==MotionSource.MANUAL
        val old=previous?:pose
        val x=if(manual)c.manualMotionX else if(c.relativeMotionMode)pose.x-old.x else pose.x-.5f
        val y=if(manual)c.manualMotionY else if(c.relativeMotionMode)pose.y-old.y else pose.y-.5f
        val rotation=if(manual)c.manualRotationDeg else if(c.includeRotationAndZoom){
            val d=if(c.relativeMotionMode)pose.degrees-old.degrees else pose.degrees
            ((d+180f)%360f+360f)%360f-180f
        }else 0f
        fun size(p:MotionPose)=sqrt(maxOf(.0001f,abs(p.scaleX*p.scaleY)))
        val zoom=if(manual)c.manualZoom else if(c.includeRotationAndZoom)
            ln((size(pose)/if(c.relativeMotionMode)size(old) else 1f).coerceIn(.1f,10f)) else 0f
        val exposure=if(!manual&&c.relativeMotionMode)
            (1f/60f)/(deltaSec.takeIf{it.isFinite()}?.coerceIn(.001f,.25f)?:1f/60f)
            else 1f
        fun safe(v:Float,limit:Float)=(v*gain*exposure).takeIf{it.isFinite()}?.coerceIn(-limit,limit)?:0f
        return MotionSample(safe(x,.25f),safe(y,.25f),safe(rotation*PI.toFloat()/180f,PI.toFloat()/2f),safe(zoom,.7f))
    }
}
