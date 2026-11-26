package com.vexo.studio.engine.scene
import com.vexo.studio.model.*
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class SavedLayerPreset(val id:String=UUID.randomUUID().toString(),val name:String,val layer:LayerNode,val tracks:List<KeyframeTrack>)

object LayerPresets {
    /** Standalone styles do not embed imported files or composition graphs. */
    fun capture(name:String,layer:LayerNode,tracks:List<KeyframeTrack>):SavedLayerPreset?{
        if(name.isBlank()||SceneGraph.referencesOf(layer.config).isNotEmpty())return null
        fun internal(value:String)=value.isBlank()||value.startsWith("internalres:")
        val config=when(val c=layer.config){
            is LayerConfig.Image->c.copy(sourceType=if(c.sourceType==ImageSourceType.IMPORTED)ImageSourceType.ALBUM_ART else c.sourceType,customUri=null,videoEnabled=false,maskImage=c.maskImage.takeIf(::internal).orEmpty())
            is LayerConfig.Text->c.copy(fontAssetUri=null)
            is LayerConfig.Particles->c.copy(customImage=c.customImage.takeIf(::internal)?:"internalres:particle_circle_blur4")
            else->c
        }
        val selected=tracks.filter{it.targetLayerId==layer.common.id&&it.propertyKey.startsWith("transform.")&&it.propertyKey.removePrefix("transform.") in TransformAnimation.keys}.take(6)
        if(selected.any{it.keyframes.size>500||it.keyframes.any{f->!f.value.isFinite()||f.timeMs !in 0L..86400000L}})return null
        return SavedLayerPreset(name=name.trim().take(60),layer=layer.copy(common=layer.common.copy(locked=false,aveeId=0,extraFields=emptyMap()),config=config),tracks=selected)
    }
    fun instantiate(preset:SavedLayerPreset,providers:Set<Int>,fallback:Int):Pair<LayerNode,List<KeyframeTrack>>{
        fun id(i:Int)=if(i in providers)i else fallback
        fun measure(m:MeasureConfig)=m.copy(providerIndex=id(m.providerIndex))
        val old=preset.layer.common
        val c=old.copy(id=UUID.randomUUID().toString(),name=preset.name,locked=false,measurePos=measure(old.measurePos),measureScale=measure(old.measureScale),measureRot=measure(old.measureRot),measureOpacity=measure(old.measureOpacity))
        val cfg=when(val x=preset.layer.config){
            is LayerConfig.Bars->x.copy(audioProviderIndex=id(x.audioProviderIndex))
            is LayerConfig.Image->x.copy(measureColorBlend=measure(x.measureColorBlend),creative=x.creative.copy(audio=measure(x.creative.audio)))
            is LayerConfig.Text->x.copy(animationAudio=measure(x.animationAudio))
            is LayerConfig.Shape->x.copy(pulse=measure(x.pulse),strokePulse=measure(x.strokePulse))
            is LayerConfig.Particles->x.copy(measureOverallSpeed=measure(x.measureOverallSpeed),burstProvider=id(x.burstProvider))
            else->x
        }
        return preset.layer.copy(common=c,config=cfg) to preset.tracks.map{it.copy(targetLayerId=c.id)}
    }
}
