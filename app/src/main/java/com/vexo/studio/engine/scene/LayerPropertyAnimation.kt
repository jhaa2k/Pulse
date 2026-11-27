package com.vexo.studio.engine.scene

import com.vexo.studio.model.*
import kotlin.math.roundToLong

/** Native property tracks are additive to existing transform tracks, not a new format. */
object LayerPropertyAnimation{
    private val labels=linkedMapOf(
        "bars.height" to "Bar height", "bars.width" to "Bar width", "bars.radius" to "Circle radius",
        "bars.angle" to "Path angle", "bars.stroke" to "Bar stroke", "bars.fill" to "Bar fill opacity",
        "bars.gradient" to "Gradient offset", "bars.waveWidth" to "Wave line width", "bars.baseline" to "Baseline offset",
        "shape.radius" to "Shape radius", "shape.stroke" to "Shape stroke", "shape.corner" to "Corner radius",
        "blur.radius" to "Blur radius", "blur.multiplier" to "Blur multiplier",
        "blur.radial" to "Radial strength", "blur.intensity" to "Bloom intensity", "blur.threshold" to "Bloom threshold",
        "blur.glow1" to "Glow scale 1", "blur.glow2" to "Glow scale 2", "blur.glow3" to "Glow scale 3",
        "motion.amount" to "Motion blur amount", "motion.trail" to "Trail persistence",
        "motion.x" to "Manual motion X", "motion.y" to "Manual motion Y"
    )
    fun keys(layer:LayerNode):List<String> = TransformAnimation.keys + when(layer.config){
        is LayerConfig.Bars->labels.keys.filter{it.startsWith("bars.")}
        is LayerConfig.Shape->labels.keys.filter{it.startsWith("shape.")}
        is LayerConfig.BlurEffect->labels.keys.filter{it.startsWith("blur.")}
        is LayerConfig.MotionBlurEffect->labels.keys.filter{it.startsWith("motion.")}
        else->emptyList()
    }
    fun label(key:String)=labels[key]?:when(key){
        "posX"->"Position X";"posY"->"Position Y";"scaleX"->"Scale X";"scaleY"->"Scale Y"
        "rotationDeg"->"Rotation";"opacity"->"Opacity";else->key
    }
    fun trackKey(key:String)=if(key in TransformAnimation.keys)"transform."+key else "property."+key
    fun clamp(key:String,v:Float):Float{
        val range=when(key){
            "bars.height"->0f..8f
            "bars.width"->.05f..1.5f
            "bars.radius"->.01f.. .8f
            "bars.angle"->-360f..360f
            "bars.stroke"->0f..24f
            "bars.fill","bars.gradient","opacity"->0f..1f
            "bars.waveWidth"->.25f..24f
            "bars.baseline"->-1f..1f
            "shape.radius"->.005f.. .8f
            "shape.stroke"->0f..32f
            "shape.corner"->0f.. .5f
            "blur.radius"->0f..24f
            "blur.multiplier"->0f..8f
            "blur.radial","blur.threshold"->0f..1f
            "blur.intensity"->0f..4f
            "blur.glow1","blur.glow2","blur.glow3"->0f..3f
            "motion.amount"->0f..3f
            "motion.trail"->0f.. .95f
            "motion.x","motion.y"->-.25f.. .25f
            else->return v
        }
        return v.coerceIn(range.start,range.endInclusive)
    }
    fun value(layer:LayerNode,key:String):Float{
        if(key in TransformAnimation.keys)return TransformAnimation.value(layer.common,key)
        return when(val c=layer.config){
            is LayerConfig.Bars->when(key){
                "bars.height"->c.heightScale;"bars.width"->c.barWidth;"bars.radius"->c.circleRadius;"bars.angle"->c.pathAngleDeg
                "bars.stroke"->c.barStrokeWidth;"bars.fill"->c.barFillOpacity;"bars.gradient"->c.colorBlendOffset
                "bars.waveWidth"->c.creativeStrokeWidth;"bars.baseline"->c.baselineOffset;else->0f
            }
            is LayerConfig.Shape->when(key){"shape.radius"->c.radius;"shape.stroke"->c.strokeWidth;"shape.corner"->c.cornerRadius;else->0f}
            is LayerConfig.BlurEffect->when(key){"blur.radius"->c.blurRadius;"blur.multiplier"->c.blurMultiplier;"blur.radial"->c.radialStrength;"blur.intensity"->c.bloomIntensity;"blur.threshold"->c.bloomThreshold;"blur.glow1"->c.layer1Scale;"blur.glow2"->c.layer2Scale;"blur.glow3"->c.layer3Scale;else->0f}
            is LayerConfig.MotionBlurEffect->when(key){"motion.amount"->c.blurAmountMultiplier;"motion.trail"->c.trailPersistence;"motion.x"->c.manualMotionX;"motion.y"->c.manualMotionY;else->0f}
            else->0f
        }
    }
    fun evaluate(layer:LayerNode,tracks:List<KeyframeTrack>,timeMs:Long):LayerConfig{
        val relevant=tracks.filter{it.targetLayerId==layer.common.id&&it.propertyKey.startsWith("property.")}
        if(relevant.isEmpty())return layer.config
        fun at(key:String,base:Float):Float{
            val t=relevant.firstOrNull{it.propertyKey==trackKey(key)}?:return base
            return KeyframeEvaluator.evaluate(t,timeMs,base).takeIf{it.isFinite()}?.let{clamp(key,it)}?:base
        }
        return when(val c=layer.config){
            is LayerConfig.Bars->c.copy(heightScale=at("bars.height",c.heightScale),barWidth=at("bars.width",c.barWidth),circleRadius=at("bars.radius",c.circleRadius),
                pathAngleDeg=at("bars.angle",c.pathAngleDeg),barStrokeWidth=at("bars.stroke",c.barStrokeWidth),barFillOpacity=at("bars.fill",c.barFillOpacity),
                colorBlendOffset=at("bars.gradient",c.colorBlendOffset),creativeStrokeWidth=at("bars.waveWidth",c.creativeStrokeWidth),baselineOffset=at("bars.baseline",c.baselineOffset))
            is LayerConfig.Shape->c.copy(radius=at("shape.radius",c.radius),strokeWidth=at("shape.stroke",c.strokeWidth),cornerRadius=at("shape.corner",c.cornerRadius))
            is LayerConfig.BlurEffect->c.copy(blurRadius=at("blur.radius",c.blurRadius),blurMultiplier=at("blur.multiplier",c.blurMultiplier),radialStrength=at("blur.radial",c.radialStrength),bloomIntensity=at("blur.intensity",c.bloomIntensity),bloomThreshold=at("blur.threshold",c.bloomThreshold),layer1Scale=at("blur.glow1",c.layer1Scale),layer2Scale=at("blur.glow2",c.layer2Scale),layer3Scale=at("blur.glow3",c.layer3Scale))
            is LayerConfig.MotionBlurEffect->c.copy(blurAmountMultiplier=at("motion.amount",c.blurAmountMultiplier),trailPersistence=at("motion.trail",c.trailPersistence),manualMotionX=at("motion.x",c.manualMotionX),manualMotionY=at("motion.y",c.manualMotionY))
            else->layer.config
        }
    }
    fun snap(timeMs:Long,fps:Int):Long{
        val rate=fps.coerceIn(1,120)
        val frame=(timeMs.coerceIn(0L,86400000L)*rate/1000.0).roundToLong()
        return (frame*1000.0/rate).roundToLong().coerceIn(0L,86400000L)
    }
}
