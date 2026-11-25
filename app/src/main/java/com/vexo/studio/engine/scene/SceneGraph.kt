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
    fun dependenciesOf(comp: CompositionNode): Set<Int> {
        val deps = linkedSetOf<Int>()
        for (layer in comp.layers) {
            if (!layer.common.visible) continue
            when (val cfg = layer.config) {
                is LayerConfig.Image -> {
                    if (cfg.sourceType == com.vexo.studio.model.ImageSourceType.COMPOSITION &&
                        cfg.sourceCompositionIndex != comp.index
                    ) {
                        deps.add(cfg.sourceCompositionIndex)
                    }
                    if (cfg.maskImage.startsWith("composition:")) {
                        cfg.maskImage.substringAfter(':').toIntOrNull()?.takeIf { it != comp.index }?.let { deps.add(it) }
                    }
                }
                is LayerConfig.BlurEffect -> {
                    if (cfg.sourceCompositionIndex != comp.index) {
                        deps.add(cfg.sourceCompositionIndex)
                    }
                }
                is LayerConfig.MotionBlurEffect -> {
                    if (cfg.targetCompositionIndex != comp.index) {
                        deps.add(cfg.targetCompositionIndex)
                    }
                }
                is LayerConfig.MirrorEffect -> {
                    if (cfg.targetCompositionIndex != comp.index) {
                        deps.add(cfg.targetCompositionIndex)
                    }
                }
                is LayerConfig.RgbSplitEffect -> {
                    if (cfg.targetCompositionIndex != comp.index) {
                        deps.add(cfg.targetCompositionIndex)
                    }
                }
                else -> Unit
            }
        }
        return deps
    }

    fun hasSelfReference(comp: CompositionNode): Boolean {
        return comp.layers.any { layer ->
            when (val cfg = layer.config) {
                is LayerConfig.Image -> cfg.sourceType == com.vexo.studio.model.ImageSourceType.COMPOSITION &&
                    cfg.sourceCompositionIndex == comp.index || cfg.maskImage == "composition:${comp.index}"
                is LayerConfig.BlurEffect -> cfg.sourceCompositionIndex == comp.index
                is LayerConfig.MotionBlurEffect -> cfg.targetCompositionIndex == comp.index
                is LayerConfig.MirrorEffect -> cfg.targetCompositionIndex == comp.index
                is LayerConfig.RgbSplitEffect -> cfg.targetCompositionIndex == comp.index
                else -> false
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
