package com.vexo.studio.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.vexo.studio.engine.gl.RenderSceneSnapshot
import com.vexo.studio.engine.gl.VexoPreviewSurface
import com.vexo.studio.model.AppSettings
import com.vexo.studio.model.AspectRatioPreset
import com.vexo.studio.model.AudioProviderConfig
import com.vexo.studio.model.BarsSegmentType
import com.vexo.studio.model.BarsShapePath
import com.vexo.studio.model.BlendMode
import com.vexo.studio.model.ChannelMode
import com.vexo.studio.model.ForceFieldType
import com.vexo.studio.model.ImageShape
import com.vexo.studio.model.ImageSourceType
import com.vexo.studio.model.LayerConfig
import com.vexo.studio.model.LayerNode
import com.vexo.studio.model.LayerType
import com.vexo.studio.model.MeasureWhat
import com.vexo.studio.model.MirrorMode
import com.vexo.studio.model.PauseBehavior
import com.vexo.studio.model.SampleProviderType
import com.vexo.studio.model.ShapeKind
import com.vexo.studio.model.SpawnAreaType
import com.vexo.studio.model.StudioTab
import com.vexo.studio.model.VexoProject
import com.vexo.studio.model.VexoTemplates
import com.vexo.studio.model.WindowType
import com.vexo.studio.playback.PlaybackUiState
import com.vexo.studio.ui.StudioUiState
import com.vexo.studio.ui.StudioViewModel
import com.vexo.studio.ui.components.RegisteredSlider
import com.vexo.studio.ui.components.StudioChipSelector
import com.vexo.studio.ui.components.StudioColorPickerRow
import com.vexo.studio.ui.components.StudioSlider
import com.vexo.studio.ui.components.StudioToggleRow
import com.vexo.studio.ui.theme.LocalVexoPalette

@UnstableApi
@Composable
fun EditorScreen(
    vm: StudioViewModel,
    uiState: StudioUiState,
    playback: PlaybackUiState,
    sceneSnapshot: StateFlow<RenderSceneSnapshot?>,
    settings: AppSettings,
    onPickAudio: () -> Unit,
    onPickImage: () -> Unit,
    onImportPreset: () -> Unit,
    onExportPreset: () -> Unit
) {
    val palette = LocalVexoPalette.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 720
    val previewShare = settings.previewShare.coerceIn(0.30f, 0.65f)

    if (uiState.isFullscreenPreview) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            VexoPreviewSurface(
                aspectRatio = uiState.project.canvas.aspectRatio,
                sceneSnapshot = sceneSnapshot.collectAsState().value,
                onTransformGesture = vm::updatePreviewTransform,
                modifier = Modifier.fillMaxSize(),
                fillBounds = false,
                targetFps = settings.previewFps,
                continuousRendering = playback.isPlaying || settings.pauseBehavior != PauseBehavior.FREEZE
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(palette.panel.copy(alpha = 0.85f))
                    .border(1.dp, palette.border, palette.shape),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = vm::resetPreviewCamera) {
                    Icon(Icons.Default.CropFree, "Reset camera", tint = palette.textPrimary)
                }
                IconButton(onClick = vm::toggleSafeAreaGuides) {
                    Icon(
                        Icons.Default.GridOn,
                        "Safe area",
                        tint = if (uiState.project.canvas.showSafeAreaGuides) palette.accent else palette.textSecondary
                    )
                }
                IconButton(onClick = vm::toggleFullscreenPreview) {
                    Icon(Icons.Default.FullscreenExit, "Exit fullscreen", tint = palette.accent)
                }
            }
            CompactTransportBar(
                playback = playback,
                onTogglePlay = { vm.playbackController.togglePlayPause() },
                onPrev = { vm.playbackController.previous() },
                onNext = { vm.playbackController.next() },
                onSeek = { vm.playbackController.seekTo(it) },
                onPickAudio = onPickAudio,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(palette.bg.copy(alpha = 0.86f))
            )
        }
        return
    }

    if (isLandscape) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(previewShare).fillMaxHeight()) {
                PreviewBlock(
                    vm = vm,
                    uiState = uiState,
                    sceneSnapshot = sceneSnapshot,
                    targetFps = settings.previewFps,
                    continuousRendering = playback.isPlaying || settings.pauseBehavior != PauseBehavior.FREEZE,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                CompactTransportBar(
                    playback = playback,
                    onTogglePlay = { vm.playbackController.togglePlayPause() },
                    onPrev = { vm.playbackController.previous() },
                    onNext = { vm.playbackController.next() },
                    onSeek = { vm.playbackController.seekTo(it) },
                    onPickAudio = onPickAudio,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(palette.border)
            )
            StudioWorkspacePanel(
                vm = vm,
                uiState = uiState,
                settings = settings,
                onPickImage = onPickImage,
                onImportPreset = onImportPreset,
                onExportPreset = onExportPreset,
                modifier = Modifier.weight(1f - previewShare).fillMaxHeight()
            )
        }
    } else if (settings.portraitOverlay && uiState.project.canvas.aspectRatio.heightRatio > uiState.project.canvas.aspectRatio.widthRatio) {
        Box(Modifier.fillMaxSize()) {
            PreviewBlock(vm, uiState, sceneSnapshot, settings.previewFps,
                playback.isPlaying || settings.pauseBehavior != PauseBehavior.FREEZE,
                modifier = Modifier.fillMaxSize())
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(1f - previewShare).background(palette.glassPanel, palette.shape).border(1.dp, palette.border, palette.shape)) {
                CompactTransportBar(playback, { vm.playbackController.togglePlayPause() }, { vm.playbackController.previous() }, { vm.playbackController.next() }, { vm.playbackController.seekTo(it) }, onPickAudio, Modifier.fillMaxWidth())
                StudioWorkspacePanel(vm, uiState, settings, onPickImage, onImportPreset, onExportPreset, Modifier.weight(1f).fillMaxWidth())
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            PreviewBlock(
                vm = vm,
                uiState = uiState,
                sceneSnapshot = sceneSnapshot,
                targetFps = settings.previewFps,
                continuousRendering = playback.isPlaying || settings.pauseBehavior != PauseBehavior.FREEZE,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(previewShare)
                    .heightIn(min = 120.dp)
            )
            CompactTransportBar(
                playback = playback,
                onTogglePlay = { vm.playbackController.togglePlayPause() },
                onPrev = { vm.playbackController.previous() },
                onNext = { vm.playbackController.next() },
                onSeek = { vm.playbackController.seekTo(it) },
                onPickAudio = onPickAudio,
                modifier = Modifier.fillMaxWidth()
            )
            StudioWorkspacePanel(
                vm = vm,
                uiState = uiState,
                settings = settings,
                onPickImage = onPickImage,
                onImportPreset = onImportPreset,
                onExportPreset = onExportPreset,
                modifier = Modifier.weight(1f - previewShare).fillMaxWidth()
            )
        }
    }
}

