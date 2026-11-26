package com.vexo.studio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import com.vexo.studio.ui.theme.StudioText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vexo.studio.model.PropertyDescriptor
import com.vexo.studio.model.PropertyRegistry
import com.vexo.studio.ui.theme.LocalVexoPalette
import kotlin.math.roundToInt

/**
 * Compact sharp-edged studio slider with:
 * - Thin 2dp track + larger 14x18dp rectangular handle
 * - Default value tick marker on the track
 * - Double-tap anywhere on track OR label to reset to default
 * - Tap numeric readout for exact keyboard editing
 * - Optional accessible property help dialog
 */
@Composable
fun StudioSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    defaultValue: Float,
    units: String = "",
    step: Float = 0.01f,
    helpText: String? = null,
    showHelpIcon: Boolean = true,
    onValueChange: (Float) -> Unit,
    onCommit: () -> Unit = {}
) {
    val palette = LocalVexoPalette.current
    val currentChange by rememberUpdatedState(onValueChange)
    val currentCommit by rememberUpdatedState(onCommit)
    var trackWidthPx by remember { mutableStateOf(1f) }
    var showNumericDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }

    val range = (max - min).coerceAtLeast(0.0001f)
    val clamped = value.coerceIn(min, max)
    val norm = ((clamped - min) / range).coerceIn(0f, 1f)
    val defaultNorm = ((defaultValue.coerceIn(min, max) - min) / range).coerceIn(0f, 1f)

    fun quantize(raw: Float): Float {
        val stepped = if (step > 0f) ((raw / step).roundToInt() * step) else raw
        return stepped.coerceIn(min, max)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(defaultValue) {
                        detectTapGestures(onDoubleTap = {
                            currentChange(defaultValue)
                            currentCommit()
                        })
                    }
            ) {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (showHelpIcon && !helpText.isNullOrBlank()) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Outlined.HelpOutline,
                        contentDescription = "Help for $label",
                        tint = palette.textSecondary,
                        modifier = Modifier
                            .size(13.dp)
                            .clickable { showHelpDialog = true }
                    )
                }
            }

            val formatted = if (step >= 1f) {
                "${clamped.roundToInt()}${if (units.isNotEmpty()) " $units" else ""}"
            } else {
                "${if (step < 0.01f) "%.3f".format(clamped) else "%.2f".format(clamped)}${if (units.isNotEmpty()) " $units" else ""}"
            }
            Box(
                modifier = Modifier
                    .border(1.dp, palette.border, palette.shape)
                    .background(palette.glassPanel, palette.shape)
                    .clickable { showNumericDialog = true }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = formatted,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textPrimary
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height((28f * palette.controlScale).dp)
                .onSizeChanged { trackWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(min, max, step, defaultValue, palette.controlScale) {
                    detectTapGestures(
                        onDoubleTap = {
                            currentChange(defaultValue)
                            currentCommit()
                        },
                        onTap = { offset ->
                            val ratio = ((offset.x - (7f * palette.controlScale).dp.toPx()) / (trackWidthPx - (14f * palette.controlScale).dp.toPx()).coerceAtLeast(1f)).coerceIn(0f, 1f)
                            currentChange(quantize(min + ratio * range))
                            currentCommit()
                        }
                    )
                }
                .pointerInput(min, max, step, palette.controlScale) {
                    detectHorizontalDragGestures(
                        onDragEnd = { currentCommit() },
                        onDragCancel = { currentCommit() }
                    ) { change, _ ->
                        change.consume()
                        val ratio = ((change.position.x - (7f * palette.controlScale).dp.toPx()) / (trackWidthPx - (14f * palette.controlScale).dp.toPx()).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        currentChange(quantize(min + ratio * range))
                    }
                }
        ) {
            val midY = size.height * 0.5f
            val pad = (7f * palette.controlScale).dp.toPx()
            val usable = (size.width - pad * 2f).coerceAtLeast(1f)

            // Base thin track
            drawRect(
                color = palette.border,
                topLeft = Offset(pad, midY - 1.dp.toPx()),
                size = Size(usable, 2.dp.toPx())
            )
            // Active fill
            drawRect(
                color = palette.accent,
                topLeft = Offset(pad, midY - 1.5.dp.toPx()),
                size = Size(usable * norm, 3.dp.toPx())
            )
            // Default value tick marker
            val defX = pad + usable * defaultNorm
            drawRect(
                color = palette.textSecondary,
                topLeft = Offset(defX - 0.5.dp.toPx(), midY - 5.dp.toPx()),
                size = Size(1.dp.toPx(), 10.dp.toPx())
            )
            // Sharp rectangular studio handle
            val thumbX = pad + usable * norm
            val thumbW = (12f * palette.controlScale).dp.toPx()
            val thumbH = (16f * palette.controlScale).dp.toPx()
            drawRoundRect(
                color = palette.accent,
                cornerRadius = CornerRadius(palette.radiusDp.coerceIn(0f, 7f).dp.toPx()),
                topLeft = Offset(thumbX - thumbW * 0.5f, midY - thumbH * 0.5f),
                size = Size(thumbW, thumbH)
            )
        }
    }

    if (showNumericDialog) {
        var input by remember { mutableStateOf(if (step >= 1f) clamped.roundToInt().toString() else "%.3f".format(clamped)) }
        AlertDialog(
            onDismissRequest = { showNumericDialog = false },
            title = { Text(label.uppercase(), style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Range: $min .. $max $units (Default: $defaultValue)",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary
                    )
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = input.toFloatOrNull()
                    if (parsed != null) {
                        onValueChange(parsed.coerceIn(min, max))
                        onCommit()
                    }
                    showNumericDialog = false
                }) { Text("APPLY") }
            },
            dismissButton = {
                TextButton(onClick = {
                    onValueChange(defaultValue)
                    onCommit()
                    showNumericDialog = false
                }) { Text("RESET") }
            }
        )
    }

    if (showHelpDialog && !helpText.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text(label.uppercase(), style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    "$helpText\n\nDefault: $defaultValue $units\nRange: $min – $max\nTip: Double-tap slider to reset.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) { Text("CLOSE") }
            }
        )
    }
}

