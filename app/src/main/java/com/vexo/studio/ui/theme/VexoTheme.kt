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
    val danger: Color
)

val LocalVexoPalette = staticCompositionLocalOf {
    VexoPalette(
        bg = Color(0xFF000000),
        panel = Color(0xFF121316),
        panelElevated = Color(0xFF1B1D21),
        border = Color(0xFF2B2E35),
        textPrimary = Color(0xFFF5F6F8),
        textSecondary = Color(0xFF8E939D),
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
    val palette = when (settings.themePreset) {
        ThemePreset.MONOCHROME_DARK -> VexoPalette(
            bg = Color(0xFF000000),
            panel = Color(0xFF121316),
            panelElevated = Color(0xFF1C1E22),
            border = Color(0xFF2C2F36),
            textPrimary = Color(0xFFF5F6F8),
            textSecondary = Color(0xFF8E939D),
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
        extraSmall = RoundedCornerShape(0.dp),
        small = RoundedCornerShape(0.dp),
        medium = RoundedCornerShape(2.dp),
        large = RoundedCornerShape(2.dp),
        extraLarge = RoundedCornerShape(2.dp)
    )

    val typography = Typography(
        titleMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            letterSpacing = 0.2.sp
        ),
        labelMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            letterSpacing = 1.1.sp
        ),
        labelSmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            letterSpacing = 0.9.sp,
            fontFeatureSettings = "tnum"
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp
        ),
        bodySmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp
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
