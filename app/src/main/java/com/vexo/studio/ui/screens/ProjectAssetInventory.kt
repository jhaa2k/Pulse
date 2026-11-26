package com.vexo.studio.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vexo.studio.data.ProjectAssets
import com.vexo.studio.model.StudioTab
import com.vexo.studio.ui.StudioUiState
import com.vexo.studio.ui.StudioViewModel
import com.vexo.studio.ui.theme.LocalVexoPalette
import com.vexo.studio.ui.theme.StudioText as Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive

private data class AssetStatus(val name:String,val readable:Boolean,val size:Long?)
@androidx.media3.common.util.UnstableApi
@Composable
internal fun ProjectAssetInventory(vm:StudioViewModel,uiState:StudioUiState){
    val palette=LocalVexoPalette.current
    val context=LocalContext.current
    val assets=remember(uiState.project){ProjectAssets.collect(uiState.project)}
    var query by remember(uiState.project.id){mutableStateOf("")}
    var refresh by remember{mutableIntStateOf(0)}
    var statuses by remember(uiState.project.id){mutableStateOf<Map<String,AssetStatus>>(emptyMap())}
    LaunchedEffect(assets,refresh){
        statuses=emptyMap()
        withContext(Dispatchers.IO){
            val resolver=context.contentResolver
            val result=linkedMapOf<String,AssetStatus>()
            for(asset in assets){
                ensureActive()
                val uri=Uri.parse(asset.uri)
                var name=uri.lastPathSegment?.substringAfterLast('/') ?: "Linked media"
                var size:Long?=null
                if(uri.scheme=="content")runCatching {
                    resolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE),null,null,null)?.use{cursor->
                        if(cursor.moveToFirst()){
                            if(!cursor.isNull(0))name=cursor.getString(0)
                            if(!cursor.isNull(1))size=cursor.getLong(1).takeIf{it>=0L}
                        }
                    }
                } else size=uri.path?.let{java.io.File(it)}?.takeIf{it.isFile}?.length()
                val readable=runCatching { resolver.openInputStream(uri)?.use{true}?:false }.getOrDefault(false)
                result[asset.uri]=AssetStatus(name,readable,size)
            }
            result.toMap()
        }.let{statuses=it}
    }
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        Text("PROJECT MEDIA (${assets.size})",style=MaterialTheme.typography.labelMedium,color=palette.accent,modifier=Modifier.weight(1f))
        TextButton(onClick={refresh++}){Text("CHECK FILES")}
    }
    if(assets.isEmpty()){
        Text("No linked local media. Import an image, video, audio or font.",style=MaterialTheme.typography.bodySmall,color=palette.textSecondary)
        return
    }
    CompactStudioSearch(query,{query=it.take(80)},"Find project media",Modifier.fillMaxWidth())
    val missing=statuses.values.count{!it.readable}
    if(missing>0)Text("$missing unavailable media file(s). Open the affected layer to replace its source.",color=palette.danger,style=MaterialTheme.typography.bodySmall)
    for(asset in assets.filter{a->query.isBlank()||(statuses[a.uri]?.name?:a.uri).contains(query,true)||a.uses.any{it.label.contains(query,true)||it.role.contains(query,true)}}){
        val status=statuses[asset.uri]
        Column(Modifier.fillMaxWidth().background(palette.panel,palette.shape).border(1.dp,palette.border,palette.shape).padding(8.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){
            Text(status?.name?:Uri.parse(asset.uri).lastPathSegment.orEmpty(),style=MaterialTheme.typography.bodySmall,color=palette.textPrimary,maxLines=2)
            val availability=when(status?.readable){true->"AVAILABLE";false->"MISSING / NO ACCESS";null->"CHECKING"}
            Text(availability+" · "+asset.uses.size+" USE(S)"+(status?.size?.let{" · "+android.text.format.Formatter.formatShortFileSize(context,it)}?:""),style=MaterialTheme.typography.labelSmall,color=if(status?.readable==false)palette.danger else palette.textSecondary)
            for(use in asset.uses){
                if(use.layerId!=null&&use.compositionIndex!=null){
                    TextButton(onClick={vm.selectComposition(use.compositionIndex);vm.selectLayer(use.layerId);vm.selectStudioTab(StudioTab.PROPERTIES)},modifier=Modifier.fillMaxWidth()){
                        Text(use.label+" / "+use.role,modifier=Modifier.weight(1f),maxLines=2)
                        Text("OPEN",color=palette.accent)
                    }
                }else Text(use.label+" / "+use.role,style=MaterialTheme.typography.labelSmall,color=palette.textSecondary)
            }
        }
    }
}
