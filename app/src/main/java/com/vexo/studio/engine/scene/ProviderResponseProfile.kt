package com.vexo.studio.engine.scene

import com.vexo.studio.model.AudioProviderConfig

/** Explicit one-tap analysis recipes; all unrelated provider fields and identity survive. */
enum class ProviderResponseProfile(val label:String) {
    BASS("Bass"), VOCAL("Vocal"), DETAIL("Detail"), PUNCH("Punch");

    fun applyTo(p:AudioProviderConfig):AudioProviderConfig = when(this){
        BASS -> p.copy(lowerHz=25f,higherHz=250f,attackMs=6f,releaseMs=110f,enhancedBands=true,adaptiveBeat=true,transientBeat=true,bandResponseGain=1.35f)
        VOCAL -> p.copy(lowerHz=180f,higherHz=4200f,attackMs=15f,releaseMs=130f,enhancedBands=true,bandResponseGain=1f)
        DETAIL -> p.copy(lowerHz=40f,higherHz=16000f,attackMs=12f,releaseMs=95f,fftSize=4096,hopSize=512,sampleOutCount=128,enhancedBands=true,bandResponseGain=1f)
        PUNCH -> p.copy(lowerHz=30f,higherHz=12000f,attackMs=4f,releaseMs=65f,adaptiveBeat=true,transientBeat=true,beatCooldownMs=90,beatPulseDurationMs=140,enhancedBands=true,bandResponseGain=1.2f)
    }
}
