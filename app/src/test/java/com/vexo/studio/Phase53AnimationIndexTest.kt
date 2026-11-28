package com.vexo.studio
import com.vexo.studio.engine.scene.*
import com.vexo.studio.model.*
import org.junit.Assert.*
import org.junit.Test

class Phase53AnimationIndexTest{
    @Test fun unorderedAndDuplicateFramesAreDeterministic(){
        val track=KeyframeTrack("a","transform.posX",listOf(Keyframe(1000,1f),Keyframe(0,0f),Keyframe(500,.2f),Keyframe(500,.5f),Keyframe(750,Float.NaN)))
        val t=PreparedKeyframeTrack.create(track)
        assertEquals(.25f,t.evaluate(250,0f),.001f)
        assertEquals(.5f,t.evaluate(500,0f),.001f)
        assertEquals(.75f,t.evaluate(750,0f),.001f)
        assertEquals(1f,t.evaluate(2000,0f),.001f)
    }
    @Test fun holdAndEndpointsRemainCorrect(){
        val t=PreparedKeyframeTrack.create(KeyframeTrack("a","x",listOf(Keyframe(0,2f,InterpolationType.HOLD),Keyframe(100,3f))))
        assertEquals(2f,t.evaluate(99,0f),.001f)
        assertEquals(3f,t.evaluate(100,0f),.001f)
    }
    @Test fun indexedLayersAndPropertiesStayIndependent(){
        val a=KeyframeTrack("a","transform.posX",listOf(Keyframe(0,.3f)))
        val b=KeyframeTrack("b","property.bars.height",listOf(Keyframe(0,4f)))
        val index=PreparedAnimationIndex.create(listOf(a,b))
        assertEquals(.3f,index.forLayer("a").evaluate("transform.posX",0,0f),.001f)
        assertFalse(index.forLayer("a").hasProperties)
        assertTrue(index.forLayer("b").hasProperties)
        assertEquals(4f,index.forLayer("b").evaluate("property.bars.height",0,0f),.001f)
        assertEquals(7f,index.forLayer("missing").evaluate("x",0,7f),.001f)
    }
    @Test fun largeTrackInterpolationMatchesLinearReference(){
        val t=PreparedKeyframeTrack.create(KeyframeTrack("a","x",(0..499).reversed().map{Keyframe(it*100L,it.toFloat())}))
        for(i in 0..998)assertEquals(i*.5f,t.evaluate(i*50L,0f),.001f)
    }
}
