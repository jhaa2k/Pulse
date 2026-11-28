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
        val alternate=(i/8)%2==1
        val variation=(i/8)%8
        val from=r.from;val to=r.to
        val bg=when(layout){
            0,4->0xFF08090D.toInt()
            1,5->0xFF11101D.toInt()
            2,6->0xFF081B22.toInt()
            else->0xFF1A0D15.toInt()
        }
        val providers=listOf(
            AudioProviderConfig(index=1,tag="Punch / bass",sampleOutCount=144,lowerHz=30f,higherHz=1000f,outputMultiplier=5f,attackMs=5f,releaseMs=65f,preSmooth=.12f,filterStrength=.22f,adaptiveBeat=true,transientBeat=true,enhancedBands=true,beatPulseDurationMs=140),
            AudioProviderConfig(index=2,tag="Detail / mids",sampleOutCount=144,lowerHz=180f,higherHz=14000f,outputMultiplier=6f,attackMs=8f,releaseMs=90f,preSmooth=.2f,filterStrength=.3f,adaptiveBeat=true,transientBeat=true,enhancedBands=true,beatPulseDurationMs=140)
        )
        val tunedProviders=providers.map{p->
            when(layout){
                0->p.copy(attackMs=4f,releaseMs=48f,beatPulseDurationMs=110)
                1->p.copy(attackMs=9f,releaseMs=110f,preSmooth=.24f)
                2->p.copy(lowerHz=if(p.index==1)25f else 800f,higherHz=if(p.index==1)450f else 16000f,releaseMs=70f)
                3->p.copy(attackMs=14f,releaseMs=140f,filterRadius=2)
                4->p.copy(sampleOutCount=64,attackMs=3f,releaseMs=40f)
                5->p.copy(attackMs=5f,releaseMs=55f,beatPulseDurationMs=100)
                6->p.copy(sampleOutCount=192,attackMs=10f,releaseMs=120f)
                else->p.copy(sampleOutCount=192,lowerHz=40f,higherHz=if(p.index==1)6000f else 16000f,preSmooth=.08f)
            }
        }
        fun bars(name:String,cfg:LayerConfig.Bars,x:Float=.5f,y:Float=.5f,sx:Float=1f,sy:Float=sx,angle:Float=0f)=LayerNode(
            LayerType.BARS,LayerCommon(name=name,posX=x,posY=y,scaleX=sx,scaleY=sy,rotationDeg=angle,measureScale=MeasureConfig(MeasureWhat.BEAT,.035f,.035f)),cfg)
        val base=r.apply(LayerConfig.Bars()).copy(audioProviderIndex=1,heightScale=r.gain*1.35f,minHeightScale=.004f,reactiveColorMix=.14f,reactiveColorArgb=0xFFFFFFFF.toInt(),reactiveSource=SpectrumReactiveSource.BASS)
        val detail=base.copy(audioProviderIndex=2,colorFromArgb=to,colorToArgb=from,showPeaks=false,segment2=BarsSegmentType.NONE)
        val bus=if(alternate)when(layout){
            0->listOf(bars("Wide centerpiece",base,.5f,.46f,.88f,.7f),bars("Lower waveform",detail.copy(creativePath=CreativeSpectrumPath.LEGACY,creativeStyle=CreativeSpectrumStyle.LINE,signedWaveform=true,curveSubdivisions=3,heightScale=.35f),.5f,.82f,.72f,.4f))
            1->listOf(bars("Upper orbit",base,.5f,.4f,.75f),bars("Outer tick dial",detail.copy(creativePath=CreativeSpectrumPath.ARC,creativeStyle=CreativeSpectrumStyle.TICKS,pathGap=.18f,circleRadius=.36f,heightScale=.3f),.5f,.4f,.9f))
            2->listOf(bars("Bass column",base,.32f,.5f,.56f,.9f),bars("Treble column",detail.copy(creativePath=CreativeSpectrumPath.LEGACY,shapePath=BarsShapePath.VERTICAL_LINE,creativeStyle=CreativeSpectrumStyle.STEPS,heightScale=.5f),.83f,.5f,.25f,.72f))
            3->listOf(bars("Hero signal",base,.5f,.45f,.86f),bars("Left satellite",detail.copy(creativePath=CreativeSpectrumPath.CIRCLE,creativeStyle=CreativeSpectrumStyle.DOTS,heightScale=.45f),.14f,.75f,.25f),bars("Right satellite",detail.copy(creativePath=CreativeSpectrumPath.CIRCLE,creativeStyle=CreativeSpectrumStyle.LINE,heightScale=.45f),.86f,.75f,.25f))
            4->listOf(bars("Main pulse",base,.5f,.45f,.64f),bars("Upper meter",detail.copy(creativePath=CreativeSpectrumPath.LEGACY,creativeStyle=CreativeSpectrumStyle.STEPS,heightScale=.4f),.5f,.13f,.8f,.24f),bars("Lower meter",detail.copy(creativePath=CreativeSpectrumPath.LEGACY,creativeStyle=CreativeSpectrumStyle.TICKS,heightScale=.4f),.5f,.87f,.8f,.24f))
            5->listOf(bars("Diagonal focus",base,.42f,.48f,.8f,angle=-18f),bars("Small signal echo",detail.copy(creativeStyle=CreativeSpectrumStyle.DOTS,heightScale=.45f),.77f,.7f,.35f,angle=35f))
            6->listOf(bars("Central spectrum",base,.5f,.46f,.66f),bars("Outer orbit",detail.copy(creativePath=CreativeSpectrumPath.CIRCLE,creativeStyle=CreativeSpectrumStyle.DASH,circleRadius=.37f,heightScale=.3f),.5f,.46f,.92f))
            else->listOf(bars("Wave focus",base,.5f,.52f,.9f,.75f),bars("Top oscillation",detail.copy(creativePath=CreativeSpectrumPath.SINE,creativeStyle=CreativeSpectrumStyle.LINE,signedWaveform=true,heightScale=.4f),.5f,.18f,.8f,.35f))
        }else when(layout){
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
        if(alternate||layout in listOf(2,4,5,7)){
            val pattern=when(layout){0->ProceduralPattern.RINGS;1->ProceduralPattern.STARFIELD;2->ProceduralPattern.GRID;3->ProceduralPattern.NONE;4->ProceduralPattern.CHECKER;5->ProceduralPattern.PLASMA;6->ProceduralPattern.RINGS;else->ProceduralPattern.GRID}
            val gradient=when(layout){1,6->CreativeGradient.RADIAL;5->CreativeGradient.CONIC;else->CreativeGradient.LINEAR}
            final+=LayerNode(LayerType.IMAGE,LayerCommon(name="Procedural stage"),LayerConfig.Image(
                sourceType=ImageSourceType.SOLID_COLOR,keepAspectRatio=false,
                creative=CreativeImageConfig(gradient=gradient,gradientFrom=bg,gradientTo=0xFF050609.toInt(),gradientAngle=if(alternate)35f else 90f,gradientScale=.85f,
                    pattern=pattern,patternDensity=if(alternate)12f else 7f,patternIntensity=if(layout==3)0f else .14f,patternSpeed=if(layout==5).35f else .08f,vignette=.25f,scanlines=if(layout==4).12f else 0f)
            ))
        }
        if(layout in listOf(1,3,6)){
            final+=LayerNode(LayerType.IMAGE,LayerCommon(name="Soft album artwork",opacity=.22f),LayerConfig.Image(sourceType=ImageSourceType.ALBUM_ART,keepAspectRatioAndCropToFit=true,saturation=.45f,blurEnabled=true,blurRadius=24))
        }
        if(layout in listOf(1,4,5,7)){
            final+=LayerNode(LayerType.PARTICLES,LayerCommon(name="Rhythmic particles",blendMode=BlendMode.ADD,opacity=.65f),LayerConfig.Particles(emitter=if(layout==4)ParticleEmitter.LINE else if(layout==5)ParticleEmitter.BOX else ParticleEmitter.RING,countLimit=180,spawnTimeSec=.04f,burstCount=18,burstThreshold=.45f,burstProvider=1,burstSource=ParticleBurstSource.BEAT_PULSE,turbulence=if(variation%2==0).4f else 1.1f,centerForce=when(variation%3){0->-.3f;1->.25f;else->0f},burstSpeedMultiplier=1.4f,reactiveEmission=.6f,colorFromArgb=from,colorToArgb=to,trailLength=if(layout==5).3f else 0f))
        }
        if(layout in listOf(0,1,3,5,6)){
            final+=LayerNode(LayerType.BLUR_EFFECT,LayerCommon(name="Spectrum bloom",blendMode=BlendMode.ADD),LayerConfig.BlurEffect(sourceCompositionIndex=1,blurRadius=if(variation%2==0)3.5f else 5f,blurMultiplier=3f,bloomThreshold=if(variation%3==0).15f else 0f,bloomIntensity=1.15f,blurCrossAxis=if(variation==5).2f else 1f,blurAngleDeg=if(variation==5)35f else 0f,layer1ColorArgb=0xAAFFFFFF.toInt(),layer2Scale=1.02f,layer2ColorArgb=0x44FFFFFF,blendModeContent=BlendMode.ADD,showUnblurredContent=true,showUnblurredContentUnder=false,unblurredBlendMode=BlendMode.PRE_MUL_ALPHA))
        }else{
            final+=LayerNode(LayerType.IMAGE,LayerCommon(name="Spectrum bus"),LayerConfig.Image(sourceType=ImageSourceType.COMPOSITION,sourceCompositionIndex=1,keepAspectRatio=false))
        }
        if(layout==7)final+=LayerNode(LayerType.MOTION_BLUR_EFFECT,LayerCommon(name="Motion echo",opacity=.35f,blendMode=BlendMode.ADD),LayerConfig.MotionBlurEffect(targetCompositionIndex=1,motionSource=MotionSource.MANUAL,manualMotionX=.015f,trailPersistence=.6f))
        if(layout in listOf(1,6))final+=LayerNode(LayerType.IMAGE,LayerCommon(name="Center artwork",scaleX=.28f,scaleY=.28f,scaleIsUniform=true,measureScale=MeasureConfig(MeasureWhat.BEAT,.035f,.035f)),LayerConfig.Image(sourceType=ImageSourceType.ALBUM_ART,shape=ImageShape.CIRCLE,keepAspectRatioAndCropToFit=true))
        if(variation==3||variation==6)final+=LayerNode(LayerType.RGB_SPLIT_EFFECT,LayerCommon(name="Chromatic accent",opacity=.16f,blendMode=BlendMode.ADD),LayerConfig.RgbSplitEffect(aberrationMode=AberrationMode.RADIAL,aberrationMix=.65f))
        if(alternate&&layout in listOf(2,5))final+=LayerNode(LayerType.IMAGE,LayerCommon(name="Artwork badge",posX=.19f,posY=.2f,scaleX=.19f,scaleY=.19f,scaleIsUniform=true),LayerConfig.Image(sourceType=ImageSourceType.ALBUM_ART,shape=if(layout==2)ImageShape.NONE else ImageShape.CIRCLE,keepAspectRatioAndCropToFit=true))
        if(layout!=0||alternate){
            val titleY=if(alternate)when(layout){1->.78f;2,5->.2f;3->.63f;4->.72f;6->.82f;else->.91f}else if(layout==3).5f else .84f
            val titleX=if(alternate&&layout in listOf(2,5)).54f else .5f
            val motion=when(layout){0,6->TextMotion.FADE;1,4->TextMotion.PULSE;2,5->TextMotion.SLIDE;3->TextMotion.REVEAL;else->TextMotion.BEAT_SCALE}
            final+=LayerNode(LayerType.TEXT,LayerCommon(name="Track title",posX=titleX,posY=titleY,scaleX=if(alternate&&layout in listOf(2,5)).7f else 1f),LayerConfig.Text(text="{Title}",fontSize=if(layout==3)28 else if(alternate)34 else 38,bold=true,colorArgb=0xFFFFFFFF.toInt(),animation=motion,animationAmount=.12f))
            if(alternate&&layout in listOf(0,1,3,6))final+=LayerNode(LayerType.TEXT,LayerCommon(name="Artist subtitle",posY=(titleY+.055f).coerceAtMost(.95f),opacity=.75f),LayerConfig.Text(text="{Artist}",fontSize=22,bold=false,animation=TextMotion.FADE,animationAmount=.1f))
        }
        return VexoProject(name=r.name,canvas=CanvasConfig(backgroundColorArgb=bg,camera=ProjectCamera(enabled=variation==4||variation==7,source=SpectrumReactiveSource.BASS,pulseZoom=.055f,shake=.006f,rollDeg=.8f)),audioProviders=tunedProviders,compositions=listOf(CompositionNode(index=0,layers=final),CompositionNode(index=1,name="Editable spectrum layers",layers=bus)))
    }
}
