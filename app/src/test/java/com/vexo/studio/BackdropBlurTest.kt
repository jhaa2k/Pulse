package com.vexo.studio
import com.vexo.studio.ui.components.BackdropBlur
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
class BackdropBlurTest {
    @Test fun flatColorStaysUnchanged(){
        val color=0xff336699.toInt()
        val pixels=IntArray(63){color}
        BackdropBlur.blurPixels(pixels,9,7,12)
        assertTrue(pixels.all{it==color})
    }
    @Test fun impulseSpreadsToNeighbors(){
        val pixels=IntArray(81){0xff000000.toInt()}
        pixels[40]=0xffffffff.toInt()
        BackdropBlur.blurPixels(pixels,9,9,1)
        assertTrue((pixels[40] and 255) in 1..254)
        assertTrue((pixels[39] and 255)>0)
        assertEquals(255,pixels[40] ushr 24)
    }
    @Test fun zeroRadiusPreservesExactPixels(){
        val pixels=intArrayOf(0xff000000.toInt(),0xffffffff.toInt())
        val before=pixels.clone()
        BackdropBlur.blurPixels(pixels,2,1,0)
        assertTrue(before.contentEquals(pixels))
    }
}
