package com.vexo.studio.engine.gl
import kotlin.math.*
object ShapeGeometry{
    fun roundedSquare(half:Float,corner:Float):List<Pair<Float,Float>>{
        val h=half.coerceAtLeast(0f);val r=(corner.coerceIn(0f,.5f)*h*2f)
        if(r==0f)return listOf(h to h,-h to h,-h to -h,h to -h)
        val centers=listOf((h-r) to (h-r),(-h+r) to (h-r),(-h+r) to (-h+r),(h-r) to (-h+r))
        return centers.flatMapIndexed{i,c->(0..6).map{j->val a=(i*PI/2+j*PI/12).toFloat();(c.first+cos(a)*r) to (c.second+sin(a)*r)}}
    }
}
