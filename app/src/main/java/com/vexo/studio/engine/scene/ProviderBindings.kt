package com.vexo.studio.engine.scene
import com.vexo.studio.model.*
object ProviderBindings {
    fun remove(project:VexoProject,index:Int):VexoProject {
        if(project.audioProviders.size<=1||project.audioProviders.none{it.index==index})return project
        val remaining=project.audioProviders.filterNot{it.index==index}
        val fallback=remaining.first().index
        fun id(i:Int)=if(i==index)fallback else i
        fun measure(m:MeasureConfig)=m.copy(providerIndex=id(m.providerIndex))
        fun layer(l:LayerNode):LayerNode{
            val c=l.common
            val cfg=when(val x=l.config){
                is LayerConfig.Bars->x.copy(audioProviderIndex=id(x.audioProviderIndex))
                is LayerConfig.Image->x.copy(measureColorBlend=measure(x.measureColorBlend),creative=x.creative.copy(audio=measure(x.creative.audio)))
                is LayerConfig.Text->x.copy(animationAudio=measure(x.animationAudio))
                is LayerConfig.Shape->x.copy(pulse=measure(x.pulse),strokePulse=measure(x.strokePulse))
                is LayerConfig.Particles->x.copy(measureOverallSpeed=measure(x.measureOverallSpeed),burstProvider=id(x.burstProvider))
                is LayerConfig.BlurEffect->x.copy(measureBlur=measure(x.measureBlur))
                is LayerConfig.RgbSplitEffect->x.copy(splitMeasure=measure(x.splitMeasure))
                else->x
            }
            return l.copy(common=c.copy(measurePos=measure(c.measurePos),measureScale=measure(c.measureScale),measureRot=measure(c.measureRot),measureOpacity=measure(c.measureOpacity)),config=cfg)
        }
        return project.copy(audioProviders=remaining,compositions=project.compositions.map{c->c.copy(layers=c.layers.map(::layer),providerIndices=c.providerIndices.map(::id).distinct())})
    }
    fun defaults(settings:AppSettings,index:Int,tag:String):AudioProviderConfig{
        val fft=com.vexo.studio.engine.audio.AudioProviderEngine.sanitizeFftSize(settings.defaultFftSize)
        return AudioProviderConfig(index=index,tag=tag,fftSize=fft,hopSize=settings.defaultHopSize.coerceIn(1,fft))
    }
}
