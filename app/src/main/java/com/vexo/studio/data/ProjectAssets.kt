package com.vexo.studio.data

import com.vexo.studio.model.*

/** One row per distinct local URI; every use remains individually addressable. */
data class ProjectAssetUse(val compositionIndex:Int?,val layerId:String?,val label:String,val role:String)
data class ProjectAsset(val uri:String,val uses:List<ProjectAssetUse>)
object ProjectAssets {
    /** Replace or detach one reference, not the source file or unrelated uses. */
    fun editLayerAsset(layer:com.vexo.studio.model.LayerNode,role:String,oldUri:String,newUri:String?,video:Boolean):com.vexo.studio.model.LayerNode {
        if(layer.common.locked)return layer
        val config=when(val c=layer.config){
            is LayerConfig.Image->when{
                role in listOf("Image","Video")&&c.customUri==oldUri&&c.sourceType==ImageSourceType.IMPORTED->if(newUri==null)c.copy(sourceType=ImageSourceType.INTERNAL,internalRes="internalres:transparent",customUri=null,videoEnabled=false) else c.copy(customUri=newUri,videoEnabled=video)
                role=="Image mask"&&c.maskImage==oldUri->c.copy(maskImage=newUri.orEmpty())
                else->c
            }
            is LayerConfig.Text->if(role=="Font"&&c.fontAssetUri==oldUri)c.copy(fontAssetUri=newUri)else c
            is LayerConfig.Particles->if(role=="Particle texture"&&c.customImage==oldUri)c.copy(customImage=newUri?:"internalres:particle_circle_blur4")else c
            is LayerConfig.BlurEffect->if(role=="Blur mask"&&c.maskImage==oldUri)c.copy(maskImage=newUri?:"internalres:transparent")else c
            else->c
        }
        return layer.copy(config=config)
    }

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
