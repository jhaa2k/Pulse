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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
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
                    .background(palette.accent)
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
                        Icon(Icons.Default.ContentCopy, "Duplicate", tint = palette.textSecondary, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = { deleteTarget = proj }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Delete, "Delete", tint = palette.danger, modifier = Modifier.size(16.dp))
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
                    .background(palette.accent)
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
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(playback.queue, key = { _, t -> t.id }) { idx, track ->
                    val current = idx == playback.currentIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, if (current) palette.accent else palette.border, palette.shape)
                            .background(palette.glassPanel, palette.shape)
                            .clickable { vm.playbackController.playIndex(idx) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            null,
                            tint = if (current) palette.accent else palette.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(track.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${track.artist} · ${formatDuration(track.durationMs)}", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, maxLines = 1)
                        }
                        TextButton(enabled=idx>0,onClick={vm.playbackController.moveQueueItem(idx,idx-1)},contentPadding=PaddingValues(0.dp),modifier=Modifier.width(30.dp)){Text("↑")}
                        TextButton(enabled=idx<playback.queue.lastIndex,onClick={vm.playbackController.moveQueueItem(idx,idx+1)},contentPadding=PaddingValues(0.dp),modifier=Modifier.width(30.dp)){Text("↓")}
                        IconButton(onClick = { vm.playbackController.removeFromQueue(idx) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Delete, "Remove", tint = palette.danger, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }
    }
}

