package com.vexo.studio.engine.scene

import com.vexo.studio.model.*
import com.vexo.studio.engine.audio.AudioProviderSnapshot
import kotlin.math.*

enum class PresetCategory { ALL, WAVE, GEOMETRIC, BLOOM, PARTICLES, MOTION }

object PresetPreviewData {
    fun categories(p:VexoProject):Set<PresetCategory>{
        val configs=p.compositions.flatMap{it.layers}.filter{it.common.visible}.map{it.config}
        val bars=configs.filterIsInstance<LayerConfig.Bars>()
        val result=mutableSetOf(PresetCategory.ALL)
        if(bars.any{it.signedWaveform||it.creativePath in setOf(CreativeSpectrumPath.SINE,CreativeSpectrumPath.DIAGONAL,CreativeSpectrumPath.LEGACY)||it.creativeStyle in setOf(CreativeSpectrumStyle.LINE,CreativeSpectrumStyle.FILL,CreativeSpectrumStyle.DOUBLE_LINE,CreativeSpectrumStyle.RIBBON)})result+=PresetCategory.WAVE
        if(bars.any{it.creativePath!=CreativeSpectrumPath.LEGACY&&it.creativePath!=CreativeSpectrumPath.SINE})result+=PresetCategory.GEOMETRIC
        if(configs.any{it is LayerConfig.BlurEffect})result+=PresetCategory.BLOOM
        if(configs.any{it is LayerConfig.Particles})result+=PresetCategory.PARTICLES
        if(p.canvas.camera.enabled||configs.any{it is LayerConfig.MotionBlurEffect||it is LayerConfig.RgbSplitEffect})result+=PresetCategory.MOTION
        return result
    }
    fun matches(p:VexoProject,query:String,category:PresetCategory):Boolean{
        val cats=categories(p)
        if(category !in cats)return false
        val terms=p.name+" "+cats.joinToString(" ")+ " "+p.compositions.flatMap{it.layers}.joinToString(" "){l->
            val b=l.config as? LayerConfig.Bars
            l.common.name+" "+(b?.creativePath?.name?:"")+" "+(b?.creativeStyle?.name?:"")
        }
        return query.trim().split(Regex("\\s+")).all{terms.contains(it,true)}
    }
    fun sample(index:Int,count:Int,time:Float):AudioProviderSnapshot{
        val n=count.coerceIn(1,512)
        val t=time.takeIf{it.isFinite()}?.coerceIn(0f,3600f)?:0f
        val pulse=exp(-((t*2f)%1f)*6f)
        val bars=FloatArray(n){i->val u=i.toFloat()/n;(.025f+.13f*abs(sin(i*.27f+t*4f+index))+.15f*pulse*(1f-u))* (1f-.45f*u)}
        val wave=FloatArray(n){i->sin(i*.23f+t*7f)*(.15f+.22f*pulse)}
        return AudioProviderSnapshot(bars,wave,pulse,pulse,pulse,.35f,.2f,.3f,.6f,FloatArray(n){bars[it]+.04f})
    }
}
