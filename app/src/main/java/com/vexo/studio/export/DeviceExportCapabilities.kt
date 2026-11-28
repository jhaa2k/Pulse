package com.vexo.studio.export

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import com.vexo.studio.model.ExportSettings
import com.vexo.studio.model.VideoResolution

/** Single capability policy for the UI and actual export. Reported support is not a runtime guarantee. */
object DeviceExportCapabilities {
    data class Candidate(val mime:String,val format:MediaFormat,val name:String,val bps:Int,val hardware:Boolean)
    private fun codecs():List<MediaCodecInfo> = runCatching{
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter{it.isEncoder}
    }.getOrDefault(emptyList())

    fun candidates(s:ExportSettings):List<Candidate> = candidates(s,codecs())

    private fun candidates(s:ExportSettings,infos:List<MediaCodecInfo>):List<Candidate>{
        val (w,h)=ExportEncoderPolicy.dimensions(s.aspectRatio.widthRatio,s.aspectRatio.heightRatio,s.resolution.shortSide)
        val fps=s.fps.coerceIn(24,60)
        val target=ExportEncoderPolicy.targetBitrate(s)
        val mimes=if(s.useHevcIfSupported)listOf(MediaFormat.MIMETYPE_VIDEO_HEVC,MediaFormat.MIMETYPE_VIDEO_AVC)
            else listOf(MediaFormat.MIMETYPE_VIDEO_AVC,MediaFormat.MIMETYPE_VIDEO_HEVC)
        return mimes.flatMap{mime->
            infos.filter{info->info.supportedTypes.any{it.equals(mime,true)}}.mapNotNull{info->
                runCatching{
                    val caps=info.getCapabilitiesForType(mime)
                    val video=caps.videoCapabilities?:return@runCatching null
                    if(MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface !in caps.colorFormats)return@runCatching null
                    if(!video.areSizeAndRateSupported(w,h,fps.toDouble()))return@runCatching null
                    val range=video.bitrateRange
                    val bitrate=ExportEncoderPolicy.fitBitrate(target,range.lower,range.upper,s.adaptiveVideoBitrate)
                        ?:return@runCatching null
                    val format=MediaFormat.createVideoFormat(mime,w,h).apply{
                        setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                        setInteger(MediaFormat.KEY_BIT_RATE,bitrate)
                        setInteger(MediaFormat.KEY_FRAME_RATE,fps)
                        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1)
                    }
                    if(!caps.isFormatSupported(format))return@runCatching null
                    val hardware=if(Build.VERSION.SDK_INT>=29)info.isHardwareAccelerated else
                        !info.name.startsWith("OMX.google.",true)&&!info.name.startsWith("c2.android.",true)
                    Candidate(mime,format,info.name,bitrate,hardware)
                }.getOrNull()
            }.sortedWith(compareByDescending<Candidate>{it.hardware}.thenBy{kotlin.math.abs(it.bps.toLong()-target)})
        }.sortedByDescending{it.hardware}
    }

    data class Choice(val fps:Int,val codec:String)
    fun matrix(s:ExportSettings):Map<VideoResolution,List<Choice>>{
        val infos=codecs()
        return VideoResolution.entries.associateWith{res->
            ExportEncoderPolicy.frameRates.mapNotNull{fps->
                val c=candidates(s.copy(resolution=res,fps=fps),infos).firstOrNull()?:return@mapNotNull null
                Choice(fps,if(c.mime==MediaFormat.MIMETYPE_VIDEO_HEVC)"HEVC" else "H.264")
            }
        }
    }
}
