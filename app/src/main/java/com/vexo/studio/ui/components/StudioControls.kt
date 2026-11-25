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
import androidx.compose.material3.Text
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
                .height((36f * palette.controlScale).dp)
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
            val thumbW = (14f * palette.controlScale).dp.toPx()
            val thumbH = (20f * palette.controlScale).dp.toPx()
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
fun StudioColorPickerRow(
    label: String,
    argb: Int,
    onColorChange: (Int) -> Unit
) {
    val palette = LocalVexoPalette.current
    val presets = listOf(
        0xFFFFFFFF.toInt(),
        0xFFC6F36A.toInt(),
        0xFF38BDF8.toInt(),
        0xFFF59E0B.toInt(),
        0xFFEF4444.toInt(),
        0xFFA855F7.toInt(),
        0xFF10B981.toInt(),
        0xFF000000.toInt()
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            presets.forEach { col ->
                val active = col == argb
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(Color(col))
                        .border(
                            width = if (active) 2.dp else 1.dp,
                            color = if (active) palette.accent else palette.border
                        )
                        .clickable { onColorChange(col) }
                )
            }
        }
    }
}
