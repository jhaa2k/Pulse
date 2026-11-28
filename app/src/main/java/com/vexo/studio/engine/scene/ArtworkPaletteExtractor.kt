package com.vexo.studio.engine.scene

import android.graphics.Bitmap
import androidx.palette.graphics.Palette
import com.vexo.studio.model.LayerConfig
import com.vexo.studio.model.VexoProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ArtworkPalette(val primary: Int, val secondary: Int, val background: Int)

/** On-device quantization; no cloud, uploads, or model dependency. */
object ArtworkPaletteExtractor {
    suspend fun extract(bitmap: Bitmap): ArtworkPalette = withContext(Dispatchers.Default) {
        val p = Palette.from(bitmap).resizeBitmapArea(16_384).maximumColorCount(24).generate()
        val first = p.getVibrantColor(p.getDominantColor(0xFFC6F36A.toInt()))
        ArtworkPalette(first, p.getLightVibrantColor(p.getMutedColor(0xFFFFFFFF.toInt())),
            p.getDarkMutedColor(0xFF10120E.toInt()))
    }

    fun apply(project: VexoProject, colors: ArtworkPalette): VexoProject = project.copy(
        canvas = project.canvas.copy(backgroundColorArgb = colors.background),
        compositions = project.compositions.map { c -> c.copy(layers = c.layers.map { l ->
            when (val cfg = l.config) {
                is LayerConfig.Bars -> l.copy(config = cfg.copy(colorFromArgb=colors.primary,colorToArgb=colors.secondary))
                is LayerConfig.Particles -> l.copy(config=cfg.copy(colorFromArgb=colors.primary,colorToArgb=colors.secondary))
                else -> l
            }
        }) }
    )
}
