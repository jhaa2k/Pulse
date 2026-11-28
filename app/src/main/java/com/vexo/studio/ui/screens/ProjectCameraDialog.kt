package com.vexo.studio.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vexo.studio.model.ProjectCamera
import com.vexo.studio.model.SpectrumReactiveSource
import com.vexo.studio.ui.components.StudioSlider
import com.vexo.studio.ui.components.StudioToggleRow
import com.vexo.studio.ui.components.StudioChipSelector
import com.vexo.studio.ui.theme.StudioText as Text

@Composable
internal fun ProjectCameraDialog(initial:ProjectCamera,providers:List<Int>,onDismiss:()->Unit,onApply:(ProjectCamera)->Unit){
    var draft by remember(initial){mutableStateOf(initial)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("CINEMATIC CAMERA")},text={
        Column(Modifier.fillMaxWidth().heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)){
            Text("Applies to the final composition and exported video. Editor zoom is separate.",style=MaterialTheme.typography.bodySmall)
            StudioToggleRow("Enable project camera",draft.enabled){draft=draft.copy(enabled=it)}
            StudioChipSelector("Audio provider",(providers+draft.providerIndex).distinct(),draft.providerIndex,{"#$it"}){draft=draft.copy(providerIndex=it)}
            StudioChipSelector("Reactive source",SpectrumReactiveSource.entries,draft.source,{it.name}){draft=draft.copy(source=it)}
            StudioSlider("Base zoom",draft.baseZoom,.5f,3f,1f,"x",.01f,onValueChange={draft=draft.copy(baseZoom=it)},onCommit={})
            StudioSlider("Beat zoom",draft.pulseZoom,0f,1f,.12f,"x",.01f,onValueChange={draft=draft.copy(pulseZoom=it)},onCommit={})
            StudioSlider("Shake strength",draft.shake,0f,.15f,.02f,"",.001f,onValueChange={draft=draft.copy(shake=it)},onCommit={})
            StudioSlider("Roll",draft.rollDeg,0f,20f,2f,"°",.25f,onValueChange={draft=draft.copy(rollDeg=it)},onCommit={})
            StudioSlider("Drift",draft.drift,0f,.12f,0f,"",.001f,onValueChange={draft=draft.copy(drift=it)},onCommit={})
            StudioSlider("Shake frequency",draft.frequency,.1f,12f,3f,"Hz",.1f,onValueChange={draft=draft.copy(frequency=it)},onCommit={})
            StudioToggleRow("Auto overscan",draft.autoOverscan){draft=draft.copy(autoOverscan=it)}
            TextButton(onClick={draft=ProjectCamera(enabled=true,providerIndex=draft.providerIndex)}){Text("RESET CAMERA SETTINGS")}
        }
    },confirmButton={TextButton(onClick={onApply(draft)}){Text("APPLY")}},dismissButton={TextButton(onClick=onDismiss){Text("CANCEL")}})
}
