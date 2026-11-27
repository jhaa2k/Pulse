package com.vexo.studio.engine.scene
import com.vexo.studio.model.BlurQuality
import kotlin.math.*

data class SmoothBlurPlan(val reductions:List<Pair<Int,Int>>,val width:Int,val height:Int,val sigmaX:Float,val sigmaY:Float,val rounds:Int){
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
