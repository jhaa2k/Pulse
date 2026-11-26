package com.vexo.studio.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import com.vexo.studio.ui.theme.StudioText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.clip
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
        val aspect=uiState.project.canvas.aspectRatio
        var landscapePreview by rememberSaveable(aspect){mutableStateOf(aspect.widthRatio>aspect.heightRatio)}
        FullscreenPreviewOrientation(landscapePreview,vm::toggleFullscreenPreview)
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            VexoPreviewSurface(
                aspectRatio = uiState.project.canvas.aspectRatio,
                sceneSnapshot = sceneSnapshot.collectAsState().value,
                onTransformGesture = vm::updatePreviewTransform,
                modifier = Modifier.fillMaxSize(),
                fillBounds = false,
                targetFps = settings.previewFps,
                renderScale = settings.previewScale,
                showDiagnostics = settings.showPreviewDiagnostics,
                continuousRendering = playback.isPlaying || settings.pauseBehavior != PauseBehavior.FREEZE
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
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
                IconButton(onClick = {landscapePreview=!landscapePreview}) {
                    Icon(Icons.Default.Refresh, "Rotate fullscreen", tint = palette.textPrimary)
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
                    renderScale = settings.previewScale,
                showDiagnostics = settings.showPreviewDiagnostics,
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
                modifier = Modifier.fillMaxSize(),renderScale=settings.previewScale,showDiagnostics=settings.showPreviewDiagnostics)
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(1f - previewShare).background(palette.panel).clipToBounds()) {
                CompactTransportBar(playback, { vm.playbackController.togglePlayPause() }, { vm.playbackController.previous() }, { vm.playbackController.next() }, { vm.playbackController.seekTo(it) }, onPickAudio, Modifier.fillMaxWidth())
                StudioWorkspacePanel(vm, uiState, settings, onPickImage, onImportPreset, onExportPreset, Modifier.weight(1f).fillMaxWidth())
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().background(palette.bg).clipToBounds()) {
            PreviewBlock(
                vm = vm,
                uiState = uiState,
                sceneSnapshot = sceneSnapshot,
                targetFps = settings.previewFps,
                renderScale = settings.previewScale,
                showDiagnostics = settings.showPreviewDiagnostics,
                continuousRendering = playback.isPlaying || settings.pauseBehavior != PauseBehavior.FREEZE,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(previewShare)
                    .clipToBounds()
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
    modifier: Modifier = Modifier,
    renderScale: Float = 1f,
    showDiagnostics:Boolean=false
) {
    val palette = LocalVexoPalette.current
    Column(modifier=modifier.background(palette.panel).clipToBounds()){
        Row(Modifier.fillMaxWidth().background(palette.panel).horizontalScroll(rememberScrollState()).padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=vm::undo,enabled=uiState.canUndo,modifier=Modifier.size(32.dp)){Icon(Icons.Default.Undo,"Undo",modifier=Modifier.size(17.dp),tint=if(uiState.canUndo)palette.textPrimary else palette.border)}
            IconButton(onClick=vm::redo,enabled=uiState.canRedo,modifier=Modifier.size(32.dp)){Icon(Icons.Default.Redo,"Redo",modifier=Modifier.size(17.dp),tint=if(uiState.canRedo)palette.textPrimary else palette.border)}
            Row(verticalAlignment=Alignment.CenterVertically){
                AspectRatioPreset.entries.forEach{ratio->
                    val active=ratio==uiState.project.canvas.aspectRatio
                    Text(ratio.label,style=MaterialTheme.typography.labelSmall,color=if(active)palette.accentOn else palette.textSecondary,modifier=Modifier.background(if(active)palette.accent else Color.Transparent).clickable{vm.setAspectRatio(ratio)}.padding(horizontal=8.dp,vertical=7.dp))
                }
            }
            Text(if(uiState.previewSelectedComposition&&uiState.selectedCompositionIndex!=0)"VIEW: SELECTED" else "VIEW: FINAL",style=MaterialTheme.typography.labelSmall,color=if(uiState.previewSelectedComposition)palette.accent else palette.textSecondary,modifier=Modifier.clickable(enabled=uiState.selectedCompositionIndex!=0){vm.toggleCompositionPreview()}.padding(horizontal=10.dp,vertical=8.dp))
            IconButton(onClick=vm::toggleSafeAreaGuides,modifier=Modifier.size(32.dp)){Icon(Icons.Default.GridOn,"Safe area",tint=if(uiState.project.canvas.showSafeAreaGuides)palette.accent else palette.textSecondary,modifier=Modifier.size(16.dp))}
            IconButton(onClick=vm::resetPreviewCamera,modifier=Modifier.size(32.dp)){Icon(Icons.Default.CropFree,"Reset zoom",tint=palette.textSecondary,modifier=Modifier.size(16.dp))}
            IconButton(onClick=vm::toggleFullscreenPreview,modifier=Modifier.size(32.dp)){Icon(Icons.Default.Fullscreen,"Fullscreen",tint=palette.textPrimary,modifier=Modifier.size(18.dp))}
        }
        VexoPreviewSurface(aspectRatio=uiState.project.canvas.aspectRatio,sceneSnapshot=sceneSnapshot.collectAsState().value,onTransformGesture=vm::updatePreviewTransform,modifier=Modifier.weight(1f).fillMaxWidth().clipToBounds(),targetFps=targetFps,continuousRendering=continuousRendering,renderScale=renderScale,showDiagnostics=showDiagnostics)
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
    val palette=LocalVexoPalette.current
    val maxDur=playback.durationMs.coerceAtLeast(1L).toFloat()
    var scrub by remember(playback.currentIndex){mutableStateOf<Float?>(null)}
    val pos=(scrub?:playback.positionMs.toFloat()).coerceIn(0f,maxDur)
    val enabled=playback.durationMs>0
    val seek by rememberUpdatedState(onSeek)
    Column(modifier.background(palette.panel).clipToBounds().padding(horizontal=10.dp,vertical=3.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){
                Text(if(playback.importTotal>0)"Importing ${playback.importCompleted}/${playback.importTotal}" else if(playback.queue.isEmpty())"Import music" else playback.trackMeta.title,style=MaterialTheme.typography.bodySmall,fontWeight=FontWeight.SemiBold,color=palette.textPrimary,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(if(playback.queue.isEmpty())"Select audio" else playback.trackMeta.artist,style=MaterialTheme.typography.labelSmall,color=palette.textSecondary,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            IconButton(onClick=onPickAudio,modifier=Modifier.size(36.dp)){Icon(Icons.Default.FolderOpen,"Import audio",tint=palette.accent,modifier=Modifier.size(18.dp))}
            IconButton(onClick=onPrev,enabled=playback.queue.isNotEmpty(),modifier=Modifier.size(36.dp)){Icon(Icons.Default.SkipPrevious,"Previous",tint=palette.textPrimary,modifier=Modifier.size(20.dp))}
            IconButton(onClick=onTogglePlay,enabled=playback.queue.isNotEmpty(),modifier=Modifier.size(40.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(palette.accent)){Icon(if(playback.isPlaying)Icons.Default.Pause else Icons.Default.PlayArrow,if(playback.isPlaying)"Pause" else "Play",tint=palette.accentOn,modifier=Modifier.size(23.dp))}
            IconButton(onClick=onNext,enabled=playback.queue.isNotEmpty(),modifier=Modifier.size(36.dp)){Icon(Icons.Default.SkipNext,"Next",tint=palette.textPrimary,modifier=Modifier.size(20.dp))}
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Text(formatDuration(pos.toLong()),style=MaterialTheme.typography.labelSmall,color=palette.accent)
            Canvas(Modifier.weight(1f).height(24.dp).semantics{
                progressBarRangeInfo=ProgressBarRangeInfo(pos,0f..maxDur)
                if(enabled)setProgress{v->seek(v.coerceIn(0f,maxDur).toLong());true}
            }.pointerInput(enabled,maxDur){
                if(enabled)detectTapGestures{point->val p=((point.x-5.dp.toPx())/(size.width-10.dp.toPx()).coerceAtLeast(1f)).coerceIn(0f,1f)*maxDur;seek(p.toLong())}
            }.pointerInput(enabled,maxDur){
                if(enabled)detectHorizontalDragGestures(onDragStart={point->scrub=((point.x-5.dp.toPx())/(size.width-10.dp.toPx()).coerceAtLeast(1f)).coerceIn(0f,1f)*maxDur},onDragEnd={scrub?.let{seek(it.toLong())};scrub=null},onDragCancel={scrub=null}){change,_->change.consume();scrub=((change.position.x-5.dp.toPx())/(size.width-10.dp.toPx()).coerceAtLeast(1f)).coerceIn(0f,1f)*maxDur}
            }){
                val pad=5.dp.toPx();val y=size.height*.5f;val end=(size.width-pad).coerceAtLeast(pad);val thumb=pad+(end-pad)*(pos/maxDur)
                drawLine(palette.border,Offset(pad,y),Offset(end,y),2.dp.toPx())
                drawLine(palette.accent,Offset(pad,y),Offset(thumb,y),2.dp.toPx())
                if(enabled)drawCircle(palette.accent,4.dp.toPx(),Offset(thumb,y))
            }
            Text(formatDuration(playback.durationMs),style=MaterialTheme.typography.labelSmall,color=palette.textSecondary)
        }
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
    Column(modifier = modifier.background(palette.panel).clipToBounds()) {
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().background(palette.bg)) {
        when (uiState.studioTab) {
            StudioTab.LAYERS -> LayersTab(vm = vm, uiState = uiState)
            StudioTab.PROPERTIES -> PropertiesTab(vm = vm, uiState = uiState, settings = settings)
            StudioTab.ASSETS -> AssetsTab(vm = vm, uiState = uiState, onPickImage = onPickImage)
            StudioTab.PRESETS -> PresetsTab(vm = vm, onImportPreset = onImportPreset, onExportPreset = onExportPreset)
        }
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
    var renameComp by remember{mutableStateOf(false)}
    var deleteComp by remember{mutableStateOf(false)}
    var renameLayerTarget by remember { mutableStateOf<LayerNode?>(null) }
    var deleteLayerTarget by remember { mutableStateOf<LayerNode?>(null) }
    var moveTarget by remember { mutableStateOf<LayerNode?>(null) }
    var groupTarget by remember { mutableStateOf<LayerNode?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var layerQuery by remember { mutableStateOf("") }
    var timeline by rememberSaveable(uiState.project.id){mutableStateOf(false)}
    var multi by rememberSaveable(uiState.project.id,uiState.selectedCompositionIndex){mutableStateOf(false)}
    var selectedIds by remember(uiState.project.id,uiState.selectedCompositionIndex){mutableStateOf(setOf<String>())}
    var confirmBatchDelete by remember{mutableStateOf(false)}

    val activeComp = uiState.project.compositions.firstOrNull { it.index == uiState.selectedCompositionIndex }
        ?: uiState.project.compositions.first()

    val validIds=selectedIds.intersect(activeComp.layers.map{it.common.id}.toSet())
    val visibleLayers=activeComp.layers.filter{layerQuery.isBlank()||it.common.name.contains(layerQuery,true)||it.common.groupName.contains(layerQuery,true)||it.type.label.contains(layerQuery,true)}
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
                    DropdownMenuItem(text={Text("Rename composition")},onClick={compositionMenu=false;renameComp=true})
                    DropdownMenuItem(text={Text("Duplicate composition")},onClick={compositionMenu=false;vm.duplicateComposition(activeComp.index)})
                    if(activeComp.index!=0)DropdownMenuItem(text={Text("Delete composition",color=palette.danger)},onClick={compositionMenu=false;deleteComp=true})
                }
            }
            TextButton(onClick={searchOpen=!searchOpen;if(!searchOpen)layerQuery=""}){Text(if(searchOpen)"Close" else "Find")}
            if(uiState.clipboardLayerType!=null)TextButton(onClick=vm::pasteLayer){Text("Paste")}
            TextButton(onClick = { showAddLayerDialog = true }) { Icon(Icons.Default.Add, null, tint = palette.accent); Text("Layer", color = palette.accent) }
        }
        Row(Modifier.fillMaxWidth().height(38.dp).horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically){
            TextButton(onClick={timeline=false}){Text("LAYERS",color=if(!timeline)palette.accent else palette.textSecondary)}
            TextButton(onClick={timeline=true;multi=false;selectedIds=emptySet()}){Text("TIMELINE",color=if(timeline)palette.accent else palette.textSecondary)}
            if(!timeline)TextButton(onClick={multi=!multi;selectedIds=emptySet()}){Text(if(multi)"DONE" else "MULTI SELECT")}
        }
        if(timeline){
            LayerTimelinePanel(vm,uiState,activeComp)
            return@Column
        }
        if(multi){
            val actions=com.vexo.studio.engine.scene.BatchLayerAction.entries
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically){
                TextButton(onClick={selectedIds=visibleLayers.map{it.common.id}.toSet()}){Text("ALL SHOWN")}
                TextButton(onClick={selectedIds=emptySet()}){Text("CLEAR")}
                Text("SELECTED "+validIds.size,style=MaterialTheme.typography.labelSmall,color=palette.accent)
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())){
                actions.forEach{action->TextButton(enabled=validIds.isNotEmpty(),onClick={if(action==com.vexo.studio.engine.scene.BatchLayerAction.DELETE)confirmBatchDelete=true else vm.batchEditLayers(validIds,action)}){Text(action.label)}}
            }
        }
        if(searchOpen)CompactStudioSearch(layerQuery,{layerQuery=it.take(80)},"Search layers",Modifier.fillMaxWidth().padding(horizontal=6.dp))
        var canvasOpen by remember{mutableStateOf(false)}
        if(activeComp.index==0)ExpandableSectionHeader("Canvas background",canvasOpen,{canvasOpen=!canvasOpen}){StudioColorPickerRow("Background",uiState.project.canvas.backgroundColorArgb,vm::setCanvasColor)}
        LazyColumn(
            modifier = Modifier
                .weight(1f).fillMaxWidth()
                .padding(horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(visibleLayers, key = { it.common.id }) { layer ->
                val isSelected = if(multi)layer.common.id in validIds else layer.common.id == uiState.selectedLayerId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, if (isSelected) palette.accent else palette.border, palette.shape)
                        .background(if (isSelected) palette.panelElevated else palette.panel)
                        .clickable { if(multi)selectedIds=if(layer.common.id in selectedIds)selectedIds-layer.common.id else selectedIds+layer.common.id else {vm.selectLayer(layer.common.id); vm.selectStudioTab(StudioTab.PROPERTIES)} }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if(multi) androidx.compose.material3.Checkbox(checked=isSelected,onCheckedChange={checked->selectedIds=if(checked)selectedIds+layer.common.id else selectedIds-layer.common.id})
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
                            text = layer.type.label + if(layer.common.groupName.isBlank()) "" else " · "+layer.common.groupName,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary,
                            maxLines = 1
                        )
                    }
                    val layerIndex=activeComp.layers.indexOfFirst{it.common.id==layer.common.id}
                    IconButton(onClick={vm.moveLayer(layer.common.id,-1)},enabled=layerIndex>0&&!layer.common.locked,modifier=Modifier.size(36.dp)){
                        Icon(Icons.Default.ArrowUpward,"Move layer up",tint=if(layerIndex>0&&!layer.common.locked)palette.textPrimary else palette.border,modifier=Modifier.size(18.dp))
                    }
                    IconButton(onClick={vm.moveLayer(layer.common.id,1)},enabled=layerIndex in 0 until activeComp.layers.lastIndex&&!layer.common.locked,modifier=Modifier.size(36.dp)){
                        Icon(Icons.Default.ArrowDownward,"Move layer down",tint=if(layerIndex in 0 until activeComp.layers.lastIndex&&!layer.common.locked)palette.textPrimary else palette.border,modifier=Modifier.size(18.dp))
                    }
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.MoreVert, "Layer actions", tint = palette.textSecondary) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(if(layer.common.locked) "Unlock" else "Lock") }, onClick = { menu = false; vm.updateLayer(layer.common.id) { it.copy(common = it.common.copy(locked = !it.common.locked)) } })
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renameLayerTarget = layer })
                            DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; vm.duplicateLayer(layer.common.id) })
                            DropdownMenuItem(text={Text("Copy layer")},onClick={menu=false;vm.copyLayer(layer.common.id)})
                            if(!layer.common.locked){
                                DropdownMenuItem(text={Text("Group label")},onClick={menu=false;groupTarget=layer})
                                if(uiState.project.compositions.size>1)DropdownMenuItem(text={Text("Move to composition")},onClick={menu=false;moveTarget=layer})
                            }
                            if(!layer.common.locked&&uiState.clipboardLayerType!=null)DropdownMenuItem(text={Text("Paste transform")},onClick={menu=false;vm.pasteLayerStyle(layer.common.id,true)})
                            if(!layer.common.locked&&uiState.clipboardLayerType==layer.type)DropdownMenuItem(text={Text("Paste style")},onClick={menu=false;vm.pasteLayerStyle(layer.common.id,false)})
                            DropdownMenuItem(text = { Text("Delete", color = palette.danger) }, onClick = { menu = false; deleteLayerTarget = layer })
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if(confirmBatchDelete)AlertDialog(onDismissRequest={confirmBatchDelete=false},title={Text("DELETE SELECTED LAYERS?")},text={Text("Locked layers are kept. Keyframes belonging to deleted layers will be removed.")},confirmButton={TextButton(onClick={vm.batchEditLayers(validIds,com.vexo.studio.engine.scene.BatchLayerAction.DELETE);selectedIds=emptySet();confirmBatchDelete=false}){Text("DELETE")}},dismissButton={TextButton(onClick={confirmBatchDelete=false}){Text("CANCEL")}})
    groupTarget?.let{layer->
        var name by remember(layer.common.id){mutableStateOf(layer.common.groupName)}
        AlertDialog(onDismissRequest={groupTarget=null},title={Text("GROUP LABEL")},text={OutlinedTextField(name,{name=it.take(80)},label={Text("Group (blank to clear)")},singleLine=true)},confirmButton={TextButton(onClick={vm.updateLayer(layer.common.id){it.copy(common=it.common.copy(groupName=name.trim()))};groupTarget=null}){Text("SAVE")}},dismissButton={TextButton(onClick={groupTarget=null}){Text("CANCEL")}})
    }
    moveTarget?.let{layer->
        AlertDialog(onDismissRequest={moveTarget=null},title={Text("MOVE TO COMPOSITION")},text={Column(Modifier.heightIn(max=320.dp).verticalScroll(rememberScrollState())){uiState.project.compositions.filter{it.index!=activeComp.index}.forEach{comp->TextButton(onClick={vm.moveLayerToComposition(layer.common.id,comp.index);moveTarget=null}){Text(comp.name)}}}},confirmButton={TextButton(onClick={moveTarget=null}){Text("CANCEL")}})
    }
    if(renameComp){
        var name by remember{mutableStateOf(activeComp.name)}
        AlertDialog(onDismissRequest={renameComp=false},title={Text("RENAME COMPOSITION")},text={OutlinedTextField(name,{name=it},singleLine=true)},confirmButton={TextButton(onClick={vm.renameComposition(activeComp.index,name);renameComp=false}){Text("SAVE")}},dismissButton={TextButton(onClick={renameComp=false}){Text("CANCEL")}})
    }
    if(deleteComp)AlertDialog(onDismissRequest={deleteComp=false},title={Text("DELETE COMPOSITION?")},text={Text("Referenced compositions are protected from deletion.")},confirmButton={TextButton(onClick={vm.deleteComposition(activeComp.index);deleteComp=false}){Text("DELETE")}},dismissButton={TextButton(onClick={deleteComp=false}){Text("CANCEL")}})
    if (showAddLayerDialog) {
        AlertDialog(
            onDismissRequest = { showAddLayerDialog = false },
            title = { Text("ADD LAYER OR EFFECT", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("LAYERS",style=MaterialTheme.typography.labelMedium,color=palette.accent)
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
                    Text("CREATIVE EFFECTS",style=MaterialTheme.typography.labelMedium,color=palette.accent)
                    Box(Modifier.fillMaxWidth().background(palette.panelElevated,palette.shape).border(1.dp,palette.border,palette.shape).clickable{vm.addGlowLayer();showAddLayerDialog=false}.padding(12.dp)){Text("GLOW • SELECTED LAYER",style=MaterialTheme.typography.labelMedium,color=palette.textPrimary)}
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
    val expandedSections = remember(uiState.project.id,uiState.selectedLayerId,editTarget) { mutableStateMapOf<String, Boolean>() }
    var propertyQuery by rememberSaveable(uiState.project.id,editTarget){mutableStateOf("")}
    var undoAnchor by remember { mutableStateOf<VexoProject?>(null) }

    fun beginDrag() {
        if (undoAnchor == null) undoAnchor = uiState.project
    }

    fun endDrag() {
        undoAnchor?.let { vm.commitUndoSnapshot(it) }
        undoAnchor = null
    }

    val latestAnchor by rememberUpdatedState(undoAnchor)
    DisposableEffect(uiState.project.id,uiState.selectedLayerId,editTarget){onDispose{latestAnchor?.let{vm.commitUndoSnapshot(it)}}}
    CompositionLocalProvider(LocalInspectorQuery provides propertyQuery){
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
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

        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            CompactStudioSearch(propertyQuery,{propertyQuery=it.take(80)},"Find property / section",Modifier.weight(1f))
        }
        if(propertyQuery.isNotBlank())Text("Matching sections only. Open one section at a time.",style=MaterialTheme.typography.labelSmall,color=palette.textSecondary)
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
            expanded = expandedSections["transform"] ?: expandedSections.isEmpty(),
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
            StudioToggleRow("Uniform Scale", layer.common.scaleIsUniform) { v -> vm.updateLayer(layer.common.id) { it.copy(common = it.common.copy(scaleIsUniform = v,scaleY=if(v)it.common.scaleX else it.common.scaleY)) } }
            StudioChipSelector("Anchor X", com.vexo.studio.model.AnchorMode.entries, layer.common.anchorX, { it.name }) { v -> vm.updateLayer(layer.common.id) { it.copy(common=it.common.copy(anchorX=v)) } }
            StudioChipSelector("Anchor Y", com.vexo.studio.model.AnchorMode.entries, layer.common.anchorY, { it.name }) { v -> vm.updateLayer(layer.common.id) { it.copy(common=it.common.copy(anchorY=v)) } }
            StudioSlider("Pivot X", layer.common.alignmentX, 0f, 1f, 0.5f, "", 0.01f, onValueChange = { v -> beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(common = it.common.copy(alignmentX = v)) } }, onCommit = ::endDrag)
            StudioSlider("Pivot Y", layer.common.alignmentY, 0f, 1f, 0.5f, "", 0.01f, onValueChange = { v -> beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(common = it.common.copy(alignmentY = v)) } }, onCommit = ::endDrag)
            RegisteredSlider("scaleX", layer.common.scaleX, settings.showPropertyHelpHints, { v ->
                beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(common = it.common.copy(scaleX = v,scaleY=if(it.common.scaleIsUniform)v else it.common.scaleY)) }
            }, ::endDrag)
            if(layer.common.scaleIsUniform)Text("Scale Y follows Scale X",style=MaterialTheme.typography.labelSmall,color=palette.textSecondary)
            else RegisteredSlider("scaleY", layer.common.scaleY, settings.showPropertyHelpHints, { v ->
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

        ExpandableSectionHeader("Quick layer tools",expandedSections["quick_tools"]==true,{toggleAccordion(expandedSections,"quick_tools")}){
            LayerPresetSavePanel(vm,layer)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())){
                TextButton(onClick={vm.copyLayer(layer.common.id)}){Text("COPY")}
                TextButton(enabled=uiState.clipboardLayerType!=null,onClick={vm.pasteLayerStyle(layer.common.id,true)}){Text("PASTE TRANSFORM")}
                TextButton(enabled=uiState.clipboardLayerType==layer.type,onClick={vm.pasteLayerStyle(layer.common.id,false)}){Text("PASTE STYLE")}
            }
            TextButton(onClick={vm.resetLayerTransform(layer.common.id)}){Text("RESET TRANSFORM")}
            StudioChipSelector("Horizontal anchor",listOf(0f,.5f,1f),layer.common.posX,{when(it){0f->"Left";1f->"Right";else->"Center"}}){vm.alignLayer(layer.common.id,it,null)}
            StudioChipSelector("Vertical anchor",listOf(0f,.5f,1f),layer.common.posY,{when(it){0f->"Top";1f->"Bottom";else->"Center"}}){vm.alignLayer(layer.common.id,null,it)}
            Text("Positions align the layer pivot, not its visible bounds.",style=MaterialTheme.typography.labelSmall)
        }
        ExpandableSectionHeader("Layer timing & fades",expandedSections["timing"]==true,{toggleAccordion(expandedSections,"timing")}){
            val playback by vm.playbackState.collectAsState()
            val t=layer.common.timing
            fun timing(edit:(com.vexo.studio.model.LayerTiming)->com.vexo.studio.model.LayerTiming){vm.updateLayer(layer.common.id){l->l.copy(common=l.common.copy(timing=edit(l.common.timing)))}}
            Text("Start / end in seconds. End 0 follows track duration.",style=MaterialTheme.typography.labelSmall)
            TimingNumber("Start",t.startMs){v->timing{it.copy(startMs=v)}}
            TimingNumber("End",t.endMs){v->timing{it.copy(endMs=v)}}
            TimingNumber("Fade in",t.fadeInMs){v->timing{it.copy(fadeInMs=v)}}
            TimingNumber("Fade out",t.fadeOutMs){v->timing{it.copy(fadeOutMs=v)}}
            Row(Modifier.horizontalScroll(rememberScrollState())){
                TextButton(onClick={timing{it.copy(startMs=playback.positionMs)}}){Text("SET IN")}
                TextButton(onClick={timing{it.copy(endMs=playback.positionMs)}}){Text("SET OUT")}
                TextButton(onClick={timing{com.vexo.studio.model.LayerTiming()}}){Text("RESET")}
            }
            if(t.endMs>0&&t.endMs<=t.startMs)Text("End must be after start",color=palette.danger)
        }
        ExpandableSectionHeader("Keyframe animation",expandedSections["keyframes"]==true,{toggleAccordion(expandedSections,"keyframes")}){
            MotionPresetPanel(vm,layer)
            TransformKeyframePanel(vm,uiState,layer)
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
                ExpandableSectionHeader("Creative spectrum",expandedSections["creative_bars"]==true,{toggleAccordion(expandedSections,"creative_bars")}){
                    SpectrumStyleGallery(layer.common.id,cfg){next->vm.updateLayer(layer.common.id){it.copy(config=next)}}
                    StudioChipSelector("Creative path",com.vexo.studio.model.CreativeSpectrumPath.entries,cfg.creativePath,{it.name}){v->vm.updateLayer(layer.common.id){l->l.copy(config=(l.config as LayerConfig.Bars).copy(creativePath=v,customPathEnabled=false))}}
                    StudioChipSelector("Creative style",com.vexo.studio.model.CreativeSpectrumStyle.entries,cfg.creativeStyle,{it.name}){v->vm.updateLayer(layer.common.id){l->l.copy(config=(l.config as LayerConfig.Bars).copy(creativeStyle=v,segment1=BarsSegmentType.BARS))}}
                    StudioSlider("Line / Outline Width",cfg.creativeStrokeWidth,.25f,24f,2f,"px",.25f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){l->l.copy(config=(l.config as LayerConfig.Bars).copy(creativeStrokeWidth=v))}},onCommit=::endDrag)
                    StudioSlider("Fill Opacity",cfg.creativeFillAlpha,0f,1f,.65f,"",.01f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){l->l.copy(config=(l.config as LayerConfig.Bars).copy(creativeFillAlpha=v))}},onCommit=::endDrag)
                    StudioSlider("Dot / Tip Size",cfg.creativeTipSize,.25f,3f,1f,"x",.05f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){l->l.copy(config=(l.config as LayerConfig.Bars).copy(creativeTipSize=v))}},onCommit=::endDrag)
                    StudioSlider("Radius",cfg.circleRadius,0.01f,0.8f,0.28f,"",0.01f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){l->l.copy(config=(l.config as LayerConfig.Bars).copy(circleRadius=v))}},onCommit=::endDrag)
                    StudioSlider("Turns",cfg.pathTurns,0.25f,5.0f,2.0f,"",0.05f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){l->l.copy(config=(l.config as LayerConfig.Bars).copy(pathTurns=v))}},onCommit=::endDrag)
                    CustomSpectrumPathEditor(layer.common.id,cfg){next->vm.updateLayer(layer.common.id){it.copy(config=next)}}
                    StudioSlider("Path Stretch X",cfg.pathStretchX,.1f,3f,1f,"x",.01f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){l->l.copy(config=(l.config as LayerConfig.Bars).copy(pathStretchX=v))}},onCommit=::endDrag)
                    StudioSlider("Path Stretch Y",cfg.pathStretchY,.1f,3f,1f,"x",.01f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){l->l.copy(config=(l.config as LayerConfig.Bars).copy(pathStretchY=v))}},onCommit=::endDrag)
                    StudioSlider("Wave spread",cfg.pathSpread,0.0f,1.0f,0.3f,"",0.01f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){l->l.copy(config=(l.config as LayerConfig.Bars).copy(pathSpread=v))}},onCommit=::endDrag)
                    StudioToggleRow("Signed PCM Waveform",cfg.signedWaveform){v->vm.updateLayer(layer.common.id){it.copy(config=(it.config as LayerConfig.Bars).copy(signedWaveform=v))}}
                    StudioSlider("Smooth Curve Segments",cfg.curveSubdivisions.toFloat(),1f,8f,1f,"",1f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){l->l.copy(config=(l.config as LayerConfig.Bars).copy(curveSubdivisions=v.toInt()))}},onCommit=::endDrag)
                    StudioSlider("Gradient Repeat",cfg.gradientRepeats.toFloat(),1f,8f,1f,"",1f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){l->l.copy(config=(l.config as LayerConfig.Bars).copy(gradientRepeats=v.toInt()))}},onCommit=::endDrag)
                    StudioSlider("Sample Offset",cfg.sampleOffset,-1f,1f,0f,"",.01f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){l->l.copy(config=(l.config as LayerConfig.Bars).copy(sampleOffset=v))}},onCommit=::endDrag)
                    StudioSlider("Spectrum Repeat",cfg.sampleRepeats.toFloat(),1f,8f,1f,"",1f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){l->l.copy(config=(l.config as LayerConfig.Bars).copy(sampleRepeats=v.toInt()))}},onCommit=::endDrag)
                    StudioSlider("Amplitude Curve",cfg.amplitudeCurve,.25f,3f,1f,"",.05f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){l->l.copy(config=(l.config as LayerConfig.Bars).copy(amplitudeCurve=v))}},onCommit=::endDrag)
                    StudioSlider("Baseline Offset",cfg.baselineOffset,-1f,1f,0f,"",.01f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){l->l.copy(config=(l.config as LayerConfig.Bars).copy(baselineOffset=v))}},onCommit=::endDrag)
                    StudioSlider("Steps",cfg.stepCount.toFloat(),2.0f,16.0f,8.0f,"",1.0f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){l->l.copy(config=(l.config as LayerConfig.Bars).copy(stepCount=v.toInt()))}},onCommit=::endDrag)
                }
                ExpandableSectionHeader("Bar fill & stroke",expandedSections["bar_paint"]==true,{toggleAccordion(expandedSections,"bar_paint")}){
                    Text("Stroke and fill apply to solid bars, capsules, steps, dots and polygon segments.",style=MaterialTheme.typography.labelSmall,color=palette.textSecondary)
                    StudioSlider("Bar Stroke Width",cfg.barStrokeWidth,0f,24f,0f,"px",.25f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){it.copy(config=(it.config as LayerConfig.Bars).copy(barStrokeWidth=v))}},onCommit=::endDrag)
                    StudioColorPickerRow("Bar Stroke Color",cfg.barStrokeColorArgb){v->vm.updateLayer(layer.common.id){it.copy(config=(it.config as LayerConfig.Bars).copy(barStrokeColorArgb=v))}}
                    StudioSlider("Bar Fill Opacity",cfg.barFillOpacity,0f,1f,1f,"",.01f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){it.copy(config=(it.config as LayerConfig.Bars).copy(barFillOpacity=v))}},onCommit=::endDrag)
                    StudioSlider("Bar Tip Width",cfg.barTipWidth,.05f,2f,1f,"x",.01f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){it.copy(config=(it.config as LayerConfig.Bars).copy(barTipWidth=v))}},onCommit=::endDrag)
                    StudioChipSelector("Gradient Mode",com.vexo.studio.model.SpectrumGradientMode.entries,cfg.barGradientMode,{it.name.replace('_',' ')}){v->vm.updateLayer(layer.common.id){it.copy(config=(it.config as LayerConfig.Bars).copy(barGradientMode=v))}}
                }
                ExpandableSectionHeader("Dynamic spectrum",expandedSections["dynamic_spectrum"]==true,{toggleAccordion(expandedSections,"dynamic_spectrum")}){
                    Text("Gradient animation follows the audio playhead. Reactive controls use the selected spectrum provider.",style=MaterialTheme.typography.labelSmall,color=palette.textSecondary)
                    StudioSlider("Gradient Speed",cfg.gradientSpeed,-2f,2f,0f,"/s",.05f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){it.copy(config=(it.config as LayerConfig.Bars).copy(gradientSpeed=v))}},onCommit=::endDrag)
                    StudioToggleRow("Cycle Gradient",cfg.gradientCycle){v->vm.updateLayer(layer.common.id){it.copy(config=(it.config as LayerConfig.Bars).copy(gradientCycle=v))}}
                    StudioChipSelector("Reactive Source",com.vexo.studio.model.SpectrumReactiveSource.entries,cfg.reactiveSource,{it.name}){v->vm.updateLayer(layer.common.id){it.copy(config=(it.config as LayerConfig.Bars).copy(reactiveSource=v))}}
                    StudioSlider("Reactive Stroke",cfg.reactiveStroke,0f,16f,0f,"px",.25f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){it.copy(config=(it.config as LayerConfig.Bars).copy(reactiveStroke=v))}},onCommit=::endDrag)
                    StudioSlider("Reactive Color Mix",cfg.reactiveColorMix,0f,1f,0f,"",.01f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){it.copy(config=(it.config as LayerConfig.Bars).copy(reactiveColorMix=v))}},onCommit=::endDrag)
                    StudioColorPickerRow("Reactive Color",cfg.reactiveColorArgb){v->vm.updateLayer(layer.common.id){it.copy(config=(it.config as LayerConfig.Bars).copy(reactiveColorArgb=v))}}
                }
                ExpandableSectionHeader("Peak marker detail",expandedSections["peak_detail"]==true,{toggleAccordion(expandedSections,"peak_detail")}){
                    StudioToggleRow("Peak Hold Markers",cfg.showPeaks){v->vm.updateLayer(layer.common.id){it.copy(config=(it.config as LayerConfig.Bars).copy(showPeaks=v))}}
                    StudioChipSelector("Peak Shape",com.vexo.studio.model.SpectrumPeakStyle.entries,cfg.peakMarkerStyle,{it.name}){v->vm.updateLayer(layer.common.id){it.copy(config=(it.config as LayerConfig.Bars).copy(peakMarkerStyle=v))}}
                    StudioSlider("Peak Width",cfg.peakMarkerWidth,.2f,3f,1f,"x",.05f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){it.copy(config=(it.config as LayerConfig.Bars).copy(peakMarkerWidth=v))}},onCommit=::endDrag)
                    StudioSlider("Peak Thickness",cfg.peakMarkerThickness,.25f,5f,1f,"x",.05f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){it.copy(config=(it.config as LayerConfig.Bars).copy(peakMarkerThickness=v))}},onCommit=::endDrag)
                    StudioSlider("Peak Offset",cfg.peakMarkerOffset,-32f,32f,0f,"px",.5f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){it.copy(config=(it.config as LayerConfig.Bars).copy(peakMarkerOffset=v))}},onCommit=::endDrag)
                    StudioColorPickerRow("Peak Color",cfg.peakMarkerColorArgb?:cfg.colorToArgb){v->vm.updateLayer(layer.common.id){it.copy(config=(it.config as LayerConfig.Bars).copy(peakMarkerColorArgb=v))}}
                    TextButton(onClick={vm.updateLayer(layer.common.id){it.copy(config=(it.config as LayerConfig.Bars).copy(peakMarkerColorArgb=null))}}){Text("USE GRADIENT COLOR")}
                }
                ExpandableSectionHeader("Wave detail",expandedSections["wave_detail"]==true,{toggleAccordion(expandedSections,"wave_detail")}){
                    Text("Use line, double line, ribbon or fill to customize connected waves.",style=MaterialTheme.typography.labelSmall,color=palette.textSecondary)
                    StudioSlider("Curve Tension",cfg.waveCurveTension,0f,1f,.5f,"",.01f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){it.copy(config=(it.config as LayerConfig.Bars).copy(waveCurveTension=v,curveSubdivisions=maxOf(2,(it.config as LayerConfig.Bars).curveSubdivisions)))}},onCommit=::endDrag)
                    StudioSlider("Double Wave Gap",cfg.waveLineGap,.25f,12f,2.5f,"x",.05f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){it.copy(config=(it.config as LayerConfig.Bars).copy(waveLineGap=v))}},onCommit=::endDrag)
                    StudioSlider("Ribbon Thickness",cfg.waveRibbonWidth,.1f,5f,1f,"x",.05f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){it.copy(config=(it.config as LayerConfig.Bars).copy(waveRibbonWidth=v))}},onCommit=::endDrag)
                    StudioSlider("Wave Dash Coverage",cfg.waveDashRatio,.05f,1f,1f,"",.01f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){it.copy(config=(it.config as LayerConfig.Bars).copy(waveDashRatio=v))}},onCommit=::endDrag)
                    if(cfg.waveDashRatio<.999f)StudioSlider("Wave Dash Count",cfg.waveDashCount.toFloat(),1f,64f,12f,"",1f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){it.copy(config=(it.config as LayerConfig.Bars).copy(waveDashCount=v.toInt()))}},onCommit=::endDrag)
                }
                ExpandableSectionHeader("Path & geometry", expandedSections["Bars_Pathgeometry"] == true, { toggleAccordion(expandedSections, "Bars_Pathgeometry") }) {
                    StudioSlider("Polygon Sides", cfg.polygonSides.toFloat(), 3f, 24f, 6f, "", 1f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(polygonSides = v.toInt())) }
                    }, onCommit = ::endDrag)
                    StudioSlider("Path Angle", cfg.pathAngleDeg, -360f, 360f, 0f, "deg", 1f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(pathAngleDeg = v)) }
                    }, onCommit = ::endDrag)
                    StudioChipSelector("Path Shape", BarsShapePath.entries, cfg.shapePath, { it.label }) { path ->
                        vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(shapePath = path,creativePath=com.vexo.studio.model.CreativeSpectrumPath.LEGACY,customPathEnabled=false)) }
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
                        vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(segment1 = seg,creativeStyle=com.vexo.studio.model.CreativeSpectrumStyle.BARS)) }
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
                    if (cfg.segment1 == BarsSegmentType.ROUND_BARS || cfg.creativeStyle==com.vexo.studio.model.CreativeSpectrumStyle.CAPSULE) StudioSlider("Roundness", cfg.roundness, 0f, 1f, 1f, "", .01f, onValueChange={v -> beginDrag(); vm.updateLayer(layer.common.id,false){it.copy(config=(it.config as LayerConfig.Bars).copy(roundness=v))}}, onCommit=::endDrag)
                    StudioSlider("Spectrum Softness",cfg.softness.toFloat(),0f,20f,2f,"",1f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){it.copy(config=(it.config as LayerConfig.Bars).copy(softness=v.toInt()))}},onCommit=::endDrag)
                    if(cfg.segment1!=BarsSegmentType.LINE || cfg.creativePath!=com.vexo.studio.model.CreativeSpectrumPath.LEGACY || cfg.creativeStyle!=com.vexo.studio.model.CreativeSpectrumStyle.BARS) StudioToggleRow("Peak Hold Markers",cfg.showPeaks){v->vm.updateLayer(layer.common.id){it.copy(config=(it.config as LayerConfig.Bars).copy(showPeaks=v))}}
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
                    StudioChipSelector("Secondary Creative Style",com.vexo.studio.model.CreativeSpectrumStyle.entries,cfg.secondaryCreativeStyle,{it.name.replace('_',' ')}){v->vm.updateLayer(layer.common.id){it.copy(config=(it.config as LayerConfig.Bars).copy(secondaryCreativeStyle=v))}}
                    StudioSlider("Segment 2 Width", cfg.segment2Width, 0.05f, 1.5f, 0.72f, "", 0.01f, onValueChange = { v -> beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(segment2Width = v)) } }, onCommit = ::endDrag)
                    StudioSlider("Segment 2 Height", cfg.segment2HeightMultiplier, -2f, 2f, 1f, "", 0.05f, onValueChange = { v -> beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(segment2HeightMultiplier = v)) } }, onCommit = ::endDrag)
                    StudioSlider("Segment 2 Fixed Height", cfg.segment2FixedHeight, -50f, 50f, 0f, "", 0.5f, onValueChange = { v -> beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(segment2FixedHeight = v)) } }, onCommit = ::endDrag)
                        StudioSlider("Segment 2 Stroke Width",cfg.segment2StrokeWidth,0f,24f,0f,"px",.25f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){it.copy(config=(it.config as LayerConfig.Bars).copy(segment2StrokeWidth=v))}},onCommit=::endDrag)
                        StudioColorPickerRow("Segment 2 Stroke Color",cfg.segment2StrokeColorArgb){v->vm.updateLayer(layer.common.id){it.copy(config=(it.config as LayerConfig.Bars).copy(segment2StrokeColorArgb=v))}}
                        StudioSlider("Segment 2 Fill Opacity",cfg.segment2FillOpacity,0f,1f,1f,"",.01f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){it.copy(config=(it.config as LayerConfig.Bars).copy(segment2FillOpacity=v))}},onCommit=::endDrag)
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
                    StudioSlider("Accumulated Delay",cfg.reactionAccumulatedDelay.toFloat(),0f,9f,0f,"",1f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){it.copy(config=(it.config as LayerConfig.Bars).copy(reactionAccumulatedDelay=v.toInt()))}},onCommit=::endDrag)
                    StudioSlider("Reaction Delay", cfg.reactionDelay.toFloat(), 0f, 9f, 0f, "frames", 1f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(reactionDelay = v.toInt())) }
                    }, onCommit = ::endDrag)
                }
            }
            is LayerConfig.Image -> {
                val c=cfg.creative
                fun change(undo:Boolean=true,edit:(com.vexo.studio.model.CreativeImageConfig)->com.vexo.studio.model.CreativeImageConfig){
                    vm.updateLayer(layer.common.id,undo){l->val image=l.config as LayerConfig.Image;l.copy(config=image.copy(creative=edit(image.creative)))}
                }
                ExpandableSectionHeader("Procedural backgrounds",expandedSections["patterns"]==true,{toggleAccordion(expandedSections,"patterns")}){
                    StudioChipSelector("Pattern",com.vexo.studio.model.ProceduralPattern.entries,c.pattern,{it.name}){v->change{it.copy(pattern=v)}}
                    if(c.pattern!=com.vexo.studio.model.ProceduralPattern.NONE){
                        StudioColorPickerRow("Pattern dark",c.gradientFrom){v->change{it.copy(gradientFrom=v)}}
                        StudioColorPickerRow("Pattern light",c.gradientTo){v->change{it.copy(gradientTo=v)}}
                        StudioSlider("Density",c.patternDensity,1.0f,48.0f,8.0f,"",1.0f,onValueChange={v->beginDrag();change(false){it.copy(patternDensity=v)}},onCommit=::endDrag)
                        StudioSlider("Intensity",c.patternIntensity,0.0f,1.0f,0.5f,"",0.01f,onValueChange={v->beginDrag();change(false){it.copy(patternIntensity=v)}},onCommit=::endDrag)
                        StudioSlider("Motion speed",c.patternSpeed,-4.0f,4.0f,0.25f,"",0.05f,onValueChange={v->beginDrag();change(false){it.copy(patternSpeed=v)}},onCommit=::endDrag)
                    }
                }
                ExpandableSectionHeader("Feathered aperture",expandedSections["aperture"]==true,{toggleAccordion(expandedSections,"aperture")}){
                    StudioChipSelector("Mask shape",com.vexo.studio.model.Aperture.entries,c.aperture,{it.name}){v->change{it.copy(aperture=v)}}
                    if(c.aperture!=com.vexo.studio.model.Aperture.NONE){
                        StudioToggleRow("Invert",c.apertureInvert){v->change{it.copy(apertureInvert=v)}}
                        StudioSlider("Size",c.apertureSize,0.01f,2.0f,0.75f,"",0.01f,onValueChange={v->beginDrag();change(false){it.copy(apertureSize=v)}},onCommit=::endDrag)
                        StudioSlider("Feather",c.apertureFeather,0.001f,0.5f,0.05f,"",0.005f,onValueChange={v->beginDrag();change(false){it.copy(apertureFeather=v)}},onCommit=::endDrag)
                        StudioSlider("Center X",c.apertureX,0.0f,1.0f,0.5f,"",0.01f,onValueChange={v->beginDrag();change(false){it.copy(apertureX=v)}},onCommit=::endDrag)
                        StudioSlider("Center Y",c.apertureY,0.0f,1.0f,0.5f,"",0.01f,onValueChange={v->beginDrag();change(false){it.copy(apertureY=v)}},onCommit=::endDrag)
                    }
                }
                ExpandableSectionHeader("Creative gradients",expandedSections["Image_gradient"]==true,{toggleAccordion(expandedSections,"Image_gradient")}){
                    StudioChipSelector("Gradient",com.vexo.studio.model.CreativeGradient.entries,c.gradient,{it.name}){v->change{it.copy(gradient=v)}}
                    if(c.gradient!=com.vexo.studio.model.CreativeGradient.NONE){
                        StudioColorPickerRow("Gradient Start",c.gradientFrom){v->change{it.copy(gradientFrom=v)}}
                        StudioColorPickerRow("Gradient End",c.gradientTo){v->change{it.copy(gradientTo=v)}}
                        StudioSlider("Gradient Angle",c.gradientAngle,-360.0f,360.0f,0.0f,"°",1.0f,onValueChange={v->beginDrag();change(false){it.copy(gradientAngle=v)}},onCommit=::endDrag)
                        StudioSlider("Center X",c.gradientCenterX,0.0f,1.0f,0.5f,"",0.01f,onValueChange={v->beginDrag();change(false){it.copy(gradientCenterX=v)}},onCommit=::endDrag)
                        StudioSlider("Center Y",c.gradientCenterY,0.0f,1.0f,0.5f,"",0.01f,onValueChange={v->beginDrag();change(false){it.copy(gradientCenterY=v)}},onCommit=::endDrag)
                        StudioSlider("Gradient Scale",c.gradientScale,0.05f,4.0f,1.0f,"x",0.01f,onValueChange={v->beginDrag();change(false){it.copy(gradientScale=v)}},onCommit=::endDrag)
                        StudioSlider("Gradient Mix",c.gradientMix,0.0f,1.0f,1.0f,"",0.01f,onValueChange={v->beginDrag();change(false){it.copy(gradientMix=v)}},onCommit=::endDrag)
                    }
                }
                ExpandableSectionHeader("Creative distortion",expandedSections["Image_distortion"]==true,{toggleAccordion(expandedSections,"Image_distortion")}){
                    StudioChipSelector("Effect",com.vexo.studio.model.CreativeDistortion.entries,c.distortion,{it.name.replace('_',' ')}){v->change{it.copy(distortion=v)}}
                    if(c.distortion!=com.vexo.studio.model.CreativeDistortion.NONE){
                        StudioSlider("Intensity",c.amount,0.0f,1.0f,0.15f,"",0.01f,onValueChange={v->beginDrag();change(false){it.copy(amount=v)}},onCommit=::endDrag)
                        StudioSlider("Frequency / Sectors",c.frequency,2.0f,24.0f,6.0f,"",1.0f,onValueChange={v->beginDrag();change(false){it.copy(frequency=v)}},onCommit=::endDrag)
                        StudioSlider("Animation Speed",c.speed,-4.0f,4.0f,1.0f,"x",0.05f,onValueChange={v->beginDrag();change(false){it.copy(speed=v)}},onCommit=::endDrag)
                        MeasureBindingControls("Effect audio reactivity",c.audio,uiState.project.audioProviders.map{it.index},::beginDrag,::endDrag){v,undo->change(undo){it.copy(audio=v)}}
                    }
                }
                ExpandableSectionHeader("Color grading & film",expandedSections["Image_grade"]==true,{toggleAccordion(expandedSections,"Image_grade")}){
                    StudioSlider("Brightness",c.brightness,-1.0f,1.0f,0.0f,"",0.01f,onValueChange={v->beginDrag();change(false){it.copy(brightness=v)}},onCommit=::endDrag)
                    StudioSlider("Contrast",c.contrast,0.0f,3.0f,1.0f,"x",0.01f,onValueChange={v->beginDrag();change(false){it.copy(contrast=v)}},onCommit=::endDrag)
                    StudioSlider("Temperature",c.temperature,-1.0f,1.0f,0.0f,"",0.01f,onValueChange={v->beginDrag();change(false){it.copy(temperature=v)}},onCommit=::endDrag)
                    StudioSlider("Hue",c.hue,-180.0f,180.0f,0.0f,"°",1.0f,onValueChange={v->beginDrag();change(false){it.copy(hue=v)}},onCommit=::endDrag)
                    StudioSlider("Vignette",c.vignette,0.0f,1.0f,0.0f,"",0.01f,onValueChange={v->beginDrag();change(false){it.copy(vignette=v)}},onCommit=::endDrag)
                    StudioSlider("Scanlines",c.scanlines,0.0f,1.0f,0.0f,"",0.01f,onValueChange={v->beginDrag();change(false){it.copy(scanlines=v)}},onCommit=::endDrag)
                    StudioSlider("Film Grain",c.grain,0.0f,1.0f,0.0f,"",0.01f,onValueChange={v->beginDrag();change(false){it.copy(grain=v)}},onCommit=::endDrag)
                    TextButton(onClick={change{it.copy(brightness=0f,contrast=1f,temperature=0f,hue=0f,vignette=0f,scanlines=0f,grain=0f)}}){Text("RESET GRADING")}
                }
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
                            StudioChipSelector("Decode mode",com.vexo.studio.model.VideoDecodeMode.entries,cfg.videoDecodeMode,{if(it==com.vexo.studio.model.VideoDecodeMode.AUTO)"Auto codec" else "Frame sampling"}){v->vm.updateLayer(layer.common.id){it.copy(config=(it.config as LayerConfig.Image).copy(videoDecodeMode=v))}}
                            StudioChipSelector("Texture quality",listOf(480,720,1280,1920),cfg.videoTextureLimit,{"$it px"}){v->vm.updateLayer(layer.common.id){it.copy(config=(it.config as LayerConfig.Image).copy(videoTextureLimit=v))}}
                            StudioChipSelector("Video sample FPS",listOf(6,12,15,24,30,60),cfg.videoSampleFps,{"$it FPS"}){v->vm.updateLayer(layer.common.id){it.copy(config=(it.config as LayerConfig.Image).copy(videoSampleFps=v))}}
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
                    MediaAssetButton("IMPORT MASK") { uri -> vm.attachLayerAsset(layer.common.id,uri,"mask") }
                    val maskChoices = listOf("") + uiState.project.compositions.filter { it.index != uiState.selectedCompositionIndex }.map { "composition:${it.index}" } + listOf("internalres:white", "internalres:black", "internalres:vignette80")
                    StudioChipSelector("Mask Source", (maskChoices + cfg.maskImage).distinct(), cfg.maskImage, { it.ifBlank { "None" } }) { v -> vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(maskImage = v)) } }
                    StudioChipSelector("Mask Mode", com.vexo.studio.model.MaskMode.entries, cfg.maskMode, { it.label }) { v -> vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(maskMode = v)) } }
                    StudioToggleRow("Color Key Chroma", cfg.colorKeyEnabled) { k ->
                        vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(colorKeyEnabled = k)) }
                    }
                    if(cfg.colorKeyEnabled){
                        StudioToggleRow("Auto Key (image corner)",cfg.autoDetectColorKey){v->vm.updateLayer(layer.common.id){it.copy(config=(it.config as LayerConfig.Image).copy(autoDetectColorKey=v))}}
                        StudioColorPickerRow("Key Color",cfg.colorKeyArgb){v->vm.updateLayer(layer.common.id){it.copy(config=(it.config as LayerConfig.Image).copy(colorKeyArgb=v,autoDetectColorKey=false))}}
                        StudioSlider("Key Strength",cfg.transparencyStrength,0f,1f,1f,"",.01f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){it.copy(config=(it.config as LayerConfig.Image).copy(transparencyStrength=v))}},onCommit=::endDrag)
                    }
                }
                ExpandableSectionHeader("Color & opacity", expandedSections["Image_Coloropacity"] == true, { toggleAccordion(expandedSections, "Image_Coloropacity") }) {
                    MeasureBindingControls("Color Blend",cfg.measureColorBlend,uiState.project.audioProviders.map{it.index},::beginDrag,::endDrag){v,undo->vm.updateLayer(layer.common.id,undo){it.copy(config=(it.config as LayerConfig.Image).copy(measureColorBlend=v))}}
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
                    StudioToggleRow("Blurred Border (fit mode)",cfg.blurredBorder){v->vm.updateLayer(layer.common.id){it.copy(config=(it.config as LayerConfig.Image).copy(blurredBorder=v))}}
                    StudioToggleRow("Image Blur", cfg.blurEnabled) { v -> vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(blurEnabled = v)) } }
                    if (cfg.blurEnabled) {
                        StudioSlider("Image Blur Radius", cfg.blurRadius.toFloat(), 0f, 64f, 7f, "px", 1f, onValueChange = { v -> beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(blurRadius = v.toInt())) } }, onCommit = ::endDrag)
                        StudioSlider("Blur Divider", cfg.blurDivider.toFloat(), 1f, 12f, 5f, "", 1f, onValueChange = { v -> beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(blurDivider = v.toInt())) } }, onCommit = ::endDrag)
                    }
                }
            }
            is LayerConfig.Text -> {
                ExpandableSectionHeader("Text animation",expandedSections["text_motion"]==true,{toggleAccordion(expandedSections,"text_motion")}){
                    StudioChipSelector("Motion",com.vexo.studio.model.TextMotion.entries,cfg.animation,{it.name}){v->vm.updateLayer(layer.common.id){l->l.copy(config=(l.config as LayerConfig.Text).copy(animation=v))}}
                    StudioToggleRow("Loop",cfg.animationLoop){v->vm.updateLayer(layer.common.id){l->l.copy(config=(l.config as LayerConfig.Text).copy(animationLoop=v))}}
                    StudioSlider("Duration (s)",cfg.animationDuration,0.1f,20.0f,2.0f,"",0.1f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){l->l.copy(config=(l.config as LayerConfig.Text).copy(animationDuration=v))}},onCommit=::endDrag)
                    StudioSlider("Delay (s)",cfg.animationDelay,0.0f,60.0f,0.0f,"",0.1f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){l->l.copy(config=(l.config as LayerConfig.Text).copy(animationDelay=v))}},onCommit=::endDrag)
                    StudioSlider("Amount",cfg.animationAmount,0.0f,1.0f,0.2f,"",0.01f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){l->l.copy(config=(l.config as LayerConfig.Text).copy(animationAmount=v))}},onCommit=::endDrag)
                    MeasureBindingControls("Text beat scale",cfg.animationAudio,uiState.project.audioProviders.map{it.index},::beginDrag,::endDrag){v,undo->vm.updateLayer(layer.common.id,undo){l->l.copy(config=(l.config as LayerConfig.Text).copy(animationAudio=v))}}
                    StudioColorPickerRow("Shadow color",cfg.shadowColorArgb){v->vm.updateLayer(layer.common.id){l->l.copy(config=(l.config as LayerConfig.Text).copy(shadowColorArgb=v))}}
                }
                ExpandableSectionHeader("Content & typography", expandedSections["Text_Contenttypography"] == true, { toggleAccordion(expandedSections, "Text_Contenttypography") }) {
                    OutlinedTextField(
                        value = cfg.text,
                        onValueChange = { text -> vm.updateLayer(layer.common.id) { l -> l.copy(config = (l.config as LayerConfig.Text).copy(text = text)) } },
                        label = { Text("TEXT ({Title}, {Artist}, {Album}, {Time}, {Duration}, {Progress})") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    RegisteredSlider("fontSize", cfg.fontSize.toFloat(), settings.showPropertyHelpHints, { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(fontSize = v.toInt())) }
                    }, ::endDrag)
                    val fontOptions=com.vexo.studio.engine.scene.StudioFonts.families.map{it.name}
                    StudioChipSelector("Font Family",fontOptions,cfg.fontFamily,{it}){name->vm.updateLayer(layer.common.id){it.copy(config=cfg.copy(fontFamily=name,fontAssetUri=null))}}
                    val currentWeight=if(cfg.fontWeight in 100..900)cfg.fontWeight else if(cfg.bold)700 else 400
                    StudioChipSelector("Font Weight",com.vexo.studio.engine.scene.StudioFonts.weights,currentWeight,{com.vexo.studio.engine.scene.StudioFonts.weightLabel(it)}){weight->vm.updateLayer(layer.common.id){it.copy(config=cfg.copy(fontWeight=weight,bold=weight>=700))}}
                    StudioToggleRow("Bold", currentWeight>=700) { b ->
                        vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(bold = b,fontWeight=if(b)700 else 400)) }
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
                ExpandableSectionHeader("Shape motion",expandedSections["shape_motion"]==true,{toggleAccordion(expandedSections,"shape_motion")}){
                    StudioSlider("Radius",cfg.radius,0.005f,0.8f,0.32f,"",0.005f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){l->l.copy(config=(l.config as LayerConfig.Shape).copy(radius=v))}},onCommit=::endDrag)
                    StudioSlider("Star inner radius",cfg.starInnerRatio,0.05f,0.99f,0.48f,"",0.01f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){l->l.copy(config=(l.config as LayerConfig.Shape).copy(starInnerRatio=v))}},onCommit=::endDrag)
                    StudioSlider("Spin (degrees/sec)",cfg.spin,-360.0f,360.0f,0.0f,"",1.0f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){l->l.copy(config=(l.config as LayerConfig.Shape).copy(spin=v))}},onCommit=::endDrag)
                    MeasureBindingControls("Radius pulse",cfg.pulse,uiState.project.audioProviders.map{it.index},::beginDrag,::endDrag){v,undo->vm.updateLayer(layer.common.id,undo){l->l.copy(config=(l.config as LayerConfig.Shape).copy(pulse=v))}}
                    MeasureBindingControls("Stroke pulse",cfg.strokePulse,uiState.project.audioProviders.map{it.index},::beginDrag,::endDrag){v,undo->vm.updateLayer(layer.common.id,undo){l->l.copy(config=(l.config as LayerConfig.Shape).copy(strokePulse=v))}}
                }
                ExpandableSectionHeader("Geometry", expandedSections["Shape_Geometry"] == true, { toggleAccordion(expandedSections, "Shape_Geometry") }) {
                    StudioChipSelector("Shape Kind", ShapeKind.entries, cfg.kind, { it.label }) { k ->
                        vm.updateLayer(layer.common.id) { it.copy(config = cfg.copy(kind = k)) }
                    }
                    if(cfg.kind==ShapeKind.RECTANGLE) StudioSlider("Corner Radius",cfg.cornerRadius,0f,0.5f,0f,"",.01f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){it.copy(config=(it.config as LayerConfig.Shape).copy(cornerRadius=v))}},onCommit=::endDrag)
                    StudioSlider("Sides", cfg.sides.toFloat(), 3f, 24f, 6f, "", 1f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(sides = v.toInt())) }
                    }, onCommit = ::endDrag)
                }
                ExpandableSectionHeader("Fill & stroke", expandedSections["Shape_Fillstroke"] == true, { toggleAccordion(expandedSections, "Shape_Fillstroke") }) {
                    StudioSlider("Stroke Width", cfg.strokeWidth, 0f, 32f, 3f, "px", 0.5f, onValueChange = { v ->
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
                ExpandableSectionHeader("Creative emitter",expandedSections["emitter"]==true,{toggleAccordion(expandedSections,"emitter")}){
                    StudioChipSelector("Emitter",com.vexo.studio.model.ParticleEmitter.entries,cfg.emitter,{it.name}){v->vm.updateLayer(layer.common.id){l->l.copy(config=(l.config as LayerConfig.Particles).copy(emitter=v))}}
                    StudioSlider("Emitter X",cfg.emitterX,0.0f,1.0f,0.5f,"",0.01f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){l->l.copy(config=(l.config as LayerConfig.Particles).copy(emitterX=v))}},onCommit=::endDrag)
                    StudioSlider("Emitter Y",cfg.emitterY,0.0f,1.0f,0.5f,"",0.01f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){l->l.copy(config=(l.config as LayerConfig.Particles).copy(emitterY=v))}},onCommit=::endDrag)
                    StudioSlider("Emitter width",cfg.emitterWidth,0.0f,2.0f,1.0f,"",0.01f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){l->l.copy(config=(l.config as LayerConfig.Particles).copy(emitterWidth=v))}},onCommit=::endDrag)
                    StudioSlider("Emitter height",cfg.emitterHeight,0.0f,2.0f,1.0f,"",0.01f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){l->l.copy(config=(l.config as LayerConfig.Particles).copy(emitterHeight=v))}},onCommit=::endDrag)
                    StudioSlider("Direction spread",cfg.directionSpread,0.0f,360.0f,37.0f,"",1.0f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){l->l.copy(config=(l.config as LayerConfig.Particles).copy(directionSpread=v))}},onCommit=::endDrag)
                    StudioSlider("Drag",cfg.drag,0.0f,10.0f,0.0f,"",0.1f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){l->l.copy(config=(l.config as LayerConfig.Particles).copy(drag=v))}},onCommit=::endDrag)
                    StudioSlider("Beat burst count",cfg.burstCount.toFloat(),0.0f,256.0f,0.0f,"",1.0f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){l->l.copy(config=(l.config as LayerConfig.Particles).copy(burstCount=v.toInt()))}},onCommit=::endDrag)
                    StudioSlider("Burst threshold",cfg.burstThreshold,0.05f,1.0f,0.65f,"",0.01f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){l->l.copy(config=(l.config as LayerConfig.Particles).copy(burstThreshold=v))}},onCommit=::endDrag)
                    StudioChipSelector("Burst provider",uiState.project.audioProviders.map{it.index},cfg.burstProvider,{"#$it"}){v->vm.updateLayer(layer.common.id){l->l.copy(config=(l.config as LayerConfig.Particles).copy(burstProvider=v))}}
                }
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
                    StudioSlider("Fade-in Size",cfg.fadeInSize,0f,20f,3f,"",.1f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){it.copy(config=(it.config as LayerConfig.Particles).copy(fadeInSize=v))}},onCommit=::endDrag)
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
                    StudioSlider("Perspective Depth (0 = 2D)", cfg.perspectiveDepth, 0f, 2000f, 900f, "", 10f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(perspectiveDepth = v)) }
                    }, onCommit = ::endDrag)
                    StudioSlider("Perspective FOV", cfg.perspectiveFov, 20f, 150f, 48.75f, "deg", 0.5f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(perspectiveFov = v)) }
                    }, onCommit = ::endDrag)
                    StudioSlider("Near Camera Fade", cfg.nearCameraFadeOutDistance, 0f, 500f, 75f, "", 1f, onValueChange = { v ->
                        beginDrag(); vm.updateLayer(layer.common.id, false) { it.copy(config = cfg.copy(nearCameraFadeOutDistance = v)) }
                    }, onCommit = ::endDrag)
                }
                ExpandableSectionHeader("Texture, mirrors & trails",expandedSections["Particles_texture"]==true,{toggleAccordion(expandedSections,"Particles_texture")}){
                    MediaAssetButton("IMPORT PARTICLE TEXTURE"){uri->vm.attachLayerAsset(layer.common.id,uri,"particle")}
                    StudioChipSelector("Sprite", (listOf("internalres:particle_circle_blur4","internalres:white","internalres:flare")+cfg.customImage).distinct(),cfg.customImage,{it.substringAfterLast('/').substringAfter("internalres:")}){v->vm.updateLayer(layer.common.id){it.copy(config=(it.config as LayerConfig.Particles).copy(customImage=v))}}
                    StudioToggleRow("Mirror X",cfg.mirrorX){v->vm.updateLayer(layer.common.id){it.copy(config=(it.config as LayerConfig.Particles).copy(mirrorX=v))}}
                    StudioToggleRow("Mirror Y",cfg.mirrorY){v->vm.updateLayer(layer.common.id){it.copy(config=(it.config as LayerConfig.Particles).copy(mirrorY=v))}}
                    StudioToggleRow("Velocity Angle",cfg.velocityAngle){v->vm.updateLayer(layer.common.id){it.copy(config=(it.config as LayerConfig.Particles).copy(velocityAngle=v))}}
                    StudioSlider("Trail Length",cfg.trailLength,0f,1f,0f,"",.01f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){it.copy(config=(it.config as LayerConfig.Particles).copy(trailLength=v))}},onCommit=::endDrag)
                    StudioSlider("Random Seed",cfg.seed.toFloat(),0f,10000f,42f,"",1f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){it.copy(config=(it.config as LayerConfig.Particles).copy(seed=v.toInt()))}},onCommit=::endDrag)
                    MeasureBindingControls("Overall Speed",cfg.measureOverallSpeed,uiState.project.audioProviders.map{it.index},::beginDrag,::endDrag){v,undo->vm.updateLayer(layer.common.id,undo){it.copy(config=(it.config as LayerConfig.Particles).copy(measureOverallSpeed=v))}}
                }
                ExpandableSectionHeader("Colors", expandedSections["Particles_Colors"] == true, { toggleAccordion(expandedSections, "Particles_Colors") }) {
                    StudioColorPickerRow("Particle Tint",cfg.colorArgb){v->vm.updateLayer(layer.common.id){it.copy(config=(it.config as LayerConfig.Particles).copy(colorArgb=v))}}
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
                    MediaAssetButton("IMPORT BLUR MASK"){uri->vm.attachLayerAsset(layer.common.id,uri,"mask")}
                    TextButton(onClick={vm.updateLayer(layer.common.id){it.copy(config=(it.config as LayerConfig.BlurEffect).copy(maskImage=""))}}){Text("CLEAR MASK")}
                    MeasureBindingControls("Blur Audio Binding",cfg.measureBlur,uiState.project.audioProviders.map{it.index},::beginDrag,::endDrag){v,undo->vm.updateLayer(layer.common.id,undo){it.copy(config=(it.config as LayerConfig.BlurEffect).copy(measureBlur=v))}}
                    StudioChipSelector("Glow Blend",BlendMode.entries,cfg.blendModeContent,{it.label}){v->vm.updateLayer(layer.common.id){it.copy(config=(it.config as LayerConfig.BlurEffect).copy(blendModeContent=v))}}
                    StudioChipSelector("Original Content Blend",BlendMode.entries,cfg.unblurredBlendMode?:cfg.blendModeContent,{it.label}){v->vm.updateLayer(layer.common.id){it.copy(config=(it.config as LayerConfig.BlurEffect).copy(unblurredBlendMode=v))}}
                    StudioSlider("Glow 1 Scale",cfg.layer1Scale,0f,4f,1f,"x",.01f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){it.copy(config=(it.config as LayerConfig.BlurEffect).copy(layer1Scale=v))}},onCommit=::endDrag)
                    StudioColorPickerRow("Glow 1 Color",cfg.layer1ColorArgb){v->vm.updateLayer(layer.common.id){it.copy(config=(it.config as LayerConfig.BlurEffect).copy(layer1ColorArgb=v))}}
                    StudioSlider("Glow 2 Scale",cfg.layer2Scale,0f,4f,0f,"x",.01f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){it.copy(config=(it.config as LayerConfig.BlurEffect).copy(layer2Scale=v))}},onCommit=::endDrag)
                    StudioColorPickerRow("Glow 2 Color",cfg.layer2ColorArgb){v->vm.updateLayer(layer.common.id){it.copy(config=(it.config as LayerConfig.BlurEffect).copy(layer2ColorArgb=v))}}
                    StudioSlider("Glow 3 Scale",cfg.layer3Scale,0f,4f,0f,"x",.01f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){it.copy(config=(it.config as LayerConfig.BlurEffect).copy(layer3Scale=v))}},onCommit=::endDrag)
                    StudioColorPickerRow("Glow 3 Color",cfg.layer3ColorArgb){v->vm.updateLayer(layer.common.id){it.copy(config=(it.config as LayerConfig.BlurEffect).copy(layer3ColorArgb=v))}}
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
                    StudioToggleRow("Source Under",cfg.showUnblurredContentUnder){v->vm.updateLayer(layer.common.id){it.copy(config=(it.config as LayerConfig.RgbSplitEffect).copy(showUnblurredContentUnder=v))}}
                    StudioToggleRow("Source Above",cfg.showUnblurredContent){v->vm.updateLayer(layer.common.id){it.copy(config=(it.config as LayerConfig.RgbSplitEffect).copy(showUnblurredContent=v))}}
                }
                ExpandableSectionHeader("Channel offsets", expandedSections["RgbSplitEffect_Channeloffsets"] == true, { toggleAccordion(expandedSections, "RgbSplitEffect_Channeloffsets") }) {
                    MeasureBindingControls("RGB Split Audio Binding",cfg.splitMeasure,uiState.project.audioProviders.map{it.index},::beginDrag,::endDrag){v,undo->vm.updateLayer(layer.common.id,undo){it.copy(config=(it.config as LayerConfig.RgbSplitEffect).copy(splitMeasure=v))}}
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
                    StudioChipSelector("Motion Source",com.vexo.studio.model.MotionSource.entries,cfg.motionSource,{it.label}){v->vm.updateLayer(layer.common.id){it.copy(config=(it.config as LayerConfig.MotionBlurEffect).copy(motionSource=v))}}
                    StudioToggleRow("Relative Motion",cfg.relativeMotionMode){v->vm.updateLayer(layer.common.id){it.copy(config=(it.config as LayerConfig.MotionBlurEffect).copy(relativeMotionMode=v))}}
                    if(cfg.motionSource==com.vexo.studio.model.MotionSource.MANUAL){
                        StudioSlider("Motion X",cfg.manualMotionX,-.25f,.25f,.025f,"",.001f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){it.copy(config=(it.config as LayerConfig.MotionBlurEffect).copy(manualMotionX=v))}},onCommit=::endDrag)
                        StudioSlider("Motion Y",cfg.manualMotionY,-.25f,.25f,0f,"",.001f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){it.copy(config=(it.config as LayerConfig.MotionBlurEffect).copy(manualMotionY=v))}},onCommit=::endDrag)
                    }
                    StudioSlider("Trail Persistence",cfg.trailPersistence,0f,.95f,.65f,"",.01f,onValueChange={v->beginDrag();vm.updateLayer(layer.common.id,false){it.copy(config=(it.config as LayerConfig.MotionBlurEffect).copy(trailPersistence=v))}},onCommit=::endDrag)
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
                Text("#${p.index} ${p.tag}", style = MaterialTheme.typography.labelSmall, color = if (active) palette.accentOn else palette.textPrimary)
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

    var providerAction by remember(provider.id){mutableStateOf("")}
    var providerName by remember(provider.id){mutableStateOf(provider.tag)}
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())){
        TextButton(onClick={providerName=provider.tag;providerAction="rename"}){Text("RENAME")}
        TextButton(enabled=uiState.project.audioProviders.size<16,onClick={vm.duplicateAudioProvider(provider.index)}){Text("DUPLICATE")}
        TextButton(onClick={providerAction="reset"}){Text("RESET")}
        TextButton(enabled=uiState.project.audioProviders.size>1,onClick={providerAction="delete"}){Text("DELETE")}
    }
    if(providerAction.isNotEmpty())AlertDialog(onDismissRequest={providerAction=""},title={Text(providerAction.uppercase()+" PROVIDER")},text={
        when(providerAction){
            "rename"->OutlinedTextField(providerName,{providerName=it.take(80)},singleLine=true,label={Text("Provider name")})
            "delete"->Text("Bindings using this provider will move to the first remaining provider.")
            else->Text("Reset analysis parameters to defaults while keeping the provider identity.")
        }
    },confirmButton={TextButton(enabled=providerAction!="rename"||providerName.isNotBlank(),onClick={
        when(providerAction){"rename"->vm.renameAudioProvider(provider.index,providerName);"delete"->vm.deleteAudioProvider(provider.index);"reset"->vm.resetAudioProvider(provider.index)}
        providerAction=""
    }){Text("CONFIRM")}},dismissButton={TextButton(onClick={providerAction=""}){Text("CANCEL")}})
    ExpandableSectionHeader(
        title = "GENERAL & SPECTRUM",
        expanded = expandedSections["provider_general"] ?: false,
        onToggle = { toggleAccordion(expandedSections, "provider_general") }
    ) {
        StudioChipSelector("Sample Mode", SampleProviderType.entries, provider.sampleProvider, { it.aveeName }) { mode ->
            vm.updateAudioProvider(provider.index) { it.copy(sampleProvider = mode) }
        }
        StudioChipSelector("FFT Size", listOf(256, 512, 1024, 2048, 4096, 8192), provider.fftSize, { "$it" }) { sz ->
            vm.updateAudioProvider(provider.index) { it.copy(fftSize = sz,hopSize=it.hopSize.coerceAtMost(sz)) }
        }
        StudioChipSelector("Window", WindowType.entries, provider.window, { it.label }) { win ->
            vm.updateAudioProvider(provider.index) { it.copy(window = win) }
        }
        StudioChipSelector("Channel Mode", ChannelMode.entries, provider.channelMode, { it.label }) { ch ->
            vm.updateAudioProvider(provider.index) { it.copy(channelMode = ch) }
        }
        StudioToggleRow("Provider Enabled", provider.enabled) { v -> vm.updateAudioProvider(provider.index) { it.copy(enabled = v) } }
        StudioSlider("Pre-smoothing",provider.preSmooth,0f,1f,.5f,"",.01f,onValueChange={v->onBeginDrag();vm.updateAudioProvider(provider.index,false){it.copy(preSmooth=v)}},onCommit=onEndDrag)
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
        StudioChipSelector("FFT hop (samples)",listOf(64,128,256,512,1024,2048,4096).filter{it<=provider.fftSize},provider.hopSize,{"$it"}){v->vm.updateAudioProvider(provider.index){it.copy(hopSize=v)}}
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
    val wasOpen=sections[key] ?: (key=="transform"&&sections.isEmpty())
    sections.clear()
    sections["transform"]=false
    sections[key]=!wasOpen
}

