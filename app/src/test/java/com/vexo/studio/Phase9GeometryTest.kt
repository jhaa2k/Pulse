package com.vexo.studio
import com.vexo.studio.engine.gl.ShapeGeometry
import org.junit.Assert.*
import org.junit.Test
class Phase9GeometryTest{
 @Test fun cornersStayWithinBounds(){
    assertEquals(4,ShapeGeometry.roundedSquare(10f,0f).size)
    val rounded=ShapeGeometry.roundedSquare(10f,.25f)
    assertEquals(28,rounded.size)
    assertTrue(rounded.all{kotlin.math.abs(it.first)<=10.001f&&kotlin.math.abs(it.second)<=10.001f})
 }
}
