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
        val THEME_PRESET = stringPreferencesKey("theme_preset")
        val CUSTOM_ACCENT = intPreferencesKey("custom_accent")
        val DEFAULT_FFT = intPreferencesKey("default_fft")
        val DEFAULT_HOP = intPreferencesKey("default_hop")
        val PREVIEW_SCALE = floatPreferencesKey("preview_scale")
        val PAUSE_BEHAVIOR = stringPreferencesKey("pause_behavior")
        val SHOW_HELP = booleanPreferencesKey("show_help_hints")
    }

    val settingsFlow: Flow<AppSettings> = context.vexoDataStore.data.map { prefs ->
        val theme = ThemePreset.entries.firstOrNull {
            it.name == prefs[Keys.THEME_PRESET]
        } ?: ThemePreset.MONOCHROME_DARK
        val pause = PauseBehavior.entries.firstOrNull {
            it.name == prefs[Keys.PAUSE_BEHAVIOR]
        } ?: PauseBehavior.DECAY

        AppSettings(
            themePreset = theme,
            customAccentArgb = prefs[Keys.CUSTOM_ACCENT] ?: 0xFFC6F36A.toInt(),
            defaultFftSize = prefs[Keys.DEFAULT_FFT] ?: 1024,
            defaultHopSize = prefs[Keys.DEFAULT_HOP] ?: 256,
            previewScale = prefs[Keys.PREVIEW_SCALE] ?: 1.0f,
            pauseBehavior = pause,
            showPropertyHelpHints = prefs[Keys.SHOW_HELP] ?: true
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.vexoDataStore.edit { prefs ->
            val current = AppSettings(
                themePreset = ThemePreset.entries.firstOrNull { it.name == prefs[Keys.THEME_PRESET] }
                    ?: ThemePreset.MONOCHROME_DARK,
                customAccentArgb = prefs[Keys.CUSTOM_ACCENT] ?: 0xFFC6F36A.toInt(),
                defaultFftSize = prefs[Keys.DEFAULT_FFT] ?: 1024,
                defaultHopSize = prefs[Keys.DEFAULT_HOP] ?: 256,
                previewScale = prefs[Keys.PREVIEW_SCALE] ?: 1.0f,
                pauseBehavior = PauseBehavior.entries.firstOrNull { it.name == prefs[Keys.PAUSE_BEHAVIOR] }
                    ?: PauseBehavior.DECAY,
                showPropertyHelpHints = prefs[Keys.SHOW_HELP] ?: true
            )
            val next = transform(current)
            prefs[Keys.THEME_PRESET] = next.themePreset.name
            prefs[Keys.CUSTOM_ACCENT] = next.customAccentArgb
            prefs[Keys.DEFAULT_FFT] = next.defaultFftSize
            prefs[Keys.DEFAULT_HOP] = next.defaultHopSize
            prefs[Keys.PREVIEW_SCALE] = next.previewScale
            prefs[Keys.PAUSE_BEHAVIOR] = next.pauseBehavior.name
            prefs[Keys.SHOW_HELP] = next.showPropertyHelpHints
        }
    }
}
