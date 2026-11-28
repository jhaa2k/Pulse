package com.vexo.studio.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.unit.TextUnit
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import com.vexo.studio.R
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
    val glassOpacity: Float = 0.82f,
    val wallpaperActive: Boolean = false
) {
    val shape get() = RoundedCornerShape(radiusDp.coerceIn(0f, 28f).dp)
    val glassPanel get() = if (wallpaperActive) panel.copy(alpha=glassOpacity.coerceIn(.65f,1f)) else if (glassEnabled) androidx.compose.ui.graphics.lerp(bg,panel,glassOpacity.coerceIn(0.65f,1f)) else panel
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
            bg = Color(0xFF0D0E10),
            panel = Color(0xFF17191D),
            panelElevated = Color(0xFF22252A),
            border = Color(0xFF33363B),
            textPrimary = Color(0xFFF4F5F7),
            textSecondary = Color(0xFFADB0B7),
            accent = customAccent,
            accentOn = if (customAccent.luminance() > .179f) Color.Black else Color.White,
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

    val themed = if(settings.customAccentAllThemes) basePalette.copy(accent=customAccent,accentOn=if(customAccent.luminance()>.179f) Color.Black else Color.White) else basePalette
    val palette = themed.copy(radiusDp = settings.cornerRadiusDp, controlScale = settings.controlScale.coerceIn(0.85f, 1.25f), glassEnabled = settings.glassEnabled, glassOpacity = settings.glassOpacity, wallpaperActive=settings.wallpaperUri.isNotBlank())
    val isLight = settings.themePreset == ThemePreset.CLEAN_LIGHT
    val colorScheme = if (isLight) {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.accentOn,
            primaryContainer = palette.panelElevated,
            onPrimaryContainer = palette.textPrimary,
            secondary = palette.accent,
            onSecondary = palette.accentOn,
            secondaryContainer = palette.panelElevated,
            onSecondaryContainer = palette.textPrimary,
            tertiary = palette.accent,
            onTertiary = palette.accentOn,
            tertiaryContainer = palette.panelElevated,
            onTertiaryContainer = palette.textPrimary,
            surfaceTint = palette.accent,
            inverseSurface = palette.textPrimary,
            inverseOnSurface = palette.bg,
            inversePrimary = palette.bg,
            onError = if (palette.danger.luminance() > .179f) Color.Black else Color.White,
            errorContainer = palette.panelElevated,
            onErrorContainer = palette.danger,
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
            primaryContainer = palette.panelElevated,
            onPrimaryContainer = palette.textPrimary,
            secondary = palette.accent,
            onSecondary = palette.accentOn,
            secondaryContainer = palette.panelElevated,
            onSecondaryContainer = palette.textPrimary,
            tertiary = palette.accent,
            onTertiary = palette.accentOn,
            tertiaryContainer = palette.panelElevated,
            onTertiaryContainer = palette.textPrimary,
            surfaceTint = palette.accent,
            inverseSurface = palette.textPrimary,
            inverseOnSurface = palette.bg,
            inversePrimary = palette.bg,
            onError = if (palette.danger.luminance() > .179f) Color.Black else Color.White,
            errorContainer = palette.panelElevated,
            onErrorContainer = palette.danger,
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

    val poppins=FontFamily(Font(R.font.poppins_regular,FontWeight.Normal),Font(R.font.poppins_medium,FontWeight.Medium),Font(R.font.poppins_semibold,FontWeight.SemiBold),Font(R.font.poppins_bold,FontWeight.Bold))
    val typography = Typography(
        titleMedium = TextStyle(
            fontFamily = poppins,
            fontWeight = FontWeight.SemiBold,
            fontSize = (15 * settings.uiTextScale.coerceIn(0.9f, 1.25f)).sp,
            letterSpacing = 0.2.sp
        ),
        labelMedium = TextStyle(
            fontFamily = poppins,
            fontWeight = FontWeight.SemiBold,
            fontSize = (11 * settings.uiTextScale.coerceIn(0.9f, 1.25f)).sp,
            letterSpacing = 1.1.sp
        ),
        labelSmall = TextStyle(
            fontFamily = poppins,
            fontWeight = FontWeight.Medium,
            fontSize = (11 * settings.uiTextScale.coerceIn(0.9f, 1.25f)).sp,
            letterSpacing = 0.45.sp,
            fontFeatureSettings = "tnum"
        ),
        bodyMedium = TextStyle(
            fontFamily = poppins,
            fontWeight = FontWeight.Normal,
            fontSize = (14 * settings.uiTextScale.coerceIn(0.9f, 1.25f)).sp,
            lineHeight = (20 * settings.uiTextScale.coerceIn(0.9f, 1.25f)).sp
        ),
        bodySmall = TextStyle(
            fontFamily = poppins,
            fontWeight = FontWeight.Normal,
            fontSize = (12 * settings.uiTextScale.coerceIn(0.9f, 1.25f)).sp,
            lineHeight = (17 * settings.uiTextScale.coerceIn(0.9f, 1.25f)).sp
        )
    )

    fun compact(style: TextStyle)=style.copy(fontFamily=poppins,
        fontSize=if(style.fontSize==TextUnit.Unspecified)style.fontSize else style.fontSize*.94f,
        lineHeight=if(style.lineHeight==TextUnit.Unspecified)style.lineHeight else style.lineHeight*.94f)
    val baseTypography=typography.copy(displayLarge=typography.displayLarge.copy(fontFamily=poppins),displayMedium=typography.displayMedium.copy(fontFamily=poppins),displaySmall=typography.displaySmall.copy(fontFamily=poppins),headlineLarge=typography.headlineLarge.copy(fontFamily=poppins),headlineMedium=typography.headlineMedium.copy(fontFamily=poppins),headlineSmall=typography.headlineSmall.copy(fontFamily=poppins),titleLarge=typography.titleLarge.copy(fontFamily=poppins),titleSmall=typography.titleSmall.copy(fontFamily=poppins),bodyLarge=typography.bodyLarge.copy(fontFamily=poppins),labelLarge=typography.labelLarge.copy(fontFamily=poppins))
    val allTypography=baseTypography.copy(displayLarge=compact(baseTypography.displayLarge),displayMedium=compact(baseTypography.displayMedium),displaySmall=compact(baseTypography.displaySmall),headlineLarge=compact(baseTypography.headlineLarge),headlineMedium=compact(baseTypography.headlineMedium),headlineSmall=compact(baseTypography.headlineSmall),titleLarge=compact(baseTypography.titleLarge),titleMedium=compact(baseTypography.titleMedium),titleSmall=compact(baseTypography.titleSmall),bodyLarge=compact(baseTypography.bodyLarge),bodyMedium=compact(baseTypography.bodyMedium),bodySmall=compact(baseTypography.bodySmall),labelLarge=compact(baseTypography.labelLarge),labelMedium=compact(baseTypography.labelMedium),labelSmall=compact(baseTypography.labelSmall))
    CompositionLocalProvider(LocalVexoPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = sharpShapes,
            typography = allTypography,
            content = { CompositionLocalProvider(LocalContentColor provides palette.textPrimary, content = content) }
        )
    }
}
