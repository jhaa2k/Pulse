package com.vexo.studio.ui.components
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import com.vexo.studio.ui.theme.LocalVexoPalette
import com.vexo.studio.ui.theme.VexoPalette
import kotlin.math.roundToInt

internal fun DrawScope.drawStudioTrack(p:VexoPalette,fraction:Float,enabled:Boolean=true,scale:Float=1f,defaultFraction:Float?=null){
    val pad=(9f*scale).dp.toPx().coerceAtMost(size.width/2)
    val y=size.height*.5f
    val width=(size.width-2*pad).coerceAtLeast(0f)
    val x=pad+width*fraction.coerceIn(0f,1f)
    val stroke=(4f*scale).dp.toPx()
    drawLine(p.border,Offset(pad,y),Offset(pad+width,y),stroke,cap=StrokeCap.Round)
    for(i in 1..19){
        val tx=pad+width*i/20f
        drawLine(p.textSecondary.copy(alpha=.30f),Offset(tx,y-(5f*scale).dp.toPx()),Offset(tx,y-(3f*scale).dp.toPx()),1.dp.toPx())
    }
    defaultFraction?.let{
        val dx=pad+width*it.coerceIn(0f,1f)
        drawLine(p.textSecondary.copy(alpha=.85f),Offset(dx,y-(9f*scale).dp.toPx()),Offset(dx,y-(4f*scale).dp.toPx()),1.dp.toPx())
    }
    if(enabled){
        drawLine(p.accent,Offset(pad,y),Offset(x,y),stroke,cap=StrokeCap.Round)
        drawCircle(p.accent.copy(alpha=.22f),(8f*scale).dp.toPx(),Offset(x,y))
        drawCircle(p.accent,(5f*scale).dp.toPx(),Offset(x,y))
        drawCircle(p.accentOn,(2f*scale).dp.toPx(),Offset(x,y))
    }
}
@Composable
internal fun CompactStudioSlider(label:String,value:Float,onChange:(Float)->Unit,modifier:Modifier=Modifier,min:Float=0f,max:Float=1f,step:Float=0f){
    val p=LocalVexoPalette.current
    val change by rememberUpdatedState(onChange)
    val safe=value.coerceIn(min,max)
    fun fromFraction(f:Float):Float{
        val raw=min+(max-min)*f.coerceIn(0f,1f)
        return if(step>0f)(min+((raw-min)/step).roundToInt()*step).coerceIn(min,max) else raw
    }
    Canvas(modifier.semantics{
        contentDescription=label
        progressBarRangeInfo=ProgressBarRangeInfo(safe,min..max)
        setProgress{change(fromFraction((it-min)/(max-min)));true}
    }.pointerInput(min,max,step,p.controlScale){
        detectTapGestures{point->
            val pad=(9f*p.controlScale).dp.toPx()
            change(fromFraction((point.x-pad)/(size.width-2*pad).coerceAtLeast(1f)))
        }
    }.pointerInput(min,max,step,p.controlScale){
        detectHorizontalDragGestures{event,_->
            event.consume()
            val pad=(9f*p.controlScale).dp.toPx()
            change(fromFraction((event.position.x-pad)/(size.width-2*pad).coerceAtLeast(1f)))
        }
    }){
        drawStudioTrack(p,(safe-min)/(max-min),scale=p.controlScale)
    }
}
