package com.vexo.studio.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import com.vexo.studio.playback.LibraryTrack
import com.vexo.studio.playback.MediaLibrary
import com.vexo.studio.ui.StudioViewModel
import com.vexo.studio.ui.components.StudioChipSelector
import com.vexo.studio.ui.theme.LocalVexoPalette

@UnstableApi
@Composable
fun DeviceLibraryPanel(vm: StudioViewModel, onBack: () -> Unit) {
    val context=LocalContext.current
    val palette=LocalVexoPalette.current
    val permission=if(Build.VERSION.SDK_INT>=33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context,permission)==PackageManager.PERMISSION_GRANTED) }
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted=it }
    var tracks by remember { mutableStateOf<List<LibraryTrack>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("Tracks") }
    var group by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val store=remember{com.vexo.studio.playback.PlaylistStore(context)}
    var playlists by remember{mutableStateOf(store.read())}
    var saveDialog by remember{mutableStateOf(false)}
    var playlistName by remember{mutableStateOf("")}
    LaunchedEffect(granted) {
        if(granted) {
            loading=true
            runCatching { MediaLibrary(context).tracks() }.onSuccess { tracks=it }.onFailure { error=it.message }
            loading=false
        }
    }
    fun key(t:LibraryTrack)=when(mode) { "Album"->t.album; "Artist"->t.artist; "Folder"->t.folder; "Genre"->t.genre; else->t.title }.ifBlank{"Unknown"}
    val filtered=tracks.filter { (group==null||if(mode=="Playlist") it.uri in playlists[group.orEmpty()].orEmpty() else key(it)==group) && (it.title.contains(query,true)||it.artist.contains(query,true)||it.album.contains(query,true)) }
    Column(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick={if(group!=null)group=null else onBack()}){Text("BACK")}
            if(filtered.isNotEmpty() && (mode!="Playlist" || group!=null)) TextButton(onClick={vm.importAudioTracks(filtered.map{Uri.parse(it.uri)});onBack()}){Text("PLAY ALL")}
        }
        Text("DEVICE MUSIC",color=palette.accent,style=MaterialTheme.typography.titleMedium)
        if(!granted){Text("Allow audio access to browse local tracks. File import also works without this permission.");Button(onClick={launcher.launch(permission)}){Text("ALLOW AUDIO ACCESS")};return@Column}
        if(loading){LinearProgressIndicator(Modifier.fillMaxWidth());return@Column}
        error?.let { Text(it,color=palette.danger) }
        StudioChipSelector("Browse",listOf("Tracks","Album","Artist","Folder","Genre","Playlist"),mode,{it}){mode=it;group=null}
        OutlinedTextField(query,{query=it},label={Text("Search music")},singleLine=true,modifier=Modifier.fillMaxWidth())
        group?.let{Text(it,style=MaterialTheme.typography.titleSmall)}
        if(filtered.isNotEmpty() && (mode!="Playlist" || group!=null)) TextButton(onClick={playlistName="";saveDialog=true}){Text("SAVE SHOWN TRACKS AS PLAYLIST")}
        if(mode=="Playlist"&&group!=null) TextButton(onClick={store.delete(group!!);playlists=store.read();group=null}){Text("DELETE PLAYLIST")}
        if(saveDialog) AlertDialog(onDismissRequest={saveDialog=false},title={Text("Save playlist")},text={OutlinedTextField(playlistName,{playlistName=it},label={Text("Playlist name")})},confirmButton={TextButton(enabled=playlistName.isNotBlank(),onClick={store.save(playlistName,filtered.map{it.uri});playlists=store.read();saveDialog=false}){Text("SAVE")}},dismissButton={TextButton(onClick={saveDialog=false}){Text("CANCEL")}})
        if(mode=="Playlist"&&group==null){
            LazyColumn(Modifier.weight(1f)){items(playlists.keys.sorted()){name->ListItem(headlineContent={Text(name)},supportingContent={Text(playlists[name].orEmpty().size.toString()+" tracks")},modifier=Modifier.clickable{group=name})}}
        }else if(mode!="Tracks"&&group==null){
            LazyColumn(Modifier.weight(1f)){items(filtered.groupBy{key(it)}.entries.sortedBy{it.key.lowercase()}){entry ->
                ListItem(headlineContent={Text(entry.key)},supportingContent={Text(entry.value.size.toString()+" tracks")},modifier=Modifier.clickable{group=entry.key})
            }}
        }else{
            LazyColumn(Modifier.weight(1f)){items(filtered,key={it.id}){track ->
                ListItem(headlineContent={Text(track.title)},supportingContent={Text(track.artist+" · "+formatDuration(track.durationMs))},modifier=Modifier.clickable{vm.importAudioTracks(listOf(Uri.parse(track.uri)));onBack()})
            }}
        }
    }
}
