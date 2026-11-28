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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ArrowBack
import kotlinx.coroutines.delay
import androidx.compose.material.icons.outlined.Save
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
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Close
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.DisposableEffect
import com.vexo.studio.ui.StudioViewModel
import com.vexo.studio.ui.screens.AudioLibraryScreen
import com.vexo.studio.ui.screens.EditorScreen
import com.vexo.studio.ui.screens.ExportScreen
import com.vexo.studio.ui.screens.ProjectsScreen
import com.vexo.studio.ui.screens.SettingsScreen
import com.vexo.studio.ui.theme.LocalVexoPalette
import com.vexo.studio.ui.theme.VexoTheme

@UnstableApi
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The launch drawable must not remain behind SurfaceView holes in task snapshots.
        window.setBackgroundDrawableResource(android.R.color.black)
        window.decorView.setBackgroundColor(android.graphics.Color.BLACK)
        window.setFormat(android.graphics.PixelFormat.OPAQUE)
        @Suppress("DEPRECATION")
        setTaskDescription(android.app.ActivityManager.TaskDescription("Vexo",null,android.graphics.Color.BLACK))
        setContent {
            val vm: StudioViewModel = viewModel()
            val loaded by vm.loadedSettings.collectAsState()
            val settings=loaded
            if(settings==null){
                Box(Modifier.fillMaxSize().background(Color.Black))
            }else{
                VexoTheme(settings = settings) {
                    VexoRootApp(vm = vm, settings = settings)
                }
            }
        }
    }
}

