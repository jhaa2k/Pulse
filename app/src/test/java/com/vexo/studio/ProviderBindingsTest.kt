package com.vexo.studio
import com.vexo.studio.model.*
import com.vexo.studio.engine.scene.ProviderBindings
import org.junit.Assert.*
import org.junit.Test
class ProviderBindingsTest{
 @Test fun defaultFftAndHop(){
    val p=ProviderBindings.defaults(AppSettings(defaultFftSize=512,defaultHopSize=1024),3,"Custom")
    assertEquals(512,p.fftSize);assertEquals(512,p.hopSize);assertEquals(3,p.index)
 }
 @Test fun removingProviderRebindsLayers(){
    val layer=LayerNode(LayerType.PARTICLES,LayerCommon(measurePos=MeasureConfig(providerIndex=3)),LayerConfig.Particles(burstProvider=3,measureOverallSpeed=MeasureConfig(providerIndex=3)))
    val p=VexoProject(audioProviders=listOf(AudioProviderConfig(index=1),AudioProviderConfig(index=3)),compositions=listOf(CompositionNode(index=0,layers=listOf(layer),providerIndices=listOf(1,3))))
    val next=ProviderBindings.remove(p,3)
    val l=next.compositions.single().layers.single()
    assertEquals(listOf(1),next.compositions.single().providerIndices)
    assertEquals(1,l.common.measurePos.providerIndex)
    assertEquals(1,(l.config as LayerConfig.Particles).burstProvider)
    assertEquals(1,(l.config as LayerConfig.Particles).measureOverallSpeed.providerIndex)
    assertEquals(next,ProviderBindings.remove(next,1))
 }
}
