package com.vexo.studio.export

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.Surface
import com.vexo.studio.engine.audio.AudioProviderEngine
import com.vexo.studio.engine.audio.AudioProviderSnapshot
import com.vexo.studio.engine.gl.RenderSceneSnapshot
import com.vexo.studio.engine.gl.TrackMetadataSnapshot
import com.vexo.studio.engine.gl.VexoGlRenderer
import com.vexo.studio.model.VexoProject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.yield
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import android.media.MediaCodecList
import java.io.RandomAccessFile
import java.io.BufferedOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

data class ExportProgress(
    val isRunning: Boolean = false,
    val progress: Float = 0f,
    val etaSeconds: Long = 0L,
    val statusText: String = "Idle",
    val publishedUri: String? = null,
    val errorMessage: String? = null
)

/**
 * Deterministic offline MP4 exporter using:
 * 1. MediaExtractor + MediaCodec PCM decoder aligned to video frame timestamps
 * 2. Shared AudioProviderEngine + VexoGlRenderer on an EGL14 MediaCodec input Surface
 * 3. MediaMuxer writing temporary MP4, verified via MediaMetadataRetriever, then
 *    published atomically to MediaStore.Video.Media or SAF URI.
 */
class VexoVideoExporter(private val context: Context) {

    suspend fun export(
        project: VexoProject,
        audioUriStr: String,
        trackMeta: TrackMetadataSnapshot,
        onProgress: (ExportProgress) -> Unit
    ): String {
        val audioUri = Uri.parse(audioUriStr)
        val settings = project.exportSettings
        val (width, height) = computeDimensions(settings.aspectRatio.widthRatio, settings.aspectRatio.heightRatio, settings.resolution.shortSide)
        val fps = settings.fps.coerceIn(24, 60)
        val bitrate = settings.videoBitrateMbps.coerceIn(2, 80) * 1_000_000

        val tempFile = File(context.cacheDir, "vexo_export_${System.currentTimeMillis()}.mp4")

        var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
        var encoderSurface: Surface? = null
        var videoEncoder: MediaCodec? = null
        var audioEncoder: MediaCodec? = null
        var audioDecoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        val pcmFile = File(context.cacheDir, tempFile.nameWithoutExtension + ".pcm")
        var pcmReader: RandomAccessFile? = null
        var inputExtractor: MediaExtractor? = null
        var glRenderer: VexoGlRenderer? = null
        val exportContext = currentCoroutineContext()

        try {
            onProgress(ExportProgress(isRunning = true, progress = 0.01f, statusText = "Preparing audio & encoder"))

            val extractor = MediaExtractor()
            inputExtractor = extractor
            extractor.setDataSource(context, audioUri, null)
            val audioTrackIndex = (0 until extractor.trackCount).firstOrNull { idx ->
                extractor.getTrackFormat(idx).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("Selected file has no audio track")

            val srcAudioFormat = extractor.getTrackFormat(audioTrackIndex)
            val sampleRate = srcAudioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = srcAudioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val durationUsRaw = if (srcAudioFormat.containsKey(MediaFormat.KEY_DURATION)) {
                srcAudioFormat.getLong(MediaFormat.KEY_DURATION)
            } else {
                trackMeta.durationMs.coerceAtLeast(5000L) * 1000L
            }

            val startUs = (settings.trimStartMs.coerceAtLeast(0L)) * 1000L
            val endUs = if (settings.trimEndMs > settings.trimStartMs) {
                minOf(durationUsRaw, settings.trimEndMs * 1000L)
            } else {
                durationUsRaw
            }
            require(startUs < endUs && endUs <= durationUsRaw) { "Invalid audio trim range" }
            require(channels in 1..2) { "Export currently supports mono or stereo audio" }
            val totalDurationUs = endUs - startUs
            val totalFrames = kotlin.math.ceil(totalDurationUs / 1_000_000.0 * fps).toInt().coerceAtLeast(1)

            val required=ExportBudget.workingBytes(totalDurationUs,bitrate,settings.audioBitrateKbps.coerceIn(96,320)*1000,sampleRate,channels)
            check(context.cacheDir.usableSpace>=required){"Not enough free cache storage. Need about %.0f MB".format(required/1_000_000.0)}
            // Select supported encoder before spending time decoding audio.
            fun format(mime:String)=MediaFormat.createVideoFormat(mime,width,height).apply{
                setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE,bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE,fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1)
            }
            val codecList=MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val candidates=if(settings.useHevcIfSupported)listOf(MediaFormat.MIMETYPE_VIDEO_HEVC,MediaFormat.MIMETYPE_VIDEO_AVC) else listOf(MediaFormat.MIMETYPE_VIDEO_AVC)
            val selected=candidates.firstNotNullOfOrNull{mime->val f=format(mime);runCatching{codecList.findEncoderForFormat(f)}.getOrNull()?.let{Triple(mime,f,it)}}
                ?:error("No compatible encoder for selected resolution, FPS and bitrate")
            val (videoMime,videoFormat,encoderName)=selected
            onProgress(ExportProgress(isRunning=true,progress=.01f,statusText="Decoding audio · "+if(videoMime==MediaFormat.MIMETYPE_VIDEO_HEVC)"HEVC" else "H.264"))
            // Decode entire trimmed PCM window into disk spool
            extractor.selectTrack(audioTrackIndex)
            if (startUs > 0) extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val srcMime = srcAudioFormat.getString(MediaFormat.KEY_MIME)!!
            audioDecoder = MediaCodec.createDecoderByType(srcMime).apply {
                configure(srcAudioFormat, null, null, 0)
                start()
            }

            val pcmDecoded = decodeTrimmedPcm(
                extractor = extractor,
                decoder = audioDecoder,
                startUs = startUs,
                endUs = endUs,
                channels = channels,
                sampleRate = sampleRate,
                target = pcmFile
            )
            pcmReader = RandomAccessFile(pcmFile, "r")
            extractor.release(); inputExtractor = null
            audioDecoder.stop(); audioDecoder.release(); audioDecoder = null

            // Configure selected video encoder + EGL surface
            videoEncoder = MediaCodec.createByCodecName(encoderName).apply {
                configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }
            encoderSurface = videoEncoder.createInputSurface()
            videoEncoder.start()

            // Configure AAC Audio Encoder
            val audioOutFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                sampleRate,
                channels
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, settings.audioBitrateKbps * 1000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                configure(audioOutFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }

            // Initialize EGL14 Context on encoderSurface
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val vers = IntArray(2)
            check(EGL14.eglInitialize(eglDisplay, vers, 0, vers, 1)) { "EGL initialization failed" }
            val attribList = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                0x3142, 1, // EGL_RECORDABLE_ANDROID
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            check(EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0) && numConfigs[0]>0) { "No recordable EGL configuration" }
            eglContext = EGL14.eglCreateContext(
                eglDisplay,
                configs[0],
                EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
                0
            )
            eglSurface = EGL14.eglCreateWindowSurface(
                eglDisplay,
                configs[0],
                encoderSurface,
                intArrayOf(EGL14.EGL_NONE),
                0
            )
            check(eglContext != EGL14.EGL_NO_CONTEXT && eglSurface != EGL14.EGL_NO_SURFACE && EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) { "Cannot bind encoder EGL surface" }

            val renderer = VexoGlRenderer(context, isExportRenderer = true).apply { initGlResources() }
            glRenderer = renderer
            val engines = project.audioProviders.associate { it.index to AudioProviderEngine(it) }

            val activeMuxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = activeMuxer
            var videoTrackMuxer = -1
            var audioTrackMuxer = -1

            val videoBufferInfo = MediaCodec.BufferInfo()
            val audioBufferInfo = MediaCodec.BufferInfo()
            val frameDurationNs = 1_000_000_000L / fps
            val deltaMs = 1000f / fps
            val monoWindow = FloatArray(8192)
            val leftWindow=FloatArray(8192)
            val rightWindow=FloatArray(8192)
            val exportStartMs = System.currentTimeMillis()

            var audioSampleCursor = 0L
            val frameBytes = 2 * channels
            val totalPcmBytes = pcmDecoded
            val totalMonoSamples = (totalPcmBytes / frameBytes).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val pcmWindowBytes = ByteArray(monoWindow.size * frameBytes)
            val pcmWindowView = ByteBuffer.wrap(pcmWindowBytes).order(ByteOrder.LITTLE_ENDIAN)
            val audioScratch = ByteArray(65536)
            val reader = checkNotNull(pcmReader)
            var audioEosSent = false
            var audioDone = false
            var videoDone = false

            data class Pending(val track: Int, val bytes: ByteArray, val pts: Long, val flags: Int)
            val pending = ArrayList<Pending>()
            var pendingBytes = 0L
            fun writeEncoded(track: Int, buf: ByteBuffer, info: MediaCodec.BufferInfo) {
                if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0 || info.size <= 0) return
                check(track >= 0) { "Encoder emitted data before track format" }
                buf.position(info.offset); buf.limit(info.offset+info.size)
                if (muxerStarted) activeMuxer.writeSampleData(track, buf, info)
                else {
                    pendingBytes += info.size
                    require(pendingBytes <= 32L*1024*1024) { "Encoder track format did not arrive" }
                    val bytes = ByteArray(info.size); buf.get(bytes)
                    pending.add(Pending(track, bytes, info.presentationTimeUs, info.flags))
                }
            }
            fun tryStartMuxer() {
                if (!muxerStarted && videoTrackMuxer >= 0 && audioTrackMuxer >= 0) {
                    activeMuxer.start()
                    muxerStarted = true
                    for (p in pending) {
                        val info=MediaCodec.BufferInfo().apply { set(0,p.bytes.size,p.pts,p.flags) }
                        activeMuxer.writeSampleData(p.track, ByteBuffer.wrap(p.bytes), info)
                    }
                    pending.clear(); pendingBytes=0L
                }
            }

