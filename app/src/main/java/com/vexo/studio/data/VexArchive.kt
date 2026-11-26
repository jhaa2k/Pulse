package com.vexo.studio.data

import android.content.Context
import android.net.Uri
import com.vexo.studio.model.LayerConfig
import com.vexo.studio.model.VexoProject
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Self-contained VEX archives: project.json plus referenced local media. */
object VexArchive {
    private const val MAX_BYTES = 256L * 1024 * 1024
    private const val MAX_ENTRIES = 256
    private const val PREFIX = "vexasset:"
    private fun isLocal(s: String) = s.startsWith("content://") || s.startsWith("file://")

    private fun remap(project: VexoProject, map: (String) -> String): VexoProject = project.copy(
        audioUri = project.audioUri?.let(map),
        exportSettings = project.exportSettings.copy(customOutputUri = null),
        compositions = project.compositions.map { comp -> comp.copy(layers = comp.layers.map { layer ->
            layer.copy(config = when(val cfg = layer.config) {
                is LayerConfig.Image -> cfg.copy(customUri = cfg.customUri?.let(map), maskImage = map(cfg.maskImage))
                is LayerConfig.Text -> cfg.copy(fontAssetUri = cfg.fontAssetUri?.let(map))
                is LayerConfig.Particles -> cfg.copy(customImage = map(cfg.customImage))
                is LayerConfig.BlurEffect -> cfg.copy(maskImage = map(cfg.maskImage))
                else -> cfg
            })
        }) }
    )

    private fun copyLimited(input: InputStream, output: OutputStream, limit: Long): Long {
        val buf = ByteArray(32768)
        var total = 0L
        while(true) {
            val n = input.read(buf)
            if(n < 0) break
            if(n == 0) continue
            total += n
            require(total <= limit) { "VEX archive exceeds size limit" }
            output.write(buf, 0, n)
        }
        return total
    }