@UnstableApi
@Composable
private fun PreviewBlock(
    vm: StudioViewModel,
    uiState: StudioUiState,
    sceneSnapshot: StateFlow<RenderSceneSnapshot?>,
    targetFps: Int,
    continuousRendering: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = LocalVexoPalette.current
    Box(modifier = modifier.background(Color.Black)) {
        VexoPreviewSurface(
            aspectRatio = uiState.project.canvas.aspectRatio,
            sceneSnapshot = sceneSnapshot.collectAsState().value,
            onTransformGesture = vm::updatePreviewTransform,
            modifier = Modifier.fillMaxSize(),
            targetFps = targetFps,
            continuousRendering = continuousRendering
        )
        // Compact aspect ratio + preview tools overlay bar
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(start = 78.dp, top = 6.dp, end = 6.dp)
                .horizontalScroll(rememberScrollState())
                .background(palette.glassPanel, palette.shape)
                .border(1.dp, palette.border, palette.shape),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AspectRatioPreset.entries.forEach { ratio ->
                val active = ratio == uiState.project.canvas.aspectRatio
                Text(
                    text = ratio.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) palette.accentOn else palette.textSecondary,
                    modifier = Modifier
                        .background(if (active) palette.accent else Color.Transparent)
                        .clickable { vm.setAspectRatio(ratio) }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
            IconButton(onClick = vm::toggleSafeAreaGuides, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.GridOn,
                    "Safe area",
                    tint = if (uiState.project.canvas.showSafeAreaGuides) palette.accent else palette.textSecondary,
                    modifier = Modifier.size(15.dp)
                )
            }
            IconButton(onClick = vm::resetPreviewCamera, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.CropFree, "Reset zoom", tint = palette.textSecondary, modifier = Modifier.size(15.dp))
            }
            IconButton(onClick = vm::toggleFullscreenPreview, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Fullscreen, "Fullscreen", tint = palette.textPrimary, modifier = Modifier.size(16.dp))
            }
        }

        // Undo / Redo mini strip
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .background(palette.glassPanel, palette.shape)
                .border(1.dp, palette.border, palette.shape)
        ) {
            IconButton(onClick = vm::undo, enabled = uiState.canUndo, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.Undo,
                    "Undo",
                    tint = if (uiState.canUndo) palette.textPrimary else palette.textSecondary.copy(alpha = 0.35f),
                    modifier = Modifier.size(15.dp)
                )
            }
            IconButton(onClick = vm::redo, enabled = uiState.canRedo, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.Redo,
                    "Redo",
                    tint = if (uiState.canRedo) palette.textPrimary else palette.textSecondary.copy(alpha = 0.35f),
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
fun CompactTransportBar(
    playback: PlaybackUiState,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onPickAudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalVexoPalette.current
    Column(
        modifier = modifier
            .background(palette.glassPanel, palette.shape)
            .border(1.dp, palette.border, palette.shape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (playback.queue.isEmpty()) "Import music to begin" else playback.trackMeta.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (playback.queue.isEmpty()) "Choose an audio file" else "${playback.trackMeta.artist} · ${formatDuration(playback.positionMs)} / ${formatDuration(playback.durationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPickAudio, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.FolderOpen, "Import audio", tint = palette.accent, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onPrev, enabled = playback.queue.isNotEmpty(), modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.SkipPrevious, "Previous", tint = palette.textPrimary, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onTogglePlay, enabled = playback.queue.isNotEmpty(), modifier = Modifier.size(48.dp).background(palette.accent, palette.shape)) {
                    Icon(
                        if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (playback.isPlaying) "Pause" else "Play",
                        tint = palette.accentOn,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = onNext, enabled = playback.queue.isNotEmpty(), modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.SkipNext, "Next", tint = palette.textPrimary, modifier = Modifier.size(20.dp))
                }
            }
        }
        val maxDur = playback.durationMs.coerceAtLeast(1L).toFloat()
        var scrubPosition by remember { mutableStateOf<Float?>(null) }
        LaunchedEffect(playback.currentIndex) { scrubPosition = null }
        val timelinePosition = (scrubPosition ?: playback.positionMs.toFloat()).coerceIn(0f, maxDur)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(timelinePosition.toLong()), style = MaterialTheme.typography.labelSmall, color = palette.accent)
            Text(formatDuration(playback.durationMs), style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        }
        Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            for (i in 0..20) {
                val x = size.width * i / 20f
                drawLine(palette.border, Offset(x, 0f), Offset(x, if (i % 5 == 0) size.height else size.height * 0.5f), strokeWidth = 1.dp.toPx())
            }
        }
        Slider(
            value = timelinePosition,
            enabled = playback.durationMs > 0L,
            onValueChange = { scrubPosition = it },
            onValueChangeFinished = {
                scrubPosition?.let { onSeek(it.toLong()) }
                scrubPosition = null
            },
            valueRange = 0f..maxDur,
            colors = SliderDefaults.colors(
                thumbColor = palette.accent,
                activeTrackColor = palette.accent,
                inactiveTrackColor = palette.border
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
        )
    }
}

@UnstableApi
@Composable
private fun StudioWorkspacePanel(
    vm: StudioViewModel,
    uiState: StudioUiState,
    settings: AppSettings,
    onPickImage: () -> Unit,
    onImportPreset: () -> Unit,
    onExportPreset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalVexoPalette.current
    Column(modifier = modifier.padding(horizontal = 6.dp, vertical = 4.dp).background(palette.glassPanel, palette.shape).border(1.dp, palette.border, palette.shape)) {
        // Editor Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.glassPanel, palette.shape)
                .border(1.dp, palette.border, palette.shape)
        ) {
            StudioTab.entries.forEach { tab ->
                val active = tab == uiState.studioTab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(2.dp)
                        .background(if (active) palette.accent else Color.Transparent, palette.shape)
                        .clickable { vm.selectStudioTab(tab) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        val glyph = when(tab) { StudioTab.LAYERS -> Icons.Default.Layers; StudioTab.PROPERTIES -> Icons.Default.Tune; StudioTab.ASSETS -> Icons.Default.Image; StudioTab.PRESETS -> Icons.Default.Dashboard }
                        Icon(glyph, contentDescription = null, tint = if(active) palette.accentOn else palette.textSecondary, modifier = Modifier.size(18.dp))
                        Text(tab.label, style = MaterialTheme.typography.labelSmall, color = if(active) palette.accentOn else palette.textSecondary, maxLines = 1)
                    }
                }
            }
        }

        when (uiState.studioTab) {
            StudioTab.LAYERS -> LayersTab(vm = vm, uiState = uiState)
            StudioTab.PROPERTIES -> PropertiesTab(vm = vm, uiState = uiState, settings = settings)
            StudioTab.ASSETS -> AssetsTab(vm = vm, uiState = uiState, onPickImage = onPickImage)
            StudioTab.PRESETS -> PresetsTab(vm = vm, onImportPreset = onImportPreset, onExportPreset = onExportPreset)
        }
    }
}

@UnstableApi
@Composable
private fun LayersTab(
    vm: StudioViewModel,
    uiState: StudioUiState
) {
    val palette = LocalVexoPalette.current
    var showAddLayerDialog by remember { mutableStateOf(false) }
    var renameLayerTarget by remember { mutableStateOf<LayerNode?>(null) }
    var deleteLayerTarget by remember { mutableStateOf<LayerNode?>(null) }

    val activeComp = uiState.project.compositions.firstOrNull { it.index == uiState.selectedCompositionIndex }
        ?: uiState.project.compositions.first()

    Column(modifier = Modifier.fillMaxSize()) {
        var compositionMenu by remember { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth().clickable { compositionMenu = true }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(activeComp.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    Icon(Icons.Default.ExpandMore, "Select composition", tint = palette.textSecondary)
                }
                DropdownMenu(expanded = compositionMenu, onDismissRequest = { compositionMenu = false }) {
                    uiState.project.compositions.forEach { comp -> DropdownMenuItem(text = { Text(comp.name) }, onClick = { vm.selectComposition(comp.index); compositionMenu = false }) }
                    DropdownMenuItem(text = { Text("New composition") }, onClick = { vm.addComposition(); compositionMenu = false })
                }
            }
            TextButton(onClick = { showAddLayerDialog = true }) { Icon(Icons.Default.Add, null, tint = palette.accent); Text("Layer", color = palette.accent) }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(activeComp.layers, key = { it.common.id }) { layer ->
                val isSelected = layer.common.id == uiState.selectedLayerId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, if (isSelected) palette.accent else palette.border, palette.shape)
                        .background(if (isSelected) palette.panelElevated else palette.panel)
                        .clickable { vm.selectLayer(layer.common.id); vm.selectStudioTab(StudioTab.PROPERTIES) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            vm.updateLayer(layer.common.id) {
                                it.copy(common = it.common.copy(visible = !it.common.visible))
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            if (layer.common.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            "Toggle visibility",
                            tint = if (layer.common.visible) palette.accent else palette.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 6.dp)) {
                        Text(
                            text = layer.common.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = layer.type.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary,
                            maxLines = 1
                        )
                    }
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.MoreVert, "Layer actions", tint = palette.textSecondary) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(if(layer.common.locked) "Unlock" else "Lock") }, onClick = { menu = false; vm.updateLayer(layer.common.id) { it.copy(common = it.common.copy(locked = !it.common.locked)) } })
                            DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; vm.moveLayer(layer.common.id, -1) })
                            DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; vm.moveLayer(layer.common.id, 1) })
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renameLayerTarget = layer })
                            DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; vm.duplicateLayer(layer.common.id) })
                            DropdownMenuItem(text = { Text("Delete", color = palette.danger) }, onClick = { menu = false; deleteLayerTarget = layer })
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showAddLayerDialog) {
        AlertDialog(
            onDismissRequest = { showAddLayerDialog = false },
            title = { Text("ADD LAYER OR EFFECT", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LayerType.entries.forEach { type ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, palette.border, palette.shape)
                                .background(palette.glassPanel, palette.shape)
                                .clickable {
                                    vm.addLayer(type)
                                    vm.selectStudioTab(StudioTab.PROPERTIES)
                                    showAddLayerDialog = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(type.label.uppercase(), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddLayerDialog = false }) { Text("CLOSE") }
            }
        )
    }

    renameLayerTarget?.let { target ->
        var text by remember { mutableStateOf(target.common.name) }
        AlertDialog(
            onDismissRequest = { renameLayerTarget = null },
            title = { Text("RENAME LAYER") },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isNotBlank()) {
                        vm.updateLayer(target.common.id) {
                            it.copy(common = it.common.copy(name = text.trim()))
                        }
                    }
                    renameLayerTarget = null
                }) { Text("SAVE") }
            },
            dismissButton = {
                TextButton(onClick = { renameLayerTarget = null }) { Text("CANCEL") }
            }
        )
    }

    deleteLayerTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteLayerTarget = null },
            title = { Text("DELETE LAYER?") },
            text = { Text("Remove \"${target.common.name}\" from ${activeComp.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteLayer(target.common.id)
                    deleteLayerTarget = null
                }) { Text("DELETE", color = palette.danger) }
            },
            dismissButton = {
                TextButton(onClick = { deleteLayerTarget = null }) { Text("CANCEL") }
            }
        )
    }
}

