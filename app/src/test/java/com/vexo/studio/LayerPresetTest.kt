package com.vexo.studio
import com.vexo.studio.model.*
import com.vexo.studio.engine.scene.*
import org.junit.Assert.*
import org.junit.Test
class LayerPresetTest{
 @Test fun portableStyleRemovesExternalMedia(){
    val layer=LayerNode(LayerType.IMAGE,LayerCommon(),LayerConfig.Image(sourceType=ImageSourceType.IMPORTED,customUri="content://test/video",videoEnabled=true,maskImage="file:///external"))
    val saved=LayerPresets.capture("Style",layer,emptyList())!!
    val c=saved.layer.config as LayerConfig.Image
    assertEquals(ImageSourceType.ALBUM_ART,c.sourceType);assertNull(c.customUri);assertFalse(c.videoEnabled);assertEquals("",c.maskImage)
 }
 @Test fun remapIdsAndMissingProvider(){
    val layer=LayerNode(LayerType.BARS,LayerCommon(),LayerConfig.Bars(audioProviderIndex=7))
    val track=KeyframeTrack(layer.common.id,"transform.posX",listOf(Keyframe(0,.5f)))
    val preset=LayerPresets.capture("Bars",layer,listOf(track))!!
    val (copy,tracks)=LayerPresets.instantiate(preset,setOf(1),1)
    assertNotEquals(layer.common.id,copy.common.id);assertEquals(copy.common.id,tracks.single().targetLayerId)
    assertEquals(1,(copy.config as LayerConfig.Bars).audioProviderIndex)
 }
 @Test fun compositionDependentNotPortable(){
    val layer=LayerNode(LayerType.IMAGE,LayerCommon(),LayerConfig.Image(sourceType=ImageSourceType.COMPOSITION,sourceCompositionIndex=2))
    assertNull(LayerPresets.capture("Effect",layer,emptyList()))
 }
}
