package com.vexo.studio.engine.gl

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Build
import com.vexo.studio.model.LayerConfig
import java.io.Closeable

object VideoTimeline {
    fun sampleTimeMs(position: Long,duration: Long,speed: Float,offset: Long,loop:Boolean,fps:Int): Long {
        if(duration<=0)return 0L
        val raw=(position.coerceAtLeast(0)*speed.coerceIn(.25f,4f)).toLong()+offset.coerceAtLeast(0)
        val t=if(loop)raw%duration else raw.coerceIn(0,duration-1)
        val f=fps.coerceIn(1,24)
        return ((t*f/1000)*1000/f).coerceIn(0,duration-1)
    }
}

/** Bounded frame-sampled video textures. GL-thread only. The same timestamps are
 * used in preview/export; source video audio is intentionally not mixed. */
class VideoFrameTextures(private val context:Context): Closeable {
    private data class Entry(val reader:MediaMetadataRetriever,val duration:Long,
        val width:Int,val height:Int,var texture:Int=0,var time:Long=-1,
        var size:Pair<Int,Int>?=null)
    private val cache=LinkedHashMap<String,Entry>(4,.75f,true)
    private val failed=HashSet<String>()
    fun size(id:Int):Pair<Int,Int>?=cache.values.firstOrNull{it.texture==id}?.size
    fun texture(cfg:LayerConfig.Image,position:Long):Int{
        val uri=cfg.customUri?:return 0
        if(uri in failed)return 0
        val key=uri+"|"+cfg.videoLoop+"|"+cfg.videoSpeed+"|"+cfg.videoOffsetMs+"|"+cfg.videoSampleFps
        val entry=cache[key]?:run{
            val retriever=MediaMetadataRetriever()
            val e=runCatching{
                retriever.setDataSource(context,Uri.parse(uri))
                val duration=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?:0L
                val width=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()?:0
                val height=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()?:0
                require(duration>0&&width>0&&height>0)
                Entry(retriever,duration,width,height)
            }.getOrElse{runCatching{retriever.release()};failed.add(uri);return 0}
            if(cache.size>=4){val oldest=cache.entries.iterator().next();release(oldest.value);cache.remove(oldest.key)}
            cache[key]=e;e
        }
        val t=VideoTimeline.sampleTimeMs(position,entry.duration,cfg.videoSpeed,cfg.videoOffsetMs,cfg.videoLoop,cfg.videoSampleFps)
        if(t==entry.time)return entry.texture
        val scale=minOf(1f,1280f/maxOf(entry.width,entry.height))
        val w=(entry.width*scale).toInt().coerceAtLeast(1)
        val h=(entry.height*scale).toInt().coerceAtLeast(1)
        val raw=runCatching{
            if(Build.VERSION.SDK_INT>=27)entry.reader.getScaledFrameAtTime(t*1000,MediaMetadataRetriever.OPTION_CLOSEST,w,h)
            else entry.reader.getFrameAtTime(t*1000,MediaMetadataRetriever.OPTION_CLOSEST)
        }.getOrNull()?:return entry.texture
        val bitmap=if(maxOf(raw.width,raw.height)>1280) {
            val s=1280f/maxOf(raw.width,raw.height)
            Bitmap.createScaledBitmap(raw,(raw.width*s).toInt().coerceAtLeast(1),(raw.height*s).toInt().coerceAtLeast(1),true)
        } else raw
        try{
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            if(entry.texture==0){
                val ids=IntArray(1);GLES20.glGenTextures(1,ids,0);entry.texture=ids[0]
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,entry.texture)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
            }else GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,entry.texture)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0)
            entry.size=bitmap.width to bitmap.height
            entry.time=t
            return entry.texture
        }finally{if(bitmap!==raw)bitmap.recycle();raw.recycle()}
    }
    private fun release(e:Entry){runCatching{e.reader.release()};if(e.texture!=0)GLES20.glDeleteTextures(1,intArrayOf(e.texture),0)}
    override fun close(){cache.values.forEach(::release);cache.clear();failed.clear()}
}
