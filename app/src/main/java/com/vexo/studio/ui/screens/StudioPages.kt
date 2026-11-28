package com.vexo.studio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.vexo.studio.ui.theme.StudioText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.vexo.studio.data.ProjectSummary
import com.vexo.studio.export.ExportProgress
import com.vexo.studio.model.AppSettings
import com.vexo.studio.model.AspectRatioPreset
import com.vexo.studio.model.PauseBehavior
import com.vexo.studio.model.ThemePreset
import com.vexo.studio.model.VexoTemplates
import com.vexo.studio.model.VideoResolution
import com.vexo.studio.playback.PlaybackUiState
import com.vexo.studio.playback.RepeatModeState
import com.vexo.studio.ui.StudioUiState
import com.vexo.studio.ui.StudioViewModel
import com.vexo.studio.ui.components.StudioChipSelector
import com.vexo.studio.ui.components.StudioColorPickerRow
import com.vexo.studio.ui.components.StudioSlider
import com.vexo.studio.ui.components.StudioToggleRow
import com.vexo.studio.ui.theme.LocalVexoPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@UnstableApi
@Composable
fun ProjectsScreen(
    vm: StudioViewModel,
    uiState: StudioUiState
) {
    val palette = LocalVexoPalette.current
    var showNewDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    val fmt = remember { SimpleDateFormat("MMM dd · HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("PROJECTS", style = MaterialTheme.typography.labelMedium, color = palette.accent)
            Box(
                modifier = Modifier
                    .background(palette.accent,palette.shape)
                    .clickable { showNewDialog = true }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, null, tint = palette.accentOn, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("NEW PROJECT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = palette.accentOn)
                }
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(uiState.projects, key = { _, item -> item.id }) { _, proj ->
                val isCurrent = proj.id == uiState.project.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, if (isCurrent) palette.accent else palette.border, palette.shape)
                        .background(palette.glassPanel, palette.shape)
                        .clickable { vm.openProject(proj.id) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(proj.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${proj.aspectRatio.label} · ${proj.layerCount} layers · ${fmt.format(Date(proj.updatedAtMs))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary
                        )
                    }
                    if (isCurrent) {
                        Text("OPEN", style = MaterialTheme.typography.labelSmall, color = palette.accent)
                        Spacer(Modifier.width(8.dp))
                    }
                    TextButton(onClick={vm.restorePreviousProject(proj.id)}){Text("RECOVER",style=MaterialTheme.typography.labelSmall)}
                    IconButton(onClick = { vm.duplicateProject(proj.id) }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Outlined.ContentCopy, "Duplicate", tint = palette.textSecondary, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = { deleteTarget = proj }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Outlined.Delete, "Delete", tint = palette.danger, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }

    if (showNewDialog) {
        var name by remember { mutableStateOf("Studio Scene") }
        AlertDialog(
            onDismissRequest = { showNewDialog = false },
            title = { Text("NEW STUDIO PROJECT") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Project Name") })
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.createNewProject(name)
                    showNewDialog = false
                }) { Text("CREATE") }
            },
            dismissButton = {
                TextButton(onClick = { showNewDialog = false }) { Text("CANCEL") }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("DELETE PROJECT?") },
            text = { Text("Delete \"${target.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteProject(target.id)
                    deleteTarget = null
                }) { Text("DELETE", color = palette.danger) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("CANCEL") }
            }
        )
    }
}

@UnstableApi
@Composable
fun AudioLibraryScreen(
    vm: StudioViewModel,
    playback: PlaybackUiState,
    onImportAudio: () -> Unit
) {
    val palette = LocalVexoPalette.current
    var showLibrary by remember { mutableStateOf(false) }
    var showEffects by remember { mutableStateOf(false) }
    if(showEffects){SoundEffectsPanel(vm){showEffects=false};return}
    if (showLibrary) {
        DeviceLibraryPanel(vm, onBack={showLibrary=false})
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("AUDIO QUEUE (${playback.queue.size})", style = MaterialTheme.typography.labelMedium, color = palette.accent)
            Box(
                modifier = Modifier
                    .background(palette.accent,palette.shape)
                    .clickable(onClick = onImportAudio)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text("IMPORT AUDIO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = palette.accentOn)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick={showLibrary=true}) { Text("DEVICE LIBRARY") }
            TextButton(onClick={showEffects=true}) { Text("EQ / FX") }
            TextButton(onClick=vm::applyArtworkPalette) { Text("AUTO PALETTE") }
        }
        var clearQueue by remember{mutableStateOf(false)}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            Text("Queue is saved per project",style=MaterialTheme.typography.labelSmall,color=palette.textSecondary)
            TextButton(enabled=playback.queue.isNotEmpty(),onClick={clearQueue=true}){Text("CLEAR QUEUE")}
        }
        if(clearQueue)AlertDialog(onDismissRequest={clearQueue=false},title={Text("CLEAR AUDIO QUEUE?")},text={Text("Removes tracks from this project, not from your device.")},confirmButton={TextButton(onClick={vm.clearProjectAudioQueue();clearQueue=false}){Text("CLEAR")}},dismissButton={TextButton(onClick={clearQueue=false}){Text("CANCEL")}})
        val sleep by vm.sleepRemaining.collectAsState()
        StudioChipSelector("Sleep timer" + if(sleep>0) " · "+formatDuration(sleep*1000) else "", listOf(0,15,30,60,120), if(sleep==0L) 0 else ((sleep+59)/60).toInt(), {if(it==0) "Off" else "$it min"}) { vm.setSleepTimer(it) }
        if(playback.importTotal>0){
            Text("Importing audio: ${playback.importCompleted}/${playback.importTotal}",style=MaterialTheme.typography.labelSmall,color=palette.accent)
            TextButton(onClick={vm.playbackController.cancelAudioImport()}){Text("CANCEL IMPORT")}
        }
        playback.errorMessage?.let{message->Text(message,color=palette.danger,style=MaterialTheme.typography.bodySmall)}
        StudioChipSelector("Track transition (fade, no overlap)",listOf(0,1000,2000,3000,5000),playback.transitionFadeMs,{if(it==0)"Off" else "${it/1000}s"},vm.playbackController::setTransitionFade)
        // Queue controls: shuffle, repeat, volume
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, palette.border, palette.shape)
                .background(palette.glassPanel, palette.shape)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { vm.playbackController.toggleShuffle() }, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Default.Shuffle,
                    "Shuffle",
                    tint = if (playback.shuffleEnabled) palette.accent else palette.textSecondary
                )
            }
            IconButton(onClick = { vm.playbackController.cycleRepeatMode() }, modifier = Modifier.size(34.dp)) {
                Icon(
                    if (playback.repeatMode == RepeatModeState.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    "Repeat",
                    tint = if (playback.repeatMode != RepeatModeState.OFF) palette.accent else palette.textSecondary
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                StudioSlider(
                    label = "Volume",
                    value = playback.volume,
                    min = 0f,
                    max = 1f,
                    defaultValue = 1f,
                    step = 0.05f,
                    showHelpIcon = false,
                    onValueChange = { vm.playbackController.setVolume(it) }
                )
            }
        }

        if (playback.queue.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, palette.border, palette.shape)
                    .background(palette.glassPanel, palette.shape)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No audio tracks imported yet. Tap IMPORT AUDIO to load MP3, WAV, FLAC, AAC, or OGG files.", style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(),verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(playback.queue, key = { _, t -> t.id }) { idx, track ->
                    val current = idx == playback.currentIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(palette.shape)
                            .background(if(current)palette.glassElevated else palette.glassPanel,palette.shape)
                            .border(1.dp, if (current) palette.accent else palette.border, palette.shape)
                            .clickable { vm.playbackController.playIndex(idx) }
                            .padding(horizontal=8.dp,vertical=6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick={if(current)vm.playbackController.togglePlayPause() else vm.playbackController.playIndex(idx)},modifier=Modifier.size(40.dp)){
                            Icon(if(current&&playback.isPlaying)Icons.Default.Pause else Icons.Default.PlayArrow,if(current&&playback.isPlaying)"Pause track" else "Play track",tint=if(current)palette.accent else palette.textSecondary,modifier=Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(track.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${if(current) "ACTIVE · " else "${idx+1} · "}${track.artist} · ${formatDuration(track.durationMs)}", style = MaterialTheme.typography.labelSmall, color = if(current)palette.accent else palette.textSecondary, maxLines = 1)
                        }
                        IconButton(enabled=idx>0,onClick={vm.playbackController.moveQueueItem(idx,idx-1)},modifier=Modifier.size(36.dp)){Icon(Icons.Default.ArrowUpward,"Move track up",tint=if(idx>0)palette.textPrimary else palette.border,modifier=Modifier.size(18.dp))}
                        IconButton(enabled=idx<playback.queue.lastIndex,onClick={vm.playbackController.moveQueueItem(idx,idx+1)},modifier=Modifier.size(36.dp)){Icon(Icons.Default.ArrowDownward,"Move track down",tint=if(idx<playback.queue.lastIndex)palette.textPrimary else palette.border,modifier=Modifier.size(18.dp))}
                        IconButton(onClick = { vm.playbackController.removeFromQueue(idx) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Outlined.Delete, "Remove", tint = palette.danger, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }
    }
}

@UnstableApi
@Composable
fun ExportScreen(
    vm: StudioViewModel,
    uiState: StudioUiState,
    playback: PlaybackUiState,
    exportState: ExportProgress,
    onPickExportUri: (String) -> Unit
) {
    val palette = LocalVexoPalette.current
    val exp = uiState.project.exportSettings
    var filename by androidx.compose.runtime.saveable.rememberSaveable(uiState.project.id){mutableStateOf(exp.outputFileName.ifBlank{com.vexo.studio.export.ExportFileName.defaultName()})}
    val safeFilename=com.vexo.studio.export.ExportFileName.normalize(filename)
    val trackDurMs = playback.durationMs.coerceAtLeast(1000L)
    // Recompute only when encoder-relevant settings change, off the UI thread.
    val capabilityState=remember(exp.aspectRatio,exp.adaptiveVideoBitrate,exp.videoBitrateMbps,exp.useHevcIfSupported){
        mutableStateOf<Map<VideoResolution,List<com.vexo.studio.export.DeviceExportCapabilities.Choice>>?>(null)
    }
    androidx.compose.runtime.LaunchedEffect(capabilityState){
        capabilityState.value=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){
            com.vexo.studio.export.DeviceExportCapabilities.matrix(exp)
        }
    }
    val capabilities=capabilityState.value
    val resolutions=VideoResolution.entries.filter{capabilities?.get(it).orEmpty().isNotEmpty()}
    val rates=capabilities?.get(exp.resolution).orEmpty()
    val currentChoice=rates.firstOrNull{it.fps==exp.fps}
    val canExport=currentChoice!=null
    val dimensions=com.vexo.studio.export.ExportEncoderPolicy.dimensions(exp.aspectRatio.widthRatio,exp.aspectRatio.heightRatio,exp.resolution.shortSide)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ExportPanel("OUTPUT SUMMARY"){
            Text("MP4 VIDEO / AAC AUDIO", style=MaterialTheme.typography.labelMedium,color=palette.accent)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                ExportMetric("RESOLUTION","${dimensions.first} × ${dimensions.second}",Modifier.weight(1f))
                ExportMetric("FRAME RATE","${exp.fps} FPS",Modifier.weight(1f))
            }
            Text(currentChoice?.codec?:"Checking / select supported quality",style=MaterialTheme.typography.labelSmall,color=palette.textSecondary)
        }
        ExportPanel("VIDEO FORMAT"){

        StudioChipSelector("Aspect Ratio", AspectRatioPreset.entries, exp.aspectRatio, { it.label }) { ratio ->
            vm.updateExportSettings { it.copy(aspectRatio = ratio) }
            vm.setAspectRatio(ratio)
        }
        if(capabilities==null){
            Text("CHECKING DEVICE ENCODERS…",style=MaterialTheme.typography.labelSmall,color=palette.textSecondary)
        }else if(resolutions.isEmpty()){
            Text("No encoder supports the current ratio and bitrate. Try automatic bitrate or another aspect ratio.",style=MaterialTheme.typography.bodySmall,color=palette.danger)
        }else{
            StudioChipSelector("Resolution",resolutions,exp.resolution,{if(it==VideoResolution.QHD_1440)"2K / 1440p" else it.label}){res->
                val supportedRates=capabilities[res].orEmpty().map{it.fps}
                val fps=com.vexo.studio.export.ExportEncoderPolicy.preferredRate(supportedRates,exp.fps)
                if(fps!=null)vm.updateExportSettings{it.copy(resolution=res,fps=fps)}
            }
            if(rates.isNotEmpty())StudioChipSelector("Frame Rate",rates.map{it.fps},exp.fps,{"$it FPS"}){fps->
                vm.updateExportSettings{it.copy(fps=fps)}
            }
            if(currentChoice!=null)Text("${dimensions.first} × ${dimensions.second} · ${exp.fps} FPS · ${currentChoice.codec}",color=palette.accent,style=MaterialTheme.typography.labelMedium)
            else Text("Saved export quality is unsupported. Select an available resolution and frame rate.",color=palette.danger,style=MaterialTheme.typography.bodySmall)
            Text("Only device-reported supported options are shown. Changing resolution may select the nearest supported FPS.",style=MaterialTheme.typography.labelSmall,color=palette.textSecondary)
        }
        }
        ExportPanel("ENCODING & QUALITY"){
        StudioToggleRow("Automatic video bitrate",exp.adaptiveVideoBitrate){v->vm.updateExportSettings{it.copy(adaptiveVideoBitrate=v)}}
        if(!exp.adaptiveVideoBitrate)StudioSlider(
            label = "Video Bitrate",
            value = exp.videoBitrateMbps.toFloat(),
            min = 2f,
            max = 80f,
            defaultValue = 12f,
            units = "Mbps",
            step = 1f,
            onValueChange = { v -> vm.updateExportSettings { it.copy(videoBitrateMbps = v.toInt()) } }
        )

        StudioToggleRow("Prefer HEVC (H.265)",exp.useHevcIfSupported){v->vm.updateExportSettings{it.copy(useHevcIfSupported=v)}}
        val heavySurface=dimensions.first.toLong()*dimensions.second>1920L*1080||exp.fps>=50
        if(!heavySurface)StudioToggleRow("Smooth edges (GPU MSAA)",exp.smoothEdges){v->vm.updateExportSettings{it.copy(smoothEdges=v)}}
        else Text("High-quality export: surface MSAA disabled to reduce GPU memory. Composition effects remain enabled.",style=MaterialTheme.typography.labelSmall,color=palette.textSecondary)
        StudioToggleRow("Smooth composition edges",exp.offscreenSmoothEdges){v->vm.updateExportSettings{it.copy(offscreenSmoothEdges=v)}}
        StudioToggleRow("High resolution image textures",exp.highResolutionTextures){v->vm.updateExportSettings{it.copy(highResolutionTextures=v)}}
        Text("H.264 and HEVC are tried in preference order, with hardware encoders prioritized. HEVC can be used as a fallback when H.264 is unavailable.",style=MaterialTheme.typography.labelSmall,color=palette.textSecondary)
        val estimatedMs=(if(exp.trimEndMs>exp.trimStartMs)minOf(trackDurMs,exp.trimEndMs) else trackDurMs)-exp.trimStartMs
        val targetBitrate=com.vexo.studio.export.ExportEncoderPolicy.targetBitrate(exp)
        if(exp.adaptiveVideoBitrate)Text("Automatic target: %.1f Mbps · adjusted to codec limits".format(targetBitrate/1_000_000.0),style=MaterialTheme.typography.labelSmall,color=palette.textSecondary)
        val estimatedBytes=com.vexo.studio.export.ExportBudget.encodedBytes(estimatedMs.coerceAtLeast(0L)*1000L,targetBitrate,exp.audioBitrateKbps*1000)
        Text("Estimated output: %.1f MB".format(estimatedBytes/1_000_000.0),style=MaterialTheme.typography.labelMedium,color=palette.accent)
        StudioChipSelector("AAC Audio Bitrate", listOf(96,128,192,256,320), exp.audioBitrateKbps, { "$it kbps" }) { v -> vm.updateExportSettings { it.copy(audioBitrateKbps=v) } }
        Text("Video + AAC · Encoder capability and temporary storage are checked before rendering.", style=MaterialTheme.typography.bodySmall, color=palette.textSecondary)
        }
        ExportPanel("TRIM & DURATION"){
        if (playback.durationMs > 0L) {
            StudioSlider(
                label = "Trim Start",
                value = exp.trimStartMs.toFloat(),
                min = 0f,
                max = trackDurMs.toFloat(),
                defaultValue = 0f,
                units = "ms",
                step = 500f,
                onValueChange = { v -> vm.updateExportSettings { it.copy(trimStartMs = v.toLong()) } }
            )
            StudioSlider(
                label = "Trim End (0 = Full)",
                value = exp.trimEndMs.toFloat(),
                min = 0f,
                max = trackDurMs.toFloat(),
                defaultValue = 0f,
                units = "ms",
                step = 500f,
                onValueChange = { v -> vm.updateExportSettings { it.copy(trimEndMs = v.toLong()) } }
            )
        }

        }
        ExportPanel("SAVE LOCATION"){
        OutlinedTextField(value=filename,onValueChange={filename=it.take(180)},enabled=!exportState.isRunning&&exp.customOutputUri==null,singleLine=true,label={Text("VIDEO FILE NAME")},modifier=Modifier.fillMaxWidth())
        Text(if(exp.customOutputUri==null)"SAVE AS: $safeFilename" else "The selected document name is used. Reset location to edit.",style=MaterialTheme.typography.labelSmall,color=palette.textSecondary)
        TextButton(enabled=!exportState.isRunning&&exp.customOutputUri==null,onClick={filename=com.vexo.studio.export.ExportFileName.defaultName()}){Text("AUTO DATE / TIME NAME")}
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, palette.border, palette.shape)
                    .background(palette.glassPanel, palette.shape)
                    .clickable(enabled=!exportState.isRunning){onPickExportUri(safeFilename)}
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (exp.customOutputUri == null) "DEST: MOVIES/VEXO" else "CUSTOM FILE SET",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textPrimary
                )
            }
            if (exp.customOutputUri != null) {
                Box(
                    modifier = Modifier
                        .border(1.dp, palette.border, palette.shape)
                        .background(palette.glassPanel, palette.shape)
                        .clickable { vm.updateExportSettings { it.copy(customOutputUri = null) } }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text("RESET", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                }
            }
        }

        }
        if (exportState.isRunning) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, palette.accent, palette.shape)
                    .background(palette.glassPanel, palette.shape)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "${exportState.statusText} (${(exportState.progress * 100).toInt()}%)",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.accent
                )
                LinearProgressIndicator(
                    progress = { exportState.progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = palette.accent,
                    trackColor = palette.border
                )
                if (exportState.etaSeconds > 0) {
                    Text("Estimated remaining: ${exportState.etaSeconds}s", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, palette.danger)
                        .clickable { vm.cancelVideoExport() }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("CANCEL EXPORT", style = MaterialTheme.typography.labelSmall, color = palette.danger)
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if(canExport)palette.accent else palette.panelElevated,palette.shape)
                    .clickable(enabled=canExport) { vm.startVideoExport(safeFilename) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(if(canExport)"START MP4 EXPORT" else if(capabilities==null)"CHECKING ENCODERS" else "SELECT SUPPORTED QUALITY", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if(canExport)palette.accentOn else palette.textSecondary)
            }
        }

        exportState.publishedUri?.let { uri ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, palette.accent, palette.shape)
                    .background(palette.glassPanel, palette.shape)
                    .padding(12.dp)
            ) {
                Text("Verified & saved to:\n$uri", style = MaterialTheme.typography.bodySmall, color = palette.accent)
            }
        }
        exportState.errorMessage?.let { err ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, palette.danger)
                    .background(palette.glassPanel, palette.shape)
                    .padding(12.dp)
            ) {
                Text("Export error: $err", style = MaterialTheme.typography.bodySmall, color = palette.danger)
            }
        }
    }
}