@UnstableApi
@Composable
private fun VexoRootApp(vm: StudioViewModel, settings:com.vexo.studio.model.AppSettings) {
    val context = LocalContext.current
    val palette = LocalVexoPalette.current
    val uiState by vm.uiState.collectAsState()
    val playback by vm.playbackState.collectAsState()
    val sceneSnapshot = vm.sceneSnapshot
    val exportState by vm.exportState.collectAsState()
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

    var pendingImage by rememberSaveable { mutableStateOf<android.net.Uri?>(null) }
    var pendingImageProject by rememberSaveable { mutableStateOf("") }
    var pendingImageLayer by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingImageComposition by rememberSaveable { mutableStateOf(-1) }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingImage = uri
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
            runCatching{context.contentResolver.takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)}
            vm.updateExportSettings { it.copy(customOutputUri = uri.toString()) }
        }
    }

    val wallpaper by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null,settings.wallpaperUri){
        value=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){com.vexo.studio.data.UiWallpaper.decode(context,settings.wallpaperUri)}
    }
    var sceneGlass by remember(uiState.project.id){mutableStateOf<android.graphics.Bitmap?>(null)}
    Box(Modifier.fillMaxSize().background(palette.bg)) {
        if(wallpaper!=null){
            com.vexo.studio.ui.components.BlurredBackdrop(wallpaper!!,if(settings.glassEnabled)maxOf(settings.wallpaperBlurDp,settings.backdropBlurDp) else settings.wallpaperBlurDp,Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(palette.bg.copy(alpha=settings.wallpaperDim.coerceIn(.25f,.95f))))
        } else if (settings.glassEnabled) {
            val artwork = playback.trackMeta.artwork
            if (artwork != null) {
                com.vexo.studio.ui.components.BlurredBackdrop(artwork,settings.backdropBlurDp,Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(palette.bg.copy(alpha = 0.36f)))
            } else if(sceneGlass!=null){
                Image(bitmap=sceneGlass!!.asImageBitmap(),contentDescription=null,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(palette.bg.copy(alpha=.36f)))
            } else {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(palette.panelElevated, palette.bg))))
            }
        }
    Scaffold(
        containerColor = if (wallpaper!=null || (settings.glassEnabled)) Color.Transparent else palette.bg,
        contentColor = palette.textPrimary,
        topBar = {
            if (!uiState.isFullscreenPreview) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.glassPanel)
                        .statusBarsPadding()
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
                            NavDestination.entries.filter{it!=NavDestination.TEMPLATES}.forEach { dest -> DropdownMenuItem(text = { Text(dest.label) }, onClick = { showAppMenu = false; vm.navigateTo(dest) }) }
                        }
                    }
                    IconButton(onClick = {vm.saveCurrentProject()},modifier=Modifier.size(36.dp)) {
                        Icon(Icons.Outlined.Save, "Save to project library", tint = if(uiState.unsaved)palette.accent else palette.textSecondary)
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
                Column(Modifier.navigationBarsPadding()) {
                if (uiState.navDestination!=NavDestination.STUDIO && playback.queue.isNotEmpty()) {
                    com.vexo.studio.ui.screens.CompactTransportBar(playback, {vm.playbackController.togglePlayPause()}, {vm.playbackController.previous()}, {vm.playbackController.next()}, {vm.playbackController.seekTo(it)}, {audioPicker.launch(arrayOf("audio/*"))}, Modifier.fillMaxWidth().padding(horizontal=8.dp))
                }
                val destinations=listOf(NavDestination.STUDIO,NavDestination.PROJECTS,NavDestination.AUDIO,NavDestination.EXPORT,NavDestination.SETTINGS)
                Surface(modifier=Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=4.dp),shape=palette.navShape,color=palette.glassPanel,tonalElevation=0.dp,shadowElevation=0.dp,border=androidx.compose.foundation.BorderStroke(1.dp,palette.border)){
                    Row(Modifier.fillMaxWidth().padding(4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(2.dp)){
                        destinations.forEach{dest->
                            val glyph=when(dest){
                                NavDestination.STUDIO->Icons.Outlined.Dashboard
                                NavDestination.PROJECTS->Icons.Outlined.FolderOpen
                                NavDestination.AUDIO->Icons.Outlined.LibraryMusic
                                NavDestination.TEMPLATES->Icons.Outlined.Dashboard
                                NavDestination.EXPORT->Icons.Outlined.FileUpload
                                NavDestination.SETTINGS->Icons.Outlined.Settings
                            }
                            Box(Modifier.weight(1f),contentAlignment=Alignment.Center){
                                StudioNavItem(dest.label,glyph,uiState.navDestination==dest){vm.navigateTo(dest)}
                            }
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
                    onPickImage = {
                        pendingImageProject = uiState.project.id
                        pendingImageLayer = uiState.selectedLayerId
                        pendingImageComposition = uiState.selectedCompositionIndex
                        imagePicker.launch(arrayOf("image/*", "video/*"))
                    },
                    onImportPreset = { presetImportPicker.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                    onExportPreset = { presetExportPicker.launch("${uiState.project.name}.VEX") },
                    onSceneGlass={sceneGlass=it}
                )
                NavDestination.PROJECTS -> ProjectsScreen(vm = vm, uiState = uiState)
                NavDestination.AUDIO -> AudioLibraryScreen(
                    vm = vm,
                    playback = playback,
                    onImportAudio = { audioPicker.launch(arrayOf("audio/*")) }
                )
                NavDestination.TEMPLATES -> LaunchedEffect(Unit){vm.navigateTo(NavDestination.TEMPLATES)}
                NavDestination.EXPORT -> ExportScreen(
                    vm = vm,
                    uiState = uiState,
                    playback = playback,
                    exportState = exportState,
                    onPickExportUri = { filename -> exportDestPicker.launch(filename) }
                )
                NavDestination.SETTINGS -> SettingsScreen(vm = vm, settings = settings)
            }
            }
            val banner=playback.errorMessage?:uiState.statusBanner
            if(banner!=null){
                val isError=playback.errorMessage!=null||banner.contains("failed",true)||banner.contains("error",true)
                val tint=if(isError)palette.danger else palette.accent
                Surface(modifier=Modifier.align(Alignment.TopCenter).padding(horizontal=12.dp,vertical=8.dp),shape=palette.shape,color=palette.panelElevated,shadowElevation=8.dp,border=androidx.compose.foundation.BorderStroke(1.dp,tint.copy(alpha=.65f))){
                    Row(Modifier.padding(start=12.dp,end=4.dp,top=3.dp,bottom=3.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        Icon(if(isError)Icons.Outlined.Info else Icons.Default.CheckCircle,null,tint=tint,modifier=Modifier.size(20.dp))
                        Text(banner,style=MaterialTheme.typography.bodySmall,color=palette.textPrimary,modifier=Modifier.weight(1f,fill=false),maxLines=4,overflow=TextOverflow.Ellipsis)
                        IconButton(onClick={vm.clearBanner();vm.playbackController.clearError()},modifier=Modifier.size(36.dp)){Icon(Icons.Outlined.Close,"Dismiss message",tint=palette.textSecondary,modifier=Modifier.size(18.dp))}
                    }
                }
            }
        }
    }

    }

    uiState.pendingPreset?.let{tpl->
        UnsavedSwitchDialog("Apply "+tpl.name+"? Save your edits first, or discard them. Music and canvas ratio stay unchanged.","APPLY",{vm.resolvePresetSwitch(true,false)},{vm.resolvePresetSwitch(false,true)},{vm.resolvePresetSwitch(false,false)})
    }
    uiState.pendingProjectSwitch?.let{target->
        val action=when(target){
            is com.vexo.studio.ui.ProjectSwitch.Open->"OPEN PROJECT"
            is com.vexo.studio.ui.ProjectSwitch.Create->"CREATE PROJECT"
        }
        UnsavedSwitchDialog("Save the current project before switching, discard unsaved changes, or cancel to continue editing.",action,{vm.resolveProjectSwitch(true,false)},{vm.resolveProjectSwitch(false,true)},{vm.resolveProjectSwitch(false,false)})
    }
    pendingImage?.let { uri ->
        val targetMatches = uiState.project.id == pendingImageProject &&
            uiState.selectedLayerId == pendingImageLayer &&
            uiState.selectedCompositionIndex == pendingImageComposition
        val selectedLayer = uiState.project.compositions.firstOrNull { it.index == pendingImageComposition }?.layers?.firstOrNull { it.common.id == pendingImageLayer }
        val replacesImage = selectedLayer?.type == com.vexo.studio.model.LayerType.IMAGE
        val lockedImage = replacesImage && selectedLayer?.common?.locked == true
        AlertDialog(
            onDismissRequest = { pendingImage = null },
            title = { Text("APPLY IMAGE / VIDEO?") },
            text = { Text(when {
                !targetMatches -> "The original selection changed while choosing media. Cancel and select media again for the intended layer."
                lockedImage -> "Unlock the selected image layer before replacing media."
                replacesImage -> "Replace media in the selected image layer, or cancel to keep the original."
                else -> "Add selected media as a new image layer in this composition, or cancel."
            }) },
            confirmButton = {
                TextButton(enabled = targetMatches && !lockedImage, onClick = {
                    if (targetMatches) vm.attachImageToSelectedLayer(uri)
                    pendingImage = null
                }) { Text("APPLY") }
            },
            dismissButton = { TextButton(onClick = { pendingImage = null }) { Text("CANCEL") } }
        )
    }
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
    val fill by animateColorAsState(if(active)p.accent else Color.Transparent,label="nav selection")
    Box(Modifier.fillMaxWidth().height(48.dp).clip(p.navShape).background(fill,p.navShape).semantics{selected=active;contentDescription=label}.clickable(onClick=onClick),contentAlignment=Alignment.Center){
        Icon(glyph,null,tint=if(active)p.accentOn else p.textSecondary,modifier=Modifier.size(23.dp))
    }
}

@Composable
private fun UnsavedSwitchDialog(message:String,action:String,onSave:()->Unit,onDiscard:()->Unit,onCancel:()->Unit){
    val p=LocalVexoPalette.current
    androidx.compose.ui.window.Dialog(onDismissRequest=onCancel){
        Surface(shape=p.dialogShape,color=p.panelElevated,modifier=Modifier.fillMaxWidth()){
            Column(Modifier.fillMaxWidth().heightIn(max=460.dp).verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text("UNSAVED EDITS",style=MaterialTheme.typography.titleLarge,color=p.textPrimary)
                Text(message,style=MaterialTheme.typography.bodyMedium,color=p.textSecondary)
                androidx.compose.material3.Button(onClick=onSave,modifier=Modifier.fillMaxWidth(),shape=p.buttonShape){Text("SAVE & "+action)}
                androidx.compose.material3.OutlinedButton(onClick=onDiscard,modifier=Modifier.fillMaxWidth(),shape=p.buttonShape){Text("DISCARD & "+action,color=p.danger)}
                TextButton(onClick=onCancel,modifier=Modifier.fillMaxWidth()){Text("CANCEL")}
            }
        }
    }
}
