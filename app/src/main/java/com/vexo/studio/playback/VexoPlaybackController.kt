package com.vexo.studio.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.Renderer
import com.vexo.studio.engine.audio.PcmRingBuffer
import com.vexo.studio.engine.gl.TrackMetadataSnapshot
import com.vexo.studio.model.AudioTrackItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class RepeatModeState { OFF, ONE, ALL }

data class PlaybackUiState(
    val queue: List<AudioTrackItem> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatModeState = RepeatModeState.OFF,
    val volume: Float = 1f,
    val trackMeta: TrackMetadataSnapshot = TrackMetadataSnapshot(),
    val errorMessage: String? = null
)

/**
 * Single-instance Media3 ExoPlayer controller managing SAF audio imports,
 * persistent permissions, metadata/artwork extraction, queue controls,
 * audio focus, noisy output handling, and PCM tap synchronization.
 */
@UnstableApi
class VexoPlaybackController(
    private val context: Context,
    val ringBuffer: PcmRingBuffer
) {
    val pcmTap = PcmTapAudioProcessor(ringBuffer)
    val sessionVisualizer = com.vexo.studio.engine.audio.SessionVisualizer(context)
    private var visualizerRequested = false
    val soundEffects = SoundEffects(context)

    fun setSessionVisualizerEnabled(enabled: Boolean): Boolean {
        visualizerRequested = enabled
        if (!enabled) { sessionVisualizer.close(); return true }
        return sessionVisualizer.attach(player.audioSessionId)
    }

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private var artworkCounter = 0
    // ExoPlayer access is confined to its application looper. The audio sink reads this snapshot.
    @Volatile private var playbackPositionUs: Long = 0L

    val player: ExoPlayer = buildPlayer()

    init {
        pcmTap.playbackClockUsProvider = { playbackPositionUs }
        soundEffects.attach(player.audioSessionId)
    }

    private fun buildPlayer(): ExoPlayer {
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(pcmTap))
                    .setEnableFloatOutput(false)
                    .build()
            }
        }

        val attrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        return ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(attrs, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onAudioSessionIdChanged(audioSessionId: Int) {
                        if (visualizerRequested) sessionVisualizer.attach(audioSessionId)
                        soundEffects.attach(audioSessionId)
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _state.value = _state.value.copy(isPlaying = isPlaying)
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        val dur = duration.coerceAtLeast(0L)
                        _state.value = _state.value.copy(durationMs = dur)
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val idx = currentMediaItemIndex
                        ringBuffer.resetGeneration()
                        updateCurrentTrackMetadata(idx)
                    }

                    override fun onPositionDiscontinuity(
                        oldPosition: Player.PositionInfo,
                        newPosition: Player.PositionInfo,
                        reason: Int
                    ) {
                        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                            ringBuffer.resetGeneration()
                        }
                        playbackPositionUs = newPosition.positionMs.coerceAtLeast(0L) * 1000L
                        _state.value = _state.value.copy(positionMs = newPosition.positionMs.coerceAtLeast(0L))
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        _state.value = _state.value.copy(
                            isPlaying = false,
                            errorMessage = "Playback error: ${error.errorCodeName}"
                        )
                    }
                })
            }
    }

    fun startForegroundService() {
        runCatching {
            val intent = Intent(context, VexoPlaybackService::class.java)
            context.startService(intent)
        }
    }

    fun loadProjectAudio(uri: String?) {
        if (uri != null && currentAudioUri() == uri) return
        player.pause()
        player.clearMediaItems()
        ringBuffer.resetGeneration()
        playbackPositionUs = 0L
        _state.value = _state.value.copy(queue = emptyList(), currentIndex = -1, positionMs = 0L, durationMs = 0L, isPlaying = false,
            trackMeta = TrackMetadataSnapshot(artworkVersion = ++artworkCounter), errorMessage = null)
        if (!uri.isNullOrBlank()) {
            importTracks(listOf(Uri.parse(uri)), playFirstImmediately = false)
            updateCurrentTrackMetadata(0)
        }
    }

    fun importTracks(uris: List<Uri>, playFirstImmediately: Boolean = true) {
        if (uris.isEmpty()) return
        val newItems = uris.map { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            extractTrackItem(uri)
        }

        val existing = _state.value.queue
        val updatedQueue = existing + newItems
        val startIndex = if (existing.isEmpty() || playFirstImmediately) existing.size else _state.value.currentIndex

        val mediaItems = updatedQueue.map { item ->
            MediaItem.Builder()
                .setUri(Uri.parse(item.uri))
                .setMediaId(item.id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(item.title)
                        .setArtist(item.artist)
                        .setAlbumTitle(item.album)
                        .build()
                )
                .build()
        }

        _state.value = _state.value.copy(
            queue = updatedQueue,
            currentIndex = startIndex,
            errorMessage = null
        )

        if (playFirstImmediately) {
            player.setMediaItems(mediaItems, startIndex, 0L)
            player.prepare()
            player.play()
            startForegroundService()
            updateCurrentTrackMetadata(startIndex)
        } else {
            val curPos = if (existing.isEmpty()) 0L else player.currentPosition
            val wasPlaying = player.isPlaying
            player.setMediaItems(mediaItems, startIndex.coerceAtLeast(0), curPos)
            player.prepare()
            if (wasPlaying) player.play()
        }
    }

    fun playIndex(index: Int) {
        val queue = _state.value.queue
        if (index !in queue.indices) return
        ringBuffer.resetGeneration()
        player.seekToDefaultPosition(index)
        player.prepare()
        player.play()
        startForegroundService()
        updateCurrentTrackMetadata(index)
    }

    fun togglePlayPause() {
        if (_state.value.queue.isEmpty()) return
        if (player.playWhenReady && player.playbackState != Player.STATE_ENDED && player.playbackState != Player.STATE_IDLE) {
            player.pause()
        } else {
            clearError()
            if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
            startForegroundService()
        }
    }

    fun seekTo(positionMs: Long) {
        ringBuffer.resetGeneration()
        val duration = player.duration
        val target = if (duration > 0L) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L)
        playbackPositionUs = target * 1000L
        player.seekTo(target)
        _state.value = _state.value.copy(positionMs = target)
    }

    fun restartTrack() = seekTo(0L)

    fun next() {
        if (player.hasNextMediaItem()) {
            ringBuffer.resetGeneration()
            player.seekToNextMediaItem()
        } else if (_state.value.queue.isNotEmpty()) {
            playIndex(0)
        }
    }

    fun previous() {
        if (player.currentPosition > 3000L) {
            restartTrack()
        } else if (player.hasPreviousMediaItem()) {
            ringBuffer.resetGeneration()
            player.seekToPreviousMediaItem()
        }
    }

    fun toggleShuffle() {
        val next = !player.shuffleModeEnabled
        player.shuffleModeEnabled = next
        _state.value = _state.value.copy(shuffleEnabled = next)
    }

    fun cycleRepeatMode() {
        val next = when (_state.value.repeatMode) {
            RepeatModeState.OFF -> RepeatModeState.ALL
            RepeatModeState.ALL -> RepeatModeState.ONE
            RepeatModeState.ONE -> RepeatModeState.OFF
        }
        player.repeatMode = when (next) {
            RepeatModeState.OFF -> Player.REPEAT_MODE_OFF
            RepeatModeState.ONE -> Player.REPEAT_MODE_ONE
            RepeatModeState.ALL -> Player.REPEAT_MODE_ALL
        }
        _state.value = _state.value.copy(repeatMode = next)
    }

    fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0f, 1f)
        player.volume = clamped
        _state.value = _state.value.copy(volume = clamped)
    }

    fun removeFromQueue(index: Int) {
        val q = _state.value.queue.toMutableList()
        if (index !in q.indices) return
        q.removeAt(index)
        player.removeMediaItem(index)
        val newIdx = player.currentMediaItemIndex.coerceIn(-1, q.lastIndex)
        _state.value = _state.value.copy(queue = q, currentIndex = newIdx)
        updateCurrentTrackMetadata(newIdx)
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    fun pollPosition(): Long {
        val pos = player.currentPosition.coerceAtLeast(0L)
        playbackPositionUs = pos * 1000L
        val dur = player.duration.coerceAtLeast(0L)
        val cur = _state.value
        if (cur.positionMs != pos || cur.durationMs != dur) {
            _state.value = cur.copy(
                positionMs = pos,
                durationMs = dur,
                trackMeta = cur.trackMeta.copy(positionMs = pos, durationMs = dur)
            )
        }
        return pos
    }

    fun currentAudioUri(): String? {
        val idx = _state.value.currentIndex
        return _state.value.queue.getOrNull(idx)?.uri ?: _state.value.queue.firstOrNull()?.uri
    }

    fun release() {
        sessionVisualizer.close()
        soundEffects.close()
        player.release()
    }

    private fun updateCurrentTrackMetadata(index: Int) {
        val item = _state.value.queue.getOrNull(index)
        if (item == null) {
            _state.value = _state.value.copy(
                currentIndex = -1,
                trackMeta = TrackMetadataSnapshot()
            )
            return
        }
        val art = extractArtworkBitmap(Uri.parse(item.uri))
        artworkCounter++
        _state.value = _state.value.copy(
            currentIndex = index,
            trackMeta = TrackMetadataSnapshot(
                title = item.title,
                artist = item.artist,
                album = item.album,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = item.durationMs,
                artwork = art,
                artworkVersion = artworkCounter
            )
        )
    }

    private fun extractTrackItem(uri: Uri): AudioTrackItem {
        val retriever = MediaMetadataRetriever()
        var title = uri.lastPathSegment?.substringAfterLast('/') ?: "Audio Track"
        var artist = "Unknown Artist"
        var album = "Unknown Album"
        var dur = 0L
        runCatching {
            retriever.setDataSource(context, uri)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: title
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() } ?: artist
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() } ?: album
            dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        }
        runCatching { retriever.release() }
        return AudioTrackItem(
            id = UUID.randomUUID().toString(),
            uri = uri.toString(),
            title = title,
            artist = artist,
            album = album,
            durationMs = dur
        )
    }

    private fun extractArtworkBitmap(uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val bytes = retriever.embeddedPicture ?: return null
            val opts = BitmapFactory.Options().apply { inSampleSize = 1 }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