private val LocalInspectorQuery=staticCompositionLocalOf{ "" }

@Composable
internal fun ExpandableSectionHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    val query=LocalInspectorQuery.current
    if(query.isNotBlank()&&!InspectorSearch.matches(title,query))return
    val opened=expanded
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
            Text(title, style = MaterialTheme.typography.bodyMedium, color = if (opened) palette.accent else palette.textPrimary, modifier = Modifier.weight(1f))
            Icon(if (opened) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (opened) "Collapse" else "Expand", tint = palette.textSecondary, modifier = Modifier.size(20.dp))
        }
        if (opened) {
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
    var pendingTexture by remember(uiState.project.id,selected?.common?.id){mutableStateOf<String?>(null)}
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

        ProjectAssetInventory(vm,uiState)
        if (selected?.type == LayerType.IMAGE && selected.common.locked)Text("Unlock this layer before replacing its media.",style=MaterialTheme.typography.labelSmall,color=palette.danger)
        if (selected?.type == LayerType.IMAGE && !selected.common.locked) {
            val cfg = selected.config as LayerConfig.Image
            Text("BUILT-IN TEXTURES", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            listOf(
                "internalres:vignette80" to "Studio Vignette 80",
                "internalres:particle_circle_blur4" to "Soft Radial Glow",
                "internalres:white" to "Pure White",
                "internalres:black" to "Solid Black"
            ).forEach { (resKey, label) ->
                val active = cfg.sourceType == ImageSourceType.INTERNAL && cfg.internalRes == resKey
                val pending=pendingTexture==resKey
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, if (active||pending) palette.accent else palette.border, palette.shape)
                        .background(palette.glassPanel, palette.shape)
                        .clickable { pendingTexture=if(pending)null else resKey }
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    Text(if(pending)"SELECTED" else if (active) "ACTIVE" else "SELECT", style = MaterialTheme.typography.labelSmall, color = palette.accent)
                }
            }
            pendingTexture?.let{key->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){
                    TextButton(onClick={pendingTexture=null}){Text("CANCEL")}
                    TextButton(onClick={vm.updateLayer(selected.common.id){l->if(l.common.locked)l else l.copy(config=(l.config as LayerConfig.Image).copy(sourceType=ImageSourceType.INTERNAL,internalRes=key,videoEnabled=false,customUri=null))};pendingTexture=null}){Text("APPLY")}
                }
            }
            TextButton(onClick={vm.updateLayer(selected.common.id){l->if(l.common.locked)l else l.copy(config=(l.config as LayerConfig.Image).copy(sourceType=ImageSourceType.ALBUM_ART,customUri=null,videoEnabled=false))};pendingTexture=null}){Text("RESTORE ALBUM ART")}
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
        LayerPresetLibrary(vm)
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
        CompactStudioSearch(query,{query=it.take(80)},"Find a preset",Modifier.fillMaxWidth())
        builtIns.filter { it.name.contains(query, ignoreCase = true) }.forEach { tpl ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, palette.border, palette.shape)
                    .background(palette.glassPanel, palette.shape)
                    .clickable { vm.applyPreset(tpl) }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(tpl.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("${tpl.compositions.sumOf { it.layers.size }} layers · Current canvas ratio", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
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

@Composable
private fun MediaAssetButton(label:String,onPicked:(android.net.Uri)->Unit){
    var pending by remember{mutableStateOf<android.net.Uri?>(null)}
    val picker=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()){uri->pending=uri}
    TextButton(onClick={picker.launch(arrayOf("image/*"))}){Text(label)}
    pending?.let{uri->AlertDialog(onDismissRequest={pending=null},title={Text("Apply asset?")},text={Text("Use selected image for this property?")},confirmButton={TextButton(onClick={onPicked(uri);pending=null}){Text("APPLY")}},dismissButton={TextButton(onClick={pending=null}){Text("CANCEL")}})}
}

@Composable
private fun MeasureBindingControls(label:String,cfg:com.vexo.studio.model.MeasureConfig,ids:List<Int>,begin:()->Unit,end:()->Unit,onChange:(com.vexo.studio.model.MeasureConfig,Boolean)->Unit){
    Text(label.uppercase(),style=MaterialTheme.typography.labelMedium)
    StudioChipSelector("Measure",MeasureWhat.entries.filter{it!=MeasureWhat.KEYFRAME},cfg.measureWhat,{it.label}){v->onChange(cfg.copy(measureWhat=v),true)}
    StudioChipSelector("Audio Provider",(ids+cfg.providerIndex).distinct(),cfg.providerIndex,{"#$it"}){v->onChange(cfg.copy(providerIndex=v),true)}
    StudioSlider("Amount X",cfg.amountA,-8f,8f,0f,"",.01f,onValueChange={v->begin();onChange(cfg.copy(amountA=v),false)},onCommit=end)
    StudioSlider("Amount Y",cfg.amountB,-8f,8f,0f,"",.01f,onValueChange={v->begin();onChange(cfg.copy(amountB=v),false)},onCommit=end)
    StudioSlider("Trigger Threshold",cfg.triggerThreshold,0f,.99f,0f,"",.01f,onValueChange={v->begin();onChange(cfg.copy(triggerThreshold=v),false)},onCommit=end)
}

@UnstableApi
@Composable
private fun TransformKeyframePanel(vm:StudioViewModel,ui:StudioUiState,layer:LayerNode){
    val playback by vm.playbackState.collectAsState()
    val keys=com.vexo.studio.engine.scene.LayerPropertyAnimation.keys(layer)
    var selectedKey by rememberSaveable(layer.common.id){mutableStateOf("posX")}
    val key=selectedKey.takeIf{it in keys}?:keys.first()
    var snap by rememberSaveable(layer.common.id){mutableStateOf(true)}
    var selectedFrame by remember(layer.common.id,key){mutableStateOf<Long?>(null)}
    fun snapTime(t:Long):Long=if(snap)com.vexo.studio.engine.scene.LayerPropertyAnimation.snap(t,ui.project.exportSettings.fps)else t.coerceIn(0L,86400000L)
    var mode by remember{mutableStateOf(com.vexo.studio.model.InterpolationType.LINEAR)}
    var draft by remember(layer.common.id,key){mutableStateOf(com.vexo.studio.engine.scene.LayerPropertyAnimation.value(layer,key).toString())}
    val track=ui.project.keyframes.firstOrNull{it.targetLayerId==layer.common.id&&it.propertyKey==com.vexo.studio.engine.scene.LayerPropertyAnimation.trackKey(key)}
    val frames=track?.keyframes.orEmpty().sortedBy{it.timeMs}
    val palette=LocalVexoPalette.current
    var editTime by remember(layer.common.id,key){mutableStateOf<Long?>(null)}
    var timeDraft by remember{mutableStateOf("")}
    val duration=maxOf(1000L,playback.durationMs,frames.lastOrNull()?.timeMs?:0L)
    var draggedTime by remember(layer.common.id,key,track){mutableStateOf<Long?>(null)}
    var dragTarget by remember(layer.common.id,key,track){mutableStateOf<Long?>(null)}
    var dragStartX by remember{mutableStateOf(0f)}
    StudioChipSelector("Property",keys,key,{com.vexo.studio.engine.scene.LayerPropertyAnimation.label(it)}){selectedKey=it}
    StudioToggleRow("Snap to export frames",snap){snap=it}
    StudioChipSelector("Interpolation",com.vexo.studio.model.InterpolationType.entries,mode,{it.name}){mode=it}
    CurvePreview(mode)
    Text("Playhead: "+formatDuration(playback.positionMs),style=MaterialTheme.typography.labelMedium)
    Text("Drag a diamond to move its time. Tap ruler to seek.",style=MaterialTheme.typography.labelSmall)
    Canvas(Modifier.fillMaxWidth().height(44.dp).background(palette.panelElevated).pointerInput(duration,playback.durationMs){
        if(playback.durationMs>0)detectTapGestures{p->vm.playbackController.seekTo((p.x/size.width.coerceAtLeast(1)*duration).toLong().coerceIn(0L,playback.durationMs))}
    }.pointerInput(duration,track,snap,ui.project.exportSettings.fps){
        detectHorizontalDragGestures(onDragStart={p->
            val closest=frames.minByOrNull{kotlin.math.abs(it.timeMs.toFloat()/duration*size.width-p.x)}
            if(closest!=null&&kotlin.math.abs(closest.timeMs.toFloat()/duration*size.width-p.x)<=18.dp.toPx()){
                draggedTime=closest.timeMs;dragTarget=closest.timeMs;dragStartX=p.x-closest.timeMs.toFloat()/duration*size.width
            }
        },onDragEnd={
            val old=draggedTime;val next=dragTarget
            if(old!=null&&next!=null&&old!=next)vm.moveTransformKeyframe(layer.common.id,key,old,snapTime(next))
            draggedTime=null;dragTarget=null
        },onDragCancel={draggedTime=null;dragTarget=null}){change,_->
            if(draggedTime!=null){change.consume();dragTarget=(((change.position.x-dragStartX)/size.width.coerceAtLeast(1))*duration).toLong().coerceIn(0L,duration)}
        }
    }){
        val y=size.height*.5f
        drawLine(palette.border,Offset(0f,y),Offset(size.width,y),2.dp.toPx())
        frames.forEach{frame->
            val t=if(frame.timeMs==draggedTime)dragTarget?:frame.timeMs else frame.timeMs
            val x=t.toFloat()/duration*size.width
            val r=if(frame.timeMs==draggedTime)6.dp.toPx() else 4.dp.toPx()
            val diamond=androidx.compose.ui.graphics.Path().apply{moveTo(x,y-r);lineTo(x+r,y);lineTo(x,y+r);lineTo(x-r,y);close()}
            drawPath(diamond,if(frame.timeMs==selectedFrame)palette.textPrimary else palette.accent)
        }
        val x=(playback.positionMs.toFloat()/duration).coerceIn(0f,1f)*size.width
        drawLine(palette.textPrimary,Offset(x,0f),Offset(x,size.height),1.dp.toPx())
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())){
        TextButton(enabled=frames.any{it.timeMs<playback.positionMs},onClick={frames.lastOrNull{it.timeMs<playback.positionMs}?.let{vm.playbackController.seekTo(it.timeMs)}}){Text("PREV KEY")}
        TextButton(enabled=frames.any{it.timeMs>playback.positionMs},onClick={frames.firstOrNull{it.timeMs>playback.positionMs}?.let{vm.playbackController.seekTo(it.timeMs)}}){Text("NEXT KEY")}
        TextButton(onClick={draft=com.vexo.studio.engine.scene.KeyframeEvaluator.evaluate(track,playback.positionMs,com.vexo.studio.engine.scene.LayerPropertyAnimation.value(layer,key)).toString()}){Text("READ VALUE")}
    }
    val editingTime=selectedFrame?.takeIf{t->frames.any{it.timeMs==t}}
    if(editingTime!=null){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Text("EDITING KEY: "+formatDuration(editingTime),color=palette.accent,style=MaterialTheme.typography.labelSmall,modifier=Modifier.weight(1f))
            TextButton(onClick={selectedFrame=null}){Text("USE PLAYHEAD")}
        }
    }
    OutlinedTextField(draft,{draft=it.take(24)},label={Text(if(editingTime!=null)"Keyframe value" else "Value at playhead")},singleLine=true,modifier=Modifier.fillMaxWidth())
    val value=draft.toFloatOrNull()?.takeIf{it.isFinite()}
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
        TextButton(enabled=value!=null&&!layer.common.locked,onClick={value?.let{vm.putTransformKeyframe(layer.common.id,key,editingTime?:snapTime(playback.positionMs),it,mode)}}){Text("ADD / UPDATE KEY")}
        TextButton(enabled=track!=null&&!layer.common.locked,onClick={vm.removeTransformKeyframe(layer.common.id,key,null);selectedFrame=null}){Text("CLEAR")}
    }
    frames.take(500).forEach{frame->
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Text(formatDuration(frame.timeMs)+" · "+("%.3f".format(frame.value))+" · "+frame.interpolation.name,modifier=Modifier.weight(1f),style=MaterialTheme.typography.labelSmall)
            TextButton(onClick={selectedFrame=frame.timeMs;vm.playbackController.seekTo(frame.timeMs);draft=frame.value.toString();mode=frame.interpolation}){Text("EDIT")}
            TextButton(onClick={editTime=frame.timeMs;timeDraft=(frame.timeMs/1000.0).toString()}){Text("TIME")}
            TextButton(onClick={vm.removeTransformKeyframe(layer.common.id,key,frame.timeMs)}){Text("X")}
        }
    }
    editTime?.let{old->
        val value=timeDraft.toDoubleOrNull()?.takeIf{it.isFinite()&&it>=0&&it<=86400}
        AlertDialog(onDismissRequest={editTime=null},title={Text("MOVE KEYFRAME")},text={
            OutlinedTextField(timeDraft,{timeDraft=it.take(16)},label={Text("Time in seconds")},singleLine=true)
        },confirmButton={TextButton(enabled=value!=null,onClick={value?.let{vm.moveTransformKeyframe(layer.common.id,key,old,snapTime((it*1000).toLong()))};editTime=null}){Text("MOVE")}},dismissButton={TextButton(onClick={editTime=null}){Text("CANCEL")}})
    }

}

