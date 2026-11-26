package com.vexo.studio.data

import com.vexo.studio.model.*

/** One row per distinct local URI; every use remains individually addressable. */
data class ProjectAssetUse(val compositionIndex:Int?,val layerId:String?,val label:String,val role:String)
data class ProjectAsset(val uri:String,val uses:List<ProjectAssetUse>)
object ProjectAssets {
    fun collect(project:VexoProject):List<ProjectAsset>{
        val refs=linkedMapOf<String,MutableList<ProjectAssetUse>>()
        fun add(uri:String?,use:ProjectAssetUse){
            if(uri!=null&&(uri.startsWith("content://")||uri.startsWith("file://")))refs.getOrPut(uri){mutableListOf()}.add(use)
        }
        add(project.audioUri,ProjectAssetUse(null,null,"Project audio","Audio"))
        for(comp in project.compositions)for(layer in comp.layers){
            fun use(uri:String?,role:String)=add(uri,ProjectAssetUse(comp.index,layer.common.id,layer.common.name,role))
            when(val cfg=layer.config){
                is LayerConfig.Image->{if(cfg.sourceType==ImageSourceType.IMPORTED)use(cfg.customUri,if(cfg.videoEnabled)"Video" else "Image");use(cfg.maskImage,"Image mask")}
                is LayerConfig.Text->use(cfg.fontAssetUri,"Font")
                is LayerConfig.Particles->use(cfg.customImage,"Particle texture")
                is LayerConfig.BlurEffect->use(cfg.maskImage,"Blur mask")
                else->Unit
            }
        }
        return refs.map{(uri,uses)->ProjectAsset(uri,uses.toList())}
    }
}