@UnstableApi
@Composable
private fun PropertiesTab(
    vm: StudioViewModel,
    uiState: StudioUiState,
    settings: AppSettings
) {
    val palette = LocalVexoPalette.current
    var editTarget by rememberSaveable(uiState.selectedLayerId) { mutableStateOf("LAYER") } // LAYER or PROVIDER
    val expandedSections = remember(uiState.selectedLayerId, uiState.selectedProviderIndex, editTarget) { mutableStateMapOf<String, Boolean>() }
    var undoAnchor by remember { mutableStateOf<VexoProject?>(null) }

    fun beginDrag() {
        if (undoAnchor == null) undoAnchor = uiState.project
    }

    fun endDrag() {
        undoAnchor?.let { vm.commitUndoSnapshot(it) }
        undoAnchor = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("LAYER", "PROVIDER").forEach { mode ->
                val active = editTarget == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, if (active) palette.accent else palette.border, palette.shape)
                        .background(if (active) palette.accent else palette.glassPanel, palette.shape)
                        .clickable { editTarget = mode }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (mode == "LAYER") "SELECTED LAYER" else "AUDIO PROVIDER",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) palette.accentOn else palette.textPrimary
                    )
                }
            }
        }

        if (editTarget == "PROVIDER") {
            AudioProviderEditorSection(
                vm = vm,
                uiState = uiState,
                settings = settings,
                expandedSections = expandedSections,
                onBeginDrag = ::beginDrag,
                onEndDrag = ::endDrag
            )
            return@Column
        }

        val layer = uiState.project.compositions.firstOrNull { it.index == uiState.selectedCompositionIndex }?.layers?.firstOrNull { it.common.id == uiState.selectedLayerId }
        if (layer == null) {
            Text(
                "Select or add a layer in the Layers tab to edit properties.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary,
                modifier = Modifier.padding(16.dp)
            )
            return@Column
        }

        if (layer.common.locked) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, palette.danger)
                    .background(palette.glassPanel, palette.shape)
                    .padding(10.dp)
            ) {
                Text("LAYER LOCKED — Unlock in Layers tab to edit.", style = MaterialTheme.typography.labelSmall, color = palette.danger)
            }
            return@Column
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(layer.common.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(layer.type.label, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
            }
            TextButton(onClick = { vm.selectStudioTab(StudioTab.LAYERS) }) { Text("Layers") }
        }
        // Section 1: Transform & Blend
        ExpandableSectionHeader(
            title = "General & Transform",
            expanded = expandedSections["transform"] ?: true,
            onToggle = { toggleAccordion(expandedSections, "transform") }
        ) {
            StudioToggleRow(
                label = "Visible",
                checked = layer.common.visible,
                onCheckedChange = { vis ->
                    vm.updateLayer(layer.common.id) { it.copy(common = it.common.copy(visible = vis)) }
                }
            )
            StudioChipSelector(
                label = "Blend Mode",
                options = BlendMode.entries,
                selected = layer.common.blendMode,
                optionLabel = { it.label },
                onSelect = { bm ->
                    vm.updateLayer(layer.common.id) { it.copy(common = it.common.copy(blendMode = bm)) }
                }
            )
            RegisteredSlider("opacity", layer.common.opacity, settings.showPropertyHelpHints, { v ->
                beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(common = it.common.copy(opacity = v)) }
            }, ::endDrag)
            RegisteredSlider("posX", layer.common.posX, settings.showPropertyHelpHints, { v ->
                beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(common = it.common.copy(posX = v)) }
            }, ::endDrag)
            RegisteredSlider("posY", layer.common.posY, settings.showPropertyHelpHints, { v ->
                beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(common = it.common.copy(posY = v)) }
            }, ::endDrag)
            StudioToggleRow("Uniform Scale", layer.common.scaleIsUniform) { v -> vm.updateLayer(layer.common.id) { it.copy(common = it.common.copy(scaleIsUniform = v)) } }
            StudioChipSelector("Anchor X", com.vexo.studio.model.AnchorMode.entries, layer.common.anchorX, { it.name }) { v -> vm.updateLayer(layer.common.id) { it.copy(common=it.common.copy(anchorX=v)) } }
            StudioChipSelector("Anchor Y", com.vexo.studio.model.AnchorMode.entries, layer.common.anchorY, { it.name }) { v -> vm.updateLayer(layer.common.id) { it.copy(common=it.common.copy(anchorY=v)) } }
            StudioSlider("Pivot X", layer.common.alignmentX, 0f, 1f, 0.5f, "", 0.01f, onValueChange = { v -> beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(common = it.common.copy(alignmentX = v)) } }, onCommit = ::endDrag)
            StudioSlider("Pivot Y", layer.common.alignmentY, 0f, 1f, 0.5f, "", 0.01f, onValueChange = { v -> beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(common = it.common.copy(alignmentY = v)) } }, onCommit = ::endDrag)
            RegisteredSlider("scaleX", layer.common.scaleX, settings.showPropertyHelpHints, { v ->
                beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(common = it.common.copy(scaleX = v)) }
            }, ::endDrag)
            RegisteredSlider("scaleY", layer.common.scaleY, settings.showPropertyHelpHints, { v ->
                beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(common = it.common.copy(scaleY = v)) }
            }, ::endDrag)
            RegisteredSlider("rotationDeg", layer.common.rotationDeg, settings.showPropertyHelpHints, { v ->
                beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(common = it.common.copy(rotationDeg = v)) }
            }, ::endDrag)
        }

        // Section 2: Audio-Reactive Measures
        ExpandableSectionHeader(
            title = "Audio reactive",
            expanded = expandedSections["measures"] ?: false,
            onToggle = { toggleAccordion(expandedSections, "measures") }
        ) {
            StudioChipSelector(
                label = "Measure Scale",
                options = MeasureWhat.entries.filter { it != MeasureWhat.KEYFRAME },
                selected = layer.common.measureScale.measureWhat,
                optionLabel = { it.label },
                onSelect = { mw ->
                    vm.updateLayer(layer.common.id) {
                        it.copy(common = it.common.copy(measureScale = it.common.measureScale.copy(measureWhat = mw)))
                    }
                }
            )
            StudioSlider(
                label = "Scale Amount X",
                value = layer.common.measureScale.amountA,
                min = -1f, max = 2f, defaultValue = 0f, step = 0.02f,
                onValueChange = { v ->
                    beginDrag()
                    vm.updateLayer(layer.common.id, false) {
                        it.copy(common = it.common.copy(measureScale = it.common.measureScale.copy(amountA = v)))
                    }
                },
                onCommit = ::endDrag
            )
            StudioSlider("Scale Trigger Threshold", layer.common.measureScale.triggerThreshold, 0f, 0.99f, 0f, "", 0.01f, onValueChange = { v -> beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(common=it.common.copy(measureScale=it.common.measureScale.copy(triggerThreshold=v))) } }, onCommit=::endDrag)
            StudioSlider("Scale Amount Y", layer.common.measureScale.amountB, -1f, 2f, 0f, "", 0.01f, onValueChange = { v -> beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(common = it.common.copy(measureScale = it.common.measureScale.copy(amountB = v))) } }, onCommit = ::endDrag)
            StudioChipSelector(
                label = "Measure Position",
                options = MeasureWhat.entries.filter { it != MeasureWhat.KEYFRAME },
                selected = layer.common.measurePos.measureWhat,
                optionLabel = { it.label },
                onSelect = { mw ->
                    vm.updateLayer(layer.common.id) {
                        it.copy(common = it.common.copy(measurePos = it.common.measurePos.copy(measureWhat = mw)))
                    }
                }
            )
            StudioSlider(
                label = "Position Amount X",
                value = layer.common.measurePos.amountA,
                min = 0f, max = 0.5f, defaultValue = 0f, step = 0.01f,
                onValueChange = { v ->
                    beginDrag()
                    vm.updateLayer(layer.common.id, false) {
                        it.copy(common = it.common.copy(measurePos = it.common.measurePos.copy(amountA = v)))
                    }
                },
                onCommit = ::endDrag
            )
            StudioSlider("Position Trigger Threshold", layer.common.measurePos.triggerThreshold, 0f, 0.99f, 0f, "", 0.01f, onValueChange = { v -> beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(common=it.common.copy(measurePos=it.common.measurePos.copy(triggerThreshold=v))) } }, onCommit=::endDrag)
            StudioSlider("Position Amount Y", layer.common.measurePos.amountB, -0.5f, 0.5f, 0f, "", 0.01f, onValueChange = { v -> beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(common = it.common.copy(measurePos = it.common.measurePos.copy(amountB = v))) } }, onCommit = ::endDrag)
            StudioChipSelector(
                label = "Measure Rotation",
                options = MeasureWhat.entries.filter { it != MeasureWhat.KEYFRAME },
                selected = layer.common.measureRot.measureWhat,
                optionLabel = { it.label },
                onSelect = { mw ->
                    vm.updateLayer(layer.common.id) {
                        it.copy(common = it.common.copy(measureRot = it.common.measureRot.copy(measureWhat = mw)))
                    }
                }
            )
            StudioSlider(
                label = "Rot Amount",
                value = layer.common.measureRot.amountA,
                min = -5f, max = 5f, defaultValue = 0f, step = 0.05f,
                onValueChange = { v ->
                    beginDrag()
                    vm.updateLayer(layer.common.id, false) {
                        it.copy(common = it.common.copy(measureRot = it.common.measureRot.copy(amountA = v, amountB = v)))
                    }
                },
                onCommit = ::endDrag
            )
        }

        ExpandableSectionHeader("Advanced audio bindings", expandedSections["bindings"] == true, { toggleAccordion(expandedSections, "bindings") }) {
            val ids = uiState.project.audioProviders.map { it.index }
            StudioChipSelector("Position Provider", ids, layer.common.measurePos.providerIndex, { "#$it" }) { v -> vm.updateLayer(layer.common.id) { it.copy(common=it.common.copy(measurePos=it.common.measurePos.copy(providerIndex=v))) } }
            StudioChipSelector("Scale Provider", ids, layer.common.measureScale.providerIndex, { "#$it" }) { v -> vm.updateLayer(layer.common.id) { it.copy(common=it.common.copy(measureScale=it.common.measureScale.copy(providerIndex=v))) } }
            StudioChipSelector("Rotation Provider", ids, layer.common.measureRot.providerIndex, { "#$it" }) { v -> vm.updateLayer(layer.common.id) { it.copy(common=it.common.copy(measureRot=it.common.measureRot.copy(providerIndex=v))) } }
            StudioSlider("Rotation Threshold", layer.common.measureRot.triggerThreshold, 0f, .99f, 0f, "", .01f, onValueChange={v -> beginDrag(); vm.updateLayer(layer.common.id,false){it.copy(common=it.common.copy(measureRot=it.common.measureRot.copy(triggerThreshold=v))) }}, onCommit=::endDrag)
            StudioChipSelector("Opacity Trigger", MeasureWhat.entries.filter { it != MeasureWhat.KEYFRAME }, layer.common.measureOpacity.measureWhat, { it.label }) { v -> vm.updateLayer(layer.common.id) { it.copy(common=it.common.copy(measureOpacity=it.common.measureOpacity.copy(measureWhat=v))) } }
            StudioChipSelector("Opacity Provider", ids, layer.common.measureOpacity.providerIndex, { "#$it" }) { v -> vm.updateLayer(layer.common.id) { it.copy(common=it.common.copy(measureOpacity=it.common.measureOpacity.copy(providerIndex=v))) } }
            StudioSlider("Opacity Amount", layer.common.measureOpacity.amountA, -1f, 1f, 0f, "", .01f, onValueChange={v -> beginDrag(); vm.updateLayer(layer.common.id,false){it.copy(common=it.common.copy(measureOpacity=it.common.measureOpacity.copy(amountA=v,amountB=v))) }}, onCommit=::endDrag)
            StudioSlider("Opacity Threshold", layer.common.measureOpacity.triggerThreshold, 0f, .99f, 0f, "", .01f, onValueChange={v -> beginDrag(); vm.updateLayer(layer.common.id,false){it.copy(common=it.common.copy(measureOpacity=it.common.measureOpacity.copy(triggerThreshold=v))) }}, onCommit=::endDrag)
        }

        when (val cfg = layer.config) {
            is LayerConfig.Bars -> {
                ExpandableSectionHeader("Path & geometry", expandedSections["Bars_Pathgeometry"] == true, { toggleAccordion(expandedSections, "Bars_Pathgeometry") }) {
                    StudioSlider("Polygon Sides", cfg.polygonSides.toFloat(), 3f, 12f, 6f, "", 1f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(polygonSides = v.toInt())) }
                    }, onCommit = ::endDrag)
                    StudioSlider("Path Angle", cfg.pathAngleDeg, -360f, 360f, 0f, "deg", 1f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(pathAngleDeg = v)) }
                    }, onCommit = ::endDrag)
                    StudioChipSelector("Path Shape", BarsShapePath.entries, cfg.shapePath, { it.label }) { path ->
                        vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(shapePath = path)) }
                    }
                    StudioSlider("Circular Path Gap", cfg.pathGap, 0f, 0.9f, 0f, "", 0.01f, onValueChange = { v -> beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(pathGap = v)) } }, onCommit = ::endDrag)
                    if (cfg.shapePath == BarsShapePath.CIRCLE || cfg.shapePath == BarsShapePath.SIDED_POLYGON) {
                        RegisteredSlider("circleRadius", cfg.circleRadius, settings.showPropertyHelpHints, { v ->
                            beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(circleRadius = v)) }
                        }, ::endDrag)
                    }
                }
                ExpandableSectionHeader("Primary segment", expandedSections["Bars_Primarysegment"] == true, { toggleAccordion(expandedSections, "Bars_Primarysegment") }) {
                    StudioSlider("Bar Height Multiplier", cfg.barHeightMultiplier, -2f, 2f, 1f, "x", 0.05f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(barHeightMultiplier = v)) }
                    }, onCommit = ::endDrag)
                    StudioSlider("Fixed Height", cfg.fixedHeight, -50f, 50f, 0f, "", 0.5f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(fixedHeight = v)) }
                    }, onCommit = ::endDrag)
                    StudioChipSelector("Segment Style", BarsSegmentType.entries, cfg.segment1, { it.label }) { seg ->
                        vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(segment1 = seg)) }
                    }
                    StudioToggleRow("Mirror Symmetry", cfg.mirror) { m ->
                        vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(mirror = m)) }
                    }
                    StudioToggleRow("Flip Input", cfg.flipInput) { f ->
                        vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(flipInput = f)) }
                    }
                    StudioToggleRow("Alternating Dir", cfg.alternatingDirection) { a ->
                        vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(alternatingDirection = a)) }
                    }
                    StudioSlider("Bar Count (0 = Provider)", cfg.barCount.toFloat(), 0f, 512f, 0f, "", 1f, onValueChange={v -> beginDrag(); vm.updateLayer(layer.common.id,false){it.copy(config=(it.config as LayerConfig.Bars).copy(barCount=v.toInt()))}}, onCommit=::endDrag)
                    StudioSlider("Bar Spacing", cfg.barSpacing, 0f, .95f, 0f, "", .01f, onValueChange={v -> beginDrag(); vm.updateLayer(layer.common.id,false){it.copy(config=(it.config as LayerConfig.Bars).copy(barSpacing=v))}}, onCommit=::endDrag)
                    if (cfg.segment1 == BarsSegmentType.ROUND_BARS) StudioSlider("Roundness", cfg.roundness, 0f, 1f, 1f, "", .01f, onValueChange={v -> beginDrag(); vm.updateLayer(layer.common.id,false){it.copy(config=(it.config as LayerConfig.Bars).copy(roundness=v))}}, onCommit=::endDrag)
                    StudioSlider("Spectrum Softness",cfg.softness.toFloat(),0f,20f,2f,"",1f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){it.copy(config=(it.config as LayerConfig.Bars).copy(softness=v.toInt()))}},onCommit=::endDrag)
                    RegisteredSlider("barWidth", cfg.barWidth, settings.showPropertyHelpHints, { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(barWidth = v)) }
                    }, ::endDrag)
                    RegisteredSlider("heightScale", cfg.heightScale, settings.showPropertyHelpHints, { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(heightScale = v)) }
                    }, ::endDrag)
                    RegisteredSlider("minHeightScale", cfg.minHeightScale, settings.showPropertyHelpHints, { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(minHeightScale = v)) }
                    }, ::endDrag)
                    RegisteredSlider("maxHeightScale", cfg.maxHeightScale, settings.showPropertyHelpHints, { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(maxHeightScale = v)) }
                    }, ::endDrag)
                }
                ExpandableSectionHeader("Secondary segment", expandedSections["Bars_Secondarysegment"] == true, { toggleAccordion(expandedSections, "Bars_Secondarysegment") }) {
                    StudioChipSelector("Secondary Segment", BarsSegmentType.entries, cfg.segment2, { it.label }) { v -> vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(segment2 = v)) } }
                    if (cfg.segment2 != BarsSegmentType.NONE) {
                    StudioSlider("Segment 2 Width", cfg.segment2Width, 0.05f, 1.5f, 0.72f, "", 0.01f, onValueChange = { v -> beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(segment2Width = v)) } }, onCommit = ::endDrag)
                    StudioSlider("Segment 2 Height", cfg.segment2HeightMultiplier, -2f, 2f, 1f, "", 0.05f, onValueChange = { v -> beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(segment2HeightMultiplier = v)) } }, onCommit = ::endDrag)
                    StudioSlider("Segment 2 Fixed Height", cfg.segment2FixedHeight, -50f, 50f, 0f, "", 0.5f, onValueChange = { v -> beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(segment2FixedHeight = v)) } }, onCommit = ::endDrag)
                        StudioToggleRow("Segment 2 Mirror", cfg.segment2Mirror) { v -> vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(segment2Mirror = v)) } }
                        StudioColorPickerRow("Segment 2 Color From", cfg.segment2ColorFrom) { v -> vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(segment2ColorFrom = v)) } }
                        StudioColorPickerRow("Segment 2 Color To", cfg.segment2ColorTo) { v -> vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(segment2ColorTo = v)) } }
                    }
                }
                ExpandableSectionHeader("Colors", expandedSections["Bars_Colors"] == true, { toggleAccordion(expandedSections, "Bars_Colors") }) {
                    StudioSlider("Gradient Offset", cfg.colorBlendOffset, 0f, 1f, 0f, "", 0.01f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(colorBlendOffset = v)) }
                    }, onCommit = ::endDrag)
                    StudioColorPickerRow("Color From", cfg.colorFromArgb) { col ->
                        vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(colorFromArgb = col)) }
                    }
                    StudioColorPickerRow("Color To", cfg.colorToArgb) { col ->
                        vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(colorToArgb = col)) }
                    }
                }
                ExpandableSectionHeader("Audio response", expandedSections["Bars_Audioresponse"] == true, { toggleAccordion(expandedSections, "Bars_Audioresponse") }) {
                    StudioChipSelector("Audio Provider", uiState.project.audioProviders.map { it.index }, cfg.audioProviderIndex, { "Provider $it" }) { v -> vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(audioProviderIndex = v)) } }
                    StudioSlider("Reaction Delay", cfg.reactionDelay.toFloat(), 0f, 9f, 0f, "frames", 1f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(reactionDelay = v.toInt())) }
                    }, onCommit = ::endDrag)
                }
            }
            is LayerConfig.Image -> {
                ExpandableSectionHeader("Source & fitting", expandedSections["Image_Sourcefitting"] == true, { toggleAccordion(expandedSections, "Image_Sourcefitting") }) {
                    StudioChipSelector("Source", ImageSourceType.entries, cfg.sourceType, { it.label }) { src ->
                        vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(sourceType = src)) }
                    }
                    if (cfg.sourceType == ImageSourceType.COMPOSITION) {
                    StudioChipSelector("Source Composition", uiState.project.compositions.map { it.index }.filter { it != uiState.selectedCompositionIndex }, cfg.sourceCompositionIndex, { "Composition $it" }) { v -> vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(sourceCompositionIndex = v)) } }
                    }
                    if(cfg.sourceType == ImageSourceType.IMPORTED) {
                        StudioToggleRow("Video source",cfg.videoEnabled){v->vm.updateLayer(layer.common.id){it.copy(config=(it.config as LayerConfig.Image).copy(videoEnabled=v))}}
                        if(cfg.videoEnabled){
                            StudioToggleRow("Loop video",cfg.videoLoop){v->vm.updateLayer(layer.common.id){it.copy(config=(it.config as LayerConfig.Image).copy(videoLoop=v))}}
                            StudioSlider("Video speed",cfg.videoSpeed,.25f,4f,1f,"x",.05f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){it.copy(config=(it.config as LayerConfig.Image).copy(videoSpeed=v))}},onCommit=::endDrag)
                            StudioSlider("Video offset",cfg.videoOffsetMs/1000f,0f,60f,0f,"s",.1f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){it.copy(config=(it.config as LayerConfig.Image).copy(videoOffsetMs=(v*1000).toLong()))}},onCommit=::endDrag)
                            StudioChipSelector("Video sample FPS",listOf(6,12,15,24),cfg.videoSampleFps,{"$it FPS"}){v->vm.updateLayer(layer.common.id){it.copy(config=(it.config as LayerConfig.Image).copy(videoSampleFps=v))}}
                        }
                    }
                    StudioChipSelector("Shape Mask", ImageShape.entries, cfg.shape, { it.label }) { sh ->
                        vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(shape = sh)) }
                    }
                    StudioToggleRow("Keep Aspect Ratio", cfg.keepAspectRatio) { k ->
                        vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(keepAspectRatio = k)) }
                    }
                    StudioToggleRow("Crop To Fit", cfg.keepAspectRatioAndCropToFit) { k ->
                        vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(keepAspectRatioAndCropToFit = k)) }
                    }
                    StudioToggleRow("Flip Horizontal", cfg.flipU) { v -> vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(flipU = v)) } }
                    StudioToggleRow("Flip Vertical", cfg.flipV) { v -> vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(flipV = v)) } }
                }
                ExpandableSectionHeader("Mask & chroma key", expandedSections["Image_Maskchromakey"] == true, { toggleAccordion(expandedSections, "Image_Maskchromakey") }) {
                    StudioColorPickerRow("Chroma Key Color", cfg.colorKeyArgb) { v -> vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(colorKeyArgb = v)) } }
                    val maskChoices = listOf("") + uiState.project.compositions.filter { it.index != uiState.selectedCompositionIndex }.map { "composition:${it.index}" } + listOf("internalres:white", "internalres:black", "internalres:vignette80")
                    StudioChipSelector("Mask Source", (maskChoices + cfg.maskImage).distinct(), cfg.maskImage, { it.ifBlank { "None" } }) { v -> vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(maskImage = v)) } }
                    StudioChipSelector("Mask Mode", com.vexo.studio.model.MaskMode.entries, cfg.maskMode, { it.label }) { v -> vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(maskMode = v)) } }
                    if (cfg.colorKeyEnabled) StudioSlider("Key Strength", cfg.transparencyStrength, 0f, 1f, 1f, "", 0.01f, onValueChange = { v -> beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(transparencyStrength = v)) } }, onCommit = ::endDrag)
                    StudioToggleRow("Color Key Chroma", cfg.colorKeyEnabled) { k ->
                        vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(colorKeyEnabled = k)) }
                    }
                }
                ExpandableSectionHeader("Color & opacity", expandedSections["Image_Coloropacity"] == true, { toggleAccordion(expandedSections, "Image_Coloropacity") }) {
                    StudioSlider("Image Opacity Strength", cfg.opacityStrength, 0f, 1f, 1f, "x", 0.01f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(opacityStrength = v)) }
                    }, onCommit = ::endDrag)
                    StudioSlider("Saturation", cfg.saturation, 0f, 2f, 1f, "", 0.02f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(saturation = v)) }
                    }, onCommit = ::endDrag)
                    StudioColorPickerRow("Secondary Tint", cfg.colorToArgb) { col ->
                        vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(colorToArgb = col)) }
                    }
                    StudioColorPickerRow("Tint Color", cfg.colorArgb) { col ->
                        vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(colorArgb = col)) }
                    }
                }
                ExpandableSectionHeader("Blur", expandedSections["Image_Blur"] == true, { toggleAccordion(expandedSections, "Image_Blur") }) {
                    StudioToggleRow("Image Blur", cfg.blurEnabled) { v -> vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(blurEnabled = v)) } }
                    if (cfg.blurEnabled) {
                        StudioSlider("Image Blur Radius", cfg.blurRadius.toFloat(), 0f, 64f, 7f, "px", 1f, onValueChange = { v -> beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(blurRadius = v.toInt())) } }, onCommit = ::endDrag)
                        StudioSlider("Blur Divider", cfg.blurDivider.toFloat(), 1f, 12f, 5f, "", 1f, onValueChange = { v -> beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(blurDivider = v.toInt())) } }, onCommit = ::endDrag)
                    }
                }
            }
            is LayerConfig.Text -> {
                ExpandableSectionHeader("Content & typography", expandedSections["Text_Contenttypography"] == true, { toggleAccordion(expandedSections, "Text_Contenttypography") }) {
                    OutlinedTextField(
                        value = cfg.text,
                        onValueChange = { text -> vm.updateLayer(layer.common.id) { l -> l.copy(config = (l.config as LayerConfig.Text).copy(text = text)) } },
                        label = { Text("TEXT ({Title}, {Artist}, {Album}, {Time})") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    RegisteredSlider("fontSize", cfg.fontSize.toFloat(), settings.showPropertyHelpHints, { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(fontSize = v.toInt())) }
                    }, ::endDrag)
                    StudioToggleRow("Bold", cfg.bold) { b ->
                        vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(bold = b)) }
                    }
                    StudioToggleRow("Italic", cfg.italic) { i ->
                        vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(italic = i)) }
                    }
                    val fontPicker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
                        if(uri!=null) vm.attachFont(layer.common.id,uri)
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                        TextButton(onClick={fontPicker.launch(arrayOf("font/*","application/octet-stream","application/x-font-ttf","application/x-font-opentype"))}){Text("IMPORT FONT")}
                        if(cfg.fontAssetUri!=null) TextButton(onClick={vm.updateLayer(layer.common.id){it.copy(config=(it.config as LayerConfig.Text).copy(fontAssetUri=null))}}){Text("RESET FONT")}
                    }
                    StudioChipSelector("Text Alignment", listOf(-1, 0, 1), cfg.alignment, { when(it) { -1 -> "Left"; 1 -> "Right"; else -> "Center" } }) { v -> vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(alignment = v)) } }
                    StudioSlider("Letter Spacing", cfg.letterSpacing, 0f, 0.5f, 0.04f, "", 0.01f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(letterSpacing = v)) }
                    }, onCommit = ::endDrag)
                    StudioSlider("Line Spacing", cfg.lineSpacing, 0.8f, 2.5f, 1.2f, "x", 0.05f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(lineSpacing = v)) }
                    }, onCommit = ::endDrag)
                }
                ExpandableSectionHeader("Color & effects", expandedSections["Text_Coloreffects"] == true, { toggleAccordion(expandedSections, "Text_Coloreffects") }) {
                    StudioSlider("Stroke Width", cfg.strokeWidth, 0f, 12f, 0f, "px", 0.5f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(strokeWidth = v)) }
                    }, onCommit = ::endDrag)
                    StudioColorPickerRow("Stroke Color", cfg.strokeColorArgb) { v -> vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(strokeColorArgb = v)) } }
                    StudioSlider("Shadow Radius", cfg.shadowRadius, 0f, 24f, 4f, "px", 0.5f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(shadowRadius = v)) }
                    }, onCommit = ::endDrag)
                    StudioColorPickerRow("Text Color", cfg.colorArgb) { col ->
                        vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(colorArgb = col)) }
                    }
                }
            }
            is LayerConfig.Shape -> {
                ExpandableSectionHeader("Geometry", expandedSections["Shape_Geometry"] == true, { toggleAccordion(expandedSections, "Shape_Geometry") }) {
                    StudioChipSelector("Shape Kind", ShapeKind.entries, cfg.kind, { it.label }) { k ->
                        vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(kind = k)) }
                    }
                    StudioSlider("Sides", cfg.sides.toFloat(), 3f, 16f, 6f, "", 1f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(sides = v.toInt())) }
                    }, onCommit = ::endDrag)
                }
                ExpandableSectionHeader("Fill & stroke", expandedSections["Shape_Fillstroke"] == true, { toggleAccordion(expandedSections, "Shape_Fillstroke") }) {
                    StudioSlider("Stroke Width", cfg.strokeWidth, 1f, 24f, 3f, "px", 0.5f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(strokeWidth = v)) }
                    }, onCommit = ::endDrag)
                    StudioColorPickerRow("Stroke Color", cfg.strokeColorArgb) { col ->
                        vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(strokeColorArgb = col)) }
                    }
                    StudioColorPickerRow("Gradient End", cfg.gradientEndArgb) { v -> vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(gradientEndArgb = v)) } }
                    StudioColorPickerRow("Fill Color", cfg.fillColorArgb) { col ->
                        vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(fillColorArgb = col)) }
                    }
                }
            }
            is LayerConfig.Particles -> {
                ExpandableSectionHeader("Emission", expandedSections["Particles_Emission"] == true, { toggleAccordion(expandedSections, "Particles_Emission") }) {
                    StudioSlider("Spawn Interval", cfg.spawnTimeSec, 0.005f, 0.25f, 0.015f, "s", 0.005f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(spawnTimeSec = v)) }
                    }, onCommit = ::endDrag)
                    StudioSlider("Lifetime", cfg.lifetimeSec, 0.1f, 10f, 6f, "s", 0.1f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(lifetimeSec = v)) }
                    }, onCommit = ::endDrag)
                    StudioChipSelector("Spawn Area", SpawnAreaType.entries, cfg.spawnArea, { it.label }) { s ->
                        vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(spawnArea = s)) }
                    }
                    RegisteredSlider("countLimit", cfg.countLimit.toFloat(), settings.showPropertyHelpHints, { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(countLimit = v.toInt())) }
                    }, ::endDrag)
                }
                ExpandableSectionHeader("Motion & forces", expandedSections["Particles_Motionforces"] == true, { toggleAccordion(expandedSections, "Particles_Motionforces") }) {
                    StudioSlider("Speed Random", cfg.speedRandom, -300f, 300f, 80f, "", 1f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(speedRandom = v)) }
                    }, onCommit = ::endDrag)
                    StudioSlider("Gravity X", cfg.gravityX, -300f, 300f, 0f, "", 1f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(gravityX = v)) }
                    }, onCommit = ::endDrag)
                    StudioSlider("Gravity Y", cfg.gravityY, -300f, 300f, 0f, "", 1f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(gravityY = v)) }
                    }, onCommit = ::endDrag)
                    StudioSlider("Vector Z", cfg.vectorAngleZ, -1f, 1f, 0f, "", 0.05f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(vectorAngleZ = v)) }
                    }, onCommit = ::endDrag)
                    StudioSlider("Side Wave Frequency", cfg.sideSineWaveFreq, 0f, 10f, 0f, "", 0.1f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(sideSineWaveFreq = v)) }
                    }, onCommit = ::endDrag)
                    StudioSlider("Side Wave Magnitude", cfg.sideSineWaveMag, 0f, 10f, 0f, "", 0.1f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(sideSineWaveMag = v)) }
                    }, onCommit = ::endDrag)
                    StudioToggleRow("Random Vector", cfg.useRandomVectorInstead) { v -> vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(useRandomVectorInstead = v)) } }
                    StudioToggleRow("Vector Relative To Center", cfg.vectorRelativeToCenter) { v -> vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(vectorRelativeToCenter = v)) } }
                    StudioChipSelector("Force Field", ForceFieldType.entries, cfg.forceField, { it.label }) { f ->
                        vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(forceField = f)) }
                    }
                    RegisteredSlider("speed", cfg.speed, settings.showPropertyHelpHints, { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(speed = v)) }
                    }, ::endDrag)
                    StudioSlider("Vector Angle", cfg.vectorAngleDeg, 0f, 360f, 270f, "deg", 5f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(vectorAngleDeg = v)) }
                    }, onCommit = ::endDrag)
                }
                ExpandableSectionHeader("Size & fading", expandedSections["Particles_Sizefading"] == true, { toggleAccordion(expandedSections, "Particles_Sizefading") }) {
                    StudioSlider("Start Size", cfg.startSize, 0f, 20f, 2f, "", 0.1f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(startSize = v)) }
                    }, onCommit = ::endDrag)
                    StudioSlider("End Size", cfg.endSize, 0f, 20f, 4f, "", 0.1f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(endSize = v)) }
                    }, onCommit = ::endDrag)
                    StudioSlider("Random End Size", cfg.endSizeRandom, 0f, 20f, 0f, "", 0.1f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(endSizeRandom = v)) }
                    }, onCommit = ::endDrag)
                    StudioSlider("Fade In / Out", cfg.fadeInAndOutTime, 0.02f, 0.49f, 0.15f, "", 0.01f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(fadeInAndOutTime = v)) }
                    }, onCommit = ::endDrag)
                    StudioSlider("Particle Scale", cfg.particleScale, 0.1f, 3f, 0.65f, "x", 0.05f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(particleScale = v)) }
                    }, onCommit = ::endDrag)
                }
                ExpandableSectionHeader("Perspective", expandedSections["Particles_Perspective"] == true, { toggleAccordion(expandedSections, "Particles_Perspective") }) {
                    StudioSlider("Perspective Depth", cfg.perspectiveDepth, 100f, 2000f, 900f, "", 10f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(perspectiveDepth = v)) }
                    }, onCommit = ::endDrag)
                    StudioSlider("Perspective FOV", cfg.perspectiveFov, 20f, 150f, 48.75f, "deg", 0.5f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(perspectiveFov = v)) }
                    }, onCommit = ::endDrag)
                    StudioSlider("Near Camera Fade", cfg.nearCameraFadeOutDistance, 0f, 500f, 75f, "", 1f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(nearCameraFadeOutDistance = v)) }
                    }, onCommit = ::endDrag)
                }
                ExpandableSectionHeader("Colors", expandedSections["Particles_Colors"] == true, { toggleAccordion(expandedSections, "Particles_Colors") }) {
                    StudioColorPickerRow("Color From", cfg.colorFromArgb) { col ->
                        vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(colorFromArgb = col)) }
                    }
                    StudioColorPickerRow("Color To", cfg.colorToArgb) { col ->
                        vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(colorToArgb = col)) }
                    }
                }
            }
            is LayerConfig.BlurEffect -> {
                ExpandableSectionHeader("Source & compositing", expandedSections["BlurEffect_Sourcecompositing"] == true, { toggleAccordion(expandedSections, "BlurEffect_Sourcecompositing") }) {
                    StudioChipSelector("Source Composition", uiState.project.compositions.map { it.index }.filter { it != uiState.selectedCompositionIndex }, cfg.sourceCompositionIndex, { "Composition $it" }) { v -> vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(sourceCompositionIndex = v)) } }
                    StudioToggleRow("Show Unblurred Content", cfg.showUnblurredContent) { v -> vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(showUnblurredContent = v)) } }
                    StudioToggleRow("Show Unblurred Under", cfg.showUnblurredContentUnder) { s ->
                        vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(showUnblurredContentUnder = s)) }
                    }
                }
                ExpandableSectionHeader("Blur parameters", expandedSections["BlurEffect_Blurparameters"] == true, { toggleAccordion(expandedSections, "BlurEffect_Blurparameters") }) {
                    RegisteredSlider("blurRadius", cfg.blurRadius, settings.showPropertyHelpHints, { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(blurRadius = v)) }
                    }, ::endDrag)
                    RegisteredSlider("blurMultiplier", cfg.blurMultiplier, settings.showPropertyHelpHints, { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(blurMultiplier = v)) }
                    }, ::endDrag)
                }
            }
            is LayerConfig.RgbSplitEffect -> {
                ExpandableSectionHeader("Source & blend", expandedSections["RgbSplitEffect_Sourceblend"] == true, { toggleAccordion(expandedSections, "RgbSplitEffect_Sourceblend") }) {
                    StudioChipSelector("Source Composition", uiState.project.compositions.map { it.index }.filter { it != uiState.selectedCompositionIndex }, cfg.targetCompositionIndex, { "Composition $it" }) { v -> vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(targetCompositionIndex = v)) } }
                    StudioChipSelector("Effect Blend", BlendMode.entries, cfg.blendModeContent, { it.label }) { v -> vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(blendModeContent = v)) } }
                }
                ExpandableSectionHeader("Channel offsets", expandedSections["RgbSplitEffect_Channeloffsets"] == true, { toggleAccordion(expandedSections, "RgbSplitEffect_Channeloffsets") }) {
                    StudioSlider("Split Y", cfg.splitAmountY, -0.1f, 0.1f, 0.008f, "", 0.002f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(splitAmountY = v)) }
                    }, onCommit = ::endDrag)
                    RegisteredSlider("splitMultiplier", cfg.splitMultiplier, settings.showPropertyHelpHints, { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(splitMultiplier = v)) }
                    }, ::endDrag)
                    StudioSlider("Split X", cfg.splitAmountX, -0.1f, 0.1f, 0.015f, "", 0.002f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(splitAmountX = v)) }
                    }, onCommit = ::endDrag)
                }
                ExpandableSectionHeader("Channel colors", expandedSections["RgbSplitEffect_Channelcolors"] == true, { toggleAccordion(expandedSections, "RgbSplitEffect_Channelcolors") }) {
                    StudioColorPickerRow("Green Channel", cfg.splitColor1Argb) { v -> vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(splitColor1Argb = v)) } }
                    StudioColorPickerRow("Blue Channel", cfg.splitColor2Argb) { v -> vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(splitColor2Argb = v)) } }
                    StudioColorPickerRow("Channel 0", cfg.splitColor0Argb) { col ->
                        vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(splitColor0Argb = col)) }
                    }
                }
            }
            is LayerConfig.MotionBlurEffect -> {
                ExpandableSectionHeader("Source & blend", expandedSections["MotionBlurEffect_Sourceblend"] == true, { toggleAccordion(expandedSections, "MotionBlurEffect_Sourceblend") }) {
                    StudioChipSelector("Source Composition", uiState.project.compositions.map { it.index }.filter { it != uiState.selectedCompositionIndex }, cfg.targetCompositionIndex, { "Composition $it" }) { v -> vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(targetCompositionIndex = v)) } }
                    StudioToggleRow("Show Source Under", cfg.showUnblurredContentUnder) { v -> vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(showUnblurredContentUnder = v)) } }
                    StudioToggleRow("Show Source Above", cfg.showUnblurredContent) { v -> vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(showUnblurredContent = v)) } }
                    StudioChipSelector("Content Blend", BlendMode.entries, cfg.blendModeContent, { it.label }) { v -> vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(blendModeContent = v)) } }
                }
                ExpandableSectionHeader("Motion trail", expandedSections["MotionBlurEffect_Motiontrail"] == true, { toggleAccordion(expandedSections, "MotionBlurEffect_Motiontrail") }) {
                    StudioColorPickerRow("Trail Tint", cfg.colorArgb) { v -> vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(colorArgb = v)) } }
                    StudioSlider("Blur Amount", cfg.blurAmountMultiplier, 0.4f, 3f, 0.85f, "x", 0.05f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(blurAmountMultiplier = v)) }
                    }, onCommit = ::endDrag)
                }
            }
            is LayerConfig.MirrorEffect -> {
                ExpandableSectionHeader("Source", expandedSections["MirrorEffect_Source"] == true, { toggleAccordion(expandedSections, "MirrorEffect_Source") }) {
                    StudioChipSelector("Source Composition", uiState.project.compositions.map { it.index }.filter { it != uiState.selectedCompositionIndex }, cfg.targetCompositionIndex, { "Composition $it" }) { v -> vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(targetCompositionIndex = v)) } }
                }
                ExpandableSectionHeader("Mirror geometry", expandedSections["MirrorEffect_Mirrorgeometry"] == true, { toggleAccordion(expandedSections, "MirrorEffect_Mirrorgeometry") }) {
                    StudioChipSelector("Mirror Mode", MirrorMode.entries, cfg.mirrorMode, { it.label }) { m ->
                        vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(mirrorMode = m)) }
                    }
                    StudioSlider("Mirror Repeats", cfg.repeatCount.toFloat(), 1f, 12f, 1f, "", 1f, onValueChange = { v -> beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(repeatCount = v.toInt())) } }, onCommit = ::endDrag)
                    StudioToggleRow("Flip Mirror", cfg.flipMirror) { f ->
                        vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(flipMirror = f)) }
                    }
                }
                ExpandableSectionHeader("Tint", expandedSections["MirrorEffect_Tint"] == true, { toggleAccordion(expandedSections, "MirrorEffect_Tint") }) {
                    StudioColorPickerRow("Mirror Tint", cfg.colorArgb) { v -> vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(colorArgb = v)) } }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@UnstableApi
