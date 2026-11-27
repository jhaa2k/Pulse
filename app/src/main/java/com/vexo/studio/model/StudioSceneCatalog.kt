package com.vexo.studio.model

import com.vexo.studio.engine.scene.SpectrumStyleLibrary
import com.vexo.studio.engine.scene.SpectrumStyleRecipe

/** Original scene designs; the catalog embeds no third-party template artwork. */
object StudioSceneCatalog {
    private val extra=listOf(
        SpectrumStyleRecipe("Prismatic Starfall",CreativeSpectrumPath.STAR,CreativeSpectrumStyle.RIBBON,96,.27f,.7f,false,0xFF7DFFEB.toInt(),0xFFE179FF.toInt(),curve=4),
        SpectrumStyleRecipe("Thunder Gate",CreativeSpectrumPath.SQUARE,CreativeSpectrumStyle.CAPSULE,72,.26f,.7f,true,0xFF62CFFF.toInt(),0xFFFFFFFF.toInt()),
        SpectrumStyleRecipe("Pulse Compass",CreativeSpectrumPath.ARC,CreativeSpectrumStyle.CAPPED,48,.34f,.72f,false,0xFFFFBC6B.toInt(),0xFFFF557B.toInt()),
        SpectrumStyleRecipe("Neon Petals",CreativeSpectrumPath.FLOWER,CreativeSpectrumStyle.DOUBLE_LINE,110,.28f,.6f,false,0xFFE8ACFF.toInt(),0xFF31FFD7.toInt(),curve=4),
        SpectrumStyleRecipe("Signal Tunnel",CreativeSpectrumPath.SPIRAL,CreativeSpectrumStyle.STEPS,68,.30f,.75f,false,0xFFBFFF5A.toInt(),0xFF52ACFF.toInt()),
        SpectrumStyleRecipe("Diamond Circuit",CreativeSpectrumPath.TRIANGLE,CreativeSpectrumStyle.OUTLINE,54,.28f,.65f,false,0xFFFFE1A0.toInt(),0xFFFB7E55.toInt()),
        SpectrumStyleRecipe("Crystal Current",CreativeSpectrumPath.SINE,CreativeSpectrumStyle.DIAMOND,64,.25f,.65f,true,0xFF8CE6FF.toInt(),0xFF7768FF.toInt()),
        SpectrumStyleRecipe("Broken Halo",CreativeSpectrumPath.OVAL,CreativeSpectrumStyle.TICKS,80,.28f,.8f,false,0xFFFAF4CB.toInt(),0xFFB1FFD1.toInt()),
        SpectrumStyleRecipe("Retro Butterfly",CreativeSpectrumPath.BUTTERFLY,CreativeSpectrumStyle.FILL,100,.30f,.7f,false,0xFFFF559A.toInt(),0xFFFFB85E.toInt(),curve=4),
        SpectrumStyleRecipe("Hyperdrive",CreativeSpectrumPath.LISSAJOUS,CreativeSpectrumStyle.DOUBLE_LINE,128,.29f,.6f,false,0xFFB4D9FF.toInt(),0xFF826DFF.toInt(),curve=5)
    )
    fun all():List<VexoProject> = (SpectrumStyleLibrary.recipes+extra).mapIndexed(::scene)
    private fun scene(i:Int,r:SpectrumStyleRecipe):VexoProject{
        val layout=i%8
        val from=r.from;val to=r.to
        val bg=when(layout){
            0,4->0xFF08090D.toInt()
            1,5->0xFF11101D.toInt()
            2,6->0xFF081B22.toInt()
            else->0xFF1A0D15.toInt()
        }
        val providers=listOf(
            AudioProviderConfig(index=1,tag="Punch / bass",sampleOutCount=144,lowerHz=30f,higherHz=1000f,outputMultiplier=5f,attackMs=5f,releaseMs=65f,preSmooth=.12f,filterStrength=.22f,adaptiveBeat=true),
            AudioProviderConfig(index=2,tag="Detail / mids",sampleOutCount=144,lowerHz=180f,higherHz=14000f,outputMultiplier=6f,attackMs=8f,releaseMs=90f,preSmooth=.2f,filterStrength=.3f,adaptiveBeat=true)
        )
        fun bars(name:String,cfg:LayerConfig.Bars,x:Float=.5f,y:Float=.5f,sx:Float=1f,sy:Float=sx,angle:Float=0f)=LayerNode(
            LayerType.BARS,LayerCommon(name=name,posX=x,posY=y,scaleX=sx,scaleY=sy,rotationDeg=angle,measureScale=MeasureConfig(MeasureWhat.BEAT,.035f,.035f)),cfg)
        val base=r.apply(LayerConfig.Bars()).copy(audioProviderIndex=1,heightScale=r.gain*1.35f,minHeightScale=.004f,reactiveColorMix=.14f,reactiveColorArgb=0xFFFFFFFF.toInt(),reactiveSource=SpectrumReactiveSource.BASS)
        val detail=base.copy(audioProviderIndex=2,colorFromArgb=to,colorToArgb=from,showPeaks=false,segment2=BarsSegmentType.NONE)
        val bus=when(layout){
            0->listOf(bars("Main spectrum",base))
            1->listOf(bars("Main ring",base,.5f,.5f,.85f),bars("Fine orbit",detail.copy(creativePath=CreativeSpectrumPath.CIRCLE,creativeStyle=CreativeSpectrumStyle.LINE,curveSubdivisions=4,circleRadius=.34f,heightScale=.35f),sx=.85f))
            2->listOf(bars("Left channel",base,.25f,.5f,.48f),bars("Right detail",detail,.75f,.5f,.48f,angle=180f))
            3->listOf(bars("Upper signal",base,.5f,.28f,.75f),bars("Lower echo",detail.copy(creativeStyle=CreativeSpectrumStyle.LINE,heightScale=.55f),.5f,.72f,.75f,angle=180f))
            4->listOf(bars("Center pulse",base,.5f,.5f,.7f),bars("Edge rail",detail.copy(creativePath=CreativeSpectrumPath.LEGACY,creativeStyle=CreativeSpectrumStyle.TICKS,heightScale=.4f),.5f,.9f,.9f,.5f))
            5->listOf(bars("Diagonal signal",base,.5f,.5f,.85f,angle=25f),bars("Cross echo",detail,.5f,.5f,.6f,angle=-25f))
            6->listOf(bars("Inner signal",base,.5f,.5f,.55f),bars("Outer outline",detail.copy(creativePath=CreativeSpectrumPath.HEXAGON,creativeStyle=CreativeSpectrumStyle.LINE,circleRadius=.34f,heightScale=.35f),sx=1.1f))
            else->listOf(bars("Large waveform",base,.5f,.52f,.9f),bars("Small counter signal",detail.copy(creativePath=CreativeSpectrumPath.ARC,creativeStyle=CreativeSpectrumStyle.DOTS,heightScale=.4f),.5f,.24f,.6f))
        }
        val final=mutableListOf<LayerNode>()
        if(layout in listOf(1,3,6)){
            final+=LayerNode(LayerType.IMAGE,LayerCommon(name="Soft album artwork",opacity=.22f),LayerConfig.Image(sourceType=ImageSourceType.ALBUM_ART,keepAspectRatioAndCropToFit=true,saturation=.45f,blurEnabled=true,blurRadius=24))
        }
        if(layout in listOf(1,4,5,7)){
            final+=LayerNode(LayerType.PARTICLES,LayerCommon(name="Rhythmic particles",blendMode=BlendMode.ADD,opacity=.65f),LayerConfig.Particles(emitter=if(layout==4)ParticleEmitter.LINE else if(layout==5)ParticleEmitter.BOX else ParticleEmitter.RING,countLimit=180,spawnTimeSec=.04f,burstCount=18,burstThreshold=.45f,burstProvider=1,colorFromArgb=from,colorToArgb=to,trailLength=if(layout==5).3f else 0f))
        }
        if(layout in listOf(0,1,3,5,6)){
            final+=LayerNode(LayerType.BLUR_EFFECT,LayerCommon(name="Spectrum bloom",blendMode=BlendMode.ADD),LayerConfig.BlurEffect(sourceCompositionIndex=1,blurRadius=3.5f,blurMultiplier=3f,layer1ColorArgb=0xAAFFFFFF.toInt(),layer2Scale=1.02f,layer2ColorArgb=0x44FFFFFF,blendModeContent=BlendMode.ADD,showUnblurredContent=true,showUnblurredContentUnder=false,unblurredBlendMode=BlendMode.PRE_MUL_ALPHA))
        }else{
            final+=LayerNode(LayerType.IMAGE,LayerCommon(name="Spectrum bus"),LayerConfig.Image(sourceType=ImageSourceType.COMPOSITION,sourceCompositionIndex=1,keepAspectRatio=false))
        }
        if(layout==7)final+=LayerNode(LayerType.MOTION_BLUR_EFFECT,LayerCommon(name="Motion echo",opacity=.35f,blendMode=BlendMode.ADD),LayerConfig.MotionBlurEffect(targetCompositionIndex=1,motionSource=MotionSource.MANUAL,manualMotionX=.015f,trailPersistence=.6f))
        if(layout in listOf(1,6))final+=LayerNode(LayerType.IMAGE,LayerCommon(name="Center artwork",scaleX=.28f,scaleY=.28f,scaleIsUniform=true,measureScale=MeasureConfig(MeasureWhat.BEAT,.035f,.035f)),LayerConfig.Image(sourceType=ImageSourceType.ALBUM_ART,shape=ImageShape.CIRCLE,keepAspectRatioAndCropToFit=true))
        if(layout!=0)final+=LayerNode(LayerType.TEXT,LayerCommon(name="Track title",posY=if(layout==3).5f else .84f),LayerConfig.Text(text="{Title}",fontSize=if(layout==3)28 else 38,bold=true,colorArgb=0xFFFFFFFF.toInt(),animation=TextMotion.BEAT_SCALE,animationAmount=.12f))
        return VexoProject(name=r.name,canvas=CanvasConfig(backgroundColorArgb=bg),audioProviders=providers,compositions=listOf(CompositionNode(index=0,layers=final),CompositionNode(index=1,name="Editable spectrum layers",layers=bus)))
    }
}
