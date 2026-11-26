package com.vexo.studio.engine.gl
object ShapeGeometry{
    fun roundedSquare(half:Float,corner:Float):List<Pair<Float,Float>>{
        val h=half.coerceAtLeast(0f)
        return SpectrumGeometry.roundedContour(h,h,corner.coerceIn(0f,.5f)*h*2f)
    }
}
