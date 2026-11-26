package com.vexo.studio

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ArrowBack
import kotlinx.coroutines.delay
import androidx.compose.material.icons.filled.Save
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import com.vexo.studio.ui.theme.StudioText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.vexo.studio.model.NavDestination
import com.vexo.studio.model.StudioTab
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.DisposableEffect
import com.vexo.studio.ui.StudioViewModel
import com.vexo.studio.ui.screens.AudioLibraryScreen
import com.vexo.studio.ui.screens.EditorScreen
import com.vexo.studio.ui.screens.ExportScreen
import com.vexo.studio.ui.screens.ProjectsScreen
import com.vexo.studio.ui.screens.SettingsScreen
import com.vexo.studio.ui.screens.TemplatesScreen
import com.vexo.studio.ui.theme.LocalVexoPalette
import com.vexo.studio.ui.theme.VexoTheme

@UnstableApi
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: StudioViewModel = viewModel()
            val settings by vm.appSettings.collectAsState()
            VexoTheme(settings = settings) {
                VexoRootApp(vm = vm)
            }
        }
    }
}

@UnstableApi
@Composable
private fun VexoRootApp(vm: StudioViewModel) {
    val context = LocalContext.current
    val palette = LocalVexoPalette.current
    val uiState by vm.uiState.collectAsState()
    val playback by vm.playbackState.collectAsState()
    val sceneSnapshot = vm.sceneSnapshot
    val exportState by vm.exportState.collectAsState()
    val settings by vm.appSettings.collectAsState()
    DisposableEffect(context){
        val token=androidx.media3.session.SessionToken(context,android.content.ComponentName(context,com.vexo.studio.playback.VexoPlaybackService::class.java))
        val future=androidx.media3.session.MediaController.Builder(context,token).buildAsync()
        onDispose{androidx.media3.session.MediaController.releaseFuture(future)}
    }

    var showRenameProject by remember { mutableStateOf(false) }
    var showAppMenu by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.statusBanner) { if (uiState.statusBanner != null) { delay(3500); vm.clearBanner() } }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            vm.importAudioTracks(uris)
        }
    }

    var pendingImage by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingImageProject by remember { mutableStateOf("") }
    var pendingImageLayer by remember { mutableStateOf<String?>(null) }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingImage=uri;pendingImageProject=uiState.project.id;pendingImageLayer=uiState.selectedLayerId
        }
    }

    val presetImportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val name = DocumentFile.fromSingleUri(context, uri)?.name ?: "project.VEX"
            vm.importPresetFromUri(uri, name)
        }
    }

    val presetExportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            vm.exportPresetToUri(uri)
        }
    }

    val exportDestPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("video/mp4")
    ) { uri ->
        if (uri != null) {
            vm.updateExportSettings { it.copy(customOutputUri = uri.toString()) }
        }
    }

    Box(Modifier.fillMaxSize().background(palette.bg)) {
        if (settings.glassEnabled && uiState.navDestination != NavDestination.STUDIO) {
            val artwork = playback.trackMeta.artwork
            if (artwork != null && Build.VERSION.SDK_INT >= 31) {
                Image(bitmap = artwork.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(settings.backdropBlurDp.coerceIn(0f, 48f).dp))
                Box(Modifier.fillMaxSize().background(palette.bg.copy(alpha = 0.68f)))
            } else {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(palette.panelElevated, palette.bg))))
            }
        }
    Scaffold(
        containerColor = palette.bg,
        topBar = {
            if (!uiState.isFullscreenPreview) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.panel)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "VEXO",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.accent
                        )
                        Text(
                            text = uiState.project.name + if(uiState.unsaved) " *" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { showRenameProject = true }
                        )
                    }
                    Box {
                        IconButton(onClick = { showAppMenu = true },modifier=Modifier.size(36.dp)) { Icon(Icons.Default.MoreVert, "Studio menu", tint = palette.textSecondary) }
                        DropdownMenu(expanded = showAppMenu, onDismissRequest = { showAppMenu = false }) {
                            NavDestination.entries.forEach { dest -> DropdownMenuItem(text = { Text(dest.label) }, onClick = { showAppMenu = false; vm.navigateTo(dest) }) }
                        }
                    }
                    IconButton(onClick = {vm.saveCurrentProject()},modifier=Modifier.size(36.dp)) {
                        Icon(Icons.Default.Save, "Save to project library", tint = if(uiState.unsaved)palette.accent else palette.textSecondary)
                    }
                    Box(
                        modifier = Modifier
                            .background(palette.accent, palette.shape)
                            .clickable { vm.navigateTo(NavDestination.EXPORT) }
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Text("EXPORT", style = MaterialTheme.typography.labelMedium, color = palette.accentOn)
                    }
                }
            }
        },
        bottomBar = {
            if (!uiState.isFullscreenPreview) {
                Column {
                if (uiState.navDestination!=NavDestination.STUDIO && playback.queue.isNotEmpty()) {
                    com.vexo.studio.ui.screens.CompactTransportBar(playback, {vm.playbackController.togglePlayPause()}, {vm.playbackController.previous()}, {vm.playbackController.next()}, {vm.playbackController.seekTo(it)}, {audioPicker.launch(arrayOf("audio/*"))}, Modifier.fillMaxWidth().padding(horizontal=8.dp))
                }
                val navState=rememberLazyListState()
                val destinations=NavDestination.entries.filter{it!=NavDestination.STUDIO}
                val activeIndex=if(uiState.navDestination==NavDestination.STUDIO)StudioTab.entries.indexOf(uiState.studioTab) else StudioTab.entries.size+destinations.indexOf(uiState.navDestination)
                LaunchedEffect(activeIndex){if(activeIndex>=0&&navState.layoutInfo.visibleItemsInfo.none{it.index==activeIndex})navState.animateScrollToItem(activeIndex)}
                Surface(modifier=Modifier.fillMaxWidth().padding(horizontal=6.dp,vertical=4.dp),shape=RoundedCornerShape(18.dp),color=palette.panel,tonalElevation=2.dp,shadowElevation=3.dp){
                    LazyRow(state=navState,modifier=Modifier.fillMaxWidth().padding(horizontal=4.dp,vertical=3.dp),
                        horizontalArrangement=Arrangement.spacedBy(2.dp),verticalAlignment=Alignment.CenterVertically){
                        items(StudioTab.entries.size){i->
                            val tab=StudioTab.entries[i]
                            val glyph=when(tab){StudioTab.LAYERS->Icons.Default.Layers;StudioTab.PROPERTIES->Icons.Default.Tune;StudioTab.ASSETS->Icons.Default.Image;StudioTab.PRESETS->Icons.Default.Dashboard}
                            StudioNavItem(tab.label,glyph,uiState.navDestination==NavDestination.STUDIO&&uiState.studioTab==tab){vm.navigateTo(NavDestination.STUDIO);vm.selectStudioTab(tab)}
                        }
                        items(destinations.size){i->
                            val dest=destinations[i]
                            val glyph=when(dest){
                                NavDestination.STUDIO->Icons.Default.Tune
                                NavDestination.PROJECTS->Icons.Default.FolderOpen
                                NavDestination.AUDIO->Icons.Default.LibraryMusic
                                NavDestination.TEMPLATES->Icons.Default.Dashboard
                                NavDestination.EXPORT->Icons.Default.FileUpload
                                NavDestination.SETTINGS->Icons.Default.Settings
                            }
                            StudioNavItem(dest.label,glyph,uiState.navDestination==dest){vm.navigateTo(dest)}
                        }
                    }
                }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)){
            Column(Modifier.fillMaxSize()){
            when (uiState.navDestination) {
                NavDestination.STUDIO -> EditorScreen(
                    vm = vm,
                    uiState = uiState,
                    playback = playback,
                    sceneSnapshot = sceneSnapshot,
                    settings = settings,
                    onPickAudio = { audioPicker.launch(arrayOf("audio/*")) },
                    onPickImage = { imagePicker.launch(arrayOf("image/*","video/*")) },
                    onImportPreset = { presetImportPicker.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                    onExportPreset = { presetExportPicker.launch("${uiState.project.name}.VEX") }
                )
                NavDestination.PROJECTS -> ProjectsScreen(vm = vm, uiState = uiState)
                NavDestination.AUDIO -> AudioLibraryScreen(
                    vm = vm,
                    playback = playback,
                    onImportAudio = { audioPicker.launch(arrayOf("audio/*")) }
                )
                NavDestination.TEMPLATES -> TemplatesScreen(
                    vm = vm,
                    onImportPreset = { presetImportPicker.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }
                )
                NavDestination.EXPORT -> ExportScreen(
                    vm = vm,
                    uiState = uiState,
                    playback = playback,
                    exportState = exportState,
                    onPickExportUri = { exportDestPicker.launch("${uiState.project.name}.mp4") }
                )
                NavDestination.SETTINGS -> SettingsScreen(vm = vm, settings = settings)
            }
            }
            val banner=playback.errorMessage?:uiState.statusBanner
            if(banner!=null){
                val isError=playback.errorMessage!=null||banner.contains("failed",true)||banner.contains("error",true)
                val tint=if(isError)palette.danger else palette.accent
                Surface(modifier=Modifier.align(Alignment.TopCenter).padding(horizontal=12.dp,vertical=8.dp),shape=RoundedCornerShape(14.dp),color=palette.panelElevated,shadowElevation=8.dp,border=androidx.compose.foundation.BorderStroke(1.dp,tint.copy(alpha=.65f))){
                    Row(Modifier.padding(start=12.dp,end=4.dp,top=3.dp,bottom=3.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        Icon(if(isError)Icons.Default.Info else Icons.Default.CheckCircle,null,tint=tint,modifier=Modifier.size(20.dp))
                        Text(banner,style=MaterialTheme.typography.bodySmall,color=palette.textPrimary,modifier=Modifier.weight(1f,fill=false),maxLines=4,overflow=TextOverflow.Ellipsis)
                        IconButton(onClick={vm.clearBanner();vm.playbackController.clearError()},modifier=Modifier.size(36.dp)){Icon(Icons.Default.Close,"Dismiss message",tint=palette.textSecondary,modifier=Modifier.size(18.dp))}
                    }
                }
            }
        }
    }

    }

    uiState.pendingPreset?.let{tpl->AlertDialog(onDismissRequest={vm.resolvePresetSwitch(false,false)},title={Text("UNSAVED EDITS")},text={Text("Apply "+tpl.name+"? Save the current edits first, or discard them. Music and canvas ratio will stay unchanged.")},confirmButton={TextButton(onClick={vm.resolvePresetSwitch(true,false)}){Text("SAVE & APPLY")}},dismissButton={Row{TextButton(onClick={vm.resolvePresetSwitch(false,true)}){Text("DISCARD & APPLY")};TextButton(onClick={vm.resolvePresetSwitch(false,false)}){Text("CANCEL")}}})}
    pendingImage?.let{uri->AlertDialog(onDismissRequest={pendingImage=null},title={Text("APPLY IMAGE / VIDEO?")},text={Text("Apply selected media to the current layer, or cancel to keep the original.")},confirmButton={TextButton(onClick={if(uiState.project.id==pendingImageProject&&uiState.selectedLayerId==pendingImageLayer)vm.attachImageToSelectedLayer(uri);pendingImage=null}){Text("APPLY")}},dismissButton={TextButton(onClick={pendingImage=null}){Text("CANCEL")}})}
    if (showRenameProject) {
        var text by remember { mutableStateOf(uiState.project.name) }
        AlertDialog(
            onDismissRequest = { showRenameProject = false },
            title = { Text("RENAME PROJECT") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    vm.renameCurrentProject(text)
                    showRenameProject = false
                }) { Text("SAVE") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameProject = false }) { Text("CANCEL") }
            }
        )
    }
}

@Composable
private fun StudioNavItem(label:String,glyph:ImageVector,active:Boolean,onClick:()->Unit){
    val p=LocalVexoPalette.current
    Column(Modifier.widthIn(min=70.dp).heightIn(min=58.dp).clip(RoundedCornerShape(14.dp)).semantics{selected=active}.clickable(onClick=onClick).padding(horizontal=7.dp,vertical=5.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)){
        Box(Modifier.widthIn(min=44.dp).height(29.dp).background(if(active)p.accent else Color.Transparent,RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center){
            Icon(glyph,label,tint=if(active)p.accentOn else p.textSecondary,modifier=Modifier.size(21.dp))
        }
        Text(label,style=MaterialTheme.typography.labelSmall,fontWeight=if(active)FontWeight.SemiBold else FontWeight.Medium,color=if(active)p.accent else p.textSecondary,maxLines=1)
    }
}