@Composable
private fun TimingNumber(label:String,ms:Long,commit:(Long)->Unit){
    var draft by remember(ms){mutableStateOf((ms/1000.0).toString())}
    val parsed=draft.toDoubleOrNull()?.takeIf{it.isFinite()&&it>=0&&it<=86400}
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        OutlinedTextField(draft,{draft=it.take(16)},label={Text(label+" (s)")},singleLine=true,modifier=Modifier.weight(1f),isError=parsed==null)
        TextButton(enabled=parsed!=null,onClick={parsed?.let{commit((it*1000).toLong())}}){Text("SET")}
    }
}

@Composable
private fun CurvePreview(mode:com.vexo.studio.model.InterpolationType){
    val palette=LocalVexoPalette.current
    Canvas(Modifier.fillMaxWidth().height(64.dp).background(palette.panelElevated)){
        val pad=6.dp.toPx()
        fun y(v:Float)=size.height-pad-(v+.15f)/1.3f*(size.height-2*pad)
        drawLine(palette.border,Offset(pad,y(0f)),Offset(size.width-pad,y(0f)),1.dp.toPx())
        drawLine(palette.border,Offset(pad,y(1f)),Offset(size.width-pad,y(1f)),1.dp.toPx())
        val path=androidx.compose.ui.graphics.Path()
        for(i in 0..64){
            val t=i/64f
            val x=pad+(size.width-2*pad)*t
            val y=y(com.vexo.studio.engine.scene.AnimationCurves.ease(mode,t))
            if(i==0)path.moveTo(x,y)else path.lineTo(x,y)
        }
        drawPath(path,palette.accent,style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
    }
}

@UnstableApi
@Composable
private fun MotionPresetPanel(vm:StudioViewModel,layer:LayerNode){
    val playback by vm.playbackState.collectAsState()
    val saved by vm.motionPresets.collectAsState()
    var recipe by remember{mutableStateOf(com.vexo.studio.engine.scene.MotionRecipe.FADE_IN)}
    var duration by remember{mutableStateOf(1f)}
    var strength by remember{mutableStateOf(1f)}
    var selectedSaved by remember{mutableStateOf("")}
    var name by remember{mutableStateOf("")}
    var confirm by remember{mutableStateOf(false)}
    var useSaved by remember{mutableStateOf(false)}
    StudioChipSelector("Motion preset",com.vexo.studio.engine.scene.MotionRecipe.entries,recipe,{it.label}){recipe=it}
    StudioSlider("Preset duration",duration,.1f,10f,1f,"s",.1f,onValueChange={duration=it})
    StudioSlider("Strength",strength,0f,2f,1f,"x",.05f,onValueChange={strength=it})
    TextButton(onClick={useSaved=false;confirm=true}){Text("APPLY AT PLAYHEAD")}
    OutlinedTextField(name,{name=it.take(60)},label={Text("Save keyframes as preset")},singleLine=true,modifier=Modifier.fillMaxWidth())
    TextButton(enabled=name.isNotBlank(),onClick={vm.saveMotionPreset(layer.common.id,name);name=""}){Text("SAVE MOTION PRESET")}
    if(saved.isNotEmpty()){
        StudioChipSelector("Saved motion",saved.map{it.name},selectedSaved,{it}){selectedSaved=it}
        Row(Modifier.horizontalScroll(rememberScrollState())){
            TextButton(enabled=saved.any{it.name==selectedSaved},onClick={useSaved=true;confirm=true}){Text("APPLY SAVED")}
            TextButton(enabled=saved.any{it.name==selectedSaved},onClick={vm.deleteMotionPreset(selectedSaved);selectedSaved=""}){Text("REMOVE SAVED")}
        }
    }
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("APPLY ANIMATION?")},text={Text("Replaces existing keyframes for the affected transform properties. Saved presets use absolute values and the selected duration.")},confirmButton={TextButton(onClick={vm.applyMotionPreset(layer.common.id,if(useSaved)null else recipe,if(useSaved)selectedSaved else null,playback.positionMs,(duration*1000).toLong(),strength);confirm=false}){Text("APPLY")}},dismissButton={TextButton(onClick={confirm=false}){Text("CANCEL")}})
}