@Composable
private fun AudioProviderEditorSection(
    vm: StudioViewModel,
    uiState: StudioUiState,
    settings: AppSettings,
    expandedSections: MutableMap<String, Boolean>,
    onBeginDrag: () -> Unit,
    onEndDrag: () -> Unit
) {
    val palette = LocalVexoPalette.current
    val provider = uiState.project.audioProviders.firstOrNull { it.index == uiState.selectedProviderIndex }
        ?: uiState.project.audioProviders.first()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        uiState.project.audioProviders.forEach { p ->
            val active = p.index == provider.index
            Box(
                modifier = Modifier
                    .border(1.dp, if (active) palette.accent else palette.border, palette.shape)
                    .background(if (active) palette.accent else palette.glassPanel, palette.shape)
                    .clickable { vm.selectAudioProvider(p.index) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("PROVIDER ${p.index}", style = MaterialTheme.typography.labelSmall, color = if (active) palette.accentOn else palette.textPrimary)
            }
        }
        Box(
            modifier = Modifier
                .border(1.dp, palette.border, palette.shape)
                .background(palette.panelElevated)
                .clickable { vm.addAudioProvider() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text("+ PROVIDER", style = MaterialTheme.typography.labelSmall, color = palette.accent)
        }
    }

    ExpandableSectionHeader(
        title = "GENERAL & SPECTRUM",
        expanded = expandedSections["provider_general"] ?: false,
        onToggle = { toggleAccordion(expandedSections, "provider_general") }
    ) {
        StudioChipSelector("Sample Mode", SampleProviderType.entries, provider.sampleProvider, { it.aveeName }) { mode ->
            vm.updateAudioProvider(provider.index) { it.copy(sampleProvider = mode) }
        }
        StudioChipSelector("FFT Size", listOf(256, 512, 1024, 2048, 4096, 8192), provider.fftSize, { "$it" }) { sz ->
            vm.updateAudioProvider(provider.index) { it.copy(fftSize = sz) }
        }
        StudioChipSelector("Window", WindowType.entries, provider.window, { it.label }) { win ->
            vm.updateAudioProvider(provider.index) { it.copy(window = win) }
        }
        StudioChipSelector("Channel Mode", ChannelMode.entries, provider.channelMode, { it.label }) { ch ->
            vm.playbackController.pcmTap.channelSelectMode = when (ch) {
                ChannelMode.MIX -> 0
                ChannelMode.LEFT -> 1
                ChannelMode.RIGHT -> 2
            }
            vm.updateAudioProvider(provider.index) { it.copy(channelMode = ch) }
        }
        StudioToggleRow("Provider Enabled", provider.enabled) { v -> vm.updateAudioProvider(provider.index) { it.copy(enabled = v) } }
        StudioSlider("Smooth", provider.smooth, 0.1f, 1f, 0.75f, "", 0.01f, onValueChange = { v ->
            onBeginDrag(); vm.updateAudioProvider(provider.index, false) { it.copy(smooth = v) }
        }, onCommit = onEndDrag)
        StudioSlider("Frequency Shift", provider.freqShift, -0.5f, 0.5f, 0f, "", 0.01f, onValueChange = { v ->
            onBeginDrag(); vm.updateAudioProvider(provider.index, false) { it.copy(freqShift = v) }
        }, onCommit = onEndDrag)
        RegisteredSlider("sampleOutCount", provider.sampleOutCount.toFloat(), settings.showPropertyHelpHints, { v ->
            onBeginDrag(); vm.updateAudioProvider(provider.index, false) { it.copy(sampleOutCount = v.toInt()) }
        }, onEndDrag)
        RegisteredSlider("outputMultiplier", provider.outputMultiplier, settings.showPropertyHelpHints, { v ->
            onBeginDrag(); vm.updateAudioProvider(provider.index, false) { it.copy(outputMultiplier = v) }
        }, onEndDrag)
        StudioToggleRow("Mirror Samples", provider.mirrorSamples) { m ->
            vm.updateAudioProvider(provider.index) { it.copy(mirrorSamples = m) }
        }
        StudioSlider("Repeat Samples", provider.repeatSamples.toFloat(), 1f, 6f, 1f, "x", 1f, onValueChange = { v ->
            onBeginDrag(); vm.updateAudioProvider(provider.index, false) { it.copy(repeatSamples = v.toInt()) }
        }, onCommit = onEndDrag)
        StudioSlider("Edge Gap", provider.startAndEndGap.toFloat(), 0f, 30f, 0f, "bars", 1f, onValueChange = { v ->
            onBeginDrag(); vm.updateAudioProvider(provider.index, false) { it.copy(startAndEndGap = v.toInt()) }
        }, onCommit = onEndDrag)
    }

    ExpandableSectionHeader(
        title = "HZ RANGE & FILTERING",
        expanded = expandedSections["provider_hz"] ?: false,
        onToggle = { toggleAccordion(expandedSections, "provider_hz") }
    ) {
        RegisteredSlider("lowerHz", provider.lowerHz, settings.showPropertyHelpHints, { v ->
            onBeginDrag(); vm.updateAudioProvider(provider.index, false) { it.copy(lowerHz = v) }
        }, onEndDrag)
        RegisteredSlider("higherHz", provider.higherHz, settings.showPropertyHelpHints, { v ->
            onBeginDrag(); vm.updateAudioProvider(provider.index, false) { it.copy(higherHz = v) }
        }, onEndDrag)
        StudioSlider("Hz Linear Factor", provider.hzLinearFactor, 0f, 1f, 0.15f, "", 0.02f, onValueChange = { v ->
            onBeginDrag(); vm.updateAudioProvider(provider.index, false) { it.copy(hzLinearFactor = v) }
        }, onCommit = onEndDrag)
        StudioSlider("A-Weighting", provider.aWeight, 0f, 1f, 0.2f, "", 0.02f, onValueChange = { v ->
            onBeginDrag(); vm.updateAudioProvider(provider.index, false) { it.copy(aWeight = v) }
        }, onCommit = onEndDrag)
        StudioSlider("Filter Radius", provider.filterRadius.toFloat(), 0f, 12f, 1f, "bins", 1f, onValueChange = { v ->
            onBeginDrag(); vm.updateAudioProvider(provider.index, false) { it.copy(filterRadius = v.toInt()) }
        }, onCommit = onEndDrag)
        StudioSlider("Filter Strength", provider.filterStrength, 0f, 1f, 0.62f, "", 0.02f, onValueChange = { v ->
            onBeginDrag(); vm.updateAudioProvider(provider.index, false) { it.copy(filterStrength = v) }
        }, onCommit = onEndDrag)
    }

    ExpandableSectionHeader(
        title = "RESPONSE & BEAT ENVELOPE",
        expanded = expandedSections["provider_response"] ?: false,
        onToggle = { toggleAccordion(expandedSections, "provider_response") }
    ) {
        RegisteredSlider("attackMs", provider.attackMs, settings.showPropertyHelpHints, { v ->
            onBeginDrag(); vm.updateAudioProvider(provider.index, false) { it.copy(attackMs = v) }
        }, onEndDrag)
        RegisteredSlider("releaseMs", provider.releaseMs, settings.showPropertyHelpHints, { v ->
            onBeginDrag(); vm.updateAudioProvider(provider.index, false) { it.copy(releaseMs = v) }
        }, onEndDrag)
        StudioSlider("Beat Smooth", provider.beatSmooth, 0.1f, 1f, 0.4f, "", 0.01f, onValueChange = { v ->
            onBeginDrag(); vm.updateAudioProvider(provider.index, false) { it.copy(beatSmooth = v) }
        }, onCommit = onEndDrag)
        StudioSlider("Beat Range Lower", provider.beatRangeValueLower, 0f, 50f, 0.7f, "", 0.1f, onValueChange = { v ->
            onBeginDrag(); vm.updateAudioProvider(provider.index, false) { it.copy(beatRangeValueLower = v) }
        }, onCommit = onEndDrag)
        StudioSlider("Beat Range Higher", provider.beatRangeValueHigher, 0f, 100f, 35f, "", 0.1f, onValueChange = { v ->
            onBeginDrag(); vm.updateAudioProvider(provider.index, false) { it.copy(beatRangeValueHigher = v) }
        }, onCommit = onEndDrag)
        StudioSlider("Peak Hold", provider.peakHoldMs, 0f, 1000f, 180f, "", 5f, onValueChange = { v ->
            onBeginDrag(); vm.updateAudioProvider(provider.index, false) { it.copy(peakHoldMs = v) }
        }, onCommit = onEndDrag)
        StudioSlider("Peak Decay", provider.peakDecay, 0.5f, 0.995f, 0.85f, "", 0.005f, onValueChange = { v ->
            onBeginDrag(); vm.updateAudioProvider(provider.index, false) { it.copy(peakDecay = v) }
        }, onCommit = onEndDrag)
        RegisteredSlider("beatSensitivity", provider.beatSensitivity, settings.showPropertyHelpHints, { v ->
            onBeginDrag(); vm.updateAudioProvider(provider.index, false) { it.copy(beatSensitivity = v) }
        }, onEndDrag)
        StudioSlider("Beat Range First", provider.beatRangeBarFirst, 0f, 1f, 0f, "", 0.02f, onValueChange = { v ->
            onBeginDrag(); vm.updateAudioProvider(provider.index, false) { it.copy(beatRangeBarFirst = v) }
        }, onCommit = onEndDrag)
        StudioSlider("Beat Range Last", provider.beatRangeBarLast, 0f, 1f, 0.2f, "", 0.02f, onValueChange = { v ->
            onBeginDrag(); vm.updateAudioProvider(provider.index, false) { it.copy(beatRangeBarLast = v) }
        }, onCommit = onEndDrag)
    }
}

internal fun toggleAccordion(sections: MutableMap<String, Boolean>, key: String) {
    sections[key] = !(sections[key] ?: (key == "transform"))
}

@Composable
internal fun ExpandableSectionHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    val palette = LocalVexoPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, palette.border, palette.shape)
            .background(palette.glassPanel, palette.shape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = if (expanded) palette.accent else palette.textPrimary, modifier = Modifier.weight(1f))
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (expanded) "Collapse" else "Expand", tint = palette.textSecondary, modifier = Modifier.size(20.dp))
        }
        if (expanded) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                content()
            }
        }
    }
}

