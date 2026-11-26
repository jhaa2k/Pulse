package com.vexo.studio.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import com.vexo.studio.ui.theme.StudioText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.vexo.studio.engine.scene.CustomSpectrumPath
import com.vexo.studio.model.LayerConfig
import com.vexo.studio.model.SpectrumPathPoint
import com.vexo.studio.ui.theme.LocalVexoPalette

@Composable
internal fun CustomSpectrumPathEditor(layerId:String,cfg:LayerConfig.Bars,apply:(LayerConfig.Bars)->Unit){
    val palette=LocalVexoPalette.current
    var open by remember(layerId){mutableStateOf(false)}
    var points by remember(layerId){mutableStateOf(emptyList<SpectrumPathPoint>())}
    var closed by remember(layerId){mutableStateOf(false)}
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
        TextButton(onClick={points=cfg.customPathPoints.take(32);closed=cfg.customPathClosed;open=true}){
            Text(if(cfg.customPathEnabled)"EDIT CUSTOM PATH" else "DRAW CUSTOM PATH")
        }
        if(cfg.customPathEnabled)TextButton(onClick={apply(cfg.copy(customPathEnabled=false))}){Text("USE PRESET")}
    }
    if(open)AlertDialog(
        onDismissRequest={open=false},
        title={Text("CUSTOM SPECTRUM PATH")},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("Tap to add points in order. Bars follow the line at even spacing.",style=MaterialTheme.typography.bodySmall,color=palette.textSecondary)
                Canvas(Modifier.fillMaxWidth().aspectRatio(1f).background(palette.bg,palette.shape).border(1.dp,palette.border,palette.shape)
                    .pointerInput(points.size){
                        detectTapGestures{pos->
                            if(points.size<32&&size.width>0&&size.height>0){
                                points=points+SpectrumPathPoint((pos.x/size.width-.5f).coerceIn(-.5f,.5f),(pos.y/size.height-.5f).coerceIn(-.5f,.5f))
                            }
                        }
                    }){
                    for(i in 1..3){
                        val f=i/4f
                        drawLine(palette.border,Offset(size.width*f,0f),Offset(size.width*f,size.height))
                        drawLine(palette.border,Offset(0f,size.height*f),Offset(size.width,size.height*f))
                    }
                    fun xy(p:SpectrumPathPoint)=Offset((p.x+.5f)*size.width,(p.y+.5f)*size.height)
                    for(i in 1 until points.size)drawLine(palette.accent,xy(points[i-1]),xy(points[i]),2.dp.toPx())
                    if(closed&&points.size>=3)drawLine(palette.accent,xy(points.last()),xy(points.first()),2.dp.toPx())
                    points.forEachIndexed{i,p->drawCircle(if(i==0)palette.textPrimary else palette.accent,4.dp.toPx(),xy(p))}
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                    Text("${points.size} / 32 POINTS",style=MaterialTheme.typography.labelSmall,color=palette.textSecondary)
                    TextButton(enabled=points.isNotEmpty(),onClick={points=points.dropLast(1)}){Text("UNDO")}
                    TextButton(enabled=points.isNotEmpty(),onClick={points=emptyList()}){Text("CLEAR")}
                }
                StudioToggleRow("Close path",closed){closed=it}
            }
        },
        confirmButton={TextButton(enabled=points.size>=if(closed)3 else 2,onClick={
            if(CustomSpectrumPath.create(points,closed)!=null){
                apply(cfg.copy(customPathEnabled=true,customPathPoints=points,customPathClosed=closed));open=false
            }
        }){Text("APPLY")}},
        dismissButton={TextButton(onClick={open=false}){Text("CANCEL")}}
    )
}
