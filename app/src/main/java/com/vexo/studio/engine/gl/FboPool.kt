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

    fun obtain(key: String, width: Int, height: Int): GlFbo {
        val w = width.coerceIn(16, 4096)
        val h = height.coerceIn(16, 4096)
        val existing = allocated[key]
        if (existing != null && existing.width == w && existing.height == h) {
            return existing
        }
        if (existing != null) {
            deleteFbo(existing)
        }
        val created = createFbo(w, h)
        allocated[key] = created
        return created
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
            error("Framebuffer incomplete: status=0x${status.toString(16)}")
        }
        return GlFbo(fboIds[0], texIds[0], width, height)
    }

    private fun deleteFbo(fbo: GlFbo) {
        GLES20.glDeleteFramebuffers(1, intArrayOf(fbo.fboId), 0)
        GLES20.glDeleteTextures(1, intArrayOf(fbo.textureId), 0)
    }
}