@UnstableApi
@Composable
fun SettingsScreen(
    vm: StudioViewModel,
    settings: AppSettings
) {
    val palette = LocalVexoPalette.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val sections = remember { mutableStateMapOf<String, Boolean>() }
        Text("Settings", style = MaterialTheme.typography.titleMedium)
        ExpandableSectionHeader("Custom wallpaper",sections["wallpaper"]==true,{toggleAccordion(sections,"wallpaper")}){WallpaperSettings(vm,settings)}
        ExpandableSectionHeader("Local crash diagnostics",sections["crash"]==true,{toggleAccordion(sections,"crash")}){LocalCrashPanel()}
        ExpandableSectionHeader("Appearance", sections["appearance"] == true, { toggleAccordion(sections,"appearance") }) {

        StudioChipSelector("Theme Preset", ThemePreset.entries, settings.themePreset, { it.label }) { theme ->
            vm.updateSettings { it.copy(themePreset = theme) }
        }
        StudioToggleRow("Apply accent to every theme",settings.customAccentAllThemes){v->vm.updateSettings{it.copy(customAccentAllThemes=v)}}
        StudioColorPickerRow("Custom Accent", settings.customAccentArgb) { col ->
            vm.updateSettings { it.copy(customAccentArgb = col) }
        }
        StudioToggleRow("Glass interface", settings.glassEnabled) { value -> vm.updateSettings { it.copy(glassEnabled = value) } }
        if (settings.glassEnabled) {
            StudioSlider("Panel opacity", settings.glassOpacity * 100f, 30f, 100f, 62f, "%", 1f, onValueChange = { value -> vm.updateSettings { it.copy(glassOpacity = value / 100f) } })
            StudioSlider("Artwork backdrop blur", settings.backdropBlurDp, 0f, 48f, 24f, "dp", 1f, onValueChange = { value -> vm.updateSettings { it.copy(backdropBlurDp = value) } })
            Text("Blur uses wallpaper, album art or sampled preview frames. Lower panel opacity to reveal the glass.", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
        }
        StudioSlider("Corner radius", settings.cornerRadiusDp, 0f, 28f, 12f, "dp", 1f, onValueChange = { value -> vm.updateSettings { it.copy(cornerRadiusDp = value) } })
        StudioSlider("Slider control size", settings.controlScale, 0.85f, 1.25f, 1f, "x", 0.05f, onValueChange = { value -> vm.updateSettings { it.copy(controlScale = value) } })
        StudioSlider("Interface text size", settings.uiTextScale, 0.9f, 1.25f, 1f, "x", 0.05f, onValueChange = { value -> vm.updateSettings { it.copy(uiTextScale = value) } })

        }
        ExpandableSectionHeader("Editor & performance", sections["editor"] == true, { toggleAccordion(sections,"editor") }) {
        StudioToggleRow("Portrait overlay workspace", settings.portraitOverlay) { value -> vm.updateSettings { it.copy(portraitOverlay = value) } }
        StudioSlider("Preview area", settings.previewShare * 100f, 30f, 65f, 44f, "%", 1f, onValueChange = { value -> vm.updateSettings { it.copy(previewShare = value / 100f) } })
        StudioChipSelector("Preview FPS limit", listOf(15, 24, 30, 60), settings.previewFps, { "$it FPS" }) { fps -> vm.updateSettings { it.copy(previewFps = fps) } }
        StudioChipSelector("Preview render quality",listOf(.5f,.75f,1f),settings.previewScale,{"${(it*100).toInt()}%"}){v->vm.updateSettings{it.copy(previewScale=v)}}
        StudioToggleRow("Adaptive preview quality",settings.adaptivePreview){v->vm.updateSettings{it.copy(adaptivePreview=v)}}
        StudioToggleRow("Live preview diagnostics",settings.showPreviewDiagnostics){v->vm.updateSettings{it.copy(showPreviewDiagnostics=v)}}
        Text("Reports measured render callbacks, CPU submission time and estimated texture/FBO allocation. Not total GPU memory or GPU execution time.",style=MaterialTheme.typography.labelSmall)
        Text("Lower preview resolution reduces GPU load. MP4 export resolution stays unchanged.",style=MaterialTheme.typography.labelSmall)
        TextButton(onClick = { vm.updateSettings { current -> current.copy(themePreset = ThemePreset.LIME_STUDIO, customAccentArgb = 0xFFC5F84A.toInt(), glassEnabled = true, glassOpacity = 0.62f, backdropBlurDp = 36f, cornerRadiusDp = 12f, controlScale = 1f, uiTextScale = 1f, previewFps = 30, previewScale=1f, adaptivePreview=false, showPreviewDiagnostics=false, previewShare = 0.41f, portraitOverlay = false) } }) { Text("RESET INTERFACE SETTINGS") }

        }
        ExpandableSectionHeader("Audio analysis", sections["audio"] == true, { toggleAccordion(sections,"audio") }) {
        StudioChipSelector("Default FFT Size", listOf(256, 512, 1024, 2048, 4096), settings.defaultFftSize, { "$it" }) { sz ->
            vm.updateSettings { it.copy(defaultFftSize = sz) }
        }
        StudioChipSelector("Default Hop Size", listOf(128, 256, 512, 1024), settings.defaultHopSize, { "$it" }) { hop ->
            vm.updateSettings { it.copy(defaultHopSize = hop) }
        }
        StudioChipSelector("Pause Behavior", PauseBehavior.entries, settings.pauseBehavior, { it.label }) { pb ->
            vm.updateSettings { it.copy(pauseBehavior = pb) }
        }
        StudioToggleRow("Show Property Help Icons", settings.showPropertyHelpHints) { show ->
            vm.updateSettings { it.copy(showPropertyHelpHints = show) }
        }
        }
        UserProfilePanel(vm,settings)
        DeveloperPanel()
    }
}

