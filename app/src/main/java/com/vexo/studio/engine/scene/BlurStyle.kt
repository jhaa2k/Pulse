package com.vexo.studio.engine.scene
import com.vexo.studio.model.*
enum class BlurStyle(val label:String){
    SOFT_FOCUS("SOFT FOCUS"), NEON_BLOOM("NEON BLOOM"), DREAM_HAZE("DREAM HAZE"), LIGHT_STREAK("LIGHT STREAK"), ZOOM_RUSH("ZOOM RUSH"), SPIN_VORTEX("SPIN VORTEX");
    fun apply(c:LayerConfig.BlurEffect):LayerConfig.BlurEffect{
        val b=c.copy(filterMode=BlurFilterMode.GAUSSIAN,blurQuality=BlurQuality.HIGH,blurAngleDeg=0f,blurCrossAxis=1f,radialStrength=.12f,radialCenterX=.5f,radialCenterY=.5f,bloomThreshold=0f,bloomKnee=.1f,bloomIntensity=1f,layer1Scale=1f,layer1ColorArgb=0xFFFFFFFF.toInt(),layer2Scale=0f,layer3Scale=0f,showUnblurredContent=false,showUnblurredContentUnder=false,glowBlendMode=BlendMode.PRE_MUL_ALPHA)
        return when(this){
            SOFT_FOCUS->b.copy(blurRadius=4f,blurMultiplier=3f)
            NEON_BLOOM->b.copy(blurRadius=5f,blurMultiplier=3.5f,bloomThreshold=.38f,bloomIntensity=1.8f,showUnblurredContentUnder=true,glowBlendMode=BlendMode.ADD)
            DREAM_HAZE->b.copy(blurRadius=10f,blurMultiplier=4f,bloomIntensity=.75f,showUnblurredContentUnder=true,glowBlendMode=BlendMode.SCREEN)
            LIGHT_STREAK->b.copy(blurRadius=7f,blurMultiplier=3f,blurCrossAxis=.08f,blurAngleDeg=30f,showUnblurredContentUnder=true,glowBlendMode=BlendMode.ADD)
            ZOOM_RUSH->b.copy(filterMode=BlurFilterMode.RADIAL_ZOOM,radialStrength=.16f,showUnblurredContentUnder=true,glowBlendMode=BlendMode.SCREEN,bloomIntensity=.8f)
            SPIN_VORTEX->b.copy(filterMode=BlurFilterMode.RADIAL_SPIN,radialStrength=.09f,showUnblurredContentUnder=true,glowBlendMode=BlendMode.SCREEN,bloomIntensity=.8f)
        }
    }
}