@Composable
fun RegisteredSlider(
    descriptorKey: String,
    value: Float,
    showHelpIcon: Boolean = true,
    onValueChange: (Float) -> Unit,
    onCommit: () -> Unit = {}
) {
    val desc: PropertyDescriptor = PropertyRegistry.descriptors[descriptorKey]
        ?: PropertyDescriptor(descriptorKey, descriptorKey, "", value, 0f, 10f, 0.1f, "")
    StudioSlider(
        label = desc.label,
        value = value,
        min = desc.min,
        max = desc.max,
        defaultValue = desc.defaultValue,
        units = desc.units,
        step = desc.step,
        helpText = desc.help,
        showHelpIcon = showHelpIcon,
        onValueChange = onValueChange,
        onCommit = onCommit
    )
}

@Composable
fun StudioToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val palette = LocalVexoPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun <T> StudioChipSelector(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit
) {
    val palette = LocalVexoPalette.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { opt ->
                val isSelected = opt == selected
                Box(
                    modifier = Modifier
                        .border(1.dp, if (isSelected) palette.accent else palette.border, palette.shape)
                        .background(if (isSelected) palette.accent else palette.glassPanel, palette.shape)
                        .clickable { onSelect(opt) }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = optionLabel(opt).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) palette.accentOn else palette.textPrimary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun StudioColorPickerRow(label:String,argb:Int,onColorChange:(Int)->Unit){
    val palette=LocalVexoPalette.current
    var open by remember{mutableStateOf(false)}
    Row(Modifier.fillMaxWidth().clip(palette.shape).clickable{open=true}.padding(vertical=6.dp,horizontal=2.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
        Text(label,style=MaterialTheme.typography.labelSmall,color=palette.textSecondary,modifier=Modifier.weight(1f))
        Text("#%08X".format(argb),style=MaterialTheme.typography.labelSmall,color=palette.textPrimary)
        ColorSwatch(argb,Modifier.size(32.dp))
    }
    if(open)StudioColorWheelDialog(label,argb,{onColorChange(it);open=false},{open=false})
}

@Composable
private fun ColorSwatch(argb:Int,modifier:Modifier=Modifier){
    val p=LocalVexoPalette.current
    Canvas(modifier.clip(RoundedCornerShape(8.dp)).border(1.dp,p.border,RoundedCornerShape(8.dp))){
        val cell=6.dp.toPx()
        for(y in 0..(size.height/cell).toInt())for(x in 0..(size.width/cell).toInt())drawRect(if((x+y)%2==0)Color(0xFFCCCCCC) else Color(0xFF777777),Offset(x*cell,y*cell),Size(cell,cell))
        drawRect(Color(argb))
    }
}

@Composable
private fun StudioColorWheelDialog(label:String,argb:Int,onApply:(Int)->Unit,onCancel:()->Unit){
    val p=LocalVexoPalette.current
    val initial=remember(argb){FloatArray(3).also{android.graphics.Color.colorToHSV(argb,it)}}
    var hue by remember(argb){mutableStateOf(initial[0])}
    var saturation by remember(argb){mutableStateOf(initial[1])}
    var value by remember(argb){mutableStateOf(initial[2])}
    var alpha by remember(argb){mutableStateOf(((argb ushr 24)and 255)/255f)}
    var hex by remember(argb){mutableStateOf("%08X".format(argb))}
    val draft=android.graphics.Color.HSVToColor((alpha*255).roundToInt(),floatArrayOf(hue,saturation,value))
    fun sync(){hex="%08X".format(android.graphics.Color.HSVToColor((alpha*255).roundToInt(),floatArrayOf(hue,saturation,value)))}
    fun select(color:Int){val hsv=FloatArray(3);android.graphics.Color.colorToHSV(color,hsv);hue=hsv[0];saturation=hsv[1];value=hsv[2];alpha=((color ushr 24)and 255)/255f;sync()}
    val raw=hex.trim().removePrefix("#")
    val parsed=raw.toLongOrNull(16)?.takeIf{raw.length==6||raw.length==8}?.let{if(raw.length==6)(it or 0xFF000000L).toInt()else it.toInt()}
    val presets=listOf(0xFFFFFFFF.toInt(),0xFFC5F84A.toInt(),0xFF38BDF8.toInt(),0xFFF59E0B.toInt(),0xFFEF4444.toInt(),0xFFA855F7.toInt(),0xFF10B981.toInt(),0xFF000000.toInt(),0)
    val choose by rememberUpdatedState<(Offset,Float,Float)->Unit>({position,w,h->
        val dx=position.x-w*.5f;val dy=position.y-h*.5f;val radius=minOf(w,h)*.5f
        hue=((atan2(dy,dx)*180f/PI.toFloat())+360f)%360f
        saturation=(sqrt(dx*dx+dy*dy)/radius.coerceAtLeast(1f)).coerceIn(0f,1f)
        sync()
    })
    AlertDialog(onDismissRequest=onCancel,title={Text(label)},text={
        Column(Modifier.fillMaxWidth().heightIn(max=490.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp),horizontalAlignment=Alignment.CenterHorizontally){
            val rainbow=listOf(Color.Red,Color.Yellow,Color.Green,Color.Cyan,Color.Blue,Color.Magenta,Color.Red)
            Canvas(Modifier.size(216.dp).pointerInput(Unit){detectTapGestures{choose(it,size.width.toFloat(),size.height.toFloat())}}.pointerInput(Unit){
                detectDragGestures(onDragStart={choose(it,size.width.toFloat(),size.height.toFloat())}){change,_->choose(change.position,size.width.toFloat(),size.height.toFloat());change.consume()}
            }){
                val radius=size.minDimension*.5f
                drawCircle(Brush.sweepGradient(rainbow,center),radius,center)
                drawCircle(Brush.radialGradient(listOf(Color.White,Color.Transparent),center,radius),radius,center)
                if(value<1f)drawCircle(Color.Black.copy(alpha=1f-value),radius,center)
                val radians=hue*PI.toFloat()/180f
                val mark=center+Offset(cos(radians),sin(radians))*radius*saturation
                drawCircle(Color.Black,8.dp.toPx(),mark,style=Stroke(3.dp.toPx()))
                drawCircle(Color.White,8.dp.toPx(),mark,style=Stroke(1.5.dp.toPx()))
            }
            StudioSlider("Hue",hue,0f,359.9f,0f,"°",1f,onValueChange={hue=it;sync()})
            StudioSlider("Saturation",saturation,0f,1f,1f,"",.01f,onValueChange={saturation=it;sync()})
            StudioSlider("Brightness",value,0f,1f,1f,"",.01f,onValueChange={value=it;sync()})
            StudioSlider("Opacity",alpha,0f,1f,1f,"",.01f,onValueChange={alpha=it;sync()})
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){Text("Original",style=MaterialTheme.typography.labelSmall);ColorSwatch(argb,Modifier.fillMaxWidth().height(32.dp))}
                Column(Modifier.weight(1f)){Text("New",style=MaterialTheme.typography.labelSmall);ColorSwatch(draft,Modifier.fillMaxWidth().height(32.dp))}
            }
            OutlinedTextField(hex,{input->
                hex=input.take(9)
                val clean=hex.trim().removePrefix("#")
                clean.toLongOrNull(16)?.takeIf{clean.length==6||clean.length==8}?.let{num->
                    val col=if(clean.length==6)(num or 0xFF000000L).toInt()else num.toInt()
                    val hsv=FloatArray(3);android.graphics.Color.colorToHSV(col,hsv);hue=hsv[0];saturation=hsv[1];value=hsv[2];alpha=((col ushr 24)and 255)/255f
                }
            },label={Text("HEX · RRGGBB / AARRGGBB")},singleLine=true,isError=parsed==null,modifier=Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)){
                presets.forEach{col->ColorSwatch(col,Modifier.size(29.dp).clickable{select(col)})}
            }
        }
    },confirmButton={TextButton(enabled=parsed!=null,onClick={parsed?.let(onApply)}){Text("APPLY")}},dismissButton={TextButton(onClick=onCancel){Text("CANCEL")}})
}
