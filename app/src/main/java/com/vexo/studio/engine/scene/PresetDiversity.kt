package com.vexo.studio.engine.scene

import com.vexo.studio.model.*

/** Color-, name- and UUID-independent signature of visible spectrum structure. */
object PresetDiversity {
    fun spectrumSignature(project:VexoProject):List<List<Any>> =
        project.compositions.filter{it.index in SceneGraph.reachable(project,0)}.flatMap{comp->
            comp.layers.filter{it.common.visible}.mapNotNull{l->
                val b=l.config as? LayerConfig.Bars?:return@mapNotNull null
                val c=l.common
                listOf(comp.index,b.creativePath,b.shapePath,b.creativeStyle,b.segment1,b.segment2,b.secondaryCreativeStyle,b.barCount,b.circleRadius,b.barWidth,b.heightScale,b.mirror,b.signedWaveform,b.curveSubdivisions,b.barStrokeWidth,b.barFillOpacity,b.waveDashRatio,b.waveRibbonWidth,c.posX,c.posY,c.scaleX,c.scaleY,c.rotationDeg)
            }
        }
}
