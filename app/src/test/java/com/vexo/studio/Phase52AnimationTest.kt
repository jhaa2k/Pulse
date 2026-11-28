package com.vexo.studio
import com.vexo.studio.engine.scene.*
import com.vexo.studio.model.*
import org.junit.Assert.*
import org.junit.Test
class Phase52AnimationTest {
    @Test fun imageAndTextPropertiesInterpolate(){
        val image=LayerNode(LayerType.IMAGE,LayerCommon(),LayerConfig.Image())
        fun track(l:LayerNode,key:String,a:Float,b:Float)=KeyframeTrack(l.common.id,LayerPropertyAnimation.trackKey(key),listOf(Keyframe(0,a),Keyframe(1000,b)))
        val result=LayerPropertyAnimation.evaluate(image,listOf(track(image,"image.brightness",0f,1f),track(image,"image.blur",0f,20f)),500) as LayerConfig.Image
        assertEquals(.5f,result.creative.brightness,.001f);assertEquals(10,result.blurRadius)
        val text=LayerNode(LayerType.TEXT,LayerCommon(),LayerConfig.Text())
        assertEquals(40,(LayerPropertyAnimation.evaluate(text,listOf(track(text,"text.size",20f,60f)),500) as LayerConfig.Text).fontSize)
    }
    @Test fun rgbAndMotionRecipesRemainValid(){
        val l=LayerNode(LayerType.RGB_SPLIT_EFFECT,LayerCommon(),LayerConfig.RgbSplitEffect())
        assertTrue("rgb.mix" in LayerPropertyAnimation.keys(l))
        assertEquals(1f,LayerPropertyAnimation.clamp("rgb.mix",4f),.001f)
        assertEquals(12,MotionRecipe.entries.size)
        assertTrue(MotionRecipe.entries.all{MotionPresets.builtin(it,l.common,0,1000,1f).isNotEmpty()})
    }
}
