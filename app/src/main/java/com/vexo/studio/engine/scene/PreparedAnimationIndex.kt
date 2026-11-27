package com.vexo.studio.engine.scene

import com.vexo.studio.model.Keyframe
import com.vexo.studio.model.KeyframeTrack

/** Immutable per-project animation index. Last valid duplicate timestamp wins;
 * invalid values are ignored, and identical property tracks retain the first track. */
class PreparedKeyframeTrack private constructor(private val frames:List<Keyframe>){
    fun evaluate(timeMs:Long,defaultValue:Float):Float{
        if(frames.isEmpty())return defaultValue
        if(timeMs<=frames.first().timeMs)return frames.first().value
        if(timeMs>=frames.last().timeMs)return frames.last().value
        var lo=0;var hi=frames.lastIndex
        while(hi-lo>1){
            val mid=(lo+hi) ushr 1
            if(frames[mid].timeMs<=timeMs)lo=mid else hi=mid
        }
        val a=frames[lo];val b=frames[hi]
        if(timeMs==a.timeMs)return a.value
        // Double subtraction also avoids Long overflow with malformed imported timestamps.
        val span=b.timeMs.toDouble()-a.timeMs.toDouble()
        val t=((timeMs.toDouble()-a.timeMs.toDouble())/span).toFloat().coerceIn(0f,1f)
        val ease=AnimationCurves.ease(a.interpolation,t)
        return (a.value.toDouble()+(b.value.toDouble()-a.value.toDouble())*ease).toFloat().takeIf{it.isFinite()}?:defaultValue
    }
    companion object{
        fun create(track:KeyframeTrack?):PreparedKeyframeTrack{
            val clean=linkedMapOf<Long,Keyframe>()
            track?.keyframes?.forEach{if(it.value.isFinite())clean[it.timeMs]=it}
            return PreparedKeyframeTrack(clean.values.sortedBy{it.timeMs})
        }
    }
}
class PreparedLayerTracks private constructor(private val tracks:Map<String,PreparedKeyframeTrack>){
    val hasProperties=tracks.keys.any{it.startsWith("property.")}
    fun contains(key:String)=key in tracks
    fun evaluate(key:String,timeMs:Long,default:Float)=tracks[key]?.evaluate(timeMs,default)?:default
    companion object{
        val EMPTY=PreparedLayerTracks(emptyMap())
        fun create(input:List<KeyframeTrack>):PreparedLayerTracks{
            if(input.isEmpty())return EMPTY
            val map=linkedMapOf<String,PreparedKeyframeTrack>()
            input.forEach{t->if(t.propertyKey !in map)map[t.propertyKey]=PreparedKeyframeTrack.create(t)}
            return PreparedLayerTracks(map)
        }
    }
}
class PreparedAnimationIndex private constructor(private val layers:Map<String,PreparedLayerTracks>){
    fun forLayer(id:String)=layers[id]?:PreparedLayerTracks.EMPTY
    companion object{
        val EMPTY=PreparedAnimationIndex(emptyMap())
        fun create(tracks:List<KeyframeTrack>):PreparedAnimationIndex=if(tracks.isEmpty())EMPTY else PreparedAnimationIndex(tracks.groupBy{it.targetLayerId}.mapValues{PreparedLayerTracks.create(it.value)})
    }
}
