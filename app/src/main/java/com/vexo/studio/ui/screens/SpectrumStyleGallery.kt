package com.vexo.studio.ui.screens
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import com.vexo.studio.ui.theme.StudioText as Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.vexo.studio.engine.scene.SpectrumStyleLibrary
import com.vexo.studio.engine.scene.SpectrumStudioMesh
import com.vexo.studio.model.LayerConfig
import com.vexo.studio.ui.theme.LocalVexoPalette
import kotlin.math.*

@Composable
internal fun SpectrumStyleGallery(layerId:String,cfg:LayerConfig.Bars,apply:(LayerConfig.Bars)->Unit){
    val palette=LocalVexoPalette.current
    var open by remember{mutableStateOf(false)}
    var usedText by rememberSaveable(layerId){mutableStateOf("")}
    var query by rememberSaveable{mutableStateOf("")}
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
        TextButton(onClick={open=true}){Text("STYLE GALLERY (${SpectrumStyleLibrary.recipes.size})")}
        TextButton(onClick={
            val used=usedText.split('|').filter{it.isNotBlank()}.toSet()
            val (recipe,next)=SpectrumStyleLibrary.next(used,cfg)
            usedText=next.joinToString("|")
            apply(recipe.apply(cfg))
        }){Text("SHUFFLE STYLE")}
    }
    Text("Shuffle cycles through unique designs. Samples are illustrative; playback uses real audio.",style=MaterialTheme.typography.labelSmall,color=palette.textSecondary)
    if(open)AlertDialog(onDismissRequest={open=false},title={Text("SPECTRUM STYLE GALLERY")},text={
        Column(Modifier.fillMaxWidth()){
            OutlinedTextField(query,{query=it.take(60)},singleLine=true,label={Text("Search style / shape")},modifier=Modifier.fillMaxWidth())
            val recipes=SpectrumStyleLibrary.recipes.filter{it.name.contains(query,true)||it.style.name.contains(query,true)||it.path.name.contains(query,true)}
            LazyColumn(Modifier.heightIn(max=380.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                items(recipes.chunked(2)){row->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                        row.forEach{recipe->
                            Column(Modifier.weight(1f).border(1.dp,palette.border).clickable{apply(recipe.apply(cfg));open=false}.padding(4.dp)){
                                SpectrumSample(recipe.apply(LayerConfig.Bars()),Modifier.fillMaxWidth().height(82.dp))
                                Text(recipe.name,style=MaterialTheme.typography.labelMedium,maxLines=1)
                                Text(recipe.style.name.replace('_',' ')+" · "+recipe.path.name.replace('_',' '),style=MaterialTheme.typography.labelSmall,color=palette.textSecondary,maxLines=2)
                            }
                        }
                        if(row.size==1)Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    },confirmButton={TextButton(onClick={open=false}){Text("CLOSE")}})
}
private data class MeshVertex(val x:Float,val y:Float,val argb:Int)
@Composable
private fun SpectrumSample(cfg:LayerConfig.Bars,modifier:Modifier){
    val paths=remember(cfg){
        val n=cfg.barCount.coerceIn(16,80)
        val bars=FloatArray(n){i->(.10f+.22f*abs(sin(i*.41f))+.12f*abs(cos(i*.89f)))*(1f-.45f*i/n)}
        val peaks=FloatArray(n){i->bars[i]+.09f}
        val points=ArrayList<MeshVertex>()
        SpectrumStudioMesh.emit(cfg,bars,peaks,240,140){x,y,c->points.add(MeshVertex(x,y,c))}
        if(cfg.segment2!=com.vexo.studio.model.BarsSegmentType.NONE){
            val second=cfg.copy(segment1=cfg.segment2,segment2=com.vexo.studio.model.BarsSegmentType.NONE,creativeStyle=cfg.secondaryCreativeStyle,barWidth=cfg.segment2Width,barHeightMultiplier=cfg.segment2HeightMultiplier,fixedHeight=cfg.segment2FixedHeight,mirror=cfg.segment2Mirror,colorFromArgb=cfg.segment2ColorFrom,colorToArgb=cfg.segment2ColorTo)
            SpectrumStudioMesh.emit(second,bars,peaks,240,140){x,y,c->points.add(MeshVertex(x,y,c))}
        }
        val grouped=linkedMapOf<Int,Path>()
        for(i in points.indices step 3){
            val a=points[i];val b=points[i+1];val c=points[i+2]
            val p=grouped.getOrPut(a.argb){Path()}
            p.moveTo(a.x,a.y);p.lineTo(b.x,b.y);p.lineTo(c.x,c.y);p.close()
        }
        grouped
    }
    Canvas(modifier.background(Color(0xFF101318))){
        val factor=minOf(size.width/240f,size.height/140f)
        withTransform({translate((size.width-240f*factor)*.5f,(size.height-140f*factor)*.5f);scale(factor,factor,pivot=Offset.Zero)}){
            paths.forEach{(color,path)->drawPath(path,Color(color))}
        }
    }
}
