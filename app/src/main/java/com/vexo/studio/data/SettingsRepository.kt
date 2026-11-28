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
        val ADAPTIVE_PREVIEW = booleanPreferencesKey("adaptive_preview")
        val DIAGNOSTICS = booleanPreferencesKey("preview_diagnostics")
        val PAUSE_BEHAVIOR = stringPreferencesKey("pause_behavior")
        val SHOW_HELP = booleanPreferencesKey("show_help_hints")
        val GLASSENABLED = booleanPreferencesKey("glassEnabled")
        val GLASSOPACITY = floatPreferencesKey("glassOpacity")
        val BACKDROPBLURDP = floatPreferencesKey("backdropBlurDp")
        val CORNERRADIUSDP = floatPreferencesKey("cornerRadiusDp")
        val SEPARATE_CORNERS = booleanPreferencesKey("separate_corners")
        val CARD_RADIUS = floatPreferencesKey("card_radius")
        val BUTTON_RADIUS = floatPreferencesKey("button_radius")
        val DIALOG_RADIUS = floatPreferencesKey("dialog_radius")
        val NAV_RADIUS = floatPreferencesKey("nav_radius")
        val PLAYER_RADIUS = floatPreferencesKey("player_radius")
        val CONTROLSCALE = floatPreferencesKey("controlScale")
        val UITEXTSCALE = floatPreferencesKey("uiTextScale")
        val PREVIEWFPS = intPreferencesKey("previewFps")
        val PREVIEWSHARE = floatPreferencesKey("previewShare")
        val PORTRAITOVERLAY = booleanPreferencesKey("portraitOverlay")
        val WALLPAPER = stringPreferencesKey("wallpaperUri")
        val WALLBLUR = floatPreferencesKey("wallpaperBlurDp")
        val WALLDIM = floatPreferencesKey("wallpaperDim")
        val ALLACCENT = booleanPreferencesKey("customAccentAllThemes")
        val PROFILE_NAME = stringPreferencesKey("profile_name")
        val PROFILE_GENDER = stringPreferencesKey("profile_gender")
        val PROFILE_BIO = stringPreferencesKey("profile_bio")
        val PROFILE_PHOTO = stringPreferencesKey("profile_photo")
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
                adaptivePreview = prefs[Keys.ADAPTIVE_PREVIEW] ?: false,
                showPreviewDiagnostics=prefs[Keys.DIAGNOSTICS]?:false,
            pauseBehavior = pause,
            showPropertyHelpHints = prefs[Keys.SHOW_HELP] ?: true,
                glassEnabled = prefs[Keys.GLASSENABLED] ?: true,
                glassOpacity = prefs[Keys.GLASSOPACITY] ?: 0.82f,
                backdropBlurDp = prefs[Keys.BACKDROPBLURDP] ?: 24f,
                cornerRadiusDp = prefs[Keys.CORNERRADIUSDP] ?: 12f,
                separateCornerRadii = prefs[Keys.SEPARATE_CORNERS] ?: false,
                cardRadiusDp = prefs[Keys.CARD_RADIUS] ?: 12f,
                buttonRadiusDp = prefs[Keys.BUTTON_RADIUS] ?: 12f,
                dialogRadiusDp = prefs[Keys.DIALOG_RADIUS] ?: 20f,
                navigationRadiusDp = prefs[Keys.NAV_RADIUS] ?: 32f,
                playerRadiusDp = prefs[Keys.PLAYER_RADIUS] ?: 16f,
                controlScale = prefs[Keys.CONTROLSCALE] ?: 1f,
                uiTextScale = prefs[Keys.UITEXTSCALE] ?: 1f,
                previewFps = prefs[Keys.PREVIEWFPS] ?: 30,
                previewShare = prefs[Keys.PREVIEWSHARE] ?: 0.41f,
                portraitOverlay = prefs[Keys.PORTRAITOVERLAY] ?: false,
                wallpaperUri = prefs[Keys.WALLPAPER] ?: "",
                wallpaperBlurDp = prefs[Keys.WALLBLUR] ?: 16f,
                wallpaperDim = prefs[Keys.WALLDIM] ?: .72f,
                customAccentAllThemes = prefs[Keys.ALLACCENT] ?: false,
                profileName = prefs[Keys.PROFILE_NAME] ?: "",
                profileGender = prefs[Keys.PROFILE_GENDER] ?: "",
                profileBio = prefs[Keys.PROFILE_BIO] ?: "",
                profilePhotoUri = prefs[Keys.PROFILE_PHOTO] ?: ""
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
                adaptivePreview = prefs[Keys.ADAPTIVE_PREVIEW] ?: false,
                showPreviewDiagnostics=prefs[Keys.DIAGNOSTICS]?:false,
                pauseBehavior = PauseBehavior.entries.firstOrNull { it.name == prefs[Keys.PAUSE_BEHAVIOR] }
                    ?: PauseBehavior.DECAY,
                showPropertyHelpHints = prefs[Keys.SHOW_HELP] ?: true,
                glassEnabled = prefs[Keys.GLASSENABLED] ?: true,
                glassOpacity = prefs[Keys.GLASSOPACITY] ?: 0.82f,
                backdropBlurDp = prefs[Keys.BACKDROPBLURDP] ?: 24f,
                cornerRadiusDp = prefs[Keys.CORNERRADIUSDP] ?: 12f,
                separateCornerRadii = prefs[Keys.SEPARATE_CORNERS] ?: false,
                cardRadiusDp = prefs[Keys.CARD_RADIUS] ?: 12f,
                buttonRadiusDp = prefs[Keys.BUTTON_RADIUS] ?: 12f,
                dialogRadiusDp = prefs[Keys.DIALOG_RADIUS] ?: 20f,
                navigationRadiusDp = prefs[Keys.NAV_RADIUS] ?: 32f,
                playerRadiusDp = prefs[Keys.PLAYER_RADIUS] ?: 16f,
                controlScale = prefs[Keys.CONTROLSCALE] ?: 1f,
                uiTextScale = prefs[Keys.UITEXTSCALE] ?: 1f,
                previewFps = prefs[Keys.PREVIEWFPS] ?: 30,
                previewShare = prefs[Keys.PREVIEWSHARE] ?: 0.41f,
                portraitOverlay = prefs[Keys.PORTRAITOVERLAY] ?: false,
                wallpaperUri = prefs[Keys.WALLPAPER] ?: "",
                wallpaperBlurDp = prefs[Keys.WALLBLUR] ?: 16f,
                wallpaperDim = prefs[Keys.WALLDIM] ?: .72f,
                customAccentAllThemes = prefs[Keys.ALLACCENT] ?: false,
                profileName = prefs[Keys.PROFILE_NAME] ?: "",
                profileGender = prefs[Keys.PROFILE_GENDER] ?: "",
                profileBio = prefs[Keys.PROFILE_BIO] ?: "",
                profilePhotoUri = prefs[Keys.PROFILE_PHOTO] ?: ""
            )
            val next = transform(current)
            prefs[Keys.THEME_PRESET] = next.themePreset.name
            prefs[Keys.CUSTOM_ACCENT] = next.customAccentArgb
            prefs[Keys.DEFAULT_FFT] = next.defaultFftSize
            prefs[Keys.DEFAULT_HOP] = next.defaultHopSize
            prefs[Keys.PREVIEW_SCALE] = next.previewScale
            prefs[Keys.ADAPTIVE_PREVIEW] = next.adaptivePreview
            prefs[Keys.DIAGNOSTICS]=next.showPreviewDiagnostics
            prefs[Keys.PAUSE_BEHAVIOR] = next.pauseBehavior.name
            prefs[Keys.SHOW_HELP] = next.showPropertyHelpHints
            prefs[Keys.GLASSENABLED] = next.glassEnabled
            prefs[Keys.GLASSOPACITY] = next.glassOpacity
            prefs[Keys.BACKDROPBLURDP] = next.backdropBlurDp
            prefs[Keys.CORNERRADIUSDP] = next.cornerRadiusDp
            prefs[Keys.SEPARATE_CORNERS] = next.separateCornerRadii
            prefs[Keys.CARD_RADIUS] = next.cardRadiusDp
            prefs[Keys.BUTTON_RADIUS] = next.buttonRadiusDp
            prefs[Keys.DIALOG_RADIUS] = next.dialogRadiusDp
            prefs[Keys.NAV_RADIUS] = next.navigationRadiusDp
            prefs[Keys.PLAYER_RADIUS] = next.playerRadiusDp
            prefs[Keys.CONTROLSCALE] = next.controlScale
            prefs[Keys.UITEXTSCALE] = next.uiTextScale
            prefs[Keys.PREVIEWFPS] = next.previewFps
            prefs[Keys.PREVIEWSHARE] = next.previewShare
            prefs[Keys.PORTRAITOVERLAY] = next.portraitOverlay
            prefs[Keys.WALLPAPER] = next.wallpaperUri
            prefs[Keys.WALLBLUR] = next.wallpaperBlurDp
            prefs[Keys.WALLDIM] = next.wallpaperDim
            prefs[Keys.ALLACCENT] = next.customAccentAllThemes
            prefs[Keys.PROFILE_NAME] = next.profileName
            prefs[Keys.PROFILE_GENDER] = next.profileGender
            prefs[Keys.PROFILE_BIO] = next.profileBio
            prefs[Keys.PROFILE_PHOTO] = next.profilePhotoUri
        }
    }
}