@UnstableApi
@Composable
private fun AssetsTab(
    vm: StudioViewModel,
    uiState: StudioUiState,
    onPickImage: () -> Unit
) {
    val palette = LocalVexoPalette.current
    val selected = vm.currentSelectedLayer()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("IMAGE & TEXTURE ASSETS", style = MaterialTheme.typography.labelMedium, color = palette.accent)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.accent)
                .clickable(onClick = onPickImage)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (selected?.type == LayerType.IMAGE) "IMPORT IMAGE / VIDEO TO SELECTED LAYER"
                else "IMPORT IMAGE / VIDEO AS NEW LAYER",
                style = MaterialTheme.typography.labelMedium,
                color = palette.accentOn
            )
        }

        if (selected?.type == LayerType.IMAGE) {
            val cfg = selected.config as LayerConfig.Image
            Text("BUILT-IN TEXTURES", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            listOf(
                "internalres:vignette80" to "Studio Vignette 80",
                "internalres:particle_circle_blur4" to "Soft Radial Glow",
                "internalres:white" to "Pure White",
                "internalres:black" to "Solid Black"
            ).forEach { (resKey, label) ->
                val active = cfg.sourceType == ImageSourceType.INTERNAL && cfg.internalRes == resKey
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, if (active) palette.accent else palette.border, palette.shape)
                        .background(palette.glassPanel, palette.shape)
                        .clickable {
                            vm.updateLayer(selected.common.id) {
                                it.copy(config = cfg.copy(sourceType = ImageSourceType.INTERNAL, internalRes = resKey))
                            }
                        }
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    Text(if (active) "ACTIVE" else "USE", style = MaterialTheme.typography.labelSmall, color = palette.accent)
                }
            }
        }
    }
}

