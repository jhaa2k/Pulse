package com.vexo.studio.engine.gl

/** Input order is least-recently-used first; selection does not touch GL state. */
object TextureCacheBudget {
    data class Entry(val key:String,val bytes:Long)
    private val pinned=setOf("internalres:white","internalres:vignette80","internalres:particle_circle_blur4")
    fun textEvictions(entries:List<Entry>,maxText:Int):Set<String>{
        val text=entries.filter{it.key.startsWith("txt_")}
        return text.take((text.size-maxText.coerceAtLeast(1)).coerceAtLeast(0)).mapTo(linkedSetOf()){it.key}
    }
    fun evictions(entries:List<Entry>,budget:Long,maxEntries:Int):Set<String>{
        var bytes=entries.sumOf{it.bytes.coerceAtLeast(0L)}
        var count=entries.size
        val removed=linkedSetOf<String>()
        // Prefer evicting transient labels before decoded image assets.
        val candidates=entries.filter{it.key !in pinned}
        for(e in candidates.filter{it.key.startsWith("txt_")}+candidates.filterNot{it.key.startsWith("txt_")}){
            if(bytes<=budget&&count<=maxEntries)break
            removed.add(e.key);bytes-=e.bytes.coerceAtLeast(0L);count--
        }
        return removed
    }
}
