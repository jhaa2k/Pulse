package com.vexo.studio.ui.screens

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.vexo.studio.engine.scene.*
import com.vexo.studio.engine.gl.*
import com.vexo.studio.model.*
import com.vexo.studio.ui.StudioViewModel
import com.vexo.studio.ui.theme.LocalVexoPalette
import com.vexo.studio.ui.theme.StudioText as Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

/** Single live renderer only for the explicitly selected preview, never for all grid cells. */
@androidx.media3.common.util.UnstableApi
@Composable
internal fun CreativePresetBrowser(vm:StudioViewModel){
    val palette=LocalVexoPalette.current
    val templates=remember{vm.availableTemplates()}
    var open by remember{mutableStateOf(false)}
    var selected by remember{mutableStateOf<VexoProject?>(null)}
    var query by rememberSaveable{mutableStateOf("")}
    var categoryName by rememberSaveable{mutableStateOf(PresetCategory.ALL.name)}
    val category=PresetCategory.entries.firstOrNull{it.name==categoryName}?:PresetCategory.ALL
    var animated by remember{mutableStateOf(false)}
    Text("CREATIVE PROJECT PRESETS · "+templates.size,style=MaterialTheme.typography.labelMedium,color=palette.accent)
    TextButton(onClick={selected=null;animated=false;open=true}){Text("BROWSE VISUAL PRESET GALLERY")}
    Text("Preview designs before applying. Current music and canvas ratio stay unchanged.",style=MaterialTheme.typography.bodySmall,color=palette.textSecondary)
    if(open){
        val filtered=remember(templates,query,category){templates.filter{PresetPreviewData.matches(it,query,category)}}
        LaunchedEffect(filtered,selected?.id){
            if(selected!=null&&filtered.none{it.id==selected?.id}){selected=null;animated=false}
        }
        AlertDialog(onDismissRequest={open=false;animated=false;selected=null},title={Text("CREATIVE PRESETS · "+filtered.size)},text={
            Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)){
                CompactStudioSearch(query,{query=it.take(80)},"Search name / shape / effect",Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)){
                    PresetCategory.entries.forEach{cat->FilterChip(selected=category==cat,onClick={categoryName=cat.name},label={Text(cat.name)})}
                }
                selected?.let{tpl->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                        Text(tpl.name,modifier=Modifier.weight(1f),style=MaterialTheme.typography.labelMedium)
                        TextButton(onClick={animated=!animated}){Text(if(animated)"STOP DEMO" else "ANIMATE")}
                    }
                    if(animated)key(tpl.id){AnimatedPresetPreview(tpl,Modifier.fillMaxWidth().height(128.dp))}
                    Text("Demo signal · artwork placeholders",style=MaterialTheme.typography.labelSmall,color=palette.textSecondary)
                }
                if(filtered.isEmpty())Text("No matching presets",color=palette.textSecondary)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max=300.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                    items(filtered.chunked(2),key={it.first().name}){row->
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                            row.forEach{tpl->
                                Column(Modifier.weight(1f).border(if(selected?.id==tpl.id)2.dp else 1.dp,if(selected?.id==tpl.id)palette.accent else palette.border,palette.shape).clickable{selected=tpl;animated=false}.padding(5.dp)){
                                    ProjectPresetThumbnail(tpl,Modifier.fillMaxWidth().height(76.dp))
                                    Text(tpl.name,style=MaterialTheme.typography.labelMedium,maxLines=1)
                                    Text("${tpl.compositions.sumOf{it.layers.size}} LAYERS",style=MaterialTheme.typography.labelSmall,color=palette.textSecondary)
                                }
                            }
                            if(row.size==1)Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        },confirmButton={TextButton(enabled=selected!=null,onClick={selected?.let(vm::applyPreset);open=false;animated=false;selected=null}){Text("APPLY")}},dismissButton={TextButton(onClick={open=false;animated=false;selected=null}){Text("CANCEL")}})
    }
}

