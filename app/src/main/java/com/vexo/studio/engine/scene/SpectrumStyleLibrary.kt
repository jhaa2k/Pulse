package com.vexo.studio.engine.scene
import com.vexo.studio.model.*
import kotlin.random.Random

data class SpectrumStyleRecipe(val name:String,val path:CreativeSpectrumPath,val style:CreativeSpectrumStyle,val count:Int,val radius:Float,val width:Float,val mirror:Boolean,val from:Int,val to:Int, val gain:Float=.9f, val spread:Float=.55f, val turns:Float=2f, val second:CreativeSpectrumStyle?=null, val waveform:Boolean=false, val curve:Int=1, val colors:Int=1, val strokeWidth:Float=0f, val fill:Float=1f, val gradient:SpectrumGradientMode=SpectrumGradientMode.PATH, val dash:Float=1f, val ribbon:Float=1f){
    fun apply(base:LayerConfig.Bars)=base.copy(
        creativePath=path,creativeStyle=style,shapePath=BarsShapePath.HORIZONTAL_LINE,
        segment1=BarsSegmentType.BARS,segment2=if(second==null)BarsSegmentType.NONE else BarsSegmentType.BARS,secondaryCreativeStyle=second?:CreativeSpectrumStyle.BARS,
        segment2Width=width*.65f,segment2HeightMultiplier=-.65f,segment2Mirror=false,segment2FixedHeight=0f,segment2ColorFrom=to,segment2ColorTo=from,
        barCount=count,circleRadius=radius,barWidth=width,mirror=mirror,barSpacing=.12f,
        pathAngleDeg=0f,pathGap=0f,pathTurns=turns,pathSpread=spread,
        heightScale=if(path==CreativeSpectrumPath.LEGACY)gain*1.6f else gain,barHeightMultiplier=1f,
        sampleOffset=0f,sampleRepeats=1,amplitudeCurve=1f,baselineOffset=0f,signedWaveform=waveform,curveSubdivisions=curve,gradientRepeats=colors,customPathEnabled=false,pathStretchX=1f,pathStretchY=1f,
        minHeightScale=.012f,maxHeightScale=.9f,fixedHeight=0f,flipInput=false,alternatingDirection=false,
        colorFromArgb=from,colorToArgb=to,colorBlendOffset=0f,
        creativeStrokeWidth=3f,creativeTipSize=1f,creativeFillAlpha=.65f,roundness=1f,stepCount=8,
        barStrokeWidth=strokeWidth,barStrokeColorArgb=to,barFillOpacity=fill,barTipWidth=1f,barGradientMode=gradient,segment2StrokeWidth=0f,segment2StrokeColorArgb=from,segment2FillOpacity=1f,
        waveRibbonWidth=ribbon,waveLineGap=2.5f,waveCurveTension=.5f,waveDashRatio=dash,waveDashCount=24,
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
        SpectrumStyleRecipe("Peak Ladder",CreativeSpectrumPath.LEGACY,CreativeSpectrumStyle.PEAKS,44,0.25f,0.76f,false,0xFFFF7B8B.toInt(),0xFFF9D97D.toInt()),
        SpectrumStyleRecipe("Circular Wave",CreativeSpectrumPath.CIRCLE,CreativeSpectrumStyle.LINE,144,.27f,.65f,false,0xFF7BE7FF.toInt(),0xFFC56EFF.toInt(),.75f),
        SpectrumStyleRecipe("Twin Circle Wave",CreativeSpectrumPath.CIRCLE,CreativeSpectrumStyle.DOUBLE_LINE,128,.28f,.7f,true,0xFFFF49B1.toInt(),0xFF45B6FF.toInt(),.85f),
        SpectrumStyleRecipe("Neon Crown",CreativeSpectrumPath.CIRCLE,CreativeSpectrumStyle.TAPERED,96,.28f,.9f,false,0xFFFA41C5.toInt(),0xFF60CBFF.toInt(),1.25f),
        SpectrumStyleRecipe("Phonk Halo",CreativeSpectrumPath.CIRCLE,CreativeSpectrumStyle.CAPSULE,120,.25f,.7f,false,0xFFFF2D59.toInt(),0xFFFFB02E.toInt(),1.25f,second=CreativeSpectrumStyle.DOTS),
        SpectrumStyleRecipe("Circular Filled Wave",CreativeSpectrumPath.CIRCLE,CreativeSpectrumStyle.FILL,128,.25f,.85f,true,0xFFAB5EFF.toInt(),0xFF60F4FF.toInt(),.8f),
        SpectrumStyleRecipe("Inward Outward Ring",CreativeSpectrumPath.CIRCLE,CreativeSpectrumStyle.BARS,100,.28f,.65f,false,0xFFFFFFFF.toInt(),0xFF81FFC9.toInt(),.95f,second=CreativeSpectrumStyle.BARS),
        SpectrumStyleRecipe("White Minimal Wave",CreativeSpectrumPath.LEGACY,CreativeSpectrumStyle.LINE,144,.24f,.7f,true,0xFFFFFFFF.toInt(),0xFFB9C4D4.toInt(),.7f),
        SpectrumStyleRecipe("Ocean Ribbon",CreativeSpectrumPath.SINE,CreativeSpectrumStyle.RIBBON,128,.24f,.9f,true,0xFF19DCF6.toInt(),0xFF376DFF.toInt(),.65f,.18f,1.5f),
        SpectrumStyleRecipe("Stereo Rail",CreativeSpectrumPath.LEGACY,CreativeSpectrumStyle.BARS,72,.25f,.76f,false,0xFF4BEADB.toInt(),0xFF8863FF.toInt(),.9f,second=CreativeSpectrumStyle.LINE),
        SpectrumStyleRecipe("Cassette LED",CreativeSpectrumPath.LEGACY,CreativeSpectrumStyle.STACK_DOTS,42,.24f,.8f,true,0xFF6AFF87.toInt(),0xFFFFA542.toInt(),.9f),
        SpectrumStyleRecipe("Crystal Arc",CreativeSpectrumPath.ARC,CreativeSpectrumStyle.DIAMOND,80,.3f,.82f,false,0xFFA6E7FF.toInt(),0xFFE4B1FF.toInt(),.9f),
        SpectrumStyleRecipe("Solar Flare",CreativeSpectrumPath.FLOWER,CreativeSpectrumStyle.TAPERED,100,.31f,.65f,false,0xFFFFB14D.toInt(),0xFFFF4160.toInt(),1.3f),
        SpectrumStyleRecipe("Orbit Ribbon",CreativeSpectrumPath.OVAL,CreativeSpectrumStyle.RIBBON,120,.33f,.85f,false,0xFF5DFFC4.toInt(),0xFF4A7CFF.toInt(),.65f,.3f),
        SpectrumStyleRecipe("Infinity Pulse",CreativeSpectrumPath.FIGURE_EIGHT,CreativeSpectrumStyle.CAPSULE,90,.32f,.7f,true,0xFFF16CFF.toInt(),0xFF60D9FF.toInt(),.75f),
        SpectrumStyleRecipe("Cyber Polygon",CreativeSpectrumPath.HEXAGON,CreativeSpectrumStyle.DOUBLE_LINE,108,.3f,.8f,false,0xFFBBFF31.toInt(),0xFF44FFD9.toInt(),.9f),
        SpectrumStyleRecipe("Heart Wave",CreativeSpectrumPath.HEART,CreativeSpectrumStyle.LINE,128,.32f,.7f,false,0xFFFF73AC.toInt(),0xFFFFD8E8.toInt(),.7f),
        SpectrumStyleRecipe("Spiral Beacons",CreativeSpectrumPath.SPIRAL,CreativeSpectrumStyle.LOLLIPOP,100,.35f,.7f,false,0xFFFBC453.toInt(),0xFFAC59FF.toInt(),.75f,.4f,3f),
        SpectrumStyleRecipe("Sub Bass Wall",CreativeSpectrumPath.LEGACY,CreativeSpectrumStyle.CAPPED,24,.25f,.9f,true,0xFFFF6C37.toInt(),0xFFFFE769.toInt(),1.6f),
        SpectrumStyleRecipe("Analog Oscilloscope",CreativeSpectrumPath.LEGACY,CreativeSpectrumStyle.LINE,192,.25f,.65f,false,0xFFB5FF56.toInt(),0xFF69FFC0.toInt(),1f,waveform=true,curve=4),
        SpectrumStyleRecipe("Circular Oscilloscope",CreativeSpectrumPath.CIRCLE,CreativeSpectrumStyle.LINE,192,.27f,.7f,false,0xFF71E9FF.toInt(),0xFFCE75FF.toInt(),.9f,waveform=true,curve=4,colors=2),
        SpectrumStyleRecipe("Silk Wave",CreativeSpectrumPath.LEGACY,CreativeSpectrumStyle.LINE,112,.25f,.7f,true,0xFFFFB3DA.toInt(),0xFF87DBFF.toInt(),.75f,curve=6,colors=3),
        SpectrumStyleRecipe("Prism Ring",CreativeSpectrumPath.CIRCLE,CreativeSpectrumStyle.LINE,144,.27f,.7f,false,0xFFFD4A99.toInt(),0xFF4DE6FF.toInt(),.85f,curve=4,colors=4),
        SpectrumStyleRecipe("Hollow Neon Bars",CreativeSpectrumPath.LEGACY,CreativeSpectrumStyle.BARS,48,.25f,.8f,true,0xFF29DEFF.toInt(),0xFFC8FF54.toInt(),1f,strokeWidth=2.5f,fill=0f),
        SpectrumStyleRecipe("Glass Capsule Ring",CreativeSpectrumPath.CIRCLE,CreativeSpectrumStyle.CAPSULE,72,.28f,.82f,false,0xFFBF54FF.toInt(),0xFF98E8FF.toInt(),.9f,strokeWidth=2f,fill=.25f,gradient=SpectrumGradientMode.HEIGHT),
        SpectrumStyleRecipe("Heat Meter",CreativeSpectrumPath.LEGACY,CreativeSpectrumStyle.STEPS,40,.25f,.75f,false,0xFF13F0C0.toInt(),0xFFFF5349.toInt(),1.1f,gradient=SpectrumGradientMode.AMPLITUDE),
        SpectrumStyleRecipe("Dashed Signal Ring",CreativeSpectrumPath.CIRCLE,CreativeSpectrumStyle.LINE,128,.28f,.7f,false,0xFFCBFF55.toInt(),0xFF54FFCF.toInt(),.8f,curve=4,dash=.48f),
        SpectrumStyleRecipe("Silk Ocean",CreativeSpectrumPath.LEGACY,CreativeSpectrumStyle.FILL,96,.25f,.7f,true,0xFF0E63FF.toInt(),0xFF38F8DA.toInt(),.85f,curve=5),
        SpectrumStyleRecipe("Wide Neon Ribbon",CreativeSpectrumPath.OVAL,CreativeSpectrumStyle.RIBBON,112,.28f,.8f,false,0xFFFF4F9A.toInt(),0xFF5BC9FF.toInt(),.8f,curve=5,ribbon=3f)
    )
    fun next(used:Set<String>,current:LayerConfig.Bars,random:Random=Random.Default):Pair<SpectrumStyleRecipe,Set<String>>{
        val remaining=recipes.filter{it.name !in used}
        val pool=remaining.ifEmpty{recipes}
        val choices=pool.filterNot{it.style==current.creativeStyle&&it.path==current.creativePath}.ifEmpty{pool}
        val selected=choices.random(random)
        return selected to ((if(remaining.isEmpty())emptySet() else used)+selected.name)
    }
}