@Composable
private fun LocalCrashPanel(){
    val context=androidx.compose.ui.platform.LocalContext.current
    var report by androidx.compose.runtime.remember{androidx.compose.runtime.mutableStateOf(com.vexo.studio.data.LocalCrashReport.read(context))}
    Text("Fatal error summaries are stored locally. No automatic upload.",style=MaterialTheme.typography.labelSmall)
    if(report.isEmpty())Text("No saved fatal error report.",style=MaterialTheme.typography.bodySmall)
    else{
        Text(report,style=MaterialTheme.typography.bodySmall)
        Row{
            TextButton(onClick={
                val clipboard=context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("VEXO crash report",report))
            }){Text("COPY")}
            TextButton(onClick={com.vexo.studio.data.LocalCrashReport.clear(context);report=""}){Text("CLEAR")}
        }
    }
}


@Composable
private fun ExportPanel(title:String,content:@Composable ()->Unit){
    val p=LocalVexoPalette.current
    Column(Modifier.fillMaxWidth().background(p.glassPanel,p.shape).border(1.dp,p.border,p.shape).padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
        Text(title,style=MaterialTheme.typography.labelMedium,color=p.accent)
        content()
    }
}
@Composable
private fun ExportMetric(label:String,value:String,modifier:Modifier){
    val p=LocalVexoPalette.current
    Column(modifier.background(p.panelElevated,p.shape).padding(10.dp)){
        Text(label,style=MaterialTheme.typography.labelSmall,color=p.textSecondary)
        Text(value,style=MaterialTheme.typography.titleMedium,color=p.textPrimary,maxLines=1)
    }
}
@UnstableApi
@Composable
private fun WallpaperSettings(vm:StudioViewModel,settings:AppSettings){
    val p=LocalVexoPalette.current
    val picker=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()){uri->if(uri!=null)vm.setWallpaper(uri)}
    Text(if(settings.wallpaperUri.isBlank())"No custom wallpaper" else "Custom wallpaper active",style=MaterialTheme.typography.bodySmall,color=p.textSecondary)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
        TextButton(onClick={picker.launch(arrayOf("image/*"))}){Text("CHOOSE IMAGE")}
        TextButton(enabled=settings.wallpaperUri.isNotBlank(),onClick={vm.updateSettings{it.copy(wallpaperUri="")}}){Text("REMOVE")}
    }
    StudioSlider("Wallpaper blur",settings.wallpaperBlurDp,0f,48f,16f,"dp",1f,onValueChange={v->vm.updateSettings{it.copy(wallpaperBlurDp=v)}})
    StudioSlider("Wallpaper dim",settings.wallpaperDim*100f,25f,95f,72f,"%",1f,onValueChange={v->vm.updateSettings{it.copy(wallpaperDim=v/100f)}})
    Text("UI only. With glass enabled, backdrop blur is also applied to wallpaper. Never enters exported video.",style=MaterialTheme.typography.labelSmall,color=p.textSecondary)
}
@Composable
private fun DeveloperPanel(){
    val p=LocalVexoPalette.current
    val links=androidx.compose.ui.platform.LocalUriHandler.current
    ExportPanel("DEVELOPER"){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Box(Modifier.size(52.dp).background(p.accent,p.shape),contentAlignment=Alignment.Center){Text("J",style=MaterialTheme.typography.headlineMedium,color=p.accentOn)}
            Column(Modifier.weight(1f)){
                Text("jhaa2k",style=MaterialTheme.typography.titleLarge,color=p.textPrimary)
                Text("VEXO AUDIO VISUALIZATION · v"+com.vexo.studio.BuildConfig.VERSION_NAME,style=MaterialTheme.typography.labelSmall,color=p.textSecondary)
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
            TextButton(onClick={links.openUri("https://github.com/jhaa2k")}){Text("PROFILE")}
            TextButton(onClick={links.openUri("https://github.com/jhaa2k/Vexo-Audio-Visualization")}){Text("REPOSITORY")}
        }
        TextButton(onClick={links.openUri("https://github.com/jhaa2k/Vexo-Audio-Visualization/releases")}){Text("LATEST APP / RELEASES")}
    }
}
