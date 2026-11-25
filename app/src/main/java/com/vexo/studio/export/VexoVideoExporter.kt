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
        val bitrate = settings.videoBitrateMbps.coerceIn(2, 40) * 1_000_000

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

        try {
            onProgress(ExportProgress(isRunning = true, progress = 0.01f, statusText = "Preparing audio & encoder"))

            val extractor = MediaExtractor()
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
            val totalDurationUs = (endUs - startUs).coerceAtLeast(1_000_000L)
            val totalFrames = ((totalDurationUs / 1_000_000.0) * fps).roundToInt().coerceAtLeast(1)

            // Decode entire trimmed PCM window into short chunks or stream sequentially
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
                channels = channels
            )
            extractor.release()
            audioDecoder.stop(); audioDecoder.release(); audioDecoder = null

            // Configure H.264 Video Encoder + EGL Surface
            val videoMime = MediaFormat.MIMETYPE_VIDEO_AVC
            val videoFormat = MediaFormat.createVideoFormat(videoMime, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            videoEncoder = MediaCodec.createEncoderByType(videoMime).apply {
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
            EGL14.eglInitialize(eglDisplay, vers, 0, vers, 1)
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
            EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)
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
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

            val renderer = VexoGlRenderer(context, isExportRenderer = true).apply { initGlResources() }
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
            val exportStartMs = System.currentTimeMillis()

            var audioSampleCursor = 0
            val totalMonoSamples = pcmDecoded.monoSamples.size
            var audioEosSent = false
            var audioDone = false
            var videoDone = false

            fun tryStartMuxer() {
                if (!muxerStarted && videoTrackMuxer >= 0 && audioTrackMuxer >= 0) {
                    activeMuxer.start()
                    muxerStarted = true
                }
            }

            fun drainVideo(endOfStream: Boolean) {
                if (endOfStream) videoEncoder.signalEndOfInputStream()
                while (true) {
                    val outIdx = videoEncoder.dequeueOutputBuffer(videoBufferInfo, 0)
                    when {
                        outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!endOfStream) break
                        }
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            videoTrackMuxer = activeMuxer.addTrack(videoEncoder.outputFormat)
                            tryStartMuxer()
                        }
                        outIdx >= 0 -> {
                            val buf = videoEncoder.getOutputBuffer(outIdx)
                            if (buf != null && (videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && videoBufferInfo.size > 0 && muxerStarted) {
                                buf.position(videoBufferInfo.offset)
                                buf.limit(videoBufferInfo.offset + videoBufferInfo.size)
                                activeMuxer.writeSampleData(videoTrackMuxer, buf, videoBufferInfo)
                            }
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
                    val remainingBytes = pcmDecoded.pcm16Bytes.size - audioSampleCursor
                    if (remainingBytes <= 0) {
                        val ptsUs = (pcmDecoded.pcm16Bytes.size / (2.0 * channels * sampleRate) * 1_000_000.0).toLong()
                        audioEncoder.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        audioEosSent = true
                    } else {
                        val toCopy = minOf(inBuf.remaining(), remainingBytes)
                        val ptsUs = (audioSampleCursor / (2.0 * channels * sampleRate) * 1_000_000.0).toLong()
                        inBuf.put(pcmDecoded.pcm16Bytes, audioSampleCursor, toCopy)
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
                            if (buf != null && (audioBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && audioBufferInfo.size > 0 && muxerStarted) {
                                buf.position(audioBufferInfo.offset)
                                buf.limit(audioBufferInfo.offset + audioBufferInfo.size)
                                activeMuxer.writeSampleData(audioTrackMuxer, buf, audioBufferInfo)
                            }
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
                monoWindow.fill(0f)
                if (copyLen > 0) {
                    System.arraycopy(pcmDecoded.monoSamples, winStart, monoWindow, monoWindow.size - copyLen, copyLen)
                }

                val audioSnapshots = mutableMapOf<Int, AudioProviderSnapshot>()
                for ((idx, eng) in engines) {
                    val cfg = project.audioProviders.first { it.index == idx }
                    val fftSize = AudioProviderEngine.sanitizeFftSize(cfg.fftSize)
                    val slice = monoWindow.copyOfRange(monoWindow.size - fftSize, monoWindow.size)
                    audioSnapshots[idx] = eng.process(
                        samples = slice,
                        sampleRate = sampleRate,
                        deltaMs = deltaMs,
                        streamGeneration = 1,
                        isPlaying = true,
                        freezeOnPause = false
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
                EGL14.eglSwapBuffers(eglDisplay, eglSurface)

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
            while (!audioDone || !videoDone) {
                yield()
                if (!audioDone) pumpAudio()
                if (!videoDone) drainVideo(endOfStream = false)
            }

            renderer.releaseGlResources()
            if (muxerStarted) {
                muxer.stop()
                muxerStarted = false
            }
            muxer.release(); muxer = null

            onProgress(ExportProgress(isRunning = true, progress = 0.98f, statusText = "Verifying & publishing MP4"))

            verifyMp4File(tempFile)
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

    private data class DecodedPcm(val monoSamples: FloatArray, val pcm16Bytes: ByteArray)

    private fun decodeTrimmedPcm(
        extractor: MediaExtractor,
        decoder: MediaCodec,
        startUs: Long,
        endUs: Long,
        channels: Int
    ): DecodedPcm {
        val info = MediaCodec.BufferInfo()
        val byteStream = java.io.ByteArrayOutputStream()
        val monoList = ArrayList<Float>(44100 * 10)
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val inIdx = decoder.dequeueInputBuffer(5000)
                if (inIdx >= 0) {
                    val buf = decoder.getInputBuffer(inIdx)!!
                    val size = extractor.readSampleData(buf, 0)
                    val pts = extractor.sampleTime
                    if (size < 0 || pts > endUs) {
                        decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(inIdx, 0, size, pts, 0)
                        extractor.advance()
                    }
                }
            }
            val outIdx = decoder.dequeueOutputBuffer(info, 5000)
            if (outIdx >= 0) {
                val outBuf = decoder.getOutputBuffer(outIdx)
                if (outBuf != null && info.size > 0 && info.presentationTimeUs >= startUs) {
                    val chunk = ByteArray(info.size)
                    outBuf.position(info.offset)
                    outBuf.get(chunk)
                    byteStream.write(chunk)

                    val bb = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN)
                    val frames = chunk.size / (2 * channels.coerceAtLeast(1))
                    for (f in 0 until frames) {
                        var sum = 0f
                        for (c in 0 until channels) {
                            sum += bb.short / 32768f
                        }
                        monoList.add((sum / channels).coerceIn(-1f, 1f))
                    }
                }
                val eos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                decoder.releaseOutputBuffer(outIdx, false)
                if (eos) outputDone = true
            }
        }
        return DecodedPcm(monoList.toFloatArray(), byteStream.toByteArray())
    }

    private fun verifyMp4File(file: File) {
        if (!file.exists() || file.length() <= 0L) {
            error("Exported MP4 file is empty")
        }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (hasVideo != "yes" || dur <= 0L) {
                error("Exported file failed media verification")
            }
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun publishVideo(tempFile: File, projectName: String, customSafUri: String?): String {
        val safeName = projectName.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "Vexo_Export" }
        val fileName = "${safeName}_${System.currentTimeMillis()}.mp4"

        if (!customSafUri.isNullOrBlank()) {
            val uri = Uri.parse(customSafUri)
            context.contentResolver.openOutputStream(uri)?.use { out ->
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

        context.contentResolver.openOutputStream(uri)?.use { out ->
            tempFile.inputStream().use { it.copyTo(out) }
        } ?: error("Failed writing video stream to MediaStore")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            context.contentResolver.update(uri, done, null, null)
        }
        return uri.toString()
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
        // Align to 16px macroblock boundary for broad hardware H.264 compatibility
        val alignedW = (rawW / 16) * 16
        val alignedH = (rawH / 16) * 16
        return alignedW.coerceAtLeast(320) to alignedH.coerceAtLeast(320)
    }
}