@UnstableApi
@Composable
private fun PresetsTab(
    vm: StudioViewModel,
    onImportPreset: () -> Unit,
    onExportPreset: () -> Unit
) {
    val palette = LocalVexoPalette.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("VEX PROJECT LIBRARY", style = MaterialTheme.typography.labelMedium, color = palette.accent)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(palette.accent)
                    .clickable(onClick = onImportPreset)
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("OPEN .VEX", style = MaterialTheme.typography.labelSmall, color = palette.accentOn)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, palette.accent, palette.shape)
                    .background(palette.glassPanel, palette.shape)
                    .clickable(onClick = onExportPreset)
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("SAVE .VEX", style = MaterialTheme.typography.labelSmall, color = palette.accent)
            }
        }

        val builtIns = remember { vm.availableTemplates() }
        var query by rememberSaveable { mutableStateOf("") }
        Text("BUILT-IN PRESETS · ${builtIns.size}", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Find a preset") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        builtIns.filter { it.name.contains(query, ignoreCase = true) }.forEach { tpl ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, palette.border, palette.shape)
                    .background(palette.glassPanel, palette.shape)
                    .clickable { vm.createNewProject(tpl.name, tpl) }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(tpl.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("${tpl.canvas.aspectRatio.label} · ${tpl.compositions.sumOf { it.layers.size }} layers", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                }
                Text("APPLY", style = MaterialTheme.typography.labelSmall, color = palette.accent)
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val sec = (ms / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(sec / 60, sec % 60)
}
