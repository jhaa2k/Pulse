package com.vexo.studio.engine.scene
import com.vexo.studio.model.BlurQuality
import kotlin.math.*

data class SmoothBlurPlan(val reductions:List<Pair<Int,Int>>,val width:Int,val height:Int,val sigmaX:Float,val sigmaY:Float,val rounds:Int){
    /** Reconstruct broad filters progressively into existing pyramid buffers.
     * Stop at the largest level inside the output budget; final display uses linear filtering.
     * Fast mode deliberately keeps the low-resolution result. */
    fun reconstructionIndices(targetW:Int,targetH:Int,quality:BlurQuality,radial:Boolean=false):List<Int>{
        if(radial||quality==BlurQuality.FAST||reductions.size<2)return emptyList()
        val limit=maxOf(targetW,targetH,16)
        return (reductions.lastIndex-1 downTo 0).filter{index->
            val (w,h)=reductions[index]
            maxOf(w,h)<=limit
        }
    }
    /** Exact scratch targets required by this pass sequence, including reconstruction. */
    fun scratchKeys(prefix:String,radial:Boolean):Set<String> = buildSet{
        reductions.indices.forEach{add("${prefix}_down_$it")}
        add("${prefix}_a")
        if(!radial&&(sigmaY>.001f||rounds>1))add("${prefix}_b")
    }
    companion object{
        fun create(sw:Int,sh:Int,tw:Int,th:Int,sigma:Float,cross:Float,quality:BlurQuality,radial:Boolean=false):SmoothBlurPlan{
            var w=sw.coerceAtLeast(16);var h=sh.coerceAtLeast(16)
            val initialMax=maxOf(w,h).toFloat()
            val safe=sigma.takeIf{it.isFinite()}?.coerceIn(0f,4096f)?:0f
            val perpendicular=cross.takeIf{it.isFinite()}?.coerceIn(0f,2f)?:1f
            val rounds=if(!radial&&quality==BlurQuality.HIGH&&safe>3.5f)2 else 1
            val reductions=mutableListOf<Pair<Int,Int>>()
            val targetLimit=maxOf(tw,th,16)
            val maxWorkingSigma=3.5f*sqrt(rounds.toFloat())
            fun projected()=safe*maxOf(1f,perpendicular)*maxOf(w,h)/initialMax
            while((w>16||h>16)&&(maxOf(w,h)>targetLimit||(!radial&&projected()>maxWorkingSigma)||(reductions.isEmpty()&&quality==BlurQuality.FAST&&maxOf(w,h)>256&&safe>1f))){
                w=((w+1)/2).coerceAtLeast(16);h=((h+1)/2).coerceAtLeast(16)
                reductions.add(w to h)
            }
            val working=(safe*maxOf(w,h)/initialMax/sqrt(rounds.toFloat()))
            return SmoothBlurPlan(reductions,w,h,working.coerceIn(0f,3.75f),(working*perpendicular).coerceIn(0f,3.75f),rounds)
        }
    }
}
