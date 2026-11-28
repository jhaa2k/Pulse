package com.vexo.studio.playback

import android.content.Context
import com.vexo.studio.model.AudioTrackItem
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

internal data class QueueResume(val tracks:List<AudioTrackItem>,val index:Int,val positionMs:Long)
internal object QueueResumeMath {
    fun restore(tracks:List<AudioTrackItem>,id:String?,position:Long):QueueResume{
        val index=tracks.indexOfFirst{it.id==id}.takeIf{it>=0}?:if(tracks.isEmpty())-1 else 0
        val duration=tracks.getOrNull(index)?.durationMs?:0L
        val pos=if(index<0||tracks[index].id!=id)0L else position.coerceIn(0L,if(duration>0)duration else Long.MAX_VALUE)
        return QueueResume(tracks,index,pos)
    }
}
/** Private project-specific queue. Never resumes playback automatically. */
internal class ProjectQueueStore(private val context:Context){
    private val serializer=ListSerializer(AudioTrackItem.serializer())
    private fun valid(id:String)=runCatching{UUID.fromString(id).toString()==id}.getOrDefault(false)
    private fun prefs(id:String)=context.getSharedPreferences("queue_"+id,Context.MODE_PRIVATE)
    fun read(id:String):QueueResume?=runCatching{
        if(!valid(id))return null
        val p=prefs(id)
        val raw=p.getString("tracks",null)?:return null
        require(raw.length<=4_000_000)
        val tracks=Json.decodeFromString(serializer,raw).take(5000).filter{it.uri.isNotBlank()}.distinctBy{it.id}
        QueueResumeMath.restore(tracks,p.getString("selected",null),p.getLong("position",0))
    }.getOrNull()
    fun save(id:String,tracks:List<AudioTrackItem>,currentId:String?,position:Long,includeTracks:Boolean){
        if(!valid(id))return
        val editor=prefs(id).edit()
        if(includeTracks){
            val encoded=Json.encodeToString(serializer,tracks.take(5000))
            require(encoded.length<=4_000_000){"Queue exceeds save limit"}
            editor.putString("tracks",encoded)
        }
        editor.putString("selected",currentId).putLong("position",position.coerceAtLeast(0)).apply()
    }
    fun copy(from:String,to:String){
        val source=read(from)?:return
        save(to,source.tracks,source.tracks.getOrNull(source.index)?.id,source.positionMs,true)
    }
    fun delete(id:String){if(valid(id))context.deleteSharedPreferences("queue_"+id)}
}
