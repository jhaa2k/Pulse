package com.vexo.studio.data
import kotlinx.serialization.json.*

/** Resolve only actual archive members. Never treat an archive entry name as a filesystem path. */
object LegacyAssetReferences {
    private val image=setOf("png","jpg","jpeg","webp","bmp","gif")
    private val font=setOf("ttf","otf")
    private val video=setOf("mp4","webm","m4v","3gp")
    fun isMedia(name:String)=name.substringAfterLast('.',"").lowercase() in (image+font+video)
    fun isNumericAsset(name:String)=name.substringAfterLast('/').matches(Regex("[0-9]{1,24}"))
    fun imageExtension(bytes:ByteArray):String? {
        fun b(i:Int)=bytes.getOrNull(i)?.toInt()?.and(255)?:-1
        return when {
            b(0)==137&&b(1)==80&&b(2)==78&&b(3)==71&&b(4)==13&&b(5)==10&&b(6)==26&&b(7)==10 -> "png"
            b(0)==255&&b(1)==216&&b(2)==255 -> "jpg"
            bytes.size>=6&&String(bytes,0,6,Charsets.US_ASCII) in setOf("GIF87a","GIF89a") -> "gif"
            bytes.size>=12&&String(bytes,0,4,Charsets.US_ASCII)=="RIFF"&&String(bytes,8,4,Charsets.US_ASCII)=="WEBP" -> "webp"
            else -> null
        }
    }
    fun isVideo(name:String)=name.substringAfterLast('.',"").lowercase() in video
    fun rawValue(raw:String?):String? = runCatching {
        val el=Json.parseToJsonElement(raw ?: return null)
        when(el){is JsonObject -> (el["v"] as? JsonPrimitive)?.contentOrNull;is JsonPrimitive -> el.contentOrNull;else -> null}
    }.getOrNull()
    fun match(ref:String?,names:Set<String>):String?{
        val name=ref?.trim()?.removePrefix("local:")?.removePrefix("asset:")?.removePrefix("./") ?: return null
        if(name.isBlank()||name.startsWith("internalres:")||name.startsWith("composition:")||name.contains("://")||name.startsWith("/")||name.split('/').any{it==".."})return null
        if(name in names)return name
        val base=name.substringAfterLast('/')
        return names.filter{it.substringAfterLast('/').equals(base,true)}.singleOrNull()
    }
}
