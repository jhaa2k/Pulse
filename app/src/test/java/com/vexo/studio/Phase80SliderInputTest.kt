package com.vexo.studio

import com.vexo.studio.ui.components.SliderValuePolicy
import org.junit.Assert.*
import org.junit.Test

class Phase80SliderInputTest{
    @Test fun invalidInputCannotReachRenderer(){
        assertNull(SliderValuePolicy.parse("NaN"))
        assertNull(SliderValuePolicy.parse("Infinity"))
        assertNull(SliderValuePolicy.parse("invalid"))
        assertEquals(1f,SliderValuePolicy.safe(Float.NaN,0f,2f,1f),0f)
    }
    @Test fun keyboardAndGestureUseSameGrid(){
        assertEquals(.35f,SliderValuePolicy.quantize(.34f,.05f,1f,.1f,.05f),.0001f)
        assertEquals(.35f,SliderValuePolicy.parse("0,35")!!,.0001f)
        assertEquals(-.25f,SliderValuePolicy.quantize(-.26f,-.25f,.25f,.1f,0f),0f)
    }
    @Test fun endpointsRemainReachable(){
        assertEquals(1f,SliderValuePolicy.quantize(1f,.05f,1f,.3f,.05f),0f)
        assertEquals(.05f,SliderValuePolicy.quantize(0f,.05f,1f,.3f,.05f),0f)
    }
}
