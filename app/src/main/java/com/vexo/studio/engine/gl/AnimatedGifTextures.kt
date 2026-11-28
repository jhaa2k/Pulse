package com.vexo.studio.engine.gl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Movie
import android.graphics.PorterDuff
import android.net.Uri
import android.opengl.GLES20
import android.opengl.GLUtils
import java.io.ByteArrayOutputStream

/** Small GL-thread-owned cache of bounded GIF sources. Movie supports seeking
 * without a running Drawable or independent UI clock. Unsupported media fall back to static decoding. */
@Suppress("DEPRECATION")
class AnimatedGifTextures(private val context:Context){
    data class Frame(val id:Int,val size:Pair<Int,Int>)
    private data class Entry(val movie:Movie,val bitmap:Bitmap,val frame:Frame,val canvas:Canvas=Canvas(bitmap),var lastTime:Int=-1)
    private val entries=LinkedHashMap<String,Entry>(4,.75f,true)
    private val unsupported=LinkedHashSet<String>()
    fun texture(uri:String?,position:Long,limit:Int):Frame?{
        if(uri.isNullOrBlank()||uri in unsupported)return null
        val entry=entries[uri]?:run{
            val made=runCatching{load(uri,limit)}.getOrNull()
            if(made==null){
                if(unsupported.size>=128)unsupported.remove(unsupported.first())
                unsupported.add(uri);return null
            }
            if(entries.size>=2){val old=entries.entries.first();dispose(old.value);entries.remove(old.key)}
            entries[uri]=made;made
        }
        val time=ImageAnimationClock.loopTime(position,entry.movie.duration())
        if(entry.lastTime==time)return entry.frame
        try{
            entry.movie.setTime(time)
            val canvas=entry.canvas
            canvas.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR)
            val saved=canvas.save()
            try{
                canvas.scale(entry.bitmap.width.toFloat()/entry.movie.width(),entry.bitmap.height.toFloat()/entry.movie.height())
                entry.movie.draw(canvas,0f,0f)
            }finally{canvas.restoreToCount(saved)}
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,entry.frame.id)
            if(entry.lastTime<0)GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,entry.bitmap,0)
            else GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D,0,0,0,entry.bitmap)
            check(GLES20.glGetError()==GLES20.GL_NO_ERROR){"GIF texture upload failed"}
            entry.lastTime=time
            return entry.frame
        }catch(e:Exception){
            entries.remove(uri)?.let(::dispose)
            if(unsupported.size>=128)unsupported.remove(unsupported.first())
            unsupported.add(uri)
            return null
        }
    }
    private fun load(uri:String,limit:Int):Entry?{
        val data=context.contentResolver.openInputStream(Uri.parse(uri))?.use{input->
            val header=ByteArray(10);var got=0
            while(got<header.size){val n=input.read(header,got,header.size-got);if(n<=0)break;got+=n}
            if(got!=header.size||ImageAnimationClock.gifSize(header)==null)return null
            val out=ByteArrayOutputStream();out.write(header)
            val buf=ByteArray(8192)
            while(true){val n=input.read(buf);if(n<0)break;require(out.size()+n<=8*1024*1024){"GIF exceeds 8 MiB"};out.write(buf,0,n)}
            out.toByteArray()
        }?:return null
        val movie=Movie.decodeByteArray(data,0,data.size)?:return null
        if(movie.duration()<=0||movie.width() !in 1..2048||movie.height() !in 1..2048)return null
        val scale=minOf(1f,limit.coerceIn(16,1024).toFloat()/maxOf(movie.width(),movie.height()))
        val w=(movie.width()*scale).toInt().coerceAtLeast(1)
        val h=(movie.height()*scale).toInt().coerceAtLeast(1)
        val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        val ids=IntArray(1)
        try{
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glGenTextures(1,ids,0)
            check(ids[0]!=0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,ids[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
            return Entry(movie,bitmap,Frame(ids[0],w to h))
        }catch(t:Throwable){
            bitmap.recycle();if(ids[0]!=0)GLES20.glDeleteTextures(1,ids,0);throw t
        }
    }
    private fun dispose(e:Entry){GLES20.glDeleteTextures(1,intArrayOf(e.frame.id),0);e.bitmap.recycle()}
    fun retain(uris:Set<String>){
        entries.keys.filter{it !in uris}.forEach{entries.remove(it)?.let(::dispose)}
        unsupported.retainAll(uris)
    }
    fun allocatedBytes():Long=entries.values.sumOf{it.frame.size.first.toLong()*it.frame.size.second*4L}
    fun close(){entries.values.forEach(::dispose);entries.clear();unsupported.clear()}
}
