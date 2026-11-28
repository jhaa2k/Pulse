package com.vexo.studio

import com.vexo.studio.engine.gl.ActiveMediaKeys
import com.vexo.studio.model.*
import org.junit.Assert.*
import org.junit.Test

class Phase79MediaLifetimeTest{
    private fun image(uri:String,video:Boolean=false,visible:Boolean=true,speed:Float=1f)=LayerNode(
        LayerType.IMAGE,LayerCommon(visible=visible),LayerConfig.Image(sourceType=ImageSourceType.IMPORTED,customUri=uri,videoEnabled=video,videoSpeed=speed))
    @Test fun hiddenAndRemovedMediaDoNotKeepDecoders(){
        val active=image("file:///a.mp4",true)
        val removed=image("file:///b.mp4",true,false)
        val gif=image("file:///c.gif")
        val usage=ActiveMediaKeys.from(listOf(active,removed,gif))
        assertEquals(setOf("file:///a.mp4"),usage.videoUris)
        assertEquals(setOf("file:///c.gif"),usage.imageUris)
        assertEquals(1,usage.videoKeys.size)
        assertTrue(ActiveMediaKeys.from(emptyList()).videoKeys.isEmpty())
    }
    @Test fun sameSourceWithDifferentTimingHasIndependentKeys(){
        val a=image("file:///a.mp4",true)
        val b=image("file:///a.mp4",true,speed=2f)
        val usage=ActiveMediaKeys.from(listOf(a,b))
        assertEquals(2,usage.videoKeys.size)
        assertEquals(1,usage.videoUris.size)
    }
    @Test fun nonImportedSourcesAreExcluded(){
        val original=image("file:///a.mp4",true)
        val changed=original.copy(config=(original.config as LayerConfig.Image).copy(sourceType=ImageSourceType.ALBUM_ART))
        assertTrue(ActiveMediaKeys.from(listOf(changed)).videoKeys.isEmpty())
    }
}
