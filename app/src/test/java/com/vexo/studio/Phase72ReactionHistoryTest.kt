package com.vexo.studio

import com.vexo.studio.engine.scene.SpectrumReactionHistory
import org.junit.Assert.*
import org.junit.Test

class Phase72ReactionHistoryTest {
    @Test fun delayDurationMatchesAcrossFrameRates(){
        for(fps in listOf(24,25,30,50,60,120)){
            val h=SpectrumReactionHistory()
            var out=0f
            for(i in 0..fps)out=h.sample(floatArrayOf(i.toFloat()/fps),6,1f/fps,"source")[0]
            assertEquals(.9f,out,.0001f)
        }
    }
    @Test fun signedWaveformInterpolatesBetweenFrames(){
        val h=SpectrumReactionHistory()
        h.sample(floatArrayOf(-1f),1,1f/30f,"wave")
        assertEquals(0f,h.sample(floatArrayOf(1f),1,1f/30f,"wave")[0],.0001f)
    }
    @Test fun switchingSourceOrCountClearsStaleFrames(){
        val h=SpectrumReactionHistory()
        h.sample(floatArrayOf(1f),9,.016f,"a")
        assertEquals(3f,h.sample(floatArrayOf(3f),9,.016f,"b")[0],0f)
        assertArrayEquals(floatArrayOf(4f,5f),h.sample(floatArrayOf(4f,5f),9,.016f,"b"),0f)
    }
    @Test fun zeroDelayAndOverflowAreSafe(){
        val h=SpectrumReactionHistory()
        h.sample(floatArrayOf(1f),9,.016f,"a")
        assertEquals(2f,h.sample(floatArrayOf(2f),0,.016f,"a")[0],0f)
        assertEquals(3f,h.sample(floatArrayOf(3f),9,.016f,"a")[0],0f)
        assertEquals(9,SpectrumReactionHistory.delayFrames(Int.MAX_VALUE,Int.MAX_VALUE))
        assertEquals(0,SpectrumReactionHistory.delayFrames(Int.MIN_VALUE,Int.MIN_VALUE))
    }
}
