package com.vexo.studio.data

import com.vexo.studio.model.VexoProject
import com.vexo.studio.model.LayerConfig
import com.vexo.studio.model.LayerType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

/** Native, versioned, typed scene serialization. No Avee conversion is involved. */
object NativeVexCodec {
    const val FORMAT = "VEXO_PROJECT"
    const val VERSION = 1
    const val MAX_JSON_BYTES = 16 * 1024 * 1024
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true; classDiscriminator = "layerKind" }

    @Serializable
    private data class Envelope(val format: String, val version: Int, val project: VexoProject)

    fun isNative(text: String): Boolean = runCatching {
        ((json.parseToJsonElement(text) as? JsonObject)?.get("format") as? JsonPrimitive)?.content == FORMAT
    }.getOrDefault(false)

    fun encode(project: VexoProject): String = json.encodeToString(Envelope.serializer(), Envelope(FORMAT, VERSION, project))

    fun decode(text: String): VexoProject {
        require(text.length <= MAX_JSON_BYTES) { "Project JSON is too large" }
        val envelope = json.decodeFromString(Envelope.serializer(), text)
        require(envelope.format == FORMAT) { "Not a VEX project" }
        require(envelope.version == VERSION) { "Unsupported VEX format version ${envelope.version}" }
        return validateStructure(envelope.project)
    }

    /** Apply the same persistence limits to imported legacy scenes before autosave. */
    fun validateStructure(p:VexoProject):VexoProject {
        require(runCatching { UUID.fromString(p.id).toString() == p.id }.getOrDefault(false)) { "Invalid project ID" }
        require(p.compositions.size in 1..64 && p.compositions.any { it.index == 0 }) { "Missing final composition or too many compositions" }
        require(p.compositions.map { it.index }.distinct().size == p.compositions.size) { "Duplicate composition index" }
        require(p.audioProviders.size in 1..16 && p.audioProviders.map { it.index }.distinct().size == p.audioProviders.size) { "Invalid audio providers" }
        val layers = p.compositions.flatMap { it.layers }
        require(layers.size <= 2048 && layers.map { it.common.id }.distinct().size == layers.size) { "Invalid layer list" }
        require(layers.all { layer ->
            layer.type == when(layer.config) {
                is LayerConfig.Bars -> LayerType.BARS
                is LayerConfig.Image -> LayerType.IMAGE
                is LayerConfig.Text -> LayerType.TEXT
                is LayerConfig.Shape -> LayerType.SHAPE
                is LayerConfig.Particles -> LayerType.PARTICLES
                is LayerConfig.BlurEffect -> LayerType.BLUR_EFFECT
                is LayerConfig.MotionBlurEffect -> LayerType.MOTION_BLUR_EFFECT
                is LayerConfig.MirrorEffect -> LayerType.MIRROR_EFFECT
                is LayerConfig.RgbSplitEffect -> LayerType.RGB_SPLIT_EFFECT
            }
        }) { "Layer type mismatch" }
        return p
    }
}
