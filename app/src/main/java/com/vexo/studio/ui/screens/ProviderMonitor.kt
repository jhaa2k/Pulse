package com.vexo.studio.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.vexo.studio.engine.scene.ProviderResponseProfile
import com.vexo.studio.model.AudioProviderConfig
import com.vexo.studio.ui.StudioViewModel
import com.vexo.studio.ui.theme.LocalVexoPalette
import com.vexo.studio.ui.theme.StudioText as Text

@androidx.media3.common.util.UnstableApi
@Composable
internal fun ProviderMonitor(vm:StudioViewModel,provider:AudioProviderConfig){
    val p=LocalVexoPalette.current
    val snapshots by vm.audioSnapshots.collectAsState()
    val snapshot=snapshots[provider.index].takeIf{provider.enabled}
    fun level(v:Float)=if(v.isFinite())v.coerceIn(0f,1f)else 0f
    Column(Modifier.fillMaxWidth().background(p.glassPanel,p.shape).border(1.dp,p.border,p.shape).padding(10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Text("LIVE ANALYSIS / #${provider.index}",style=MaterialTheme.typography.labelMedium,color=p.accent,modifier=Modifier.weight(1f))
            Text(if(!provider.enabled)"OFF" else if(snapshot==null)"NO SIGNAL" else "FFT ${provider.fftSize}",style=MaterialTheme.typography.labelSmall,color=p.textSecondary)
        }
        Canvas(Modifier.fillMaxWidth().height(64.dp).background(p.panelElevated,p.shape)){
            val data=snapshot?.bars
            val count=48
            val slot=size.width/count
            for(i in 0 until count){
                val from=if(data!=null)i*data.size/count else 0
                val to=if(data!=null)((i+1)*data.size/count).coerceAtLeast(from+1).coerceAtMost(data.size) else 0
                var peak=0f
                if(data!=null)for(j in from until to)peak=maxOf(peak,level(data[j]))
                val h=(size.height-8.dp.toPx())*peak
                val w=(slot*.64f).coerceAtLeast(1f)
                drawRoundRect(p.accent.copy(alpha=if(peak>0f) .95f else .15f),Offset(i*slot+(slot-w)/2f,size.height-maxOf(h,2.dp.toPx())-3.dp.toPx()),Size(w,maxOf(h,2.dp.toPx())),CornerRadius(w/2f))
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
            listOf("BASS" to (snapshot?.bass?:0f),"MID" to (snapshot?.mid?:0f),"TREBLE" to (snapshot?.treble?:0f),"BEAT" to (snapshot?.beatPulse?:0f)).forEach{(label,value)->
                Column(Modifier.weight(1f)){
                    Text(label,style=MaterialTheme.typography.labelSmall,color=p.textSecondary)
                    Box(Modifier.fillMaxWidth().height(5.dp).background(p.border,p.shape)){
                        Box(Modifier.fillMaxWidth(level(value)).fillMaxHeight().background(p.accent,p.shape))
                    }
                }
            }
        }
        Text("${provider.lowerHz.toInt()} – ${provider.higherHz.toInt()} HZ · ${provider.sampleOutCount} SAMPLES",style=MaterialTheme.typography.labelSmall,color=p.textSecondary)
        Text("QUICK RESPONSE",style=MaterialTheme.typography.labelMedium,color=p.accent)
        ProviderResponseProfile.entries.chunked(2).forEach{row->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                row.forEach{profile->
                    TextButton(onClick={vm.updateAudioProvider(provider.index){profile.applyTo(it)}},modifier=Modifier.weight(1f).background(p.panelElevated,p.shape)){Text(profile.label)}
                }
            }
        }
        Text("Profiles adjust analysis only. Undo restores previous values.",style=MaterialTheme.typography.labelSmall,color=p.textSecondary)
    }
}
