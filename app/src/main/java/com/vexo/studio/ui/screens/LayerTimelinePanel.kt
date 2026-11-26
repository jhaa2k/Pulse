package com.vexo.studio.ui.screens
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import com.vexo.studio.ui.theme.StudioText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.vexo.studio.model.*
import com.vexo.studio.engine.scene.*
import com.vexo.studio.ui.StudioViewModel
import com.vexo.studio.ui.StudioUiState
import com.vexo.studio.ui.theme.LocalVexoPalette
import com.vexo.studio.ui.components.StudioChipSelector

@UnstableApi
@Composable
internal fun ColumnScope.LayerTimelinePanel(vm:StudioViewModel,ui:StudioUiState,comp:CompositionNode){
    val palette=LocalVexoPalette.current
    val playback by vm.playbackState.collectAsState()
    var mode by remember{mutableStateOf(TimelineDragMode.MOVE)}
    val duration=playback.durationMs
    val total=maxOf(if(duration>0)1000L else 10000L,duration,comp.layers.maxOfOrNull{maxOf(it.common.timing.startMs+100L,it.common.timing.endMs)}?:0L)
    Text("LAYER TIMELINE",style=MaterialTheme.typography.labelMedium,color=palette.accent,modifier=Modifier.padding(6.dp))
    StudioChipSelector("Drag mode",TimelineDragMode.entries,mode,{it.name.replace('_',' ')}){mode=it}
    Text(if(duration<=0)"Import audio to enable timeline seeking." else "Tap ruler to seek. Drag a layer using the selected mode.",style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(horizontal=6.dp))
    Row(Modifier.fillMaxWidth().padding(6.dp),verticalAlignment=Alignment.CenterVertically){
        Text("0:00",style=MaterialTheme.typography.labelSmall,modifier=Modifier.width(84.dp))
        Canvas(Modifier.weight(1f).height(30.dp).background(palette.panelElevated).pointerInput(total,duration){
            if(duration>0)detectTapGestures{p->vm.playbackController.seekTo((p.x/size.width.coerceAtLeast(1)*total).toLong().coerceIn(0L,duration))}
        }){
            for(i in 0..4){val x=size.width*i/4f;drawLine(palette.border,Offset(x,0f),Offset(x,size.height),1.dp.toPx())}
            val x=(playback.positionMs.toFloat()/total).coerceIn(0f,1f)*size.width
            drawLine(palette.accent,Offset(x,0f),Offset(x,size.height),2.dp.toPx())
        }

    }
    Text("Timeline span: "+"%d:%02d".format(total/60000,total/1000%60),style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(horizontal=6.dp))
    LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(horizontal=6.dp)){
        items(comp.layers,key={it.common.id}){layer->
            val t=layer.common.timing
            var draft by remember(layer.common.id,t){mutableStateOf<LayerTiming?>(null)}
            var delta by remember(layer.common.id,t){mutableStateOf(0f)}
            val shown=draft?:t
            val markers=remember(ui.project.keyframes,layer.common.id){ui.project.keyframes.filter{it.targetLayerId==layer.common.id}.flatMap{it.keyframes}.map{it.timeMs}.distinct().sorted()}
            Row(Modifier.fillMaxWidth().height(42.dp),verticalAlignment=Alignment.CenterVertically){
                Text(layer.common.name,style=MaterialTheme.typography.labelSmall,color=if(ui.selectedLayerId==layer.common.id)palette.accent else palette.textPrimary,maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.width(84.dp).clickable{vm.selectLayer(layer.common.id);vm.selectStudioTab(StudioTab.PROPERTIES)})
                Canvas(Modifier.weight(1f).height(32.dp).background(palette.panelElevated).pointerInput(t,total,mode,layer.common.locked){
                    if(!layer.common.locked)detectHorizontalDragGestures(onDragStart={delta=0f;draft=t},onDragEnd={draft?.let{vm.setTimelineTiming(layer.common.id,it)};draft=null},onDragCancel={draft=null}){change,amount->
                        change.consume();delta+=amount
                        draft=TimelineEditing.edit(t,(delta/size.width.coerceAtLeast(1)*total).toLong(),total,mode)
                    }
                }){
                    val start=(shown.startMs.toFloat()/total).coerceIn(0f,1f)*size.width
                    val end=((if(shown.endMs>0)shown.endMs else total).toFloat()/total).coerceIn(0f,1f)*size.width
                    val width=(end-start).coerceAtLeast(0f)
                    val color=if(layer.common.visible)palette.accent else palette.textSecondary
                    drawRect(color.copy(alpha=if(layer.common.locked).25f else .55f),Offset(start,4.dp.toPx()),Size(width,(size.height-8.dp.toPx()).coerceAtLeast(0f)))
                    val fadeIn=(shown.fadeInMs.toFloat()/total*size.width).coerceIn(0f,width)
                    val fadeOut=(shown.fadeOutMs.toFloat()/total*size.width).coerceIn(0f,width)
                    if(fadeIn>0)drawLine(palette.textPrimary,Offset(start,size.height-4.dp.toPx()),Offset(start+fadeIn,4.dp.toPx()),1.dp.toPx())
                    if(fadeOut>0)drawLine(palette.textPrimary,Offset(end-fadeOut,4.dp.toPx()),Offset(end,size.height-4.dp.toPx()),1.dp.toPx())
                    markers.filter{it in 0..total}.forEach{time->
                        val x=time.toFloat()/total*size.width;val y=size.height*.5f;val r=3.dp.toPx()
                        val path=androidx.compose.ui.graphics.Path().apply{moveTo(x,y-r);lineTo(x+r,y);lineTo(x,y+r);lineTo(x-r,y);close()}
                        drawPath(path,palette.textPrimary)
                    }
                    val play=(playback.positionMs.toFloat()/total).coerceIn(0f,1f)*size.width
                    drawLine(palette.textPrimary,Offset(play,0f),Offset(play,size.height),1.dp.toPx())
                }
            }
        }
    }
}
