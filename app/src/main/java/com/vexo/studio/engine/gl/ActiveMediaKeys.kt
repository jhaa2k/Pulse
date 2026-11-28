package com.vexo.studio.engine.gl

import com.vexo.studio.model.ImageSourceType
import com.vexo.studio.model.LayerConfig
import com.vexo.studio.model.LayerNode

/** Exact playback identity; URI alone is insufficient for different speed/offset layers. */
object ActiveMediaKeys {
    data class Usage(val videoKeys:Set<String>,val videoUris:Set<String>,val imageUris:Set<String>)
    fun videoKey(c:LayerConfig.Image):String?{
        val uri=c.customUri?.takeIf{it.isNotBlank()}?:return null
        return uri+"|"+c.videoTextureLimit.coerceIn(256,1920)+"|"+c.videoLoop+"|"+c.videoSpeed+"|"+c.videoOffsetMs+"|"+c.videoSampleFps
    }
    fun from(layers:List<LayerNode>):Usage{
        val keys=linkedSetOf<String>();val videos=linkedSetOf<String>();val images=linkedSetOf<String>()
        for(l in layers){
            if(!l.common.visible)continue
            val c=l.config as? LayerConfig.Image?:continue
            if(c.sourceType!=ImageSourceType.IMPORTED)continue
            val uri=c.customUri?.takeIf{it.isNotBlank()}?:continue
            if(c.videoEnabled){videoKey(c)?.let{keys.add(it)};videos.add(uri)}else images.add(uri)
        }
        return Usage(keys,videos,images)
    }
}
