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
    fun reachable(project:VexoProject,root:Int):Set<Int>{
        val nodes=project.compositions.associateBy{it.index}
        val visited=linkedSetOf<Int>()
        fun visit(index:Int){
            val node=nodes[index]?:return
            if(!visited.add(index))return
            dependenciesOf(node).forEach(::visit)
        }
        visit(root)
        return visited
    }

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

    fun resolve(project: VexoProject): ResolvedSceneGraph {
        val byIndex = project.compositions.associateBy { it.index }
        val state = mutableMapOf<Int, Int>() // 0=unvisited, 1=visiting, 2=visited
        val result = mutableListOf<CompositionNode>()
        var cycleDetected = false
        var cycleMsg: String? = null

        fun dfs(idx: Int) {
            val st = state[idx] ?: 0
            if (st == 2) return
            if (st == 1) {
                cycleDetected = true
                cycleMsg = "Cycle detected involving composition:$idx"
                return
            }
            val node = byIndex[idx] ?: return
            if (hasSelfReference(node)) {
                cycleDetected = true
                cycleMsg = "Composition $idx references itself"
            }
            state[idx] = 1
            for (dep in dependenciesOf(node)) {
                if (byIndex.containsKey(dep)) {
                    dfs(dep)
                }
            }
            state[idx] = 2
            result.add(node)
        }

        // Start from Final Composition (0) if present, then any remaining compositions
        if (byIndex.containsKey(0)) {
            dfs(0)
        }
        for (comp in project.compositions) {
            if ((state[comp.index] ?: 0) == 0) {
                dfs(comp.index)
            }
        }
        return ResolvedSceneGraph(result, cycleDetected, cycleMsg)
    }
}
