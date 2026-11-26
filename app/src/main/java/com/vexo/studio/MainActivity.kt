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
import androidx.compose.material3.Text
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

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            vm.attachImageToSelectedLayer(uri)
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
        if (settings.glassEnabled) {
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
        containerColor = Color.Transparent,
        topBar = {
            if (!uiState.isFullscreenPreview) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.glassPanel, palette.shape)
                        .border(1.dp, palette.border, palette.shape)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
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
                            text = uiState.project.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { showRenameProject = true }
                        )
                    }
                    Box {
                        IconButton(onClick = { showAppMenu = true }) { Icon(Icons.Default.MoreVert, "Studio menu", tint = palette.textSecondary) }
                        DropdownMenu(expanded = showAppMenu, onDismissRequest = { showAppMenu = false }) {
                            NavDestination.entries.forEach { dest -> DropdownMenuItem(text = { Text(dest.label) }, onClick = { showAppMenu = false; vm.navigateTo(dest) }) }
                        }
                    }
                    IconButton(onClick = { presetExportPicker.launch("${uiState.project.name}.VEX") }) {
                        Icon(Icons.Default.Save, "Save VEX project", tint = palette.textSecondary)
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
            if (!uiState.isFullscreenPreview && uiState.navDestination != NavDestination.STUDIO) {
                Column {
                if (playback.queue.isNotEmpty()) {
                    com.vexo.studio.ui.screens.CompactTransportBar(playback, {vm.playbackController.togglePlayPause()}, {vm.playbackController.previous()}, {vm.playbackController.next()}, {vm.playbackController.seekTo(it)}, {audioPicker.launch(arrayOf("audio/*"))}, Modifier.fillMaxWidth().padding(horizontal=8.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                        .background(palette.glassPanel, palette.shape).border(1.dp, palette.border, palette.shape)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val destinations = if (uiState.navDestination == NavDestination.STUDIO) listOf(NavDestination.STUDIO, NavDestination.PROJECTS, NavDestination.AUDIO, NavDestination.TEMPLATES, NavDestination.SETTINGS) else NavDestination.entries.toList()
                    destinations.forEach { dest ->
                        val active = dest == uiState.navDestination
                        val icon = when(dest) {
                            NavDestination.STUDIO -> Icons.Default.Tune
                            NavDestination.PROJECTS -> Icons.Default.FolderOpen
                            NavDestination.AUDIO -> Icons.Default.LibraryMusic
                            NavDestination.TEMPLATES -> Icons.Default.Dashboard
                            NavDestination.EXPORT -> Icons.Default.FileUpload
                            NavDestination.SETTINGS -> Icons.Default.Settings
                        }
                        Column(
                            modifier = Modifier.semantics { selected = active }.clickable { vm.navigateTo(dest) }
                                .heightIn(min = 56.dp).padding(horizontal = 8.dp, vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Box(Modifier.background(if(active) palette.accent else Color.Transparent, palette.shape).padding(horizontal = 12.dp, vertical = 5.dp)) {
                                Icon(icon, contentDescription = dest.label, tint = if(active) palette.accentOn else palette.textSecondary, modifier = Modifier.size(20.dp))
                            }
                            Text(dest.label, style = MaterialTheme.typography.labelSmall, color = if(active) palette.accent else palette.textSecondary, maxLines = 1)
                        }
                    }
                }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val banner = playback.errorMessage ?: uiState.statusBanner
            if (banner != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.panelElevated)
                        .border(1.dp, palette.accent, palette.shape)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(banner, style = MaterialTheme.typography.bodySmall, color = palette.textPrimary, modifier = Modifier.weight(1f))
                    Text(
                        "DISMISS",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.accent,
                        modifier = Modifier.clickable {
                            vm.clearBanner()
                            vm.playbackController.clearError()
                        }
                    )
                }
            }

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
    }

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