            fun drainVideo(endOfStream: Boolean) {
                if (endOfStream) videoEncoder.signalEndOfInputStream()
                val deadline = android.os.SystemClock.elapsedRealtime()+30_000L
                while (true) {
                    exportContext.ensureActive()
                    val outIdx = videoEncoder.dequeueOutputBuffer(videoBufferInfo, if(endOfStream) 10_000 else 0)
                    when {
                        outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!endOfStream) break
                            check(android.os.SystemClock.elapsedRealtime()<deadline) { "Video encoder stalled" }
                        }
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            videoTrackMuxer = activeMuxer.addTrack(videoEncoder.outputFormat)
                            tryStartMuxer()
                        }
                        outIdx >= 0 -> {
                            val buf = videoEncoder.getOutputBuffer(outIdx)
                            if (buf != null) writeEncoded(videoTrackMuxer,buf,videoBufferInfo)
                            val eos = (videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                            videoEncoder.releaseOutputBuffer(outIdx, false)
                            if (eos) {
                                videoDone = true
                                break
                            }
                        }
                    }
                }
            }

            fun pumpAudio() {
                if (audioDone) return
                while (!audioEosSent) {
                    val inIdx = audioEncoder.dequeueInputBuffer(0)
                    if (inIdx < 0) break
                    val inBuf = audioEncoder.getInputBuffer(inIdx) ?: break
                    inBuf.clear()
                    val remainingBytes = totalPcmBytes - audioSampleCursor
                    if (remainingBytes <= 0) {
                        val ptsUs = (totalPcmBytes / (2.0 * channels * sampleRate) * 1_000_000.0).toLong()
                        audioEncoder.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        audioEosSent = true
                    } else {
                        val toCopy = (minOf(inBuf.remaining().toLong(), remainingBytes, audioScratch.size.toLong()).toInt() / frameBytes) * frameBytes
                        check(toCopy>0) { "AAC input buffer is too small" }
                        val ptsUs = (audioSampleCursor / (2.0 * channels * sampleRate) * 1_000_000.0).toLong()
                        reader.seek(audioSampleCursor)
                        reader.readFully(audioScratch,0,toCopy)
                        inBuf.put(audioScratch,0,toCopy)
                        audioSampleCursor += toCopy
                        audioEncoder.queueInputBuffer(inIdx, 0, toCopy, ptsUs, 0)
                    }
                }
                while (true) {
                    val outIdx = audioEncoder.dequeueOutputBuffer(audioBufferInfo, 0)
                    when {
                        outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            audioTrackMuxer = activeMuxer.addTrack(audioEncoder.outputFormat)
                            tryStartMuxer()
                        }
                        outIdx >= 0 -> {
                            val buf = audioEncoder.getOutputBuffer(outIdx)
                            if (buf != null) writeEncoded(audioTrackMuxer,buf,audioBufferInfo)
                            val eos = (audioBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                            audioEncoder.releaseOutputBuffer(outIdx, false)
                            if (eos) {
                                audioDone = true
                                break
                            }
                        }
                    }
                }
            }

            // Render deterministic video frames & interleave audio muxing
            for (frameIdx in 0 until totalFrames) {
                yield() // Respect coroutine cancellation immediately

                val timeSec = frameIdx.toFloat() / fps
                val sampleEnd = ((timeSec * sampleRate).toInt()).coerceIn(0, totalMonoSamples)
                val winStart = (sampleEnd - monoWindow.size).coerceAtLeast(0)
                val copyLen = sampleEnd - winStart
                monoWindow.fill(0f);leftWindow.fill(0f);rightWindow.fill(0f)
                if (copyLen > 0) {
                    reader.seek(winStart.toLong()*frameBytes)
                    reader.readFully(pcmWindowBytes,0,copyLen*frameBytes)
                    pcmWindowView.position(0)
                    for(i in 0 until copyLen) {
                        val l=pcmWindowView.short/32768f
                        val r=if(channels==2)pcmWindowView.short/32768f else l
                        val j=monoWindow.size-copyLen+i
                        monoWindow[j]=(l+r)*.5f;leftWindow[j]=l;rightWindow[j]=r
                    }
                }

                val audioSnapshots = mutableMapOf<Int, AudioProviderSnapshot>()
                for ((idx, eng) in engines) {
                    val cfg = project.audioProviders.first { it.index == idx }
                    val slice=when(cfg.channelMode){
                        com.vexo.studio.model.ChannelMode.LEFT->leftWindow
                        com.vexo.studio.model.ChannelMode.RIGHT->rightWindow
                        com.vexo.studio.model.ChannelMode.MIX->monoWindow
                    }
                    audioSnapshots[idx] = eng.process(
                        samples = slice,
                        sampleRate = sampleRate,
                        deltaMs = deltaMs,
                        streamGeneration = 1,
                        isPlaying = true,
                        freezeOnPause = false,
                        sampleEndFrame=sampleEnd.toLong()
                    )
                }

                val scene = RenderSceneSnapshot(
                    project = project,
                    audioSnapshots = audioSnapshots,
                    trackMeta = trackMeta.copy(positionMs = (startUs / 1000L) + (timeSec * 1000L).toLong()),
                    isPlaying = true,
                    totalTimeSec = timeSec,
                    playingTimeSec = timeSec
                )

                renderer.renderScene(scene, 1f / fps, width, height)
                EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, frameIdx * frameDurationNs)
                check(EGL14.eglSwapBuffers(eglDisplay, eglSurface)) { "Encoder surface swap failed" }

                pumpAudio()
                drainVideo(endOfStream = false)

                if (frameIdx % 4 == 0 || frameIdx == totalFrames - 1) {
                    val p = ((frameIdx + 1).toFloat() / totalFrames).coerceIn(0f, 0.96f)
                    val elapsedSec = ((System.currentTimeMillis() - exportStartMs) / 1000.0).coerceAtLeast(0.5)
                    val eta = if (p > 0.02f) ((elapsedSec / p) * (1f - p)).toLong() else 0L
                    onProgress(
                        ExportProgress(
                            isRunning = true,
                            progress = p,
                            etaSeconds = eta,
                            statusText = "Rendering frame ${frameIdx + 1} / $totalFrames"
                        )
                    )
                }
            }

            // Finish encoders
            drainVideo(endOfStream = true)
            val drainDeadline=android.os.SystemClock.elapsedRealtime()+60_000L
            while (!audioDone || !videoDone) {
                yield()
                check(android.os.SystemClock.elapsedRealtime()<drainDeadline) { "Encoder drain timed out" }
                if (!audioDone) pumpAudio()
                if (!videoDone) drainVideo(endOfStream = false)
            }

            renderer.releaseGlResources(); glRenderer=null
            check(muxerStarted) { "Encoder did not produce both MP4 tracks" }
            if (muxerStarted) {
                muxer.stop()
                muxerStarted = false
            }
            muxer.release(); muxer = null

            onProgress(ExportProgress(isRunning = true, progress = 0.98f, statusText = "Verifying & publishing MP4"))

            verifyMp4File(tempFile,width,height,totalDurationUs,videoMime)
            currentCoroutineContext().ensureActive()
            val publishedUri = publishVideo(tempFile, project.name, settings.customOutputUri)
            tempFile.delete()

            onProgress(
                ExportProgress(
                    isRunning = false,
                    progress = 1f,
                    statusText = "Export complete",
                    publishedUri = publishedUri
                )
            )
            return publishedUri
        } catch (ce: CancellationException) {
            tempFile.delete()
            onProgress(ExportProgress(isRunning = false, progress = 0f, statusText = "Export cancelled"))
            throw ce
        } catch (e: Exception) {
            tempFile.delete()
            onProgress(
                ExportProgress(
                    isRunning = false,
                    progress = 0f,
                    statusText = "Export failed",
                    errorMessage = e.message ?: "Unknown export error"
                )
            )
            throw e
        } finally {
            runCatching { glRenderer?.releaseGlResources() }
            runCatching { inputExtractor?.release() }
            runCatching { pcmReader?.close() }
            pcmFile.delete()
            runCatching { audioDecoder?.stop(); audioDecoder?.release() }
            runCatching { videoEncoder?.stop(); videoEncoder?.release() }
            runCatching { audioEncoder?.stop(); audioEncoder?.release() }
            runCatching {
                if (muxerStarted) muxer?.stop()
                muxer?.release()
            }
            runCatching { encoderSurface?.release() }
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglTerminate(eglDisplay)
            }
        }
    }

    private suspend fun decodeTrimmedPcm(
        extractor: MediaExtractor, decoder: MediaCodec, startUs: Long, endUs: Long,
        channels: Int, sampleRate: Int, target: File
    ): Long {
        val info=MediaCodec.BufferInfo()
        val frameBytes=channels*2
        var inputDone=false
        var outputDone=false
        var bytesWritten=0L
        var lastProgress=android.os.SystemClock.elapsedRealtime()
        BufferedOutputStream(target.outputStream()).use { output ->
            while(!outputDone) {
                currentCoroutineContext().ensureActive()
                check(android.os.SystemClock.elapsedRealtime()-lastProgress<30_000L) { "Audio decoder stalled" }
                if(!inputDone) {
                    val i=decoder.dequeueInputBuffer(5000)
                    if(i>=0) {
                        val buf=checkNotNull(decoder.getInputBuffer(i));buf.clear()
                        val size=extractor.readSampleData(buf,0);val pts=extractor.sampleTime
                        if(size<0||pts>=endUs){decoder.queueInputBuffer(i,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true}
                        else {decoder.queueInputBuffer(i,0,size,pts,0);extractor.advance()}
                        lastProgress=android.os.SystemClock.elapsedRealtime()
                    }
                }
                val i=decoder.dequeueOutputBuffer(info,5000)
                if(i==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val fmt=decoder.outputFormat
                    require(fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)==sampleRate && fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)==channels) {"Decoder changed PCM format"}
                    if(fmt.containsKey(MediaFormat.KEY_PCM_ENCODING)) require(fmt.getInteger(MediaFormat.KEY_PCM_ENCODING)==android.media.AudioFormat.ENCODING_PCM_16BIT) {"Decoder output is not PCM16"}
                } else if(i>=0) {
                    try {
                        val buf=decoder.getOutputBuffer(i)
                        if(buf!=null&&info.size>=frameBytes) {
                            val frames=info.size/frameBytes
                            val startFrame=kotlin.math.ceil((startUs-info.presentationTimeUs)*sampleRate/1_000_000.0).toInt().coerceIn(0,frames)
                            val endFrame=kotlin.math.ceil((endUs-info.presentationTimeUs)*sampleRate/1_000_000.0).toInt().coerceIn(startFrame,frames)
                            val size=(endFrame-startFrame)*frameBytes
                            if(size>0) {
                                val chunk=ByteArray(size)
                                buf.limit(info.offset+endFrame*frameBytes);buf.position(info.offset+startFrame*frameBytes)
                                buf.get(chunk);output.write(chunk);bytesWritten+=size
                            }
                        }
                        outputDone=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM !=0
                    } finally { decoder.releaseOutputBuffer(i,false) }
                    lastProgress=android.os.SystemClock.elapsedRealtime()
                }
            }
        }
        require(bytesWritten>0) {"No decoded audio in selected trim range"}
        return bytesWritten
    }

    private fun verifyMp4File(file: File,width:Int,height:Int,expectedDurationUs:Long,videoMime:String) {
        if (!file.exists() || file.length() <= 0L) {
            error("Exported MP4 file is empty")
        }
        val extractor=MediaExtractor()
        try{
            extractor.setDataSource(file.absolutePath)
            val formats=(0 until extractor.trackCount).map{extractor.getTrackFormat(it)}
            val video=formats.firstOrNull{it.getString(MediaFormat.KEY_MIME)==videoMime} ?: error("MP4 has no selected video codec track")
            check(formats.any{it.getString(MediaFormat.KEY_MIME)=="audio/mp4a-latm"}){"MP4 has no AAC track"}
            check(video.getInteger(MediaFormat.KEY_WIDTH)==width && video.getInteger(MediaFormat.KEY_HEIGHT)==height){"Export dimensions differ from selection"}
        }finally{extractor.release()}
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (hasVideo != "yes" || dur <= 0L || kotlin.math.abs(dur*1000L-expectedDurationUs)>maxOf(500_000L,expectedDurationUs/20)) {
                error("Exported file failed media verification")
            }
        } finally {
            runCatching { retriever.release() }
        }
    }

    private suspend fun publishVideo(tempFile: File, projectName: String, customSafUri: String?): String {
        val safeName = projectName.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "Vexo_Export" }
        val fileName = "${safeName}_${System.currentTimeMillis()}.mp4"

        if (!customSafUri.isNullOrBlank()) {
            val uri = Uri.parse(customSafUri)
            // A user-selected document may already exist. Do not delete it on failure or interrupt its final copy.
            currentCoroutineContext().ensureActive()
            context.contentResolver.openOutputStream(uri,"wt")?.use { out ->
                tempFile.inputStream().use { it.copyTo(out) }
            } ?: error("Cannot write to selected SAF destination")
            return uri.toString()
        }

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Vexo")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore insert failed")

        try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                tempFile.inputStream().use { input ->
                    val buffer=ByteArray(256*1024)
                    while(true){currentCoroutineContext().ensureActive();val n=input.read(buffer);if(n<0)break;out.write(buffer,0,n)}
                }
            } ?: error("Failed writing video stream to MediaStore")
            currentCoroutineContext().ensureActive()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                check(context.contentResolver.update(uri, done, null, null)>0){"Failed to finalize MediaStore video"}
            }
            return uri.toString()
        } catch (e: Exception) {
            runCatching { context.contentResolver.delete(uri,null,null) }
            throw e
        }
    }

    private fun computeDimensions(wRatio: Int, hRatio: Int, shortSide: Int): Pair<Int, Int> {
        val rawW: Int
        val rawH: Int
        if (wRatio <= hRatio) {
            rawW = shortSide
            rawH = (shortSide.toFloat() * hRatio / wRatio).roundToInt()
        } else {
            rawH = shortSide
            rawW = (shortSide.toFloat() * wRatio / hRatio).roundToInt()
        }
        // Preserve requested resolution; H.264 requires even dimensions. Device capability is checked separately.
        val alignedW = (rawW / 2) * 2
        val alignedH = (rawH / 2) * 2
        return alignedW.coerceAtLeast(320) to alignedH.coerceAtLeast(320)
    }
}