    fun write(context: Context, project: VexoProject, output: OutputStream) {
        val refs = linkedMapOf<String, String>()
        val packed = remap(project) { value ->
            if (!isLocal(value)) value else refs.getOrPut(value) { PREFIX + "assets/media_${refs.size}" }
        }
        require(refs.size < MAX_ENTRIES) { "Too many media assets" }
        val payload = NativeVexCodec.encode(packed).toByteArray(Charsets.UTF_8)
        require(payload.size <= NativeVexCodec.MAX_JSON_BYTES) { "Project JSON is too large" }
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("project.json")); zip.write(payload); zip.closeEntry()
            var total = payload.size.toLong()
            refs.forEach { (uri, ref) ->
                val source = context.contentResolver.openInputStream(Uri.parse(uri)) ?: error("Cannot read a linked media file")
                source.use {
                    zip.putNextEntry(ZipEntry(ref.removePrefix(PREFIX)))
                    total += copyLimited(it, zip, MAX_BYTES - total)
                    zip.closeEntry()
                }
            }
        }
    }

    fun read(context: Context, input: InputStream, fallbackName: String): VexoProject {
        val buffered = BufferedInputStream(input)
        buffered.mark(4)
        val a = buffered.read(); val b = buffered.read(); buffered.reset()
        if (a != 0x50 || b != 0x4b) {
            val bytes = ByteArrayOutputStream()
            copyLimited(buffered, bytes, NativeVexCodec.MAX_JSON_BYTES.toLong())
            val text = bytes.toString("UTF-8").removePrefix("\uFEFF")
            return if (NativeVexCodec.isNative(text)) NativeVexCodec.decode(text) else AveeJsonCodec.importFromText(text, fallbackName)
        }
        val dir = File(context.filesDir, "vex_media/${UUID.randomUUID()}").apply { mkdirs() }
        val assets = linkedMapOf<String, File>()
        var projectText: String? = null
        var legacyText: String? = null
        val legacyAssets=linkedMapOf<String,File>()
        var total = 0L
        try {
            ZipInputStream(buffered).use { zip ->
                var count = 0
                while(true) {
                    val entry = zip.nextEntry ?: break
                    require(++count <= MAX_ENTRIES) { "Too many VEX entries" }
                    val name = entry.name
                    require(!name.startsWith("/") && !name.contains('\\') && name.split('/').none { it == ".." }) { "Unsafe archive path" }
                    if(!entry.isDirectory) {
                        when {
                            name == "project.json" || name.substringAfterLast('/') == "scene.json" -> {
                                val bytes = ByteArrayOutputStream()
                                total += copyLimited(zip, bytes, minOf(MAX_BYTES-total, NativeVexCodec.MAX_JSON_BYTES.toLong()))
                                if(name == "project.json") { require(projectText == null); projectText = bytes.toString("UTF-8") }
                                else { require(legacyText==null) { "Duplicate scene.json" };legacyText = bytes.toString("UTF-8").removePrefix("\uFEFF") }
                            }
                            name.matches(Regex("assets/media_[0-9]+")) -> {
                                require(name !in assets) { "Duplicate VEX asset" }
                                val file = File(dir, name.substringAfterLast('/'))
                                file.outputStream().use { total += copyLimited(zip, it, MAX_BYTES-total) }
                                assets[name] = file
                            }
                            LegacyAssetReferences.isMedia(name) -> {
                                require(name !in legacyAssets) { "Duplicate preset asset" }
                                val ext=name.substringAfterLast('.').lowercase()
                                val file=File(dir,"legacy_${legacyAssets.size}.$ext")
                                file.outputStream().use{total+=copyLimited(zip,it,minOf(MAX_BYTES-total,64L*1024*1024))}
                                legacyAssets[name]=file
                            }
                            else -> total += copyLimited(zip, object : OutputStream() { override fun write(b: Int) {} ; override fun write(b: ByteArray, off: Int, len: Int) {} }, MAX_BYTES-total)
                        }
                    }
                    zip.closeEntry()
                }
            }
            val native = projectText
            if(native == null) {
                val raw=AveeJsonCodec.importFromText(legacyText ?: error("No scene.json or project.json in archive"), fallbackName)
                val used=mutableSetOf<File>()
                fun linked(value:String?):String? {
                    val name=LegacyAssetReferences.match(value,legacyAssets.keys) ?: return null
                    val file=legacyAssets.getValue(name);used.add(file)
                    return Uri.fromFile(file).toString()
                }
                fun safe(value:String):String = linked(value) ?: if(isLocal(value)) "" else value
                val project=raw.copy(compositions=raw.compositions.map{comp->comp.copy(layers=comp.layers.map{layer->
                    val cfg=when(val c=layer.config){
                        is LayerConfig.Image -> {
                            val original=LegacyAssetReferences.rawValue(layer.common.extraFields["customImage"])
                            val resolved=linked(original?:c.customUri)
                            if(resolved!=null)c.copy(sourceType=com.vexo.studio.model.ImageSourceType.IMPORTED,customUri=resolved,maskImage=safe(c.maskImage),videoEnabled=LegacyAssetReferences.isVideo(original.orEmpty()))
                            else if(c.sourceType==com.vexo.studio.model.ImageSourceType.IMPORTED)c.copy(sourceType=com.vexo.studio.model.ImageSourceType.ALBUM_ART,customUri=null,maskImage=safe(c.maskImage))
                            else c.copy(maskImage=safe(c.maskImage))
                        }
                        is LayerConfig.Text -> c.copy(fontAssetUri=linked(c.fontAssetUri))
                        is LayerConfig.Particles -> c.copy(customImage=safe(c.customImage).ifBlank{"internalres:particle_circle_blur4"})
                        is LayerConfig.BlurEffect -> c.copy(maskImage=safe(c.maskImage))
                        else -> c
                    }
                    layer.copy(config=cfg)
                })})
                (assets.values+legacyAssets.values).filter{it !in used}.forEach{it.delete()}
                if(used.isEmpty())dir.deleteRecursively()
                return project
            }
            val project = remap(NativeVexCodec.decode(native)) { ref ->
                if(!ref.startsWith(PREFIX)) {
                    require(!isLocal(ref)) { "Unbundled local media reference" }
                    ref
                } else Uri.fromFile(assets[ref.removePrefix(PREFIX)] ?: error("Missing bundled media")).toString()
            }
            legacyAssets.values.forEach{it.delete()}
            if(assets.isEmpty()) dir.deleteRecursively()
            return project
        } catch(t: Throwable) {
            dir.deleteRecursively()
            throw t
        }
    }
}
