package com.vexo.studio.data

import com.vexo.studio.model.AnchorMode
import com.vexo.studio.model.AudioProviderConfig
import com.vexo.studio.model.BarsSegmentType
import com.vexo.studio.model.BarsShapePath
import com.vexo.studio.model.BlendMode
import com.vexo.studio.model.CompositionNode
import com.vexo.studio.model.ForceFieldType
import com.vexo.studio.model.ImageShape
import com.vexo.studio.model.ImageSourceType
import com.vexo.studio.model.LayerCommon
import com.vexo.studio.model.LayerConfig
import com.vexo.studio.model.LayerNode
import com.vexo.studio.model.LayerType
import com.vexo.studio.model.MaskMode
import com.vexo.studio.model.MeasureConfig
import com.vexo.studio.model.MeasureWhat
import com.vexo.studio.model.MirrorMode
import com.vexo.studio.model.MotionSource
import com.vexo.studio.model.SampleProviderType
import com.vexo.studio.model.SpawnAreaType
import com.vexo.studio.model.VexoProject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.put
import java.io.InputStream
import java.io.OutputStream
import java.io.BufferedInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.math.roundToInt

/**
 * Parses and serializes the Avee Player Windows/VIZ JSON object schema
 * (AudioProvider, Image, Bars, Text, Particles, BlurEffect, RgbSplitEffect,
 * MotionBlurEffect, MirrorEffect) while preserving unknown fields for
 * lossless round-trip import/export and enforcing zip traversal safety.
 */
object AveeJsonCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private const val MAX_VIZ_ENTRY_BYTES = 16 * 1024 * 1024 // 16 MB guard against zip bombs

    fun importFromText(rawText: String, fallbackName: String = "Imported Preset"): VexoProject {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) error("Empty preset file")

        val root = json.parseToJsonElement(trimmed)
        if (root is JsonObject) {
            if (root.containsKey("vexoProject") || root.containsKey("vexoSchema")) return decodeNativeProject(root)
            val comps = root["compositions"] as? JsonArray
            if (comps != null) return decodeAveeRoot(root, comps, fallbackName)
        }
        val objects = mutableListOf<JsonObject>()
        collectAveeObjects(root, objects)

        if (objects.isEmpty()) {
            // Maybe it is our native Vexo project JSON wrapper
            if (root is JsonObject && root.containsKey("vexoProject")) {
                return decodeNativeProject(root)
            }
            error("No valid Avee or Vexo objects found in JSON")
        }

        val providers = mutableListOf<AudioProviderConfig>()
        val layers = mutableListOf<LayerNode>()

        for (obj in objects) {
            val objType = obj["objType"]?.primitiveString() ?: obj["v"]?.primitiveString() ?: continue
            when (objType) {
                "AudioProvider" -> providers.add(parseAudioProvider(obj, providers.size + 1))
                "Image" -> layers.add(parseImageLayer(obj))
                "Bars" -> layers.add(parseBarsLayer(obj))
                "Text" -> layers.add(parseTextLayer(obj))
                "Particles" -> layers.add(parseParticlesLayer(obj))
                "BlurEffect" -> layers.add(parseBlurEffect(obj))
                "RgbSplitEffect" -> layers.add(parseRgbSplitEffect(obj))
                "MotionBlurEffect" -> layers.add(parseMotionBlurEffect(obj))
                "MirrorEffect" -> layers.add(parseMirrorEffect(obj))
            }
        }

        val finalProviders = if (providers.isEmpty()) listOf(AudioProviderConfig()) else providers
        // Separate scene layers vs post-effects cleanly into Composition 1 + Final Composition 0
        val sceneLayers = layers.filter {
            it.type == LayerType.IMAGE || it.type == LayerType.BARS ||
                it.type == LayerType.TEXT || it.type == LayerType.PARTICLES ||
                it.type == LayerType.SHAPE
        }
        val effectLayers = layers.filter {
            it.type == LayerType.BLUR_EFFECT || it.type == LayerType.RGB_SPLIT_EFFECT ||
                it.type == LayerType.MOTION_BLUR_EFFECT || it.type == LayerType.MIRROR_EFFECT
        }

        val compositions = if (effectLayers.isNotEmpty() && sceneLayers.isNotEmpty()) {
            listOf(
                CompositionNode(index = 0, name = "Final Composition", layers = effectLayers),
                CompositionNode(index = 1, name = "Composition 1", layers = sceneLayers)
            )
        } else {
            listOf(CompositionNode(index = 0, name = "Final Composition", layers = layers))
        }

        return VexoProject(
            name = fallbackName,
            audioProviders = finalProviders,
            compositions = compositions
        )
    }

    private fun decodeAveeRoot(root: JsonObject, comps: JsonArray, name: String): VexoProject {
        require(comps.size in 1..128) { "Invalid composition count" }
        val providers = mutableListOf<AudioProviderConfig>()
        val result = comps.mapIndexed { index, value ->
            val comp = value as? JsonObject ?: error("Invalid composition $index")
            val elements = (comp["elements"] ?: comp["layers"]) as? JsonArray ?: JsonArray(emptyList())
            require(elements.size <= 2048) { "Too many elements" }
            val layers = mutableListOf<LayerNode>()
            val preserved = linkedMapOf<Int, String>()
            val providerIndices = mutableListOf<Int>()
            elements.forEachIndexed { position, el ->
                val obj = el as? JsonObject
                if (obj == null) preserved[position] = el.toString()
                else if (obj["objType"]?.primitiveString() == "AudioProvider") {
                    val providerIndex = providers.size + 1
                    providers.add(parseAudioProvider(obj, providerIndex))
                    providerIndices.add(providerIndex)
                } else {
                    val layer = decodeLayerByObjType(obj)
                    if (layer != null) layers.add(layer) else preserved[position] = obj.toString()
                }
            }
            CompositionNode(index = index, name = if (index == 0) "Final Composition" else comp["_name"]?.primitiveString() ?: "Composition $index",
                layers = layers, aveeMetadata = comp.filterKeys { it != "elements" && it != "layers" }.mapValues { it.value.toString() },
                preservedElements = preserved, providerIndices = providerIndices)
        }
        return VexoProject(name = name, audioProviders = providers.ifEmpty { listOf(AudioProviderConfig()) },
            compositions = result, aveeRootMetadata = root.filterKeys { it != "compositions" }.mapValues { it.value.toString() })
    }

    fun importAuto(input: InputStream, name: String): VexoProject {
        val stream = BufferedInputStream(input)
        stream.mark(4)
        val a = stream.read()
        val b = stream.read()
        stream.reset()
        return if (a == 0x50 && b == 0x4b) importFromVizZip(stream, name)
        else importFromText(readBounded(stream, MAX_VIZ_ENTRY_BYTES).toString(Charsets.UTF_8).removePrefix("\uFEFF"), name)
    }

    fun exportViz(project: VexoProject, output: OutputStream) {
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("scene.json"))
            zip.write(exportAveeScene(project).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    fun exportAveeScene(project: VexoProject): String {
        val root = buildJsonObject {
            project.aveeRootMetadata.forEach { (k, v) -> runCatching { json.parseToJsonElement(v) }.getOrNull()?.let { put(k, it) } }
            put("objType", "Root")
            put("ver", "10")
            put("compositions", buildJsonArray {
                project.compositions.forEach { comp ->
                    add(buildJsonObject {
                        comp.aveeMetadata.forEach { (k, v) -> runCatching { json.parseToJsonElement(v) }.getOrNull()?.let { put(k, it) } }
                        put("objType", "Composition")
                        put("v", "Composition")
                        put("ver", "10")
                        val assigned = project.compositions.flatMap { it.providerIndices }.toSet()
                        val elements = mutableListOf<JsonElement>()
                        project.audioProviders.filter { it.index in comp.providerIndices || (comp.index == 0 && it.index !in assigned) }.forEach { elements.add(encodeAudioProvider(it)) }
                        comp.layers.forEach { elements.add(encodeLayer(it)) }
                        comp.preservedElements.toSortedMap().forEach { (index, raw) -> runCatching { json.parseToJsonElement(raw) }.getOrNull()?.let { elements.add(index.coerceIn(0, elements.size), it) } }
                        put("elements", JsonArray(elements))
                    })
                }
            })
        }
        return json.encodeToString(JsonElement.serializer(), root)
    }

    fun importFromVizZip(inputStream: InputStream, fallbackName: String = "VIZ Preset"): VexoProject {
        ZipInputStream(inputStream).use { zis ->
            var scanned = 0
            var entry = zis.nextEntry
            while (entry != null) {
                require(++scanned <= 128) { "Too many ZIP entries" }
                val safeName = entry.name.replace('\\', '/')
                if (safeName.contains("..") || safeName.startsWith("/")) {
                    error("Unsafe zip path detected: ${entry.name}")
                }
                if (!entry.isDirectory && safeName.substringAfterLast('/') == "scene.json") {
                    val bytes = readBounded(zis, MAX_VIZ_ENTRY_BYTES)
                    return importFromText(bytes.toString(Charsets.UTF_8), fallbackName)
                }
                if (!entry.isDirectory) readBounded(zis, MAX_VIZ_ENTRY_BYTES)
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        error("No JSON preset found inside .viz archive")
    }

    fun exportProjectAsAveeJson(project: VexoProject): String {
        val root = buildJsonObject {
            put("vexoSchema", project.schemaVersion)
            put("aveeRootMetadata", JsonObject(project.aveeRootMetadata.mapValues { JsonPrimitive(it.value) }))
            put("name", project.name)
            put("aspectRatio", project.canvas.aspectRatio.name)
            put("audioProviders", buildJsonArray {
                project.audioProviders.forEach { add(encodeAudioProvider(it)) }
            })
            put("compositions", buildJsonArray {
                project.compositions.forEach { comp ->
                    add(buildJsonObject {
                        put("aveeMetadata", JsonObject(comp.aveeMetadata.mapValues { JsonPrimitive(it.value) }))
                        put("preservedElements", JsonObject(comp.preservedElements.mapKeys { it.key.toString() }.mapValues { JsonPrimitive(it.value) }))
                        put("providerIndices", JsonArray(comp.providerIndices.map { JsonPrimitive(it) }))
                        put("index", comp.index)
                        put("name", comp.name)
                        put("layers", buildJsonArray {
                            comp.layers.forEach { add(encodeLayer(it)) }
                        })
                    })
                }
            })
        }
        return json.encodeToString(JsonElement.serializer(), root)
    }

    private fun decodeNativeProject(root: JsonObject): VexoProject {
        val inner = root["vexoProject"] as? JsonObject ?: root
        val name = inner["name"]?.primitiveString() ?: "Imported Studio"
        val providers = (inner["audioProviders"] as? JsonArray)?.mapIndexedNotNull { idx, el ->
            (el as? JsonObject)?.let { parseAudioProvider(it, idx + 1) }
        }?.ifEmpty { listOf(AudioProviderConfig()) } ?: listOf(AudioProviderConfig())
        val comps = (inner["compositions"] as? JsonArray)?.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val idx = obj["index"]?.primitiveInt() ?: 0
            val cName = obj["name"]?.primitiveString() ?: "Composition $idx"
            val cLayers = (obj["layers"] as? JsonArray)?.mapNotNull { lEl ->
                (lEl as? JsonObject)?.let { decodeLayerByObjType(it) }
            } ?: emptyList()
            CompositionNode(index = idx, name = cName, layers = cLayers,
                aveeMetadata = (obj["aveeMetadata"] as? JsonObject)?.mapValues { it.value.primitiveString() ?: "" } ?: emptyMap(),
                preservedElements = (obj["preservedElements"] as? JsonObject)?.mapNotNull { (k,v) -> k.toIntOrNull()?.let { it to (v.primitiveString() ?: "") } }?.toMap() ?: emptyMap(),
                providerIndices = (obj["providerIndices"] as? JsonArray)?.mapNotNull { it.primitiveInt() } ?: emptyList())
        }?.ifEmpty { listOf(CompositionNode(index = 0)) } ?: listOf(CompositionNode(index = 0))
        return VexoProject(name = name, audioProviders = providers, compositions = comps,
            aveeRootMetadata = (inner["aveeRootMetadata"] as? JsonObject)?.mapValues { it.value.primitiveString() ?: "" } ?: emptyMap())
    }

    private fun decodeLayerByObjType(obj: JsonObject): LayerNode? {
        return when (obj["objType"]?.primitiveString() ?: obj["v"]?.primitiveString()) {
            "Image" -> parseImageLayer(obj)
            "Bars" -> parseBarsLayer(obj)
            "Text" -> parseTextLayer(obj)
            "Particles" -> parseParticlesLayer(obj)
            "BlurEffect" -> parseBlurEffect(obj)
            "RgbSplitEffect" -> parseRgbSplitEffect(obj)
            "MotionBlurEffect" -> parseMotionBlurEffect(obj)
            "MirrorEffect" -> parseMirrorEffect(obj)
            else -> null
        }
    }

    private fun collectAveeObjects(el: JsonElement, out: MutableList<JsonObject>) {
        when (el) {
            is JsonObject -> {
                if (el.containsKey("objType")) {
                    out.add(el)
                } else {
                    for ((_, child) in el) {
                        collectAveeObjects(child, out)
                    }
                }
            }
            is JsonArray -> el.forEach { collectAveeObjects(it, out) }
            else -> Unit
        }
    }

    private fun parseAudioProvider(obj: JsonObject, index: Int): AudioProviderConfig {
        val rawFftPower = obj.propInt("fftSize", 10)
        val fftSize = if (rawFftPower in 7..13) 1 shl rawFftPower else rawFftPower
        return AudioProviderConfig(
            index = index,
            tag = obj["_name"]?.primitiveString() ?: "Audio Provider",
            enabled = obj.propBool("enabled", true), attackMs = obj.propFloat("attackMs", 8f), releaseMs = obj.propFloat("releaseMs", 75f),
            peakHoldMs = obj.propFloat("peakHoldMs", 180f), peakDecay = obj.propFloat("peakDecay", 0.85f), beatSensitivity = obj.propFloat("beatSensitivity", 1.2f),
            sampleProvider = SampleProviderType.fromAvee(obj.propString("sampleProvider", "Spectrum")),
            fftSize = fftSize,
            sampleOutCount = obj.propInt("sampleOutCount", 96).coerceIn(1, 512),
            mirrorSamples = obj.propBool("mirrorSamples", false),
            repeatSamples = obj.propInt("repeatSamples", 1).coerceIn(1, 6),
            startAndEndGap = obj.propInt("starAndEndGap", 0).coerceIn(0, 30),
            smooth = obj.propFloat("smooth", 0.75f),
            preSmooth = obj.propFloat("preSmooth", 0.5f),
            filterRadius = obj.propInt("filterRadius", 1),
            filterStrength = obj.propFloat("filterStrength", 0.623f),
            aWeight = obj.propFloat("aWeight", 0.2f),
            outputMultiplier = obj.propFloat("outputMultiplier", 0.753f).coerceAtLeast(0.1f),
            lowerHz = obj.propFloat("lowerHz", 52.5f),
            higherHz = obj.propFloat("higherHz", 500f),
            hzLinearFactor = obj.propFloat("hzLinearFactor", 1f),
            freqShift = obj.propFloat("freqShift", 0f),
            beatSmooth = obj.propFloat("beatSmooth", 0.4f),
            beatRangeBarFirst = obj.propFloat("beatRangeBarFirst", 0f),
            beatRangeBarLast = obj.propFloat("beatRangeBarLast", 0.2f),
            beatRangeValueLower = obj.propFloat("beatRangeValueLower", 0.7f),
            beatRangeValueHigher = obj.propFloat("beatRangeValueHigher", 35f),
            extraFields = extractExtraFields(obj, emptySet())
        )
    }

    private fun parseCommon(obj: JsonObject, defaultName: String): LayerCommon {
        val (px, py) = obj.propFloat2("position", 0.5f, 0.5f)
        val (ax, ay) = obj.propFloat2("alignmentPosition", 0.5f, 0.5f)
        val (sx, sy) = obj.propFloat2("scale", 1f, 1f)
        return LayerCommon(
            aveeId = obj.propInt("_id", 0),
            name = obj["_name"]?.primitiveString() ?: defaultName,
            visible = obj.propBool("visible", true),
            blendMode = BlendMode.fromAvee(obj.propString("blendMode", "PreMulAlpha")),
            posX = px,
            posY = py,
            anchorX = AnchorMode.fromAvee(obj.propString("anchorX", "unset")),
            anchorY = AnchorMode.fromAvee(obj.propString("anchorY", "unset")),
            alignmentX = ax,
            alignmentY = ay,
            scaleX = sx,
            scaleY = sy,
            scaleIsUniform = obj.propBool("scaleIsUniform", false),
            rotationDeg = obj.propFloat("rotation", 0f),
            opacity = obj.propFloat("opacity", 1f),
            measurePos = parseMeasure(obj["MeasurePos"]),
            measureScale = parseMeasure(obj["measureScale"]),
            measureRot = parseMeasure(obj["measureRot"]),
            extraFields = extractExtraFields(obj, emptySet())
        )
    }

    private fun parseImageLayer(obj: JsonObject): LayerNode {
        val customImg = obj.propString("customImage", "")
        val (srcType, compIdx) = when {
            customImg.startsWith("composition:") ->
                ImageSourceType.COMPOSITION to (customImg.substringAfter("composition:").toIntOrNull() ?: 0)
            customImg.startsWith("internalres:") -> ImageSourceType.INTERNAL to 0
            else -> ImageSourceType.ALBUM_ART to 0
        }
        return LayerNode(
            type = LayerType.IMAGE,
            common = parseCommon(obj, "Image"),
            config = LayerConfig.Image(
                sourceType = srcType,
                internalRes = customImg.ifEmpty { "internalres:white" },
                sourceCompositionIndex = compIdx,
                colorArgb = parseHsla4fToArgb(obj.propString("Color", "1 1 1 1")),
                colorToArgb = parseHsla4fToArgb(obj.propString("ColorTo", "1 1 1 1")),
                measureColorBlend = parseMeasure(obj["MeasureColorBlend"]),
                shape = ImageShape.fromAvee(obj.propString("Shape", "None")),
                keepAspectRatio = obj.propBool("keepAspectRatio", false),
                keepAspectRatioAndCropToFit = obj.propBool("keepAspectRatioAndCropToFit", false),
                saturation = parseMvarfValue(obj.propString("saturation", "Constant 1 1"), 1f),
                colorKeyEnabled = obj.propBool("colorKeyEnabled", false),
                colorKeyArgb = obj.propInt("colorKey", -16711936),
                autoDetectColorKey = obj.propBool("autoDetectColorKey", true),
                transparencyStrength = obj.propFloat("transparencyStrength", 1f),
                opacityStrength = obj.propFloat("opacityStrength", 1f),
                blurEnabled = obj.propBool("blurEnabled", false),
                blurRadius = obj.propInt("blurRadius", 7),
                blurDivider = obj.propInt("blurDivider", 5),
                blurredBorder = obj.propBool("blurredBorder", false),
                maskImage = obj.propString("MaskImage", ""),
                maskMode = MaskMode.fromAvee(obj.propString("maskMode", "Transparency"))
            )
        )
    }

    private fun parseBarsLayer(obj: JsonObject): LayerNode {
        val seg1Obj = obj["Segment1"] as? JsonObject
        val seg1Type = BarsSegmentType.fromAvee(seg1Obj?.get("v")?.primitiveString() ?: "Bars")
        val seg2Obj = obj["Segment2"] as? JsonObject
        val seg2Type = BarsSegmentType.fromAvee(seg2Obj?.get("v")?.primitiveString() ?: "None")
        return LayerNode(
            type = LayerType.BARS,
            common = parseCommon(obj, "Bars/Segments"),
            config = LayerConfig.Bars(
                audioProviderIndex = obj.propInt("audioProviderIndex", 1),
                shapePath = BarsShapePath.fromAvee(obj.propString("ShapePath", "HorizontalLine")),
                segment1 = seg1Type,
                segment2 = seg2Type,
                barWidth = seg1Obj?.propFloat("barWidth", 0.6f) ?: 0.6f,
                barHeightMultiplier = seg1Obj?.propFloat("barHeightMultiplier", 1f) ?: 1f,
                fixedHeight = seg1Obj?.propFloat("fixedHeight", 0f) ?: 0f,
                mirror = seg1Obj?.propBool("mirror", false) ?: false,
                colorFromArgb = seg1Obj?.propInt("colorFrom", -1) ?: -1,
                colorToArgb = seg1Obj?.propInt("colorTo", -1) ?: -1,
                heightScale = obj.propFloat("heightScale", 3f),
                minHeightScale = obj.propFloat("minHeightScale", 0.006f),
                maxHeightScale = obj.propFloat("maxHeightScale", 1f),
                flipInput = obj.propBool("flipInput", false),
                softness = obj.propInt("softness", 6),
                reactionDelay = obj.propInt("reactionDelay", 0),
                reactionAccumulatedDelay = obj.propInt("reactionAccumulatedDelay", 0),
                colorBlendOffset = parseMvarfValue(obj.propString("colorBlendOffset", "Constant 0 0"), 0f),
                circleRadius = obj.propFloat("circleRadius", 0.28f),
                polygonSides = obj.propInt("polygonSides", 6),
                pathAngleDeg = obj.propFloat("pathAngleDeg", 0f),
                alternatingDirection = obj.propBool("alternatingDirection", false)
            )
        )
    }

    private fun parseTextLayer(obj: JsonObject): LayerNode = LayerNode(
        type = LayerType.TEXT,
        common = parseCommon(obj, "Text"),
        config = LayerConfig.Text(
            text = obj.propString("text", "{Artist}"),
            colorArgb = obj.propInt("color", -1),
            fontSize = obj.propInt("fontSize", 56).coerceIn(8, 120),
            fontAssetUri = obj.propString("typeFace", "").ifBlank { null },
            bold = obj.propBool("bold", true), italic = obj.propBool("italic", false),
            alignment = obj.propInt("alignment", 0), letterSpacing = obj.propFloat("letterSpacing", 0.04f),
            lineSpacing = obj.propFloat("lineSpacing", 1.2f), strokeWidth = obj.propFloat("strokeWidth", 0f),
            strokeColorArgb = obj.propInt("strokeColor", 0xFF000000.toInt()), shadowRadius = obj.propFloat("shadowRadius", 4f)
        )
    )

    private fun parseParticlesLayer(obj: JsonObject): LayerNode {
        val spawnObj = obj["SpawnArea"] as? JsonObject
        val (gx, gy) = obj.propFloat2("gravity", 0f, 0f)
        return LayerNode(
            type = LayerType.PARTICLES,
            common = parseCommon(obj, "Particles"),
            config = LayerConfig.Particles(
                countLimit = obj.propInt("CountLimit", 650).coerceIn(1, 5000),
                spawnArea = SpawnAreaType.fromAvee(spawnObj?.get("v")?.primitiveString()),
                vectorAngleDeg = spawnObj?.propFloat("vectorAngle", 270f) ?: 270f,
                vectorAngleZ = spawnObj?.propFloat("vectorAngleZ", 1f) ?: 1f,
                useRandomVectorInstead = spawnObj?.propBool("useRandomVectorInstead", false) ?: false,
                vectorRelativeToCenter = spawnObj?.propBool("vectorRelativeToCenter", false) ?: false,
                forceField = ForceFieldType.fromAvee(obj.propString("ForceField", "None")),
                speed = obj.propFloat("Speed", 100f),
                speedRandom = obj.propFloat("speedRandom", 100f),
                spawnTimeSec = obj.propFloat("spawnTime", 0.01f),
                lifetimeSec = obj.propFloat("lifetime", 8f),
                gravityX = gx,
                gravityY = gy,
                colorArgb = obj.propInt("color", -1),
                colorFromArgb = parseHsla4fToArgb(obj.propString("ColorFrom", "0 0 1 1")),
                colorToArgb = parseHsla4fToArgb(obj.propString("ColorTo", "0 0 0.22 0.22")),
                startSize = obj.propFloat("startSize", 0f),
                endSize = obj.propFloat("endSize", 4f),
                endSizeRandom = obj.propFloat("endSizeRandom", 0f),
                fadeInSize = obj.propFloat("fadeInSize", 4f),
                fadeInAndOutTime = obj.propFloat("fadeInAndOutTime", 0.1f),
                particleScale = obj.propFloat("particleScale", 0.5f),
                perspectiveDepth = obj.propFloat("perspectiveDepth", 900f),
                perspectiveFov = obj.propFloat("perspectiveFov", 48.75f),
                nearCameraFadeOutDistance = obj.propFloat("nearCameraFadeOutDistance", 75f),
                velocityAngle = obj.propBool("velocityAngle", true),
                mirrorX = obj.propBool("mirrorX", false),
                mirrorY = obj.propBool("mirrorY", false),
                trailLength = obj.propFloat("trailLength", 0f),
                sideSineWaveFreq = obj.propFloat("sideSineWaveFreq", 0f),
                sideSineWaveMag = obj.propFloat("sideSineWaveMag", 0f),
                measureOverallSpeed = parseMeasure(obj["MeasureOverallSpeed"]),
                customImage = obj.propString("customImage", "internalres:particle_circle_blur4")
            )
        )
    }

    private fun parseBlurEffect(obj: JsonObject): LayerNode = LayerNode(
        type = LayerType.BLUR_EFFECT,
        common = parseCommon(obj, "Blur Effect"),
        config = LayerConfig.BlurEffect(
            sourceCompositionIndex = obj.propInt("sourceCompositionIndex", 1),
            blurRadius = obj.propFloat("blurRadius", 2f),
            blurMultiplier = obj.propFloat("blurMultiplier", 4.1f),
            layer1ColorArgb = obj.propInt("1layerColor", -1),
            layer2ColorArgb = obj.propInt("2layerColor", -1),
            layer3ColorArgb = obj.propInt("3layerColor", -1),
            colorArgb = obj.propInt("color", -16777216),
            blendModeContent = BlendMode.fromAvee(obj.propString("blendModeContent", "PreMulAlpha")),
            showUnblurredContent = obj.propBool("showUnblurredContent", false),
            showUnblurredContentUnder = obj.propBool("showUnblurredContentUnder", true),
            maskImage = obj.propString("MaskImage", "internalres:transparent")
        )
    )

    private fun parseRgbSplitEffect(obj: JsonObject): LayerNode {
        val target = obj.propString("targetImage", "composition:1")
            .substringAfter("composition:", "1").toIntOrNull() ?: 1
        return LayerNode(
            type = LayerType.RGB_SPLIT_EFFECT,
            common = parseCommon(obj, "Rgb Split"),
            config = LayerConfig.RgbSplitEffect(
                targetCompositionIndex = target,
                splitMultiplier = parseMvarfValue(obj.propString("splitMultiplier", "Constant 6 6"), 6f),
                splitAmountX = obj.propFloat("splitAmountX", 0.015f), splitAmountY = obj.propFloat("splitAmountY", 0.008f),
                splitMeasure = parseMvarf2Measure(obj.propString("splitAmount", "BeatRandomShake 0.5 0.5")),
                splitColor0Argb = obj.propInt("splitColor0", -65536),
                splitColor1Argb = obj.propInt("splitColor1", -16711936),
                splitColor2Argb = obj.propInt("splitColor2", -16776961),
                blendModeContent = BlendMode.fromAvee(obj.propString("blendModeContent", "Add")),
                showUnblurredContent = obj.propBool("showUnblurredContent", false),
                showUnblurredContentUnder = obj.propBool("showUnblurredContentUnder", false)
            )
        )
    }

    private fun parseMotionBlurEffect(obj: JsonObject): LayerNode {
        val target = obj.propString("TargetImage", "composition:1")
            .substringAfter("composition:", "1").toIntOrNull() ?: 1
        return LayerNode(
            type = LayerType.MOTION_BLUR_EFFECT,
            common = parseCommon(obj, "Motion Blur Effect"),
            config = LayerConfig.MotionBlurEffect(
                targetCompositionIndex = target,
                blurAmountMultiplier = parseMvarfValue(obj.propString("blurAmountMultiplier", "Constant 1 1"), 1f),
                motionSource = MotionSource.fromAvee(obj.propString("motionSource", "EffectTransform")),
                relativeMotionMode = obj.propBool("relativeMotionMode", true),
                blendModeContent = BlendMode.fromAvee(obj.propString("blendModeContent", "Add")),
                colorArgb = obj.propInt("color", -1),
                showUnblurredContent = obj.propBool("showUnblurredContent", false),
                showUnblurredContentUnder = obj.propBool("showUnblurredContentUnder", false)
            )
        )
    }

    private fun parseMirrorEffect(obj: JsonObject): LayerNode {
        val target = obj.propString("TargetImage", "composition:1")
            .substringAfter("composition:", "1").toIntOrNull() ?: 1
        return LayerNode(
            type = LayerType.MIRROR_EFFECT,
            common = parseCommon(obj, "Mirror Effect"),
            config = LayerConfig.MirrorEffect(
                targetCompositionIndex = target,
                mirrorMode = MirrorMode.fromAvee(obj.propString("mirrorMode", "Horizontal")),
                flipMirror = obj.propBool("flipMirror", false),
                colorArgb = obj.propInt("color", -1)
            )
        )
    }

    private fun encodeAudioProvider(p: AudioProviderConfig): JsonObject = mergeOriginal(p.extraFields, buildJsonObject {
        put("_name", p.tag)
        put("objType", "AudioProvider")
        put("ver", "10")
        put("v", "AudioProvider")
        put("sampleProvider", propObj(p.sampleProvider.aveeName))
        put("enabled", propObj(if (p.enabled) 1 else 0)); put("attackMs", propObj(p.attackMs)); put("releaseMs", propObj(p.releaseMs))
        put("peakHoldMs", propObj(p.peakHoldMs)); put("peakDecay", propObj(p.peakDecay)); put("beatSensitivity", propObj(p.beatSensitivity))
        val pow = kotlin.math.log2(p.fftSize.toDouble()).roundToInt().coerceIn(7, 13)
        put("fftSize", propObj(pow))
        put("sampleOutCount", propObj(p.sampleOutCount))
        put("mirrorSamples", propObj(if (p.mirrorSamples) 1 else 0))
        put("repeatSamples", propObj(p.repeatSamples))
        put("starAndEndGap", propObj(p.startAndEndGap))
        put("smooth", propObj(p.smooth))
        put("preSmooth", propObj(p.preSmooth))
        put("filterRadius", propObj(p.filterRadius))
        put("filterStrength", propObj(p.filterStrength))
        put("aWeight", propObj(p.aWeight))
        put("outputMultiplier", propObj(p.outputMultiplier))
        put("lowerHz", propObj(p.lowerHz))
        put("higherHz", propObj(p.higherHz))
        put("hzLinearFactor", propObj(p.hzLinearFactor))
        put("freqShift", propObj(p.freqShift))
        put("beatSmooth", propObj(p.beatSmooth))
        put("beatRangeBarFirst", propObj(p.beatRangeBarFirst))
        put("beatRangeBarLast", propObj(p.beatRangeBarLast))
        put("beatRangeValueLower", propObj(p.beatRangeValueLower))
        put("beatRangeValueHigher", propObj(p.beatRangeValueHigher))
    })

    private fun encodeLayer(layer: LayerNode): JsonObject = mergeOriginal(layer.common.extraFields, buildJsonObject {
        val c = layer.common
        put("_name", c.name)
        put("objType", layer.type.aveeObjType)
        put("v", layer.type.aveeObjType)
        put("ver", "10")
        put("visible", propObj(if (c.visible) 1 else 0))
        put("blendMode", propObj(c.blendMode.aveeName))
        put("position", propObj("${c.posX} ${c.posY}"))
        put("alignmentPosition", propObj("${c.alignmentX} ${c.alignmentY}"))
        put("anchorX", propObj(c.anchorX.aveeName))
        put("anchorY", propObj(c.anchorY.aveeName))
        put("scale", propObj("${c.scaleX} ${c.scaleY}"))
        put("scaleIsUniform", propObj(if (c.scaleIsUniform) 1 else 0))
        put("rotation", propObj(c.rotationDeg))
        put("_id", propObj(c.aveeId))
        put("opacity", propObj(c.opacity))
        put("MeasurePos", encodeMeasure(c.measurePos))
        put("measureScale", encodeMeasure(c.measureScale))
        put("measureRot", encodeMeasure(c.measureRot))
        when (val cfg = layer.config) {
            is LayerConfig.Bars -> {
                put("audioProviderIndex", propObj(cfg.audioProviderIndex))
                put("ShapePath", propObj(cfg.shapePath.aveeName))
                put("heightScale", propObj(cfg.heightScale))
                put("minHeightScale", propObj(cfg.minHeightScale))
                put("maxHeightScale", propObj(cfg.maxHeightScale))
                put("flipInput", propObj(if (cfg.flipInput) 1 else 0))
                put("softness", propObj(cfg.softness))
                put("reactionDelay", propObj(cfg.reactionDelay))
                put("reactionAccumulatedDelay", propObj(cfg.reactionAccumulatedDelay))
                put("colorBlendOffset", propObj("Constant ${cfg.colorBlendOffset} ${cfg.colorBlendOffset}"))
                put("circleRadius", propObj(cfg.circleRadius))
                put("polygonSides", propObj(cfg.polygonSides))
                put("pathAngleDeg", propObj(cfg.pathAngleDeg))
                put("alternatingDirection", propObj(if (cfg.alternatingDirection) 1 else 0))
                put("Segment1", buildJsonObject {
                    put("v", cfg.segment1.aveeName)
                    put("barWidth", propObj(cfg.barWidth))
                    put("barHeightMultiplier", propObj(cfg.barHeightMultiplier))
                    put("fixedHeight", propObj(cfg.fixedHeight))
                    put("mirror", propObj(if (cfg.mirror) 1 else 0))
                    put("colorFrom", propObj(cfg.colorFromArgb))
                    put("colorTo", propObj(cfg.colorToArgb))
                })
                put("Segment2", propObj(cfg.segment2.aveeName))
            }
            is LayerConfig.Image -> {
                val imgRef = when (cfg.sourceType) {
                    ImageSourceType.COMPOSITION -> "composition:${cfg.sourceCompositionIndex}"
                    ImageSourceType.INTERNAL -> cfg.internalRes
                    else -> cfg.customUri ?: ""
                }
                put("customImage", propObj(imgRef))
                put("Shape", propObj(cfg.shape.aveeName))
                put("keepAspectRatio", propObj(if (cfg.keepAspectRatio) 1 else 0))
                put("keepAspectRatioAndCropToFit", propObj(if (cfg.keepAspectRatioAndCropToFit) 1 else 0))
                put("colorKeyEnabled", propObj(if (cfg.colorKeyEnabled) 1 else 0))
                put("colorKey", propObj(cfg.colorKeyArgb))
                put("blurEnabled", propObj(if (cfg.blurEnabled) 1 else 0))
                put("blurRadius", propObj(cfg.blurRadius))
                put("opacityStrength", propObj(cfg.opacityStrength))
                put("transparencyStrength", propObj(cfg.transparencyStrength))
                put("saturation", propObj("Constant ${cfg.saturation} ${cfg.saturation}"))
            }
            is LayerConfig.Text -> {
                put("text", propObj(cfg.text))
                put("color", propObj(cfg.colorArgb))
                put("fontSize", propObj(cfg.fontSize))
                put("bold", propObj(if (cfg.bold) 1 else 0)); put("italic", propObj(if (cfg.italic) 1 else 0))
                put("alignment", propObj(cfg.alignment)); put("letterSpacing", propObj(cfg.letterSpacing)); put("lineSpacing", propObj(cfg.lineSpacing))
                put("strokeWidth", propObj(cfg.strokeWidth)); put("strokeColor", propObj(cfg.strokeColorArgb)); put("shadowRadius", propObj(cfg.shadowRadius))
            }
            is LayerConfig.Shape -> {
                put("shapeKind", propObj(cfg.kind.name))
                put("sides", propObj(cfg.sides))
                put("fillColor", propObj(cfg.fillColorArgb))
                put("strokeColor", propObj(cfg.strokeColorArgb))
            }
            is LayerConfig.Particles -> {
                put("CountLimit", propObj(cfg.countLimit))
                put("Speed", propObj(cfg.speed))
                put("speedRandom", propObj(cfg.speedRandom))
                put("spawnTime", propObj(cfg.spawnTimeSec))
                put("lifetime", propObj(cfg.lifetimeSec))
                put("ForceField", propObj(cfg.forceField.aveeName))
                put("customImage", propObj(cfg.customImage))
                put("gravity", propObj("${cfg.gravityX} ${cfg.gravityY}"))
                put("particleScale", propObj(cfg.particleScale))
                put("startSize", propObj(cfg.startSize))
                put("endSize", propObj(cfg.endSize))
                put("fadeInAndOutTime", propObj(cfg.fadeInAndOutTime))
                put("endSizeRandom", propObj(cfg.endSizeRandom)); put("perspectiveDepth", propObj(cfg.perspectiveDepth)); put("perspectiveFov", propObj(cfg.perspectiveFov))
                put("nearCameraFadeOutDistance", propObj(cfg.nearCameraFadeOutDistance)); put("sideSineWaveFreq", propObj(cfg.sideSineWaveFreq)); put("sideSineWaveMag", propObj(cfg.sideSineWaveMag))
                put("SpawnArea", buildJsonObject { put("v", cfg.spawnArea.aveeName); put("vectorAngle", propObj(cfg.vectorAngleDeg)); put("vectorAngleZ", propObj(cfg.vectorAngleZ)); put("useRandomVectorInstead", propObj(if (cfg.useRandomVectorInstead) 1 else 0)); put("vectorRelativeToCenter", propObj(if (cfg.vectorRelativeToCenter) 1 else 0)) })
            }
            is LayerConfig.BlurEffect -> {
                put("sourceCompositionIndex", propObj(cfg.sourceCompositionIndex))
                put("blurRadius", propObj(cfg.blurRadius))
                put("blurMultiplier", propObj(cfg.blurMultiplier))
                put("showUnblurredContent", propObj(if (cfg.showUnblurredContent) 1 else 0))
                put("showUnblurredContentUnder", propObj(if (cfg.showUnblurredContentUnder) 1 else 0))
            }
            is LayerConfig.RgbSplitEffect -> {
                put("targetImage", propObj("composition:${cfg.targetCompositionIndex}"))
                put("splitAmountX", propObj(cfg.splitAmountX)); put("splitAmountY", propObj(cfg.splitAmountY))
                put("splitMultiplier", propObj("Constant ${cfg.splitMultiplier} ${cfg.splitMultiplier}"))
                put("splitColor0", propObj(cfg.splitColor0Argb))
                put("splitColor1", propObj(cfg.splitColor1Argb))
                put("splitColor2", propObj(cfg.splitColor2Argb))
            }
            is LayerConfig.MotionBlurEffect -> {
                put("TargetImage", propObj("composition:${cfg.targetCompositionIndex}"))
                put("blurAmountMultiplier", propObj("Constant ${cfg.blurAmountMultiplier} ${cfg.blurAmountMultiplier}"))
                put("motionSource", propObj(cfg.motionSource.aveeName))
            }
            is LayerConfig.MirrorEffect -> {
                put("TargetImage", propObj("composition:${cfg.targetCompositionIndex}"))
                put("mirrorMode", propObj(cfg.mirrorMode.aveeName))
                put("flipMirror", propObj(if (cfg.flipMirror) 1 else 0)); put("color", propObj(cfg.colorArgb))
            }
        }
    })

    private fun mergeOriginal(raw: Map<String, String>, edited: JsonObject): JsonObject {
        val old = raw.mapNotNull { (k, v) -> runCatching { k to json.parseToJsonElement(v) }.getOrNull() }.toMap()
        fun merge(a: JsonObject, b: JsonObject): JsonObject = JsonObject(a + b.mapValues { (k, v) ->
            val prior = a[k]
            if (prior is JsonObject && v is JsonObject) merge(prior, v) else v
        })
        return merge(JsonObject(old), edited)
    }

    private fun propObj(v: String): JsonObject = buildJsonObject { put("v", v) }
    private fun propObj(v: Int): JsonObject = buildJsonObject { put("v", v) }
    private fun propObj(v: Float): JsonObject = buildJsonObject { put("v", v) }

    private fun encodeMeasure(m: MeasureConfig): JsonObject = buildJsonObject {
        put("measureWhat", propObj(m.measureWhat.aveeName))
        put("A", propObj(m.amountA))
        put("B", propObj(m.amountB))
    }

    private fun parseMeasure(el: JsonElement?): MeasureConfig {
        val obj = el as? JsonObject ?: return MeasureConfig()
        val whatObj = obj["measureWhat"] as? JsonObject
        val whatName = whatObj?.get("v")?.primitiveString() ?: "Nothing"
        val a = (obj["A"] as? JsonObject)?.get("v")?.primitiveFloat() ?: 0.5f
        val b = (obj["B"] as? JsonObject)?.get("v")?.primitiveFloat() ?: 0.5f
        return MeasureConfig(
            measureWhat = MeasureWhat.fromAvee(whatName),
            amountA = a,
            amountB = b
        )
    }

    private fun parseMvarfValue(raw: String, defaultVal: Float): Float {
        val parts = raw.trim().split(' ')
        return parts.getOrNull(1)?.toFloatOrNull() ?: defaultVal
    }

    private fun parseMvarf2Measure(raw: String): MeasureConfig {
        val parts = raw.trim().split(' ')
        val what = MeasureWhat.fromAvee(parts.getOrNull(0))
        val a = parts.getOrNull(1)?.toFloatOrNull() ?: 0.5f
        val b = parts.getOrNull(2)?.toFloatOrNull() ?: 0.5f
        return MeasureConfig(measureWhat = what, amountA = a, amountB = b)
    }

    /**
     * Converts Avee "H S L A" normalized floats (0..1) to Android ARGB Int.
     */
    fun parseHsla4fToArgb(raw: String): Int {
        val parts = raw.trim().split(' ').mapNotNull { it.toFloatOrNull() }
        if (parts.size < 4) return 0xFFFFFFFF.toInt()
        val h = (parts[0].coerceIn(0f, 1f)) * 360f
        val s = parts[1].coerceIn(0f, 1f)
        val l = parts[2].coerceIn(0f, 1f)
        val a = (parts[3].coerceIn(0f, 1f) * 255f).roundToInt()

        val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (r1, g1, b1) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val r = ((r1 + m) * 255f).roundToInt().coerceIn(0, 255)
        val g = ((g1 + m) * 255f).roundToInt().coerceIn(0, 255)
        val b = ((b1 + m) * 255f).roundToInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun extractExtraFields(obj: JsonObject, known: Set<String>): Map<String, String> {
        val map = linkedMapOf<String, String>()
        for ((k, v) in obj) {
            if (k !in known) {
                map[k] = v.toString()
            }
        }
        return map
    }

    private val knownAudioProviderKeys = setOf(
        "_name", "objType", "v", "sampleProvider", "fftSize", "sampleOutCount",
        "mirrorSamples", "repeatSamples", "starAndEndGap", "smooth", "preSmooth",
        "filterRadius", "filterStrength", "aWeight", "outputMultiplier",
        "lowerHz", "higherHz", "hzLinearFactor", "freqShift", "beatSmooth",
        "beatRangeBarFirst", "beatRangeBarLast", "beatRangeValueLower", "beatRangeValueHigher"
    )

    private fun readBounded(zis: InputStream, maxBytes: Int): ByteArray {
        val buffer = ByteArray(8192)
        val out = java.io.ByteArrayOutputStream()
        var total = 0
        while (true) {
            val read = zis.read(buffer)
            if (read <= 0) break
            total += read
            if (total > maxBytes) error("Decompressed entry exceeds safety budget")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun JsonElement.primitiveString(): String? =
        (this as? JsonPrimitive)?.content

    private fun JsonElement.primitiveInt(): Int? =
        (this as? JsonPrimitive)?.intOrNull ?: (this as? JsonPrimitive)?.content?.toIntOrNull()

    private fun JsonElement.primitiveFloat(): Float? =
        (this as? JsonPrimitive)?.floatOrNull ?: (this as? JsonPrimitive)?.content?.toFloatOrNull()

    private fun JsonObject.propEl(key: String): JsonElement? {
        val child = this[key] ?: return null
        return if (child is JsonObject && child.containsKey("v")) child["v"] else child
    }

    private fun JsonObject.propString(key: String, fallback: String): String =
        propEl(key)?.primitiveString() ?: fallback

    private fun JsonObject.propInt(key: String, fallback: Int): Int =
        propEl(key)?.primitiveInt() ?: fallback

    private fun JsonObject.propFloat(key: String, fallback: Float): Float =
        propEl(key)?.primitiveFloat() ?: fallback

    private fun JsonObject.propBool(key: String, fallback: Boolean): Boolean {
        val el = propEl(key) ?: return fallback
        val i = el.primitiveInt()
        if (i != null) return i != 0
        val s = el.primitiveString()
        return when {
            s.equals("true", true) -> true
            s.equals("false", true) -> false
            else -> fallback
        }
    }

    private fun JsonObject.propFloat2(key: String, defX: Float, defY: Float): Pair<Float, Float> {
        val raw = propString(key, "$defX $defY").trim().split(' ')
        val x = raw.getOrNull(0)?.toFloatOrNull() ?: defX
        val y = raw.getOrNull(1)?.toFloatOrNull() ?: defY
        return x to y
    }
}
