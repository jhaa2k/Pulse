package com.vexo.studio.ui.components
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import com.vexo.studio.ui.theme.LocalVexoPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Three separable box passes approximate Gaussian blur, with bounded pixel work on API 24+. */
internal object BackdropBlur {
    fun apply(source:Bitmap,radiusPx:Float,maxSide:Int=640):Bitmap{
        if(radiusPx<=0f)return source
        val scale=(maxSide.toFloat()/maxOf(source.width,source.height)).coerceAtMost(1f)
        val w=(source.width*scale).roundToInt().coerceAtLeast(1)
        val h=(source.height*scale).roundToInt().coerceAtLeast(1)
        val resized=Bitmap.createScaledBitmap(source,w,h,true)
        val readable=if(android.os.Build.VERSION.SDK_INT>=26&&resized.config==Bitmap.Config.HARDWARE)resized.copy(Bitmap.Config.ARGB_8888,false)?:return source else resized
        val pixels=IntArray(w*h)
        readable.getPixels(pixels,0,w,0,0,w,h)
        val tmp=IntArray(pixels.size)
        val radius=(radiusPx*scale*.58f).roundToInt().coerceIn(1,64)
        repeat(3){
            pass(pixels,tmp,w,h,radius,false)
            pass(tmp,pixels,w,h,radius,true)
        }
        return Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888)
    }
    private fun pass(src:IntArray,dst:IntArray,w:Int,h:Int,r:Int,vertical:Boolean){
        val lines=if(vertical)w else h
        val length=if(vertical)h else w
        val stride=if(vertical)w else 1
        val count=r*2+1
        for(line in 0 until lines){
            val base=if(vertical)line else line*w
            var a=0;var red=0;var g=0;var b=0
            for(i in -r..r){
                val c=src[base+i.coerceIn(0,length-1)*stride]
                a+=c ushr 24;red+=(c ushr 16) and 255;g+=(c ushr 8) and 255;b+=c and 255
            }
            for(i in 0 until length){
                dst[base+i*stride]=((a/count) shl 24) or ((red/count) shl 16) or ((g/count) shl 8) or (b/count)
                val left=src[base+(i-r).coerceIn(0,length-1)*stride]
                val right=src[base+(i+r+1).coerceIn(0,length-1)*stride]
                a+=(right ushr 24)-(left ushr 24)
                red+=((right ushr 16) and 255)-((left ushr 16) and 255)
                g+=((right ushr 8) and 255)-((left ushr 8) and 255)
                b+=(right and 255)-(left and 255)
            }
        }
    }
}
@Composable
internal fun BlurredBackdrop(source:Bitmap,radiusDp:Float,modifier:Modifier=Modifier){
    BoxWithConstraints(modifier){
        val density=LocalDensity.current
        val displayW=with(density){maxWidth.toPx()}.coerceAtLeast(1f)
        val displayH=with(density){maxHeight.toPx()}.coerceAtLeast(1f)
        val fit=maxOf(displayW/source.width,displayH/source.height).coerceAtLeast(.001f)
        val radius=radiusDp.coerceIn(0f,48f)*density.density/fit
        val result by produceState<Bitmap?>(null,source,radius){
            value=withContext(Dispatchers.Default){BackdropBlur.apply(source,radius)}
        }
        Image((if(radius<=0f)source else result?:source).asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
    }
}
@Composable
internal fun SceneGlass(frame:Bitmap?,modifier:Modifier=Modifier,content:@Composable BoxScope.()->Unit){
    val p=LocalVexoPalette.current
    Box(modifier.clip(p.shape)){
        if(p.glassEnabled&&frame!=null)Image(frame.asImageBitmap(),null,Modifier.matchParentSize(),contentScale=ContentScale.Crop)
        Box(Modifier.matchParentSize().background(p.glassPanel))
        content()
    }
}