@UnstableApi
@Composable
fun TemplatesScreen(
    vm: StudioViewModel,
    onImportPreset: () -> Unit
) {
    val palette = LocalVexoPalette.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("STUDIO TEMPLATES", style = MaterialTheme.typography.labelMedium, color = palette.accent)
            Box(
                modifier = Modifier
                    .border(1.dp, palette.accent, palette.shape)
                    .background(palette.glassPanel, palette.shape)
                    .clickable(onClick = onImportPreset)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("OPEN .VEX", style = MaterialTheme.typography.labelSmall, color = palette.accent)
            }
        }

        val templates = remember { vm.availableTemplates() }
        var templateQuery by remember { mutableStateOf("") }
        OutlinedTextField(value=templateQuery,onValueChange={templateQuery=it},label={Text("Search templates")},singleLine=true,modifier=Modifier.fillMaxWidth())
        templates.filter { it.name.contains(templateQuery,ignoreCase=true) }.forEach { tpl ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, palette.border, palette.shape)
                    .background(palette.glassPanel, palette.shape)
                    .clickable { vm.applyPreset(tpl) }
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(tpl.name, style = MaterialTheme.typography.titleMedium)
                    Text(tpl.canvas.aspectRatio.label, style = MaterialTheme.typography.labelSmall, color = palette.accent)
                }
                Text(
                    "${tpl.compositions.size} compositions · ${tpl.compositions.sumOf { it.layers.size }} layers · FFT ${tpl.audioProviders.first().fftSize}",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary
                )
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
    onPickExportUri: () -> Unit
) {
    val palette = LocalVexoPalette.current
    val exp = uiState.project.exportSettings
    val trackDurMs = playback.durationMs.coerceAtLeast(1000L)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("OFFLINE VIDEO EXPORT (MP4 + AAC)", style = MaterialTheme.typography.labelMedium, color = palette.accent)

        StudioChipSelector("Aspect Ratio", AspectRatioPreset.entries, exp.aspectRatio, { it.label }) { ratio ->
            vm.updateExportSettings { it.copy(aspectRatio = ratio) }
            vm.setAspectRatio(ratio)
        }
        StudioChipSelector("Resolution", VideoResolution.entries, exp.resolution, { it.label }) { res ->
            vm.updateExportSettings { it.copy(resolution = res) }
        }
        StudioChipSelector("Frame Rate", listOf(24, 25, 30, 60), exp.fps, { "$it FPS" }) { fps ->
            vm.updateExportSettings { it.copy(fps = fps) }
        }
        StudioSlider(
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
        StudioToggleRow("Smooth edges (GPU MSAA)",exp.smoothEdges){v->vm.updateExportSettings{it.copy(smoothEdges=v)}}
        StudioToggleRow("Smooth composition edges",exp.offscreenSmoothEdges){v->vm.updateExportSettings{it.copy(offscreenSmoothEdges=v)}}
        StudioToggleRow("High resolution image textures",exp.highResolutionTextures){v->vm.updateExportSettings{it.copy(highResolutionTextures=v)}}
        Text("HEVC is used only if the device reports support; otherwise H.264 is selected.",style=MaterialTheme.typography.labelSmall,color=palette.textSecondary)
        val estimatedMs=(if(exp.trimEndMs>exp.trimStartMs)minOf(trackDurMs,exp.trimEndMs) else trackDurMs)-exp.trimStartMs
        val estimatedBytes=com.vexo.studio.export.ExportBudget.encodedBytes(estimatedMs.coerceAtLeast(0L)*1000L,exp.videoBitrateMbps.coerceIn(2,80)*1_000_000,exp.audioBitrateKbps*1000)
        Text("Estimated output: %.1f MB".format(estimatedBytes/1_000_000.0),style=MaterialTheme.typography.labelMedium,color=palette.accent)
        StudioChipSelector("AAC Audio Bitrate", listOf(96,128,192,256,320), exp.audioBitrateKbps, { "$it kbps" }) { v -> vm.updateExportSettings { it.copy(audioBitrateKbps=v) } }
        Text("Video + AAC · Encoder capability and temporary storage are checked before rendering.", style=MaterialTheme.typography.bodySmall, color=palette.textSecondary)
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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, palette.border, palette.shape)
                    .background(palette.glassPanel, palette.shape)
                    .clickable(onClick = onPickExportUri)
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
                    .background(palette.accent)
                    .clickable { vm.startVideoExport() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("START MP4 EXPORT", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = palette.accentOn)
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
        ExpandableSectionHeader("Local crash diagnostics",sections["crash"]==true,{toggleAccordion(sections,"crash")}){LocalCrashPanel()}
        ExpandableSectionHeader("Appearance", sections["appearance"] == true, { toggleAccordion(sections,"appearance") }) {

        StudioChipSelector("Theme Preset", ThemePreset.entries, settings.themePreset, { it.label }) { theme ->
            vm.updateSettings { it.copy(themePreset = theme) }
        }
        StudioColorPickerRow("Custom Accent", settings.customAccentArgb) { col ->
            vm.updateSettings { it.copy(customAccentArgb = col) }
        }
        StudioToggleRow("Glass interface", settings.glassEnabled) { value -> vm.updateSettings { it.copy(glassEnabled = value) } }
        if (settings.glassEnabled) {
            StudioSlider("Panel opacity", settings.glassOpacity * 100f, 65f, 100f, 82f, "%", 1f, onValueChange = { value -> vm.updateSettings { it.copy(glassOpacity = value / 100f) } })
            StudioSlider("Artwork backdrop blur", settings.backdropBlurDp, 0f, 48f, 24f, "dp", 1f, onValueChange = { value -> vm.updateSettings { it.copy(backdropBlurDp = value) } })
            Text("Artwork blur uses Android 12+; older devices use a lightweight gradient.", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
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
        TextButton(onClick = { vm.updateSettings { current -> current.copy(themePreset = ThemePreset.LIME_STUDIO, customAccentArgb = 0xFFC5F84A.toInt(), glassEnabled = true, glassOpacity = 0.82f, backdropBlurDp = 24f, cornerRadiusDp = 12f, controlScale = 1f, uiTextScale = 1f, previewFps = 30, previewScale=1f, adaptivePreview=false, showPreviewDiagnostics=false, previewShare = 0.41f, portraitOverlay = false) } }) { Text("RESET INTERFACE SETTINGS") }

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
