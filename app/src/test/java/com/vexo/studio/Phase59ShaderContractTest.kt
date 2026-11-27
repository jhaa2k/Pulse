package com.vexo.studio

import com.vexo.studio.engine.gl.VexoShaders
import org.junit.Assert.*
import org.junit.Test

class Phase59ShaderContractTest {
    // Shader source contracts; actual compilation and pixel output require a GLES device.
    @Test fun neutralPassRetainsStoredPremultipliedRgb(){
        val shader=VexoShaders.IMAGE_FRAGMENT
        assertTrue(shader.contains("neutral?tex.rgb:satColor*tex.a"))
        assertTrue(shader.contains("premul*uTint.rgb*coverageTotal*uGlowGain"))
    }
    @Test fun gradientsInterpolatePremultipliedEndpoints(){
        val shader=VexoShaders.IMAGE_FRAGMENT
        assertEquals(2,Regex("uGradientFrom\\.rgb\\*uGradientFrom\\.a").findAll(shader).count())
        assertEquals(2,Regex("uGradientTo\\.rgb\\*uGradientTo\\.a").findAll(shader).count())
    }
    @Test fun maskAndColorKeyAttenuateRgbAndAlphaTogether(){
        val shader=VexoShaders.IMAGE_FRAGMENT
        assertTrue(shader.contains("coverageTotal *= clamp(coverage,0.0,1.0)"))
        assertTrue(shader.contains("coverageTotal *= mix(1.0, smoothstep"))
        assertTrue(shader.contains("tex.a*coverageTotal*min(1.0,uGlowGain)"))
    }
}
