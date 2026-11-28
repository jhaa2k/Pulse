package com.vexo.studio.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vexo.studio.data.UiWallpaper
import com.vexo.studio.model.AppSettings
import com.vexo.studio.ui.StudioViewModel
import com.vexo.studio.ui.theme.LocalVexoPalette
import com.vexo.studio.ui.components.StudioChipSelector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun UserProfilePanel(vm:StudioViewModel,settings:AppSettings){
    val p=LocalVexoPalette.current
    val context=LocalContext.current
    val photo by produceState<android.graphics.Bitmap?>(null,settings.profilePhotoUri){
        value=withContext(Dispatchers.IO){UiWallpaper.decode(context,settings.profilePhotoUri)}
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->uri?.let(vm::setProfilePhoto)}
    var editing by rememberSaveable{mutableStateOf(false)}
    Column(Modifier.fillMaxWidth().clip(p.shape).background(p.glassPanel,p.shape).border(1.dp,p.border,p.shape).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("CREATOR PROFILE",style=MaterialTheme.typography.labelMedium,color=p.accent)
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Box(Modifier.size(64.dp).clip(CircleShape).background(p.panelElevated).border(2.dp,p.accent,CircleShape),contentAlignment=Alignment.Center){
                val bitmap=photo
                if(bitmap!=null)Image(bitmap.asImageBitmap(),"Profile picture",Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                else Text(settings.profileName.trim().take(1).uppercase().ifBlank{"V"},style=MaterialTheme.typography.headlineMedium,color=p.accent)
            }
            Column(Modifier.weight(1f)){
                Text(settings.profileName.ifBlank{"Your creator name"},style=MaterialTheme.typography.titleMedium,color=p.textPrimary,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(settings.profileGender.ifBlank{"Creative studio member"},style=MaterialTheme.typography.labelSmall,color=p.textSecondary)
                if(settings.profileBio.isNotBlank())Text(settings.profileBio,style=MaterialTheme.typography.bodySmall,color=p.textSecondary,maxLines=3,overflow=TextOverflow.Ellipsis)
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){
            TextButton(onClick={editing=true}){Text(if(settings.profileName.isBlank())"CREATE PROFILE" else "EDIT PROFILE")}
            TextButton(onClick={picker.launch(arrayOf("image/*"))}){Text("PHOTO")}
            if(settings.profilePhotoUri.isNotBlank())TextButton(onClick={vm.updateSettings{it.copy(profilePhotoUri="")}}){Text("REMOVE")}
        }
        Text("Local profile only · no account or upload required",style=MaterialTheme.typography.labelSmall,color=p.textSecondary)
    }
    if(editing){
        var name by rememberSaveable{mutableStateOf(settings.profileName)}
        var gender by rememberSaveable{mutableStateOf(settings.profileGender)}
        var bio by rememberSaveable{mutableStateOf(settings.profileBio)}
        AlertDialog(onDismissRequest={editing=false},title={Text("CREATOR PROFILE")},text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedTextField(name,{name=it.take(60)},label={Text("Name")},singleLine=true,modifier=Modifier.fillMaxWidth())
                StudioChipSelector("Gender (optional)",listOf("","Male","Female","Non-binary","Prefer not to say"),gender,{it.ifBlank{"Not set"}}){gender=it}
                OutlinedTextField(bio,{bio=it.take(160)},label={Text("Creative bio / specialty")},minLines=2,maxLines=3,modifier=Modifier.fillMaxWidth())
                Text("Profile details stay on this device.",style=MaterialTheme.typography.labelSmall,color=p.textSecondary)
            }
        },confirmButton={TextButton(enabled=name.isNotBlank(),onClick={vm.updateSettings{it.copy(profileName=name.trim(),profileGender=gender,profileBio=bio.trim())};editing=false}){Text("SAVE")}},dismissButton={TextButton(onClick={editing=false}){Text("CANCEL")}})
    }
}
