package com.vexo.studio.engine.gl

object SpectrumFilters {
    fun smooth(values:FloatArray,radius:Int):FloatArray {
        val r=radius.coerceIn(0,20)
        if(r==0||values.isEmpty())return values
        return FloatArray(values.size){i->
            var sum=0f;var weight=0
            for(d in -r..r){val w=r+1-kotlin.math.abs(d);sum+=values[(i+d).coerceIn(0,values.lastIndex)]*w;weight+=w}
            sum/weight
        }
    }
}
