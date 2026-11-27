package com.vexo.studio.engine.gl

import android.opengl.GLES20

data class GlFbo(
    val fboId: Int,
    val textureId: Int,
    val width: Int,
    val height: Int
)

/**
 * Reusable ping-pong and composition FBO/texture pool for offscreen sub-compositions,
 * separable blur passes, and motion blur trails. Validates framebuffer completeness.
 */
class FboPool {
    private val allocated = mutableMapOf<String, GlFbo>()
    private var maxDimension=0
    private fun limit():Int{
        if(maxDimension==0){
            val tex=IntArray(1);val viewport=IntArray(2)
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE,tex,0)
            GLES20.glGetIntegerv(GLES20.GL_MAX_VIEWPORT_DIMS,viewport,0)
            maxDimension=minOf(tex[0],viewport[0],viewport[1])
            check(maxDimension>=16){"Cannot query GPU framebuffer limits"}
        }
        return maxDimension
    }
    fun allocatedBytes():Long=allocated.values.sumOf{it.width.toLong()*it.height*4L}

    fun obtain(key: String, width: Int, height: Int): GlFbo {
        val w = width.coerceAtLeast(16)
        val h = height.coerceAtLeast(16)
        val max=limit()
        check(w<=max&&h<=max){"GPU framebuffer limit is $max px; requested ${w}×${h}. Reduce preview scale or export resolution."}
        val existing = allocated[key]
        if (existing != null && existing.width == w && existing.height == h) {
            return existing
        }
        if (existing != null) {
            allocated.remove(key)
            deleteFbo(existing)
        }
        val created = createFbo(w, h)
        allocated[key] = created
        return created
    }

    fun release(key:String){allocated.remove(key)?.let(::deleteFbo)}

    /** Exact membership prevents down_1 from accidentally matching down_10. */
    fun retainGroup(prefix:String,keys:Set<String>){
        allocated.keys.filter{it.startsWith(prefix+"_")&&it !in keys}.forEach(::release)
    }

    fun releaseByPrefix(prefix: String) {
        val keys = allocated.keys.filter { it.startsWith(prefix) }
        keys.forEach { key -> allocated.remove(key)?.let { deleteFbo(it) } }
    }

    fun retainSceneBuffers(compositionIds:Set<Int>,layerIds:Set<String>){
        val stale=allocated.keys.filter{key->
            when {
                key.startsWith("comp_")->key.removePrefix("comp_").toIntOrNull() !in compositionIds
                key.startsWith("trail_")->key.removePrefix("trail_") !in layerIds
                else->false
            }
        }
        stale.forEach{allocated.remove(it)?.let(::deleteFbo)}
    }

    fun releaseAll() {
        allocated.values.forEach { deleteFbo(it) }
        allocated.clear()
        maxDimension=0
    }

    private fun createFbo(width: Int, height: Int): GlFbo {
        val previousFbo = IntArray(1)
        val previousTex = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, previousFbo, 0)
        GLES20.glGetIntegerv(GLES20.GL_TEXTURE_BINDING_2D, previousTex, 0)
        val texIds = IntArray(1)
        val fboIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texIds[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
            width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
        )

        GLES20.glGenFramebuffers(1, fboIds, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboIds[0])
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, texIds[0], 0
        )
        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        if (status == GLES20.GL_FRAMEBUFFER_COMPLETE) {
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, previousFbo[0])
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, previousTex[0])
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            deleteFbo(GlFbo(fboIds[0], texIds[0], width, height))
            error("GPU cannot allocate ${width}×${height} framebuffer (0x${status.toString(16)}). Reduce preview scale or export resolution.")
        }
        return GlFbo(fboIds[0], texIds[0], width, height)
    }

    private fun deleteFbo(fbo: GlFbo) {
        GLES20.glDeleteFramebuffers(1, intArrayOf(fbo.fboId), 0)
        GLES20.glDeleteTextures(1, intArrayOf(fbo.textureId), 0)
    }
}
