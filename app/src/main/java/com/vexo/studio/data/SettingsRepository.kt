package com.vexo.studio.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vexo.studio.model.AppSettings
import com.vexo.studio.model.PauseBehavior
import com.vexo.studio.model.ThemePreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.vexoDataStore by preferencesDataStore(name = "vexo_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val THEME_PRESET = stringPreferencesKey("theme_preset_vex")
        val CUSTOM_ACCENT = intPreferencesKey("custom_accent_vex")
        val DEFAULT_FFT = intPreferencesKey("default_fft")
        val DEFAULT_HOP = intPreferencesKey("default_hop")
        val PREVIEW_SCALE = floatPreferencesKey("preview_scale")
        val DIAGNOSTICS = booleanPreferencesKey("preview_diagnostics")
        val PAUSE_BEHAVIOR = stringPreferencesKey("pause_behavior")
        val SHOW_HELP = booleanPreferencesKey("show_help_hints")
        val GLASSENABLED = booleanPreferencesKey("glassEnabled")
        val GLASSOPACITY = floatPreferencesKey("glassOpacity")
        val BACKDROPBLURDP = floatPreferencesKey("backdropBlurDp")
        val CORNERRADIUSDP = floatPreferencesKey("cornerRadiusDp")
        val CONTROLSCALE = floatPreferencesKey("controlScale")
        val UITEXTSCALE = floatPreferencesKey("uiTextScale")
        val PREVIEWFPS = intPreferencesKey("previewFps")
        val PREVIEWSHARE = floatPreferencesKey("previewShare")
        val PORTRAITOVERLAY = booleanPreferencesKey("portraitOverlay")
    }

    val settingsFlow: Flow<AppSettings> = context.vexoDataStore.data.map { prefs ->
        val theme = ThemePreset.entries.firstOrNull {
            it.name == prefs[Keys.THEME_PRESET]
        } ?: ThemePreset.LIME_STUDIO
        val pause = PauseBehavior.entries.firstOrNull {
            it.name == prefs[Keys.PAUSE_BEHAVIOR]
        } ?: PauseBehavior.DECAY

        AppSettings(
            themePreset = theme,
            customAccentArgb = prefs[Keys.CUSTOM_ACCENT] ?: 0xFFC5F84A.toInt(),
            defaultFftSize = prefs[Keys.DEFAULT_FFT] ?: 1024,
            defaultHopSize = prefs[Keys.DEFAULT_HOP] ?: 256,
            previewScale = prefs[Keys.PREVIEW_SCALE] ?: 1.0f,
                showPreviewDiagnostics=prefs[Keys.DIAGNOSTICS]?:false,
            pauseBehavior = pause,
            showPropertyHelpHints = prefs[Keys.SHOW_HELP] ?: true,
                glassEnabled = prefs[Keys.GLASSENABLED] ?: true,
                glassOpacity = prefs[Keys.GLASSOPACITY] ?: 0.82f,
                backdropBlurDp = prefs[Keys.BACKDROPBLURDP] ?: 24f,
                cornerRadiusDp = prefs[Keys.CORNERRADIUSDP] ?: 12f,
                controlScale = prefs[Keys.CONTROLSCALE] ?: 1f,
                uiTextScale = prefs[Keys.UITEXTSCALE] ?: 1f,
                previewFps = prefs[Keys.PREVIEWFPS] ?: 30,
                previewShare = prefs[Keys.PREVIEWSHARE] ?: 0.44f,
                portraitOverlay = prefs[Keys.PORTRAITOVERLAY] ?: false
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.vexoDataStore.edit { prefs ->
            val current = AppSettings(
                themePreset = ThemePreset.entries.firstOrNull { it.name == prefs[Keys.THEME_PRESET] }
                    ?: ThemePreset.LIME_STUDIO,
                customAccentArgb = prefs[Keys.CUSTOM_ACCENT] ?: 0xFFC5F84A.toInt(),
                defaultFftSize = prefs[Keys.DEFAULT_FFT] ?: 1024,
                defaultHopSize = prefs[Keys.DEFAULT_HOP] ?: 256,
                previewScale = prefs[Keys.PREVIEW_SCALE] ?: 1.0f,
                showPreviewDiagnostics=prefs[Keys.DIAGNOSTICS]?:false,
                pauseBehavior = PauseBehavior.entries.firstOrNull { it.name == prefs[Keys.PAUSE_BEHAVIOR] }
                    ?: PauseBehavior.DECAY,
                showPropertyHelpHints = prefs[Keys.SHOW_HELP] ?: true,
                glassEnabled = prefs[Keys.GLASSENABLED] ?: true,
                glassOpacity = prefs[Keys.GLASSOPACITY] ?: 0.82f,
                backdropBlurDp = prefs[Keys.BACKDROPBLURDP] ?: 24f,
                cornerRadiusDp = prefs[Keys.CORNERRADIUSDP] ?: 12f,
                controlScale = prefs[Keys.CONTROLSCALE] ?: 1f,
                uiTextScale = prefs[Keys.UITEXTSCALE] ?: 1f,
                previewFps = prefs[Keys.PREVIEWFPS] ?: 30,
                previewShare = prefs[Keys.PREVIEWSHARE] ?: 0.44f,
                portraitOverlay = prefs[Keys.PORTRAITOVERLAY] ?: false
            )
            val next = transform(current)
            prefs[Keys.THEME_PRESET] = next.themePreset.name
            prefs[Keys.CUSTOM_ACCENT] = next.customAccentArgb
            prefs[Keys.DEFAULT_FFT] = next.defaultFftSize
            prefs[Keys.DEFAULT_HOP] = next.defaultHopSize
            prefs[Keys.PREVIEW_SCALE] = next.previewScale
            prefs[Keys.DIAGNOSTICS]=next.showPreviewDiagnostics
            prefs[Keys.PAUSE_BEHAVIOR] = next.pauseBehavior.name
            prefs[Keys.SHOW_HELP] = next.showPropertyHelpHints
            prefs[Keys.GLASSENABLED] = next.glassEnabled
            prefs[Keys.GLASSOPACITY] = next.glassOpacity
            prefs[Keys.BACKDROPBLURDP] = next.backdropBlurDp
            prefs[Keys.CORNERRADIUSDP] = next.cornerRadiusDp
            prefs[Keys.CONTROLSCALE] = next.controlScale
            prefs[Keys.UITEXTSCALE] = next.uiTextScale
            prefs[Keys.PREVIEWFPS] = next.previewFps
            prefs[Keys.PREVIEWSHARE] = next.previewShare
            prefs[Keys.PORTRAITOVERLAY] = next.portraitOverlay
        }
    }
}
