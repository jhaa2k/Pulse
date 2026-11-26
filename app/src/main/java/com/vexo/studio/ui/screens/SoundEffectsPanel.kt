package com.vexo.studio.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.vexo.studio.ui.StudioViewModel
import com.vexo.studio.ui.components.*
import com.vexo.studio.ui.theme.LocalVexoPalette

@UnstableApi
@Composable
fun SoundEffectsPanel(vm: StudioViewModel, onBack:()->Unit){
    val effects=vm.playbackController.soundEffects
    val state by effects.state.collectAsState()
    val palette=LocalVexoPalette.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        TextButton(onClick=onBack){Text("BACK")}
        Text("SOUND STUDIO",style=MaterialTheme.typography.titleLarge,color=palette.accent)
        StudioToggleRow("Enable audio effects",state.enabled,effects::enable)
        Text("Equalizer · "+state.bands.size+" device bands",style=MaterialTheme.typography.titleSmall)
        if(state.presets.isNotEmpty()) StudioChipSelector("Preset",state.presets.indices.toList(),state.preset,{state.presets[it]},effects::preset)
        state.bands.forEach{band->
            StudioSlider(band.hz.toString()+" Hz",band.level/100f,state.minLevel/100f,state.maxLevel/100f,0f,"dB",.1f,onValueChange={effects.band(band.index,(it*100).toInt())})
        }
        if(state.bassSupported) StudioSlider("Bass boost",state.bass/10f,0f,100f,0f,"%",1f,onValueChange={effects.bass((it*10).toInt())})
        else Text("Bass boost unavailable on this audio session")
        if(state.virtualSupported) StudioSlider("Virtualizer",state.virtual/10f,0f,100f,0f,"%",1f,onValueChange={effects.virtual((it*10).toInt())})
        else Text("Virtualizer unavailable on this audio session")
        if(state.reverbSupported) StudioChipSelector("Reverb",(0..6).toList(),state.reverb,{listOf("None","Small room","Medium room","Large room","Medium hall","Large hall","Plate")[it]},effects::reverb)
        else Text("Reverb unavailable on this audio session")
        state.message?.let{Text(it,color=palette.danger)}
        Text("Playback effects depend on your device. Offline exports use original decoded audio.",style=MaterialTheme.typography.bodySmall)
    }
}
