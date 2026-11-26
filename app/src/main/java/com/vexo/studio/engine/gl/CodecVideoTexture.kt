package com.vexo.studio.engine.gl

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.Surface
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * Sequential surface video decoder. All codec and GL operations run on the
 * owning render thread; frame notifications run on a separate handler.
 * RGBA texture output uses the same top-origin layout as imported bitmaps.
 */
internal class CodecVideoTexture(context:Context,uri:Uri,limit:Int,private val exporting:Boolean):Closeable {
    private val extractor=MediaExtractor()
    private var codec:MediaCodec?=null
    private var started=false
    private var surface:Surface?=null
    private var surfaceTexture:SurfaceTexture?=null
    private var callbacks:HandlerThread?=null
    private val ready=Semaphore(0)
    private var oes=0
    private var fbo=0
    var texture=0
        private set
    var durationMs=0L
        private set
    var size=1 to 1
        private set
    private var program:GlShaderProgram?=null
    private val info=MediaCodec.BufferInfo()
    private val transform=FloatArray(16)
    private val vertices=buffer(floatArrayOf(-1f,-1f,1f,-1f,-1f,1f,1f,1f))
    private var uv=buffer(floatArrayOf(0f,1f,1f,1f,0f,0f,1f,0f))
    private var inputEnd=false
    private var outputEnd=false
    private var lastRequested=-1L
    private var lastPts=-1L
    private var hasFrame=false

    init {
        try {
            extractor.setDataSource(context,uri,null)
            val track=(0 until extractor.trackCount).firstOrNull{extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/")==true}
                ?:error("Video track not found")
            val format=extractor.getTrackFormat(track)
            durationMs=format.getLong(MediaFormat.KEY_DURATION)/1000
            require(durationMs>0)
            val width=format.getInteger(MediaFormat.KEY_WIDTH)
            val height=format.getInteger(MediaFormat.KEY_HEIGHT)
            val rotation=if(format.containsKey(MediaFormat.KEY_ROTATION))format.getInteger(MediaFormat.KEY_ROTATION) else 0
            // Own rotation in the OES blit, avoiding duplicate platform rotation.
            format.setInteger(MediaFormat.KEY_ROTATION,0)
            val rotated=rotation==90||rotation==270
            val dw=if(rotated)height else width
            val dh=if(rotated)width else height
            val max=IntArray(1);GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE,max,0)
            val scale=minOf(1f,minOf(limit,max[0]).toFloat()/maxOf(dw,dh))
            size=(dw*scale).toInt().coerceAtLeast(1) to (dh*scale).toInt().coerceAtLeast(1)
            uv=buffer(VideoDecodeMath.uv(rotation))
            initGl()
            val thread=HandlerThread("vexo-video-frame").apply{start()}
            callbacks=thread
            val st=SurfaceTexture(oes).apply{
                setDefaultBufferSize(width,height)
                setOnFrameAvailableListener({ready.release()},Handler(thread.looper))
            }
            surfaceTexture=st
            surface=Surface(st)
            val mime=checkNotNull(format.getString(MediaFormat.KEY_MIME))
            val list=MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val preferred=if(Build.VERSION.SDK_INT>=29)list.codecInfos.firstOrNull{c->
                !c.isEncoder&&c.isHardwareAccelerated&&runCatching{c.getCapabilitiesForType(mime).isFormatSupported(format)}.getOrDefault(false)
            }?.name else null
            val name=preferred?:list.findDecoderForFormat(format)?:error("No supported video decoder")
            codec=MediaCodec.createByCodecName(name)
            codec!!.configure(format,surface,null,0)
            codec!!.start();started=true
            extractor.selectTrack(track)
        }catch(t:Throwable){close();throw t}
    }

