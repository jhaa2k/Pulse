package com.vexo.studio.engine.scene

import com.vexo.studio.model.CompositionNode
import com.vexo.studio.model.LayerConfig
import com.vexo.studio.model.VexoProject

data class ResolvedSceneGraph(
    val order: List<CompositionNode>,
    val hasCycle: Boolean,
    val cycleDetails: String? = null
)

/**
 * Resolves composition dependency edges topologically so sub-compositions render
 * to offscreen textures before compositions that reference them, while detecting
 * self-references and cycles.
 */
object SceneGraph {
    fun referencesOf(cfg:LayerConfig):Set<Int>{
        val result=linkedSetOf<Int>()
        fun mask(value:String){if(value.startsWith("composition:"))value.substringAfter(':').toIntOrNull()?.let{result.add(it)}}
        when(cfg){
            is LayerConfig.Image->{if(cfg.sourceType==com.vexo.studio.model.ImageSourceType.COMPOSITION)result.add(cfg.sourceCompositionIndex);mask(cfg.maskImage)}
            is LayerConfig.BlurEffect->{result.add(cfg.sourceCompositionIndex);mask(cfg.maskImage)}
            is LayerConfig.MotionBlurEffect->result.add(cfg.targetCompositionIndex)
            is LayerConfig.MirrorEffect->result.add(cfg.targetCompositionIndex)
            is LayerConfig.RgbSplitEffect->result.add(cfg.targetCompositionIndex)
            else->Unit
        }
        return result
    }

    fun dependenciesOf(comp: CompositionNode): Set<Int> {
        return comp.layers.filter{it.common.visible}.flatMap{referencesOf(it.config)}.filter{it!=comp.index}.toSet()
    }

    fun hasSelfReference(comp:CompositionNode):Boolean =
        comp.layers.any{it.common.visible && comp.index in referencesOf(it.config)}

    /** Only the dependency closure of the displayed composition needs rendering. */
    fun reachable(project:VexoProject,root:Int):Set<Int> =
        resolveOutput(project,root).order.mapTo(linkedSetOf()){it.index}

    /** Resolve only the selected output. Unrelated draft cycles cannot affect it. */
    fun resolveOutput(project:VexoProject,root:Int):ResolvedSceneGraph =
        resolveRoots(project,listOf(root))

    data class MissingReference(val compositionIndex:Int,val layerName:String,val targetIndex:Int)
    /** Inspect only active dependencies for the requested output, not unused draft compositions. */
    fun missingReferences(project:VexoProject,root:Int=0):List<MissingReference>{
        val available=project.compositions.map{it.index}.toSet()
        val active=reachable(project,root)
        return project.compositions.filter{it.index in active}.flatMap{comp->
            comp.layers.filter{it.common.visible}.flatMap{layer->
                referencesOf(layer.config).filter{it !in available}.map{MissingReference(comp.index,layer.common.name,it)}
            }
        }
    }

    fun resolve(project: VexoProject): ResolvedSceneGraph =
        resolveRoots(project,listOf(0)+project.compositions.map{it.index})

    private data class Visit(val node:CompositionNode,val edges:Iterator<Int>)

    private fun resolveRoots(project:VexoProject,roots:List<Int>):ResolvedSceneGraph {
        val byIndex=project.compositions.associateBy{it.index}
        val state=mutableMapOf<Int,Int>()
        val order=mutableListOf<CompositionNode>()
        val stack=ArrayDeque<Visit>()
        var cycleMsg:String?=null

        fun enter(node:CompositionNode){
            state[node.index]=1
            // Retain self edges so the same cycle detection handles every reference.
            val edges=node.layers.asSequence().filter{it.common.visible}
                .flatMap{referencesOf(it.config).asSequence()}.distinct().iterator()
            stack.addLast(Visit(node,edges))
        }

        for(root in roots){
            val node=byIndex[root]?:continue
            if(state[root]!=null)continue
            enter(node)
            while(stack.isNotEmpty()){
                val top=stack.last()
                if(!top.edges.hasNext()){
                    state[top.node.index]=2
                    order.add(top.node)
                    stack.removeLast()
                    continue
                }
                val dep=top.edges.next()
                val target=byIndex[dep]?:continue
                when(state[dep]){
                    1->if(cycleMsg==null){
                        cycleMsg=if(dep==top.node.index)"Composition $dep references itself"
                            else "Cycle detected involving composition:$dep"
                    }
                    2->Unit
                    else->enter(target)
                }
            }
        }
        return ResolvedSceneGraph(order,cycleMsg!=null,cycleMsg)
    }
}
