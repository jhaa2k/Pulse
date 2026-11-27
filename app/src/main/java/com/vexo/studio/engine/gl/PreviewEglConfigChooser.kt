package com.vexo.studio.engine.gl

import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay

/** Prefer MSAA when available; low-memory preview uses the original single-sample path. */
class PreviewEglConfigChooser(private val lowRam:Boolean):GLSurfaceView.EGLConfigChooser{
    override fun chooseConfig(egl:EGL10,display:EGLDisplay):EGLConfig{
        for(samples in if(lowRam)listOf(0)else listOf(4,2,0)){
            val attributes=intArrayOf(
                EGL10.EGL_RED_SIZE,8,EGL10.EGL_GREEN_SIZE,8,EGL10.EGL_BLUE_SIZE,8,EGL10.EGL_ALPHA_SIZE,8,
                EGL10.EGL_SURFACE_TYPE,EGL10.EGL_WINDOW_BIT,
                0x3040,4, // EGL_RENDERABLE_TYPE / ES2
                0x3032,if(samples>0)1 else 0,0x3031,samples,
                EGL10.EGL_NONE
            )
            val count=IntArray(1)
            val configs=arrayOfNulls<EGLConfig>(1)
            if(egl.eglChooseConfig(display,attributes,configs,1,count)&&count[0]>0){
                configs[0]?.let{return it}
            }
        }
        error("No compatible RGBA8 OpenGL ES preview configuration")
    }
}