    private fun initGl(){
        val previous=IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING,previous,0)
        val active=IntArray(1);GLES20.glGetIntegerv(GLES20.GL_ACTIVE_TEXTURE,active,0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        val ids=IntArray(2)
        try{
            GLES20.glGenTextures(2,ids,0);oes=ids[0];texture=ids[1]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,oes)
            params(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);params(GLES20.GL_TEXTURE_2D)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,size.first,size.second,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,null)
            val fb=IntArray(1);GLES20.glGenFramebuffers(1,fb,0);fbo=fb[0]
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,fbo)
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,GLES20.GL_COLOR_ATTACHMENT0,GLES20.GL_TEXTURE_2D,texture,0)
            check(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)==GLES20.GL_FRAMEBUFFER_COMPLETE){"Video framebuffer allocation failed"}
            program=GlShaderProgram(VERTEX,FRAGMENT)
        }finally{
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,previous[0])
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,0)
            GLES20.glActiveTexture(active[0])
        }
    }
    private fun params(target:Int){
        GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
    }

    fun frame(timeMs:Long):Int{
        val target=timeMs.coerceIn(0L,durationMs-1)*1000L
        val c=checkNotNull(codec)
        if(VideoDecodeMath.shouldSeek(lastRequested,target)){
            c.flush()
            extractor.seekTo(target,MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            inputEnd=false;outputEnd=false;lastPts=-1;hasFrame=false
            ready.drainPermits()
        }
        lastRequested=target
        if(hasFrame&&(lastPts>=target||outputEnd))return texture
        val deadline=SystemClock.elapsedRealtime()+if(exporting)3000L else 750L
        while(!outputEnd&&SystemClock.elapsedRealtime()<deadline){
            if(!inputEnd){
                val idx=c.dequeueInputBuffer(0)
                if(idx>=0){
                    val input=checkNotNull(c.getInputBuffer(idx));input.clear()
                    val n=extractor.readSampleData(input,0)
                    if(n<0){c.queueInputBuffer(idx,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputEnd=true}
                    else{c.queueInputBuffer(idx,0,n,extractor.sampleTime,0);extractor.advance()}
                }
            }
            val index=c.dequeueOutputBuffer(info,5000)
            if(index>=0){
                val pts=info.presentationTimeUs
                val eos=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0
                val valid=info.size>0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG==0
                // Decode forward, retaining near-target frames so EOS can still hold the final image.
                val render=valid&&(pts>=target||target-pts<=120_000L||eos)
                c.releaseOutputBuffer(index,render)
                if(render){
                    check(ready.tryAcquire(if(exporting)1000L else 350L,TimeUnit.MILLISECONDS)){"Decoder surface frame timeout"}
                    val st=checkNotNull(surfaceTexture);st.updateTexImage();st.getTransformMatrix(transform)
                    blit();hasFrame=true;lastPts=pts
                }
                outputEnd=eos
                if(hasFrame&&(lastPts>=target||outputEnd))return texture
            }
        }
        if(hasFrame&&outputEnd)return texture
        error("Video decode stalled at "+timeMs+" ms")
    }

    private fun blit(){
        val oldFb=IntArray(1);val oldView=IntArray(4);val oldActive=IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING,oldFb,0)
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT,oldView,0)
        GLES20.glGetIntegerv(GLES20.GL_ACTIVE_TEXTURE,oldActive,0)
        val blend=GLES20.glIsEnabled(GLES20.GL_BLEND)
        val shader=checkNotNull(program)
        try{
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,fbo)
            GLES20.glViewport(0,0,size.first,size.second)
            GLES20.glDisable(GLES20.GL_BLEND)
            shader.use()
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,oes)
            GLES20.glUniform1i(shader.uniform("uVideo"),0)
            GLES20.glUniformMatrix4fv(shader.uniform("uTransform"),1,false,transform,0)
            val pos=shader.attrib("aPos");val coord=shader.attrib("aUv")
            vertices.position(0);uv.position(0)
            GLES20.glEnableVertexAttribArray(pos);GLES20.glVertexAttribPointer(pos,2,GLES20.GL_FLOAT,false,0,vertices)
            GLES20.glEnableVertexAttribArray(coord);GLES20.glVertexAttribPointer(coord,2,GLES20.GL_FLOAT,false,0,uv)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4)
            GLES20.glDisableVertexAttribArray(pos);GLES20.glDisableVertexAttribArray(coord)
        }finally{
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,0)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,oldFb[0])
            GLES20.glViewport(oldView[0],oldView[1],oldView[2],oldView[3])
            if(blend)GLES20.glEnable(GLES20.GL_BLEND)else GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glActiveTexture(oldActive[0])
        }
    }
    override fun close(){
        runCatching{if(started)codec?.stop()};runCatching{codec?.release()};codec=null;started=false
        runCatching{surface?.release()};surface=null
        runCatching{surfaceTexture?.release()};surfaceTexture=null
        runCatching{extractor.release()}
        callbacks?.quitSafely();callbacks=null
        program?.release();program=null
        if(fbo!=0){GLES20.glDeleteFramebuffers(1,intArrayOf(fbo),0);fbo=0}
        if(texture!=0||oes!=0){GLES20.glDeleteTextures(2,intArrayOf(texture,oes),0);texture=0;oes=0}
    }
    companion object {
        private fun buffer(values:FloatArray)=ByteBuffer.allocateDirect(values.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply{put(values);position(0)}
        private const val VERTEX="""
            attribute vec2 aPos;
            attribute vec2 aUv;
            uniform mat4 uTransform;
            varying vec2 vUv;
            void main(){gl_Position=vec4(aPos,0.0,1.0);vUv=(uTransform*vec4(aUv,0.0,1.0)).xy;}
        """
        private const val FRAGMENT="""
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uVideo;
            varying vec2 vUv;
            void main(){gl_FragColor=vec4(texture2D(uVideo,vUv).rgb,1.0);}
        """
    }
}
