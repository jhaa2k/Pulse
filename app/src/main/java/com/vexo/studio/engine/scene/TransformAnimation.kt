package com.vexo.studio.engine.scene
import com.vexo.studio.model.LayerCommon
import com.vexo.studio.model.KeyframeTrack
object TransformAnimation{
    val keys=listOf("posX","posY","scaleX","scaleY","rotationDeg","opacity")
    fun value(c:LayerCommon,key:String):Float=when(key){
        "posX"->c.posX;"posY"->c.posY;"scaleX"->c.scaleX;"scaleY"->c.scaleY;"rotationDeg"->c.rotationDeg;"opacity"->c.opacity;else->0f
    }
    fun evaluate(c:LayerCommon,tracks:List<KeyframeTrack>,timeMs:Long):LayerCommon{
        val matching=tracks.filter{it.targetLayerId==c.id&&it.propertyKey.startsWith("transform.")}
        fun at(key:String,default:Float):Float{
            val base=value(c,key).takeIf{it.isFinite()}?:default
            return KeyframeEvaluator.evaluate(matching.firstOrNull{it.propertyKey=="transform."+key},timeMs,base).takeIf{it.isFinite()}?:base
        }
        val x=at("scaleX",1f)
        return c.copy(posX=at("posX",.5f),posY=at("posY",.5f),scaleX=x,scaleY=if(c.scaleIsUniform)x else at("scaleY",1f),rotationDeg=at("rotationDeg",0f),opacity=at("opacity",1f).coerceIn(0f,1f))
    }
}
