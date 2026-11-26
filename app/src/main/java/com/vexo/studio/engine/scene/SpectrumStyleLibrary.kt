package com.vexo.studio.engine.scene
import com.vexo.studio.model.*
import kotlin.random.Random

data class SpectrumStyleRecipe(val name:String,val path:CreativeSpectrumPath,val style:CreativeSpectrumStyle,val count:Int,val radius:Float,val width:Float,val mirror:Boolean,val from:Int,val to:Int){
    fun apply(base:LayerConfig.Bars)=base.copy(
        creativePath=path,creativeStyle=style,shapePath=BarsShapePath.HORIZONTAL_LINE,
        segment1=BarsSegmentType.BARS,segment2=BarsSegmentType.NONE,secondaryCreativeStyle=CreativeSpectrumStyle.BARS,
        barCount=count,circleRadius=radius,barWidth=width,mirror=mirror,barSpacing=.12f,
        pathAngleDeg=0f,pathGap=0f,pathTurns=2f,pathSpread=.55f,
        heightScale=if(path==CreativeSpectrumPath.LEGACY)1.5f else .9f,barHeightMultiplier=1f,
        minHeightScale=.012f,maxHeightScale=.9f,fixedHeight=0f,flipInput=false,alternatingDirection=false,
        colorFromArgb=from,colorToArgb=to,colorBlendOffset=0f,
        creativeStrokeWidth=3f,creativeTipSize=1f,creativeFillAlpha=.65f,roundness=1f,stepCount=8,
        showPeaks=style==CreativeSpectrumStyle.PEAKS
    )
}
object SpectrumStyleLibrary{
    val recipes=listOf(
        SpectrumStyleRecipe("Classic Ring",CreativeSpectrumPath.CIRCLE,CreativeSpectrumStyle.BARS,80,0.24f,0.58f,false,0xFFB8E92D.toInt(),0xFF64E7FB.toInt()),
        SpectrumStyleRecipe("Orbit Dots",CreativeSpectrumPath.OVAL,CreativeSpectrumStyle.DOTS,58,0.32f,0.8f,false,0xFFF0B9FF.toInt(),0xFF7948F8.toInt()),
        SpectrumStyleRecipe("LED Meter",CreativeSpectrumPath.LEGACY,CreativeSpectrumStyle.STEPS,34,0.25f,0.72f,false,0xFF65E06A.toInt(),0xFFFFF070.toInt()),
        SpectrumStyleRecipe("Sine Trace",CreativeSpectrumPath.SINE,CreativeSpectrumStyle.LINE,100,0.25f,0.13f,false,0xFF37F9E1.toInt(),0xFF4A76FF.toInt()),
        SpectrumStyleRecipe("Mountain Fill",CreativeSpectrumPath.LEGACY,CreativeSpectrumStyle.FILL,72,0.25f,0.9f,false,0xFF5A70FF.toInt(),0xFFEC57CE.toInt()),
        SpectrumStyleRecipe("Outline Hex",CreativeSpectrumPath.HEXAGON,CreativeSpectrumStyle.OUTLINE,60,0.28f,0.68f,false,0xFF99F8D9.toInt(),0xFF1AA9FF.toInt()),
        SpectrumStyleRecipe("Spiked Star",CreativeSpectrumPath.STAR,CreativeSpectrumStyle.TRIANGLE,70,0.28f,0.8f,false,0xFFFFF292.toInt(),0xFFF76634.toInt()),
        SpectrumStyleRecipe("Crystal Heart",CreativeSpectrumPath.HEART,CreativeSpectrumStyle.DIAMOND,54,0.3f,0.72f,false,0xFFFF668C.toInt(),0xFFFFDEF3.toInt()),
        SpectrumStyleRecipe("Capsule Wave",CreativeSpectrumPath.LEGACY,CreativeSpectrumStyle.CAPSULE,42,0.25f,0.75f,true,0xFF2DF8B1.toInt(),0xFF8FEAF9.toInt()),
        SpectrumStyleRecipe("Arc Dash",CreativeSpectrumPath.ARC,CreativeSpectrumStyle.DASH,66,0.32f,0.8f,false,0xFFFFC847.toInt(),0xFFF63B72.toInt()),
        SpectrumStyleRecipe("Twin Orbit",CreativeSpectrumPath.FIGURE_EIGHT,CreativeSpectrumStyle.DOUBLE_LINE,100,0.3f,0.9f,true,0xFF7CB9FF.toInt(),0xFFDDAAFF.toInt()),
        SpectrumStyleRecipe("Flow Ribbon",CreativeSpectrumPath.SPIRAL,CreativeSpectrumStyle.RIBBON,88,0.33f,0.75f,false,0xFFFFA95D.toInt(),0xFFB43CFF.toInt()),
        SpectrumStyleRecipe("Signal Zigzag",CreativeSpectrumPath.ZIGZAG,CreativeSpectrumStyle.ZIGZAG,56,0.25f,0.7f,false,0xFFF9F9F9.toInt(),0xFFC5F84A.toInt()),
        SpectrumStyleRecipe("Pinwheel",CreativeSpectrumPath.FLOWER,CreativeSpectrumStyle.LOLLIPOP,45,0.33f,0.63f,false,0xFFFFEC88.toInt(),0xFFFA6CBA.toInt()),
        SpectrumStyleRecipe("Cross Circuit",CreativeSpectrumPath.SQUARE,CreativeSpectrumStyle.CROSS,40,0.31f,0.68f,false,0xFF89EFFF.toInt(),0xFFA3B1FF.toInt()),
        SpectrumStyleRecipe("Chevron Rail",CreativeSpectrumPath.DIAGONAL,CreativeSpectrumStyle.CHEVRON,34,0.25f,0.8f,false,0xFFFE6C59.toInt(),0xFFFCE89E.toInt()),
        SpectrumStyleRecipe("Butterfly Spikes",CreativeSpectrumPath.BUTTERFLY,CreativeSpectrumStyle.TAPERED,76,0.38f,0.75f,false,0xFF8EE6FF.toInt(),0xFFFA82FF.toInt()),
        SpectrumStyleRecipe("Capped EQ",CreativeSpectrumPath.LEGACY,CreativeSpectrumStyle.CAPPED,40,0.25f,0.85f,false,0xFFA4F742.toInt(),0xFF32CB9E.toInt()),
        SpectrumStyleRecipe("Pixel Beads",CreativeSpectrumPath.TRIANGLE,CreativeSpectrumStyle.STACK_DOTS,36,0.32f,0.72f,false,0xFFFFF9B5.toInt(),0xFFFA6B6B.toInt()),
        SpectrumStyleRecipe("Hollow Bubbles",CreativeSpectrumPath.CIRCLE,CreativeSpectrumStyle.HOLLOW_DOTS,52,0.29f,0.8f,false,0xFF9C85FF.toInt(),0xFFFD8FB1.toInt()),
        SpectrumStyleRecipe("Lissajous Nodes",CreativeSpectrumPath.LISSAJOUS,CreativeSpectrumStyle.WAVE_POINTS,92,0.32f,0.65f,false,0xFF47D7FF.toInt(),0xFFE3FC9A.toInt()),
        SpectrumStyleRecipe("Saw Rail",CreativeSpectrumPath.LEGACY,CreativeSpectrumStyle.SAWTOOTH,28,0.25f,0.95f,false,0xFFF98B50.toInt(),0xFFFFF0AB.toInt()),
        SpectrumStyleRecipe("Stripe Pulse",CreativeSpectrumPath.HEXAGON,CreativeSpectrumStyle.STRIPES,48,0.26f,0.82f,false,0xFFB1FF2B.toInt(),0xFF62F7F4.toInt()),
        SpectrumStyleRecipe("Radial Fan",CreativeSpectrumPath.FLOWER,CreativeSpectrumStyle.FAN,52,0.3f,0.7f,false,0xFFE5ABFF.toInt(),0xFF8159FC.toInt()),
        SpectrumStyleRecipe("Minimal Ticks",CreativeSpectrumPath.ARC,CreativeSpectrumStyle.TICKS,64,0.33f,0.8f,false,0xFFEEEEEE.toInt(),0xFF90D5FF.toInt()),
        SpectrumStyleRecipe("Peak Ladder",CreativeSpectrumPath.LEGACY,CreativeSpectrumStyle.PEAKS,44,0.25f,0.76f,false,0xFFFF7B8B.toInt(),0xFFF9D97D.toInt())
    )
    fun next(used:Set<String>,current:LayerConfig.Bars,random:Random=Random.Default):Pair<SpectrumStyleRecipe,Set<String>>{
        val remaining=recipes.filter{it.name !in used}
        val pool=remaining.ifEmpty{recipes}
        val choices=pool.filterNot{it.style==current.creativeStyle&&it.path==current.creativePath}.ifEmpty{pool}
        val selected=choices.random(random)
        return selected to ((if(remaining.isEmpty())emptySet() else used)+selected.name)
    }
}