@Composable
private fun AnimatedPresetPreview(project:VexoProject,modifier:Modifier){
    var time by remember(project.id){mutableStateOf(0f)}
    LaunchedEffect(project.id){while(true){delay(50);time+=.05f}}
    val snapshot=remember(project,time){
        RenderSceneSnapshot(project,project.audioProviders.associate{it.index to PresetPreviewData.sample(it.index,it.sampleOutCount,time)},TrackMetadataSnapshot(title="DEMO TRACK",artist="VEXO STUDIO",positionMs=(time*1000).toLong(),durationMs=3600000),true,time,time)
    }
    VexoPreviewSurface(project.canvas.aspectRatio,snapshot,{_,_,_->},modifier,targetFps=20,renderScale=.75f)
}

@Composable
private fun ProjectPresetThumbnail(project:VexoProject,modifier:Modifier){
    val bitmap by produceState<Bitmap?>(null,project.id){
        value=withContext(Dispatchers.Default){runCatching{renderThumbnail(project)}.getOrNull()}
    }
    val b=bitmap
    if(b!=null)Image(b.asImageBitmap(),contentDescription="Spectrum geometry preview for "+project.name,modifier=modifier,contentScale=ContentScale.Fit)
    else Box(modifier.background(Color(project.canvas.backgroundColorArgb)))
}

/** Software-rasterized real spectrum triangles with per-vertex color and layer transforms.
 * Lightweight thumbnail intentionally excludes blur, particles and external artwork.
 */
private fun renderThumbnail(project:VexoProject):Bitmap{
    val w=256;val h=144
    val bmp=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
    val canvas=android.graphics.Canvas(bmp)
    canvas.drawColor(project.canvas.backgroundColorArgb)
    val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=android.graphics.Color.WHITE}
    for(comp in project.compositions){
        for(layer in comp.layers){
            val cfg=layer.config as? LayerConfig.Bars?:continue
            if(!layer.common.visible)continue
            val common=layer.common
            val sample=PresetPreviewData.sample(cfg.audioProviderIndex,cfg.barCount.coerceIn(12,96),.12f)
            val coords=ArrayList<Float>();val colors=ArrayList<Int>()
            fun draw(b:LayerConfig.Bars){
                coords.clear();colors.clear()
                SpectrumStudioMesh.emit(b,if(b.signedWaveform)sample.waveform else sample.bars,sample.peakBars,w,h){x,y,col->
                    coords.add(x);coords.add(y)
                    colors.add((col and 0xFFFFFF) or (((col ushr 24)*common.opacity.coerceIn(0f,1f)).toInt() shl 24))
                }
                if(coords.isNotEmpty())canvas.drawVertices(android.graphics.Canvas.VertexMode.TRIANGLES,coords.size,coords.toFloatArray(),0,null,0,colors.toIntArray(),0,null,0,0,paint)
            }
            canvas.save()
            canvas.translate(common.posX*w,common.posY*h)
            canvas.rotate(common.rotationDeg)
            canvas.scale(common.scaleX,if(common.scaleIsUniform)common.scaleX else common.scaleY)
            canvas.translate(-common.alignmentX*w,-common.alignmentY*h)
            if(cfg.segment1!=BarsSegmentType.NONE)draw(cfg)
            if(cfg.segment2!=BarsSegmentType.NONE)draw(cfg.copy(segment1=cfg.segment2,segment2=BarsSegmentType.NONE,creativeStyle=cfg.secondaryCreativeStyle,barWidth=cfg.segment2Width,barHeightMultiplier=cfg.segment2HeightMultiplier,fixedHeight=cfg.segment2FixedHeight,mirror=cfg.segment2Mirror,alternatingDirection=cfg.segment2AlternatingDirection?:cfg.alternatingDirection,barWidthAffectedByShape=cfg.segment2WidthAffectedByShape,colorFromArgb=cfg.segment2ColorFrom,colorToArgb=cfg.segment2ColorTo,barStrokeWidth=cfg.segment2StrokeWidth,barStrokeColorArgb=cfg.segment2StrokeColorArgb,barFillOpacity=cfg.segment2FillOpacity))
            canvas.restore()
        }
    }
    return bmp
}
