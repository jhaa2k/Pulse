package com.vexo.studio.model

object CreativeSceneTemplates{
    private fun scene(name:String,from:Int,to:Int,path:CreativeSpectrumPath,style:CreativeSpectrumStyle,emitter:ParticleEmitter):VexoProject{
        val background=LayerNode(LayerType.IMAGE,LayerCommon(name="Gradient background"),LayerConfig.Image(sourceType=ImageSourceType.SOLID_COLOR,keepAspectRatio=false,creative=CreativeImageConfig(gradient=CreativeGradient.RADIAL,gradientFrom=from,gradientTo=0xFF080810.toInt(),vignette=.5f)))
        val particles=LayerNode(LayerType.PARTICLES,LayerCommon(name="Beat sparks",blendMode=BlendMode.ADD,opacity=.75f),LayerConfig.Particles(emitter=emitter,emitterWidth=.5f,emitterHeight=.5f,perspectiveDepth=0f,countLimit=360,spawnTimeSec=.03f,burstCount=32,burstThreshold=.6f,vectorRelativeToCenter=true,colorFromArgb=to,colorToArgb=from,drag=.3f,trailLength=.2f))
        val bars=LayerNode(LayerType.BARS,LayerCommon(name="Creative spectrum"),LayerConfig.Bars(creativePath=path,creativeStyle=style,barCount=96,mirror=false,circleRadius=.27f,pathTurns=2f,heightScale=1.6f,colorFromArgb=0xFFFFFFFF.toInt(),colorToArgb=to))
        val title=LayerNode(LayerType.TEXT,LayerCommon(name="Animated track title",posY=.85f),LayerConfig.Text(text="{Title}",fontSize=64,animation=TextMotion.BEAT_SCALE,animationAmount=.4f,shadowRadius=6f,shadowColorArgb=to))
        return VexoProject(name=name,compositions=listOf(CompositionNode(index=0,layers=listOf(background,particles,bars,title))))
    }
    fun all()=listOf(
        scene("Cyber Spiral",0xFF151D67.toInt(),0xFF58F9E4.toInt(),CreativeSpectrumPath.SPIRAL,CreativeSpectrumStyle.DOTS,ParticleEmitter.RING),
        scene("Heartbeat",0xFF5A1238.toInt(),0xFFFF5184.toInt(),CreativeSpectrumPath.HEART,CreativeSpectrumStyle.BARS,ParticleEmitter.DISC),
        scene("Electric Sine",0xFF101C36.toInt(),0xFFC6F36A.toInt(),CreativeSpectrumPath.SINE,CreativeSpectrumStyle.STEPS,ParticleEmitter.LINE)
    )
}
