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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
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
    val transitionFadeMs: Int = 0,
    val trackMeta: TrackMetadataSnapshot = TrackMetadataSnapshot(),
    val errorMessage: String? = null,
    val importTotal:Int=0,
    val importCompleted:Int=0
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
    private val artworkScope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var artworkJob:Job?=null
    private var importJob:Job?=null
    private var importGeneration=0L
    private val queueStore=ProjectQueueStore(context)
    private var queueProjectId:String?=null
    private var lastSavedQueue:List<AudioTrackItem>?=null
    private var lastQueueSave=0L
    private var restoringQueue=false
    private val _sleepRemaining=MutableStateFlow(0L)
    val sleepRemaining=_sleepRemaining.asStateFlow()
    private var sleepDeadline=0L
    private var lastSleepTick=0L
    private val playbackPrefs=context.getSharedPreferences("playback_options",Context.MODE_PRIVATE)
    private val transitionHandler=Handler(android.os.Looper.getMainLooper())
    private var fadeInStartedMs=0L
    private val transitionTick=object:Runnable{
        override fun run(){
            val now=android.os.SystemClock.elapsedRealtime()
            if(sleepDeadline>0L&&now-lastSleepTick>=250L){
                lastSleepTick=now
                val left=PlaybackClock.remainingSeconds(sleepDeadline,now)
                _sleepRemaining.value=left
                if(left==0L){sleepDeadline=0L;player.pause()}
            }
            if(now-lastQueueSave>=5000L){lastQueueSave=now;saveQueue()}
            val st=_state.value
            val duration=player.duration
            val remaining=if(duration>0)duration-player.currentPosition else Long.MAX_VALUE
            val since=android.os.SystemClock.elapsedRealtime()-fadeInStartedMs
            val gain=PlaybackTransition.gain(st.transitionFadeMs,remaining,since,player.hasNextMediaItem(),player.isPlaying)
            val target=st.volume*gain
            if(kotlin.math.abs(player.volume-target)>.002f)player.volume=target
            transitionHandler.postDelayed(this,40)
        }
    }
    // ExoPlayer access is confined to its application looper. The audio sink reads this snapshot.
    @Volatile private var playbackPositionUs: Long = 0L

    val player: ExoPlayer = buildPlayer()

    init {
        pcmTap.playbackClockUsProvider = { playbackPositionUs }
        soundEffects.attach(player.audioSessionId)
        val volume=runCatching{playbackPrefs.getFloat("volume",1f)}.getOrDefault(1f).takeIf{it.isFinite()}?.coerceIn(0f,1f)?:1f
        val repeat=runCatching{RepeatModeState.valueOf(playbackPrefs.getString("repeat","OFF")?:"OFF")}.getOrDefault(RepeatModeState.OFF)
        val shuffle=playbackPrefs.getBoolean("shuffle",false)
        player.volume=volume
        player.shuffleModeEnabled=shuffle
        player.repeatMode=when(repeat){RepeatModeState.OFF->Player.REPEAT_MODE_OFF;RepeatModeState.ONE->Player.REPEAT_MODE_ONE;RepeatModeState.ALL->Player.REPEAT_MODE_ALL}
        _state.value=_state.value.copy(volume=volume,repeatMode=repeat,shuffleEnabled=shuffle,transitionFadeMs=playbackPrefs.getInt("fade_ms",0).coerceIn(0,5000))
        transitionHandler.post(transitionTick)
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
                        if(!isPlaying)saveQueue()
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        val dur = duration.coerceAtLeast(0L)
                        _state.value = _state.value.copy(durationMs = dur)
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val idx = currentMediaItemIndex
                        ringBuffer.resetGeneration()
                        fadeInStartedMs=if(reason==Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)android.os.SystemClock.elapsedRealtime() else 0L
                        updateCurrentTrackMetadata(idx)
                        saveQueue()
                    }

                    override fun onPositionDiscontinuity(
                        oldPosition: Player.PositionInfo,
                        newPosition: Player.PositionInfo,
                        reason: Int
                    ) {
                        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                            ringBuffer.resetGeneration()
                            fadeInStartedMs=0L
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

    fun cancelAudioImport(){
        importGeneration++
        importJob?.cancel();importJob=null
        _state.value=_state.value.copy(importTotal=0,importCompleted=0)
    }

    private fun saveQueue(){
        val id=queueProjectId?:return
        if(restoringQueue||_state.value.importTotal>0)return
        val q=_state.value.queue
        val index=player.currentMediaItemIndex
        runCatching{queueStore.save(id,q,q.getOrNull(index)?.id,player.currentPosition,q!==lastSavedQueue)}.onSuccess{lastSavedQueue=q}
    }
    fun forgetProjectQueue(id:String){
        if(queueProjectId==id){queueProjectId=null;lastSavedQueue=null;cancelAudioImport()}
        runCatching{queueStore.delete(id)}
    }
    fun loadProjectAudio(projectId:String,uri: String?) {
        if(queueProjectId==projectId)return
        saveQueue()
        cancelAudioImport()
        restoringQueue=true
        queueProjectId=projectId;lastSavedQueue=null
        artworkJob?.cancel();artworkJob=null
        player.pause()
        player.clearMediaItems()
        ringBuffer.resetGeneration()
        playbackPositionUs = 0L
        _state.value = _state.value.copy(queue = emptyList(), currentIndex = -1, positionMs = 0L, durationMs = 0L, isPlaying = false,
            trackMeta = TrackMetadataSnapshot(artworkVersion = ++artworkCounter), errorMessage = null)
        val restored=queueStore.read(projectId)
        if(restored!=null){
            val tracks=restored.tracks
            _state.value=_state.value.copy(queue=tracks,currentIndex=restored.index)
            if(tracks.isNotEmpty()){
                player.setMediaItems(tracks.map(::mediaItem),restored.index,restored.positionMs)
                player.prepare();updateCurrentTrackMetadata(restored.index)
            }
            lastSavedQueue=tracks
        }
        restoringQueue=false
        if(restored==null&&!uri.isNullOrBlank())importTracks(listOf(Uri.parse(uri)),playFirstImmediately=false)
    }

    fun importTracks(uris:List<Uri>,playFirstImmediately:Boolean=true,onImported:(AudioTrackItem)->Unit={}){
        if(uris.isEmpty())return
        cancelAudioImport()
        val generation=importGeneration
        val room=(5000-_state.value.queue.size).coerceAtLeast(0)
        if(room==0){_state.value=_state.value.copy(errorMessage="Queue is full (5000 tracks)");return}
        val input=uris.take(room)
        _state.value=_state.value.copy(importTotal=input.size,importCompleted=0,errorMessage=null)
        importJob=artworkScope.launch{
            try{
                val items=withContext(Dispatchers.IO){
                    val result=ArrayList<AudioTrackItem>(input.size)
                    for((index,uri) in input.withIndex()){
                        ensureActive()
                        runCatching{context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}
                        result.add(extractTrackItem(uri))
                        if(index%16==0||index==input.lastIndex)withContext(Dispatchers.Main.immediate){
                            if(generation==importGeneration)_state.value=_state.value.copy(importCompleted=index+1)
                        }
                    }
                    result
                }
                if(generation!=importGeneration)return@launch
                appendImportedTracks(items,playFirstImmediately)
                if(items.isNotEmpty())onImported(items.first())
                if(uris.size>input.size)_state.value=_state.value.copy(errorMessage="Import limited to 5000 tracks per batch")
            }catch(cancelled:CancellationException){throw cancelled}
            catch(err:Exception){if(generation==importGeneration)_state.value=_state.value.copy(errorMessage="Audio import failed: "+err.javaClass.simpleName)}
            finally{if(generation==importGeneration){_state.value=_state.value.copy(importTotal=0,importCompleted=0);importJob=null;saveQueue()}}
        }
    }

    private fun appendImportedTracks(newItems:List<AudioTrackItem>,playFirstImmediately:Boolean){
        if(newItems.isEmpty())return
        val existing = _state.value.queue
        val updatedQueue = existing + newItems
        val startIndex = if (existing.isEmpty() || playFirstImmediately) existing.size else _state.value.currentIndex

        val mediaItems = updatedQueue.map(::mediaItem)

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
        } else if(existing.isNotEmpty()) {
            player.addMediaItems(mediaItems.drop(existing.size))
        } else {
            player.setMediaItems(mediaItems,0,0L)
            player.prepare()
            updateCurrentTrackMetadata(0)
        }
    }

    private fun mediaItem(item:AudioTrackItem)=MediaItem.Builder().setUri(Uri.parse(item.uri)).setMediaId(item.id).setMediaMetadata(MediaMetadata.Builder().setTitle(item.title).setArtist(item.artist).setAlbumTitle(item.album).build()).build()

    fun moveQueueItem(from:Int,to:Int){
        val q=_state.value.queue.toMutableList()
        if(from !in q.indices||to !in q.indices||from==to)return
        q.add(to,q.removeAt(from))
        restoringQueue=true
        try{_state.value=_state.value.copy(queue=q);player.moveMediaItem(from,to);_state.value=_state.value.copy(currentIndex=player.currentMediaItemIndex)}finally{restoringQueue=false}
        saveQueue()
    }
    fun clearQueue(){
        cancelAudioImport();restoringQueue=true
        try{player.pause();player.clearMediaItems();ringBuffer.resetGeneration();_state.value=_state.value.copy(queue=emptyList(),currentIndex=-1,positionMs=0,durationMs=0,trackMeta=TrackMetadataSnapshot(artworkVersion=++artworkCounter))}finally{restoringQueue=false}
        saveQueue()
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

    fun setSleepTimer(minutes:Int){
        val count=minutes.coerceIn(0,180)
        sleepDeadline=if(count>0)android.os.SystemClock.elapsedRealtime()+count*60_000L else 0L
        lastSleepTick=0L
        _sleepRemaining.value=count*60L
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
        saveQueue()
    }

    fun restartTrack() = seekTo(0L)

    fun next() {
        if (player.hasNextMediaItem()) {
            ringBuffer.resetGeneration()
            player.seekToNextMediaItem()
        } else if (_state.value.repeatMode==RepeatModeState.ALL && _state.value.queue.isNotEmpty()) {
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
        playbackPrefs.edit().putBoolean("shuffle",next).apply()
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
        playbackPrefs.edit().putString("repeat",next.name).apply()
    }

    fun setTransitionFade(ms:Int){
        val v=ms.coerceIn(0,5000)
        _state.value=_state.value.copy(transitionFadeMs=v)
        playbackPrefs.edit().putInt("fade_ms",v).apply()
        if(v==0)player.volume=_state.value.volume
    }

    fun setVolume(vol: Float) {
        if(!vol.isFinite())return
        val clamped = vol.coerceIn(0f, 1f)
        player.volume = clamped
        _state.value = _state.value.copy(volume = clamped)
        playbackPrefs.edit().putFloat("volume",clamped).apply()
    }

    fun removeFromQueue(index: Int) {
        val q = _state.value.queue.toMutableList()
        if (index !in q.indices) return
        q.removeAt(index)
        _state.value=_state.value.copy(queue=q)
        player.removeMediaItem(index)
        val newIdx = player.currentMediaItemIndex.coerceIn(-1, q.lastIndex)
        _state.value = _state.value.copy(queue = q, currentIndex = newIdx)
        updateCurrentTrackMetadata(newIdx)
        saveQueue()
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
        transitionHandler.removeCallbacks(transitionTick)
        saveQueue()
        cancelAudioImport()
        artworkScope.cancel()
        sessionVisualizer.close()
        soundEffects.close()
        player.release()
    }

    private fun updateCurrentTrackMetadata(index:Int){
        val item=_state.value.queue.getOrNull(index)
        artworkJob?.cancel()
        val generation=++artworkCounter
        if(item==null){
            _state.value=_state.value.copy(currentIndex=-1,positionMs=0L,durationMs=0L,trackMeta=TrackMetadataSnapshot(artworkVersion=generation))
            return
        }
        val pos=player.currentPosition.coerceAtLeast(0L)
        val dur=player.duration.takeIf{it>0}?:item.durationMs
        _state.value=_state.value.copy(currentIndex=index,positionMs=pos,durationMs=dur,errorMessage=null,
            trackMeta=TrackMetadataSnapshot(title=item.title,artist=item.artist,album=item.album,positionMs=pos,durationMs=dur,artworkVersion=generation))
        artworkJob=artworkScope.launch{
            val bitmap=withContext(Dispatchers.IO){extractArtworkBitmap(Uri.parse(item.uri))}
            val state=_state.value
            if(generation==artworkCounter&&state.queue.getOrNull(state.currentIndex)?.id==item.id){
                _state.value=state.copy(trackMeta=state.trackMeta.copy(artwork=bitmap,artworkVersion=++artworkCounter))
            }else bitmap?.recycle()
        }
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
            if(bytes.size>20*1024*1024)return null
            val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
            BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
            var sample=1
            while(maxOf(bounds.outWidth,bounds.outHeight)/sample>1024)sample*=2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
