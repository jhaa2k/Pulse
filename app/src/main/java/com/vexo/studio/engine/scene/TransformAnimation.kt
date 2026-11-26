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
        if(matching.isEmpty())return c
        fun at(key:String)=KeyframeEvaluator.evaluate(matching.firstOrNull{it.propertyKey=="transform."+key},timeMs,value(c,key))
        return c.copy(posX=at("posX"),posY=at("posY"),scaleX=at("scaleX"),scaleY=at("scaleY"),rotationDeg=at("rotationDeg"),opacity=at("opacity").coerceIn(0f,1f))
    }
}