@UnstableApi
@Composable
private fun LayerPresetSavePanel(vm:StudioViewModel,layer:LayerNode){
    var open by remember(layer.common.id){mutableStateOf(false)}
    var name by remember(layer.common.id){mutableStateOf(layer.common.name)}
    val supported=com.vexo.studio.engine.scene.SceneGraph.referencesOf(layer.config).isEmpty()
    TextButton(enabled=supported,onClick={open=true}){Text("SAVE LAYER PRESET")}
    if(!supported)Text("Composition-dependent layers can be saved in a VEX project instead.",style=MaterialTheme.typography.labelSmall)
    if(open)AlertDialog(onDismissRequest={open=false},title={Text("SAVE LAYER STYLE")},text={
        Column{
            OutlinedTextField(name,{name=it.take(60)},label={Text("Preset name")},singleLine=true)
            Text("Includes transform, native effects, timing and keyframes. Imported image/video becomes album art; external masks, fonts and textures are not embedded. Same name replaces the saved preset.")
        }
    },confirmButton={TextButton(enabled=name.isNotBlank(),onClick={vm.saveLayerPreset(layer.common.id,name);open=false}){Text("SAVE")}},dismissButton={TextButton(onClick={open=false}){Text("CANCEL")}})
}
@UnstableApi
@Composable
private fun LayerPresetLibrary(vm:StudioViewModel){
    val palette=LocalVexoPalette.current
    val presets by vm.layerPresets.collectAsState()
    var query by rememberSaveable{mutableStateOf("")}
    var remove by remember{mutableStateOf<String?>(null)}
    Text("MY LAYER PRESETS · "+presets.size+"/32",style=MaterialTheme.typography.labelMedium,color=palette.accent)
    vm.currentSelectedLayer()?.let{LayerPresetSavePanel(vm,it)}
    if(presets.isEmpty())Text("Save a selected layer to reuse its style in another project.",style=MaterialTheme.typography.labelSmall)
    else {
        CompactStudioSearch(query,{query=it.take(80)},"Find layer preset",Modifier.fillMaxWidth())
        presets.filter{it.name.contains(query,true)||it.layer.type.label.contains(query,true)}.forEach{p->
            Row(Modifier.fillMaxWidth().background(palette.panelElevated).padding(6.dp),verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    Text(p.name,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium)
                    Text(p.layer.type.label+" · "+p.tracks.sumOf{it.keyframes.size}+" keys",style=MaterialTheme.typography.labelSmall,color=palette.textSecondary)
                }
                TextButton(onClick={vm.addSavedLayerPreset(p.id)}){Text("ADD")}
                IconButton(onClick={remove=p.id},modifier=Modifier.size(32.dp)){Icon(Icons.Default.Delete,"Delete preset",tint=palette.danger,modifier=Modifier.size(16.dp))}
            }
        }
    }
    remove?.let{id->AlertDialog(onDismissRequest={remove=null},title={Text("DELETE SAVED PRESET?")},text={Text("Existing project layers will not be affected.")},confirmButton={TextButton(onClick={vm.deleteLayerPreset(id);remove=null}){Text("DELETE")}},dismissButton={TextButton(onClick={remove=null}){Text("CANCEL")}})}
}
