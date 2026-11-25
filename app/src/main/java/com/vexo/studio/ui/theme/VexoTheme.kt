package com.vexo.studio.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vexo.studio.model.AppSettings
import com.vexo.studio.model.ThemePreset

data class VexoPalette(
    val bg: Color,
    val panel: Color,
    val panelElevated: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val accentOn: Color,
    val danger: Color,
    val radiusDp: Float = 12f,
    val controlScale: Float = 1f,
    val glassEnabled: Boolean = true,
    val glassOpacity: Float = 0.82f
) {
    val shape get() = RoundedCornerShape(radiusDp.coerceIn(0f, 28f).dp)
    val glassPanel get() = panel.copy(alpha = if (glassEnabled) glassOpacity.coerceIn(0.65f, 1f) else 1f)
}

val LocalVexoPalette = staticCompositionLocalOf {
    VexoPalette(
        bg = Color(0xFF0B0C0E),
        panel = Color(0xFF121316),
        panelElevated = Color(0xFF1B1D21),
        border = Color(0xFF2B2E35),
        textPrimary = Color(0xFFF5F6F8),
        textSecondary = Color(0xFFA5A9B2),
        accent = Color(0xFFFFFFFF),
        accentOn = Color(0xFF000000),
        danger = Color(0xFFEF4444)
    )
}

@Composable
fun VexoTheme(
    settings: AppSettings,
    content: @Composable () -> Unit
) {
    val customAccent = Color(settings.customAccentArgb)
    val basePalette = when (settings.themePreset) {
        ThemePreset.MONOCHROME_DARK -> VexoPalette(
            bg = Color(0xFF0B0C0E),
            panel = Color(0xFF121316),
            panelElevated = Color(0xFF1C1E22),
            border = Color(0xFF2C2F36),
            textPrimary = Color(0xFFF5F6F8),
            textSecondary = Color(0xFFA5A9B2),
            accent = Color(0xFFFFFFFF),
            accentOn = Color(0xFF000000),
            danger = Color(0xFFEF4444)
        )
        ThemePreset.LIME_STUDIO -> VexoPalette(
            bg = Color(0xFF090B08),
            panel = Color(0xFF131611),
            panelElevated = Color(0xFF1C2018),
            border = Color(0xFF2C3226),
            textPrimary = Color(0xFFEFF2E6),
            textSecondary = Color(0xFF939B88),
            accent = customAccent,
            accentOn = Color(0xFF090B08),
            danger = Color(0xFFEF4444)
        )
        ThemePreset.AMBER_CONSOLE -> VexoPalette(
            bg = Color(0xFF090807),
            panel = Color(0xFF151310),
            panelElevated = Color(0xFF201D18),
            border = Color(0xFF332D24),
            textPrimary = Color(0xFFF6F2EA),
            textSecondary = Color(0xFF9F9689),
            accent = Color(0xFFF59E0B),
            accentOn = Color(0xFF090807),
            danger = Color(0xFFEF4444)
        )
        ThemePreset.CLEAN_LIGHT -> VexoPalette(
            bg = Color(0xFFF4F4F5),
            panel = Color(0xFFFFFFFF),
            panelElevated = Color(0xFFE9EAEC),
            border = Color(0xFFD1D5DB),
            textPrimary = Color(0xFF0A0B0D),
            textSecondary = Color(0xFF525866),
            accent = Color(0xFF111318),
            accentOn = Color(0xFFFFFFFF),
            danger = Color(0xFFDC2626)
        )
    }

    val palette = basePalette.copy(radiusDp = settings.cornerRadiusDp, controlScale = settings.controlScale.coerceIn(0.85f, 1.25f), glassEnabled = settings.glassEnabled, glassOpacity = settings.glassOpacity)
    val isLight = settings.themePreset == ThemePreset.CLEAN_LIGHT
    val colorScheme = if (isLight) {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.accentOn,
            background = palette.bg,
            surface = palette.panel,
            surfaceVariant = palette.panelElevated,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.border,
            error = palette.danger
        )
    } else {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.accentOn,
            background = palette.bg,
            surface = palette.panel,
            surfaceVariant = palette.panelElevated,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.border,
            error = palette.danger
        )
    }

    val sharpShapes = Shapes(
        extraSmall = palette.shape,
        small = palette.shape,
        medium = palette.shape,
        large = palette.shape,
        extraLarge = palette.shape
    )

    val typography = Typography(
        titleMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = (15 * settings.uiTextScale.coerceIn(0.9f, 1.25f)).sp,
            letterSpacing = 0.2.sp
        ),
        labelMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = (11 * settings.uiTextScale.coerceIn(0.9f, 1.25f)).sp,
            letterSpacing = 1.1.sp
        ),
        labelSmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = (11 * settings.uiTextScale.coerceIn(0.9f, 1.25f)).sp,
            letterSpacing = 0.45.sp,
            fontFeatureSettings = "tnum"
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = (14 * settings.uiTextScale.coerceIn(0.9f, 1.25f)).sp,
            lineHeight = (20 * settings.uiTextScale.coerceIn(0.9f, 1.25f)).sp
        ),
        bodySmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = (12 * settings.uiTextScale.coerceIn(0.9f, 1.25f)).sp,
            lineHeight = (17 * settings.uiTextScale.coerceIn(0.9f, 1.25f)).sp
        )
    )

    CompositionLocalProvider(LocalVexoPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = sharpShapes,
            typography = typography,
            content = content
        )
    }
}
