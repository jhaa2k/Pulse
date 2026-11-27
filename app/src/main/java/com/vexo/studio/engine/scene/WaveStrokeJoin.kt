package com.vexo.studio.engine.scene
import com.vexo.studio.model.CompositionNode
import com.vexo.studio.model.LayerConfig
import kotlin.math.*

object OffscreenEdges{
    fun hasGeometry(comp:CompositionNode):Boolean=comp.layers.any{it.common.visible&&(it.config is LayerConfig.Bars||it.config is LayerConfig.Shape)}
}

/** Shared miter offset for adjacent stroke strips. Limit acute corners to prevent spikes. */
object WaveStrokeJoin{
    fun offset(px:Float,py:Float,x:Float,y:Float,nx:Float,ny:Float,half:Float):Pair<Float,Float>{
        if(!half.isFinite()||half<=0f)return 0f to 0f
        fun normal(dx:Float,dy:Float):Pair<Float,Float>?{
            val length=sqrt(dx*dx+dy*dy)
            return if(length.isFinite()&&length>1e-5f)dy/length to -dx/length else null
        }
        val incoming=normal(x-px,y-py);val outgoing=normal(nx-x,ny-y)
        val a=incoming?:outgoing?:return 0f to half
        val b=outgoing?:a
        val sx=a.first+b.first;val sy=a.second+b.second
        val length=sqrt(sx*sx+sy*sy)
        if(length<.001f)return a.first*half to a.second*half
        val mx=sx/length;val my=sy/length
        val gain=(half/(mx*b.first+my*b.second).coerceAtLeast(.5f)).coerceAtMost(half*2f)
        return mx*gain to my*gain
    }
}
