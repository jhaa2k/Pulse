package com.vexo.studio.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.vexo.studio.ui.theme.StudioText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.vexo.studio.ui.theme.LocalVexoPalette

@Composable
internal fun CompactStudioSearch(value:String,onChange:(String)->Unit,placeholder:String,modifier:Modifier=Modifier){
    val palette=LocalVexoPalette.current
    BasicTextField(value=value,onValueChange=onChange,singleLine=true,
        textStyle=MaterialTheme.typography.bodySmall.copy(color=palette.textPrimary),
        cursorBrush=SolidColor(palette.accent),
        modifier=modifier.semantics{contentDescription=placeholder},
        decorationBox={inner->
            Row(Modifier.fillMaxWidth().heightIn(min=42.dp).background(palette.panel,palette.shape)
                .border(1.dp,palette.border,palette.shape).padding(start=10.dp,end=2.dp),
                verticalAlignment=Alignment.CenterVertically){
                Icon(Icons.Default.Search,null,tint=palette.textSecondary,modifier=Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)){
                    if(value.isEmpty())Text(placeholder,style=MaterialTheme.typography.bodySmall,color=palette.textSecondary,maxLines=1)
                    inner()
                }
                if(value.isNotEmpty())IconButton(onClick={onChange("")},modifier=Modifier.size(36.dp)){
                    Icon(Icons.Default.Close,"Clear search",tint=palette.textSecondary,modifier=Modifier.size(16.dp))
                }else Spacer(Modifier.width(10.dp))
            }
        })
}
private fun Context.findActivity():Activity? = when(this){
    is Activity->this
    is ContextWrapper->baseContext.findActivity()
    else->null
}

@android.annotation.SuppressLint("SourceLockedOrientationActivity")
@Composable
internal fun FullscreenPreviewOrientation(landscape:Boolean,onExit:()->Unit){
    val activity=LocalContext.current.findActivity()
    BackHandler(onBack=onExit)
    DisposableEffect(activity){
        val previous=activity?.requestedOrientation
        val controller=activity?.let{WindowCompat.getInsetsController(it.window,it.window.decorView)}
        val behavior=controller?.systemBarsBehavior
        controller?.systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose{
            if(previous!=null)activity?.requestedOrientation=previous
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if(behavior!=null)controller?.systemBarsBehavior=behavior
        }
    }
    LaunchedEffect(activity,landscape){
        activity?.requestedOrientation=if(landscape)ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }
}
