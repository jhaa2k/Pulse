package com.vexo.studio
import com.vexo.studio.model.*
import com.vexo.studio.engine.scene.SpectrumStyleLibrary
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test
class SpectrumStyleLibraryTest {
 @Test fun catalogHas26DistinctStyles(){
    val all=SpectrumStyleLibrary.recipes
    assertEquals(26,all.size);assertEquals(26,all.map{it.style}.distinct().size)
    assertTrue(all.map{it.path}.distinct().size>=14)
 }
 @Test fun shuffleDoesNotRepeatInFirstCycle(){
    var used=emptySet<String>();var c=LayerConfig.Bars()
    val rng=Random(42)
    repeat(26){
        val (r,next)=SpectrumStyleLibrary.next(used,c,rng)
        assertFalse(r.name in used)
        c=r.apply(c);used=next
    }
    assertEquals(26,used.size)
 }
 @Test fun preserveProvider(){
    assertEquals(5,SpectrumStyleLibrary.recipes.first().apply(LayerConfig.Bars(audioProviderIndex=5)).audioProviderIndex)
 }
}
