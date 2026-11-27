package com.vexo.studio.data

import com.vexo.studio.engine.scene.SceneGraph
import com.vexo.studio.model.*

/** Non-destructive compatibility inspection. Warnings do not remove or rewrite layers. */
object TemplateDiagnostics {
    data class Issue(val code:String,val compositionIndex:Int?,val layerName:String?,val detail:String,val active:Boolean)
    data class Report(val issues:List<Issue>,val unsupportedElements:Int){
        fun banner(project:VexoProject):String {
            val prefix="${project.compositions.size} compositions · ${project.compositions.sumOf{it.layers.size}} editable layers"
            val active=issues.count{it.active}
            val draft=issues.size-active
            val first=issues.firstOrNull{it.active}?:issues.firstOrNull()
            return buildString {
                append(prefix)
                if(unsupportedElements>0)append(" · $unsupportedElements unsupported elements preserved")
                if(issues.isNotEmpty()){
                    append(" · $active active / $draft other warnings")
                    if(first!=null)append(" · "+first.detail.take(160))
                }
            }
        }
    }

    private fun assetReferences(c:LayerConfig):List<Pair<String,String?>> = when(c){
        is LayerConfig.Image->listOf("image" to c.customUri,"image mask" to c.maskImage)
        is LayerConfig.Text->listOf("font" to c.fontAssetUri)
        is LayerConfig.Particles->listOf("particle image" to c.customImage)
        is LayerConfig.BlurEffect->listOf("blur mask" to c.maskImage)
        else->emptyList()
    }
    private fun unpacked(value:String?)=value!=null&&(value.startsWith("local:")||value.startsWith("asset:")||value.startsWith("vexasset:"))
    fun requireNoUnpackedAssets(project:VexoProject){
        require(!unpacked(project.audioUri)){"Bundled audio requires the original archive"}
        for(comp in project.compositions)for(layer in comp.layers){
            for((kind,value) in assetReferences(layer.config)){
                require(!unpacked(value)){"Missing bundled $kind in ${layer.common.name}: ${value.orEmpty().take(120)}"}
            }
        }
    }

    fun inspect(project:VexoProject):Report{
        val issues=mutableListOf<Issue>()
        val indices=project.compositions.map{it.index}.toSet()
        val providerIndices=project.audioProviders.map{it.index}.toSet()
        val enabledProviders=project.audioProviders.filter{it.enabled}.map{it.index}.toSet()
        val rootGraph=SceneGraph.resolveOutput(project,0)
        val activeComps=rootGraph.order.map{it.index}.toSet()
        val graph=SceneGraph.resolve(project)
        if(rootGraph.hasCycle)issues.add(Issue("CYCLE",null,null,rootGraph.cycleDetails?:"Active composition cycle",true))
        else if(graph.hasCycle)issues.add(Issue("CYCLE",null,null,graph.cycleDetails?:"Draft composition cycle",false))
        val layerIds=project.compositions.flatMap{it.layers}.map{it.common.id}.toSet()
        for(comp in project.compositions){
            for(layer in comp.layers){
                val c=layer.common
                val cfg=layer.config
                val active=comp.index in activeComps&&c.visible
                fun warn(code:String,detail:String){issues.add(Issue(code,comp.index,c.name,detail,active))}
                for(target in SceneGraph.referencesOf(cfg)){
                    if(target !in indices)warn("MISSING_COMPOSITION","${c.name}: missing composition $target")
                    else if(target==comp.index&&!active)warn("SELF_REFERENCE","${c.name}: references its own composition")
                }
                for((kind,value) in assetReferences(cfg)){
                    if(unpacked(value))warn("UNPACKED_ASSET","${c.name}: unresolved $kind")
                    if(value?.startsWith("composition:")==true&&value.substringAfter(':').toIntOrNull()==null)
                        warn("INVALID_REFERENCE","${c.name}: invalid $kind composition reference")
                }
                if(cfg is LayerConfig.Image&&cfg.sourceType==ImageSourceType.IMPORTED&&cfg.customUri.isNullOrBlank())
                    warn("MISSING_IMAGE","${c.name}: imported image has no source")
                val used=linkedSetOf<Int>()
                fun measure(m:MeasureConfig){
                    if(m.measureWhat in audioMeasures)used.add(m.providerIndex)
                }
                measure(c.measurePos);measure(c.measureScale);measure(c.measureRot);measure(c.measureOpacity)
                when(cfg){
                    is LayerConfig.Bars->{used.add(cfg.audioProviderIndex);measure(cfg.colorBlendMeasure)}
                    is LayerConfig.Image->{measure(cfg.measureColorBlend);measure(cfg.creative.audio)}
                    is LayerConfig.Text->measure(cfg.animationAudio)
                    is LayerConfig.Shape->{measure(cfg.pulse);measure(cfg.strokePulse)}
                    is LayerConfig.Particles->{measure(cfg.measureOverallSpeed);if(cfg.burstCount>0||cfg.reactiveEmission!=0f)used.add(cfg.burstProvider)}
                    is LayerConfig.BlurEffect->measure(cfg.measureBlur)
                    is LayerConfig.MotionBlurEffect->measure(cfg.blurAmountMeasure)
                    is LayerConfig.RgbSplitEffect->{measure(cfg.splitMeasure);measure(cfg.splitMultiplierMeasure)}
                    else->Unit
                }
                for(provider in used){
                    if(provider !in providerIndices)warn("MISSING_PROVIDER","${c.name}: missing audio provider $provider")
                    else if(provider !in enabledProviders)warn("DISABLED_PROVIDER","${c.name}: audio provider $provider disabled")
                }
                // Known legacy properties that are not implemented; raw metadata stays intact.
                val unsupported=buildList {
                    if(cfg is LayerConfig.Image&&c.extraFields.keys.any{it.equals("measureAnimationSpeed",true)})add("animated-image speed")
                    if(cfg is LayerConfig.Bars&&c.extraFields.keys.any{it.equals("barWidthAffectedByShape",true)})add("shape-dependent bar width")
                }
                if(unsupported.isNotEmpty())warn("LEGACY_PROPERTY","${c.name}: preserved but not implemented: "+unsupported.joinToString())
            }
        }
        for(track in project.keyframes){
            if(track.targetLayerId !in layerIds)issues.add(Issue("ORPHAN_KEYFRAME",null,null,"Keyframe target layer not found",false))
        }
        return Report(issues,project.compositions.sumOf{it.preservedElements.size})
    }

    private val audioMeasures=setOf(MeasureWhat.BEAT,MeasureWhat.TOTAL_TIME_AND_BEAT,
        MeasureWhat.BEAT_RANDOM_SHAKE,MeasureWhat.BEAT_CAM_SHAKE_MORE,MeasureWhat.BEAT_CAM_SHAKE_LESS,
        MeasureWhat.BEAT_CAM_SHAKE_ROT_MORE,MeasureWhat.BEAT_CAM_SHAKE_ROT_LESS,
        MeasureWhat.BEAT_TRIGGER_ANIM,MeasureWhat.BASS,MeasureWhat.MID,MeasureWhat.TREBLE,MeasureWhat.RMS,MeasureWhat.PEAK)
}
