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
        val f=fps.coerceIn(1,60)
        return ((t*f/1000)*1000/f).coerceIn(0,duration-1)
    }
}

/** Bounded frame-sampled video textures. GL-thread only. The same timestamps are
 * used in preview/export; source video audio is intentionally not mixed. */
class VideoFrameTextures(private val context:Context,private val exporting:Boolean=false): Closeable {
    private val codecs=LinkedHashMap<String,CodecVideoTexture>(2,.75f,true)
    private val codecFailures=LinkedHashSet<String>()
    private data class Entry(val reader:MediaMetadataRetriever,val duration:Long,
        val width:Int,val height:Int,var texture:Int=0,var time:Long=-1,
        var size:Pair<Int,Int>?=null)
    private val cache=LinkedHashMap<String,Entry>(4,.75f,true)
    private val failed=LinkedHashSet<String>()
    fun size(id:Int):Pair<Int,Int>?=codecs.values.firstOrNull{it.texture==id}?.size ?: cache.values.firstOrNull{it.texture==id}?.size
    fun texture(cfg:LayerConfig.Image,position:Long):Int{
        val uri=cfg.customUri?:return 0
        val quality=cfg.videoTextureLimit.coerceIn(256,1920)
        val codecKey=ActiveMediaKeys.videoKey(cfg)?:return 0
        if(cfg.videoDecodeMode==com.vexo.studio.model.VideoDecodeMode.AUTO && codecKey !in codecFailures){
            val result=runCatching{
                val decoder=codecs[codecKey]?:run{
                    if(codecs.size>=2){val oldest=codecs.entries.iterator().next();oldest.value.close();codecs.remove(oldest.key)}
                    CodecVideoTexture(context,Uri.parse(uri),quality,exporting).also{codecs[codecKey]=it}
                }
                val t=VideoTimeline.sampleTimeMs(position,decoder.durationMs,cfg.videoSpeed,cfg.videoOffsetMs,cfg.videoLoop,cfg.videoSampleFps)
                decoder.frame(t)
            }
            result.getOrNull()?.takeIf{it!=0}?.let{return it}
            codecs.remove(codecKey)?.close();rememberFailure(codecFailures,codecKey)
            android.util.Log.w("VexoVideo","Surface decoder unavailable; using sampled frames",result.exceptionOrNull())
        }
        if(uri in failed)return 0
        val key=codecKey
        val entry=cache[key]?:run{
            val retriever=MediaMetadataRetriever()
            val e=runCatching{
                retriever.setDataSource(context,Uri.parse(uri))
                val duration=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?:0L
                val width=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()?:0
                val height=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()?:0
                require(duration>0&&width>0&&height>0)
                Entry(retriever,duration,width,height)
            }.getOrElse{runCatching{retriever.release()};rememberFailure(failed,uri);return 0}
            if(cache.size>=4){val oldest=cache.entries.iterator().next();release(oldest.value);cache.remove(oldest.key)}
            cache[key]=e;e
        }
        val t=VideoTimeline.sampleTimeMs(position,entry.duration,cfg.videoSpeed,cfg.videoOffsetMs,cfg.videoLoop,cfg.videoSampleFps)
        if(t==entry.time)return entry.texture
        val scale=minOf(1f,quality.toFloat()/maxOf(entry.width,entry.height))
        val w=(entry.width*scale).toInt().coerceAtLeast(1)
        val h=(entry.height*scale).toInt().coerceAtLeast(1)
        val raw=runCatching{
            if(Build.VERSION.SDK_INT>=27)entry.reader.getScaledFrameAtTime(t*1000,MediaMetadataRetriever.OPTION_CLOSEST,w,h)
            else entry.reader.getFrameAtTime(t*1000,MediaMetadataRetriever.OPTION_CLOSEST)
        }.getOrNull()?:return entry.texture
        val bitmap=if(maxOf(raw.width,raw.height)>quality) {
            val s=quality.toFloat()/maxOf(raw.width,raw.height)
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
            check(entry.texture!=0){"Video texture allocation failed"}
            val size=bitmap.width to bitmap.height
            if(entry.size==size)GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D,0,0,0,bitmap)
            else GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0)
            check(GLES20.glGetError()==GLES20.GL_NO_ERROR){"Video texture upload failed"}
            entry.size=size
            entry.time=t
            return entry.texture
        }catch(e:Exception){
            cache.remove(key)?.let(::release);rememberFailure(failed,uri)
            return 0
        }finally{if(bitmap!==raw)bitmap.recycle();raw.recycle()}
    }
    private fun rememberFailure(set:LinkedHashSet<String>,key:String){
        if(set.size>=128)set.remove(set.first())
        set.add(key)
    }
    fun retain(keys:Set<String>,uris:Set<String>){
        codecs.keys.filter{it !in keys}.forEach{codecs.remove(it)?.close()}
        cache.keys.filter{it !in keys}.forEach{cache.remove(it)?.let(::release)}
        codecFailures.retainAll(keys);failed.retainAll(uris)
    }
    fun allocatedBytes():Long=codecs.values.sumOf{it.size.first.toLong()*it.size.second*4L}+
        cache.values.sumOf{val s=it.size;if(s==null)0L else s.first.toLong()*s.second*4L}
    private fun release(e:Entry){runCatching{e.reader.release()};if(e.texture!=0)GLES20.glDeleteTextures(1,intArrayOf(e.texture),0)}
    override fun close(){codecs.values.forEach{it.close()};codecs.clear();codecFailures.clear();cache.values.forEach(::release);cache.clear();failed.clear()}
}
