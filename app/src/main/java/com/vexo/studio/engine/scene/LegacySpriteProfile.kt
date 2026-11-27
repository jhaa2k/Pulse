package com.vexo.studio.engine.scene

import kotlin.math.pow

/** Procedural approximations of distinct legacy sprite profiles. Original
 * Avee texture binaries are not bundled. Radius is normalized to the square. */
object LegacySpriteProfile {
    private val keys=setOf("internalres:particle_circle_blur4","internalres:particle_circle_w_a_64",
        "internalres:particle_blur01","internalres:particle_blur01_more","internalres:particle_blur_inv")
    fun supports(key:String)=key in keys
    private fun smooth(a:Float,b:Float,r:Float):Float {
        val t=((r-a)/(b-a)).coerceIn(0f,1f)
        return t*t*(3f-2f*t)
    }
    fun alpha(key:String,radius:Float):Float {
        val r=radius.takeIf{it.isFinite()}?.coerceAtLeast(0f)?:1f
        return when(key){
            "internalres:particle_circle_w_a_64"->1f-smooth(.88f,1f,r)
            "internalres:particle_blur01"->(1f-r.coerceIn(0f,1f)).pow(1.5f)
            "internalres:particle_blur01_more"->(1f-r.coerceIn(0f,1f)).pow(3f)
            "internalres:particle_blur_inv"->smooth(.08f,.95f,r)
            else->if(r<.45f)1f-(1f-.333333f)*(r/.45f) else .333333f*(1f-(r-.45f)/.55f).coerceIn(0f,1f)
        }
    }
}
