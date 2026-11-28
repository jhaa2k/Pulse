package com.vexo.studio.model

/** Native editable image-layer recipes. They are not baked bitmaps. */
object CreativeRecipes{
    val names=listOf("Aurora Gradient","Neon Radial","Sunset Gradient","Retro CRT","Beat Wave","Kaleidoscope","Pixel Glitch","Pulse Zoom","Neon Grid","Plasma Field","Starfield")
    fun config(name:String,source:Int?):LayerConfig.Image {
        val base=if(source!=null)LayerConfig.Image(sourceType=ImageSourceType.COMPOSITION,sourceCompositionIndex=source,keepAspectRatio=false)
            else LayerConfig.Image(sourceType=ImageSourceType.ALBUM_ART,keepAspectRatioAndCropToFit=true)
        val beat=MeasureConfig(measureWhat=MeasureWhat.BEAT,amountA=.45f,amountB=.45f)
        return when(name){
            "Aurora Gradient"->base.copy(sourceType=ImageSourceType.SOLID_COLOR,keepAspectRatio=false,creative=CreativeImageConfig(gradient=CreativeGradient.LINEAR,gradientFrom=0xFF0B1026.toInt(),gradientTo=0xFF15E6AD.toInt(),gradientAngle=35f))
            "Neon Radial"->base.copy(sourceType=ImageSourceType.SOLID_COLOR,keepAspectRatio=false,creative=CreativeImageConfig(gradient=CreativeGradient.RADIAL,gradientFrom=0xFFDA32FF.toInt(),gradientTo=0xFF080018.toInt(),vignette=.55f))
            "Sunset Gradient"->base.copy(sourceType=ImageSourceType.SOLID_COLOR,keepAspectRatio=false,creative=CreativeImageConfig(gradient=CreativeGradient.LINEAR,gradientFrom=0xFFFF8B38.toInt(),gradientTo=0xFF301254.toInt(),gradientAngle=90f))
            "Retro CRT"->base.copy(creative=CreativeImageConfig(contrast=1.3f,temperature=.2f,scanlines=.4f,grain=.28f,vignette=.45f))
            "Beat Wave"->base.copy(creative=CreativeImageConfig(distortion=CreativeDistortion.WAVE,amount=.08f,frequency=5f,audio=beat))
            "Kaleidoscope"->base.copy(creative=CreativeImageConfig(distortion=CreativeDistortion.KALEIDOSCOPE,amount=1f,frequency=8f,speed=.1f))
            "Pixel Glitch"->base.copy(creative=CreativeImageConfig(distortion=CreativeDistortion.PIXELATE,amount=.25f,audio=beat))
            "Pulse Zoom"->base.copy(creative=CreativeImageConfig(distortion=CreativeDistortion.ZOOM_PULSE,amount=.08f,audio=beat))
            "Neon Grid"->base.copy(sourceType=ImageSourceType.SOLID_COLOR,keepAspectRatio=false,creative=CreativeImageConfig(pattern=ProceduralPattern.GRID,patternDensity=14f,patternIntensity=1f,gradientFrom=0xFF08101B.toInt(),gradientTo=0xFF9AFF32.toInt()))
            "Plasma Field"->base.copy(sourceType=ImageSourceType.SOLID_COLOR,keepAspectRatio=false,creative=CreativeImageConfig(pattern=ProceduralPattern.PLASMA,patternDensity=8f,patternIntensity=1f,gradientFrom=0xFF301055.toInt(),gradientTo=0xFFFF5A96.toInt()))
            "Starfield"->base.copy(sourceType=ImageSourceType.SOLID_COLOR,keepAspectRatio=false,creative=CreativeImageConfig(pattern=ProceduralPattern.STARFIELD,patternDensity=32f,patternIntensity=1f,gradientFrom=0xFF050711.toInt(),gradientTo=0xFFDFEFFF.toInt()))
            else->base
        }
    }
}
