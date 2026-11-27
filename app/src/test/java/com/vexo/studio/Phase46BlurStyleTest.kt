package com.vexo.studio
import com.vexo.studio.engine.scene.BlurStyle
import com.vexo.studio.model.*
import org.junit.Assert.*
import org.junit.Test
class Phase46BlurStyleTest{
    @Test fun distinctStylesPreserveCompositionRoutingAndBinding(){
        val base=LayerConfig.BlurEffect(sourceCompositionIndex=7,maskImage="composition:4",measureBlur=MeasureConfig(MeasureWhat.BEAT,.3f,.3f))
        val styles=BlurStyle.entries.map{it.apply(base)}
        assertEquals(6,styles.distinct().size)
        assertTrue(styles.all{it.sourceCompositionIndex==7&&it.maskImage=="composition:4"&&it.measureBlur==base.measureBlur})
        assertEquals(BlurFilterMode.RADIAL_SPIN,styles.last().filterMode)
    }
}
