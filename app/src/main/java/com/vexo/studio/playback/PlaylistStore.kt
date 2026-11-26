package com.vexo.studio.playback

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray

/** Private local playlist storage; entries are MediaStore content URIs. */
class PlaylistStore(context: Context) {
    private val prefs=context.getSharedPreferences("music_playlists",Context.MODE_PRIVATE)
    fun read(): Map<String,List<String>> = runCatching {
        val j=JSONObject(prefs.getString("lists","{}")?:"{}")
        j.keys().asSequence().associateWith{key->
            val a=j.getJSONArray(key);(0 until a.length()).map{a.getString(it)}
        }
    }.getOrDefault(emptyMap())
    fun save(name:String,uris:List<String>){
        val clean=name.trim().take(80)
        require(clean.isNotBlank())
        val map=read().toMutableMap();map[clean]=uris.distinct()
        write(map)
    }
    fun delete(name:String){write(read().filterKeys{it!=name})}
    private fun write(map:Map<String,List<String>>){
        val j=JSONObject();map.forEach{(k,v)->j.put(k,JSONArray(v))}
        prefs.edit().putString("lists",j.toString()).apply()
    }
}
