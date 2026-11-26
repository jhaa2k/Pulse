package com.vexo.studio.engine.scene
import com.vexo.studio.model.*
import java.util.UUID

enum class BatchLayerAction(val label:String){
    SHOW("SHOW"),HIDE("HIDE"),LOCK("LOCK"),UNLOCK("UNLOCK"),CENTER("CENTER"),RESET_TIMING("RESET TIMING"),DUPLICATE("DUPLICATE"),DELETE("DELETE")
}
object BatchLayerEdits{
    fun apply(p:VexoProject,composition:Int,ids:Set<String>,action:BatchLayerAction):VexoProject{
        val target=p.compositions.firstOrNull{it.index==composition}?:return p
        val selected=target.layers.filter{it.common.id in ids}
        val editable=selected.filterNot{it.common.locked}.map{it.common.id}.toSet()
        if(action==BatchLayerAction.DUPLICATE&&p.compositions.sumOf{it.layers.size}+editable.size>2048)return p
        val clones=mutableMapOf<String,String>()
        val layers=target.layers.flatMap{l->
            val c=l.common
            if(c.id !in ids)listOf(l)
            else when(action){
                BatchLayerAction.LOCK->listOf(l.copy(common=c.copy(locked=true)))
                BatchLayerAction.UNLOCK->listOf(l.copy(common=c.copy(locked=false)))
                else->if(c.locked)listOf(l) else when(action){
                    BatchLayerAction.SHOW->listOf(l.copy(common=c.copy(visible=true)))
                    BatchLayerAction.HIDE->listOf(l.copy(common=c.copy(visible=false)))
                    BatchLayerAction.CENTER->listOf(l.copy(common=c.copy(posX=.5f,posY=.5f,anchorX=AnchorMode.UNSET,anchorY=AnchorMode.UNSET)))
                    BatchLayerAction.RESET_TIMING->listOf(l.copy(common=c.copy(timing=LayerTiming())))
                    BatchLayerAction.DELETE->emptyList()
                    BatchLayerAction.DUPLICATE->{val id=UUID.randomUUID().toString();clones[c.id]=id;listOf(l,l.copy(common=c.copy(id=id,name=c.name+" Copy")))}
                    else->listOf(l)
                }
            }
        }
        val tracks=when(action){
            BatchLayerAction.DELETE->p.keyframes.filterNot{it.targetLayerId in editable}
            BatchLayerAction.DUPLICATE->p.keyframes+p.keyframes.mapNotNull{t->clones[t.targetLayerId]?.let{t.copy(targetLayerId=it)}}
            else->p.keyframes
        }
        return p.copy(compositions=p.compositions.map{if(it.index==composition)it.copy(layers=layers) else it},keyframes=tracks)
    }
}
