package com.vexo.studio.engine.scene
import com.vexo.studio.model.*
import kotlinx.serialization.Serializable
import kotlin.math.roundToLong

enum class MotionRecipe(val label:String){
    FADE_IN("Fade in"),FADE_OUT("Fade out"),SLIDE_UP("Slide up"),POP("Pop"),SPIN("Spin"),PULSE("Pulse"),SHAKE("Shake"),BOUNCE("Bounce")
}
@Serializable data class RelativeMotionTrack(val propertyKey:String,val keys:List<Keyframe>)
@Serializable data class SavedMotionPreset(val name:String,val durationMs:Long,val tracks:List<RelativeMotionTrack>)
object MotionPresets {
    fun builtin(recipe:MotionRecipe,layer:LayerCommon,start:Long,duration:Long,strength:Float):List<KeyframeTrack>{
        val s=strength.coerceIn(0f,2f);val d=duration.coerceIn(100,60000);val at=start.coerceIn(0,86400000)
        val ease=InterpolationType.EASE_OUT
        fun track(key:String,points:List<Triple<Float,Float,InterpolationType>>):KeyframeTrack{
            val frames=points.map{(p,v,m)->Keyframe(at+(p*d).roundToLong(),v,m)}
            val baseline=TransformAnimation.value(layer,key)
            return KeyframeTrack(layer.id,"transform."+key,(if(at>0)listOf(Keyframe(0,baseline,InterpolationType.HOLD))+frames else frames).distinctBy{it.timeMs}.sortedBy{it.timeMs})
        }
        fun pt(t:Float,v:Float,m:InterpolationType=ease)=Triple(t,v,m)
        return when(recipe){
            MotionRecipe.FADE_IN->listOf(track("opacity",listOf(pt(0f,0f),pt(1f,layer.opacity))))
            MotionRecipe.FADE_OUT->listOf(track("opacity",listOf(pt(0f,layer.opacity),pt(1f,0f))))
            MotionRecipe.SLIDE_UP->listOf(track("posY",listOf(pt(0f,layer.posY+.2f*s),pt(1f,layer.posY))))
            MotionRecipe.POP->listOf("scaleX","scaleY").map{key->val b=TransformAnimation.value(layer,key);track(key,listOf(pt(0f,b*(1f-.5f*s).coerceAtLeast(.05f),InterpolationType.BACK_OUT),pt(1f,b)))}
            MotionRecipe.SPIN->listOf(track("rotationDeg",listOf(pt(0f,layer.rotationDeg-360f*s,InterpolationType.EASE_IN_OUT),pt(1f,layer.rotationDeg))))
            MotionRecipe.PULSE->listOf("scaleX","scaleY").map{key->val b=TransformAnimation.value(layer,key);track(key,listOf(pt(0f,b,InterpolationType.EASE_IN_OUT),pt(.5f,b*(1f+.25f*s),InterpolationType.EASE_IN_OUT),pt(1f,b)))}
            MotionRecipe.SHAKE->listOf(track("posX",listOf(pt(0f,layer.posX,InterpolationType.LINEAR),pt(.2f,layer.posX-.03f*s,InterpolationType.LINEAR),pt(.4f,layer.posX+.03f*s,InterpolationType.LINEAR),pt(.6f,layer.posX-.02f*s,InterpolationType.LINEAR),pt(.8f,layer.posX+.01f*s,InterpolationType.LINEAR),pt(1f,layer.posX))))
            MotionRecipe.BOUNCE->listOf(track("posY",listOf(pt(0f,layer.posY-.25f*s,InterpolationType.BOUNCE_OUT),pt(1f,layer.posY))))
        }
    }
    fun capture(name:String,tracks:List<KeyframeTrack>):SavedMotionPreset?{
        val valid=tracks.filter{it.propertyKey.removePrefix("transform.") in TransformAnimation.keys && it.propertyKey.startsWith("transform.")&&it.keyframes.isNotEmpty()}
        val all=valid.flatMap{it.keyframes}
        val start=all.minOfOrNull{it.timeMs}?:return null
        val end=all.maxOf{it.timeMs}
        if(start<0||end>86400000||valid.size>6||valid.any{it.keyframes.size>500}||all.any{!it.value.isFinite()})return null
        return SavedMotionPreset(name.trim().take(60),maxOf(1L,end-start),valid.map{RelativeMotionTrack(it.propertyKey,it.keyframes.sortedBy{f->f.timeMs}.map{f->f.copy(timeMs=f.timeMs-start)})})
    }
    fun applySaved(p:SavedMotionPreset,layer:LayerCommon,start:Long,duration:Long):List<KeyframeTrack>{
        val at=start.coerceIn(0,86400000)
        val span=duration.coerceIn(100,60000)
        return p.tracks.filter{it.propertyKey.startsWith("transform.")&&it.propertyKey.removePrefix("transform.") in TransformAnimation.keys}.take(6).mapNotNull{t->
            val frames=t.keys.filter{it.value.isFinite()}.take(500).map{f->f.copy(timeMs=at+(f.timeMs.coerceIn(0,p.durationMs.coerceAtLeast(1)).toDouble()/p.durationMs.coerceAtLeast(1)*span).roundToLong())}
            if(frames.isEmpty())null else KeyframeTrack(layer.id,t.propertyKey,((if(at>0)listOf(Keyframe(0,TransformAnimation.value(layer,t.propertyKey.removePrefix("transform.")),InterpolationType.HOLD))else emptyList())+frames).distinctBy{it.timeMs}.sortedBy{it.timeMs}.take(500))
        }
    }
    fun replace(existing:List<KeyframeTrack>,incoming:List<KeyframeTrack>):List<KeyframeTrack>{
        val ids=incoming.map{it.targetLayerId to it.propertyKey}.toSet()
        return existing.filterNot{(it.targetLayerId to it.propertyKey) in ids}+incoming
    }
}
