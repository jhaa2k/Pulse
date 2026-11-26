package com.vexo.studio
import com.vexo.studio.model.*
import com.vexo.studio.data.NativeVexCodec
import org.junit.Assert.*
import org.junit.Test
class CreativeRecipeTest{
 @Test fun allRecipesRoundTrip(){
    val layers=CreativeRecipes.names.map{LayerNode(LayerType.IMAGE,LayerCommon(name=it),CreativeRecipes.config(it,null))}
    val p=VexoProject(compositions=listOf(CompositionNode(index=0,layers=layers)))
    assertEquals(p,NativeVexCodec.decode(NativeVexCodec.encode(p)))
    assertEquals(11,layers.size)
 }
 @Test fun gradientsUseSolidSourceAndEffectsUseComposition(){
    assertEquals(ImageSourceType.SOLID_COLOR,CreativeRecipes.config("Aurora Gradient",2).sourceType)
    assertEquals(2,CreativeRecipes.config("Beat Wave",2).sourceCompositionIndex)
    assertEquals(CreativeDistortion.WAVE,CreativeRecipes.config("Beat Wave",2).creative.distortion)
 }
}
