package com.vexo.studio

import android.app.Application
import androidx.media3.common.util.UnstableApi
import com.vexo.studio.data.ProjectRepository
import com.vexo.studio.data.SettingsRepository
import com.vexo.studio.engine.audio.PcmRingBuffer
import com.vexo.studio.playback.VexoPlaybackController

@UnstableApi
class VexoApplication : Application() {
    val pcmRingBuffer: PcmRingBuffer by lazy { PcmRingBuffer(65536) }
    val playbackController: VexoPlaybackController by lazy {
        VexoPlaybackController(this, pcmRingBuffer)
    }
    val projectRepository: ProjectRepository by lazy { ProjectRepository(this) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    override fun onTerminate() {
        playbackController.release()
        super.onTerminate()
    }
}
