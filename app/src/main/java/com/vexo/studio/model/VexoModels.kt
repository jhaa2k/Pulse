package com.vexo.studio.model

import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
enum class NavDestination(val label: String) {
    STUDIO("Studio"),
    PROJECTS("Projects"),
    AUDIO("Audio"),
    TEMPLATES("Templates"),
    EXPORT("Export"),
    SETTINGS("Settings")
}

@Serializable
enum class StudioTab(val label: String) {
    LAYERS("Layers"),
    PROPERTIES("Properties"),
    ASSETS("Assets"),
    PRESETS("Presets")
}

@Serializable
enum class AspectRatioPreset(
    val label: String,
    val widthRatio: Int,
    val heightRatio: Int
) {
    RATIO_16_9("16:9", 16, 9),
    RATIO_9_16("9:16", 9, 16),
    RATIO_1_1("1:1", 1, 1),
    RATIO_4_5("4:5", 4, 5)
}

@Serializable
enum class VideoResolution(
    val label: String,
    val shortSide: Int
) {
    SD_480("480p", 480),
    HD_720("720p", 720),
    FHD_1080("1080p", 1080),
    QHD_1440("1440p", 1440),
    UHD_4K("4K", 2160)
}

@Serializable
enum class BlendMode(val aveeName: String, val label: String) {
    ALPHA("Alpha", "Alpha"),
    PRE_MUL_ALPHA("PreMulAlpha", "PreMul Alpha"),
    SCREEN("Screen", "Screen"),
    ADD("Add", "Add"),
    ADD_ALPHA("AddAlpha", "Add Alpha"),
    MULTIPLY("Multiply", "Multiply"),
    OVERWRITE("Overwrite", "Overwrite");

    companion object {
        fun fromAvee(raw: String?): BlendMode =
            entries.firstOrNull { it.aveeName.equals(raw, ignoreCase = true) } ?: PRE_MUL_ALPHA
    }
}

@Serializable
enum class AnchorMode(val aveeName: String) {
    UNSET("unset"),
    START("start"),
    CENTER("center"),
    END("end");

    companion object {
        fun fromAvee(raw: String?): AnchorMode =
            entries.firstOrNull { it.aveeName.equals(raw, ignoreCase = true) } ?: UNSET
    }
}

@Serializable
enum class SampleProviderType(val aveeName: String) {
    SPECTRUM("Spectrum"),
    SPECTRUM2("Spectrum2"),
    WAVEFORM("Waveform");

    companion object {
        fun fromAvee(raw: String?): SampleProviderType =
            entries.firstOrNull { it.aveeName.equals(raw, ignoreCase = true) } ?: SPECTRUM
    }
}

@Serializable
enum class WindowType(val label: String) {
    HANN("Hann"),
    HAMMING("Hamming"),
    RECTANGULAR("Rectangular")
}

@Serializable
enum class ChannelMode(val label: String) {
    MIX("Stereo Mix"),
    LEFT("Left"),
    RIGHT("Right")
}

@Serializable
enum class MeasureWhat(val aveeName: String, val label: String) {
    NOTHING("Nothing", "Nothing"),
    BEAT("Beat", "Beat"),
    TOTAL_TIME("TotalTime", "Total Time"),
    TOTAL_TIME_BACKWARD("TotalTimeBackward", "Time Backward"),
    TOTAL_TIME_WHEN_PLAYING("TotalTimeWhenPlaying", "Time Playing"),
    TOTAL_TIME_AND_BEAT("TotalTimeAndBeat", "Time + Beat"),
    TRACK_POSITION("TrackPosition", "Track Pos"),
    BEAT_RANDOM_SHAKE("BeatRandomShake", "Beat Random Shake"),
    BEAT_CAM_SHAKE_MORE("BeatCamShakeMore", "Cam Shake +"),
    BEAT_CAM_SHAKE_LESS("BeatCamShakeLess", "Cam Shake -"),
    BEAT_CAM_SHAKE_ROT_MORE("BeatCamShakeRotMore", "Cam Rot +"),
    BEAT_CAM_SHAKE_ROT_LESS("BeatCamShakeRotLess", "Cam Rot -"),
    CONSTANT_SHAKE("ConstantShake", "Constant Shake"),
    CONSTANT_SHAKE_MORE("ConstantShakeMore", "Constant +"),
    CONSTANT_SHAKE_ROT_MORE("ConstantShakeRotMore", "Const Rot +"),
    CONSTANT_SHAKE_ROT_LESS("ConstantShakeRotLess", "Const Rot -"),
    BEAT_TRIGGER_ANIM("BeatTriggerAnim", "Beat Trigger"),
    BASS("Bass", "Bass (20-250Hz)"),
    MID("Mid", "Mid (250-4kHz)"),
    TREBLE("Treble", "Treble (4k-16kHz)"),
    RMS("RMS", "RMS Level"),
    PEAK("Peak", "Peak Level"),
    KEYFRAME("Keyframe", "Keyframe");

    companion object {
        fun fromAvee(raw: String?): MeasureWhat =
            entries.firstOrNull { it.aveeName.equals(raw, ignoreCase = true) } ?: if(raw.equals("TotalTimeAndBack",ignoreCase=true))TOTAL_TIME_BACKWARD else NOTHING
    }
}

@Serializable
data class MeasureConfig(
    val measureWhat: MeasureWhat = MeasureWhat.NOTHING,
    val amountA: Float = 0f,
    val amountB: Float = 0f,
    val providerIndex: Int = 1,
    val smoothing: Float = 0.25f,
    val seed: Int = 1337,
    val triggerThreshold: Float = 0f,
    val legacyParameterSemantics: Boolean = false
)

@Serializable
enum class BarsShapePath(val aveeName: String, val label: String) {
    HORIZONTAL_LINE("HorizontalLine", "Horizontal Line"),
    VERTICAL_LINE("VerticalLine", "Vertical Line"),
    CIRCLE("Circle", "Circle"),
    SIDED_POLYGON("SidedPolygon", "Polygon"),
    LETTER("Letter", "Wave Letter");

    companion object {
        fun fromAvee(raw: String?): BarsShapePath =
            entries.firstOrNull { it.aveeName.equals(raw, ignoreCase = true) } ?: HORIZONTAL_LINE
    }
}

@Serializable
enum class BarsSegmentType(val aveeName: String, val label: String) {
    NONE("None", "None"),
    BARS("Bars", "Bars"),
    LINE("Line", "Line"),
    SHARP_BARS("SharpBars", "Sharp Bars"),
    ROUND_BARS("RoundBars", "Round Bars");

    companion object {
        fun fromAvee(raw: String?): BarsSegmentType =
            entries.firstOrNull { it.aveeName.equals(raw, ignoreCase = true) } ?: BARS
    }
}

@Serializable
enum class ImageShape(val aveeName: String, val label: String) {
    NONE("None", "Rectangle"),
    CIRCLE("Circle", "Circle"),
    SIDED_POLYGON("SidedPolygon", "Polygon");

    companion object {
        fun fromAvee(raw: String?): ImageShape =
            entries.firstOrNull { it.aveeName.equals(raw, ignoreCase = true) } ?: NONE
    }
}

@Serializable
enum class MaskMode(val aveeName: String, val label: String) {
    TRANSPARENCY("Transparency", "Transparency"),
    TRANSPARENCY_AND_BLACKS("TransparencyAndBlacks", "Alpha + Blacks"),
    TRANSPARENCY_AND_WHITES("TransparencyAndWhites", "Alpha + Whites"),
    INVERTED_TRANSPARENCY("InvertedTransparency", "Inverted Alpha");

    companion object {
        fun fromAvee(raw: String?): MaskMode =
            entries.firstOrNull { it.aveeName.equals(raw, ignoreCase = true) } ?: TRANSPARENCY
    }
}

@Serializable
enum class ImageSourceType(val label: String) {
    ALBUM_ART("Album Art"),
    IMPORTED("Imported Image"),
    INTERNAL("Built-in Asset"),
    COMPOSITION("Composition"),
    SOLID_COLOR("Generated Color")
}

@Serializable
enum class ShapeKind(val label: String) {
    RECTANGLE("Rectangle"),
    ELLIPSE("Ellipse"),
    POLYGON("Polygon"),
    STAR("Star"),
    LINE("Line")
}

@Serializable
enum class SpawnAreaType(val aveeName: String, val label: String) {
    HORIZONTAL_LINE("HorizontalLine", "Horizontal Line"),
    RECTANGLE("Rectangle", "Rectangle");

    companion object {
        fun fromAvee(raw: String?): SpawnAreaType =
            entries.firstOrNull { it.aveeName.equals(raw, ignoreCase = true) } ?: RECTANGLE
    }
}

@Serializable
enum class ForceFieldType(val aveeName: String, val label: String) {
    NONE("None", "None"),
    VORTEX("Vortex", "Vortex");

    companion object {
        fun fromAvee(raw: String?): ForceFieldType =
            entries.firstOrNull { it.aveeName.equals(raw, ignoreCase = true) } ?: NONE
    }
}

@Serializable
enum class MirrorMode(val aveeName: String, val label: String) {
    HORIZONTAL("Horizontal", "Horizontal"),
    VERTICAL("Vertical", "Vertical"),
    HORIZONTAL_AND_VERTICAL("HorizontalAndVertical", "Both");

    companion object {
        fun fromAvee(raw: String?): MirrorMode =
            entries.firstOrNull { it.aveeName.equals(raw, ignoreCase = true) } ?: HORIZONTAL
    }
}

@Serializable
enum class MotionSource(val aveeName: String, val label: String) {
    EFFECT_TRANSFORM("EffectTransform", "Effect Transform"),
    MANUAL("Manual", "Manual");

    companion object {
        fun fromAvee(raw: String?): MotionSource =
            entries.firstOrNull { it.aveeName.equals(raw, ignoreCase = true) } ?: EFFECT_TRANSFORM
    }
}

@Serializable
enum class InterpolationType { HOLD, LINEAR, EASE_IN_OUT, EASE_IN, EASE_OUT, SINE_IN_OUT, BACK_OUT, BOUNCE_OUT }

@Serializable
data class Keyframe(
    val timeMs: Long,
    val value: Float,
    val interpolation: InterpolationType = InterpolationType.LINEAR
)

@Serializable
data class KeyframeTrack(
    val targetLayerId: String,
    val propertyKey: String,
    val keyframes: List<Keyframe> = emptyList()
)

@Serializable
data class AudioProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val index: Int = 1,
    val tag: String = "Audio Provider",
    val enabled: Boolean = true,
    val sampleProvider: SampleProviderType = SampleProviderType.SPECTRUM,
    val fftSize: Int = 1024,
    val hopSize: Int = 256,
    val window: WindowType = WindowType.HANN,
    val channelMode: ChannelMode = ChannelMode.MIX,
    val sampleOutCount: Int = 96,
    val mirrorSamples: Boolean = false,
    val repeatSamples: Int = 1,
    val startAndEndGap: Int = 0,
    val smooth: Float = 0.75f,
    val preSmooth: Float = 0.5f,
    val filterRadius: Int = 1,
    val filterStrength: Float = 0.62f,
    val aWeight: Float = 0.2f,
    val outputMultiplier: Float = 4.0f,
    val lowerHz: Float = 32f,
    val higherHz: Float = 16000f,
    val hzLinearFactor: Float = 0.15f,
    val freqShift: Float = 0f,
    val attackMs: Float = 8f,
    val releaseMs: Float = 75f,
    val peakHoldMs: Float = 180f,
    val peakDecay: Float = 0.85f,
    val beatSmooth: Float = 0.4f,
    val beatRangeBarFirst: Float = 0f,
    val beatRangeBarLast: Float = 0.2f,
    val beatRangeValueLower: Float = 0.7f,
    val beatRangeValueHigher: Float = 35f,
    val beatSensitivity: Float = 1.2f,
    val adaptiveBeat: Boolean = false,
    val transientBeat: Boolean = false,
    val enhancedBands: Boolean = false,
    val beatCooldownMs: Int = 100,
    val beatPulseDurationMs: Int = 180,
    val bandResponseGain: Float = 1f,
    val extraFields: Map<String, String> = emptyMap()
)

@Serializable
enum class LayerType(val aveeObjType: String, val label: String) {
    BARS("Bars", "Bars / Spectrum"),
    IMAGE("Image", "Image"),
    TEXT("Text", "Text"),
    SHAPE("Shape", "Shape"),
    PARTICLES("Particles", "Particles"),
    BLUR_EFFECT("BlurEffect", "Blur Effect"),
    MOTION_BLUR_EFFECT("MotionBlurEffect", "Motion Blur"),
    MIRROR_EFFECT("MirrorEffect", "Mirror Effect"),
    RGB_SPLIT_EFFECT("RgbSplitEffect", "RGB Split")
}

@Serializable
data class LayerCommon(
    val id: String = UUID.randomUUID().toString(),
    val aveeId: Int = 0,
    val name: String = "Layer",
    val visible: Boolean = true,
    val locked: Boolean = false,
    val blendMode: BlendMode = BlendMode.PRE_MUL_ALPHA,
    val opacity: Float = 1f,
    val posX: Float = 0.5f,
    val posY: Float = 0.5f,
    val anchorX: AnchorMode = AnchorMode.UNSET,
    val anchorY: AnchorMode = AnchorMode.UNSET,
    val alignmentX: Float = 0.5f,
    val alignmentY: Float = 0.5f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val scaleIsUniform: Boolean = false,
    val rotationDeg: Float = 0f,
    val measurePos: MeasureConfig = MeasureConfig(),
    val measureScale: MeasureConfig = MeasureConfig(),
    val measureRot: MeasureConfig = MeasureConfig(),
    val measureOpacity: MeasureConfig = MeasureConfig(),
    val groupName: String = "",
    val timing: LayerTiming = LayerTiming(),
    val extraFields: Map<String, String> = emptyMap()
)

@Serializable
sealed interface LayerConfig {
    @Serializable
    data class Bars(
        val audioProviderIndex: Int = 1,
        val shapePath: BarsShapePath = BarsShapePath.HORIZONTAL_LINE,
        val segment1: BarsSegmentType = BarsSegmentType.BARS,
        val segment2: BarsSegmentType = BarsSegmentType.NONE,
        val segment2Width: Float = 0.72f,
        val segment2HeightMultiplier: Float = 1f,
        val segment2FixedHeight: Float = 0f,
        val segment2Mirror: Boolean = false,
        val segment2ColorFrom: Int = -1,
        val segment2ColorTo: Int = -1,
        val pathGap: Float = 0f,
        val barWidth: Float = 0.72f,
        val barHeightMultiplier: Float = 1.0f,
        val fixedHeight: Float = 0f,
        val mirror: Boolean = true,
        val colorFromArgb: Int = 0xFFFFFFFF.toInt(),
        val colorToArgb: Int = 0xFFC6F36A.toInt(),
        val colorBlendOffset: Float = 0f,
        val colorBlendMeasure: MeasureConfig = MeasureConfig(),
        val heightScale: Float = 2.4f,
        val minHeightScale: Float = 0.012f,
        val maxHeightScale: Float = 1.0f,
        val flipInput: Boolean = false,
        val softness: Int = 2,
        val reactionDelay: Int = 0,
        val reactionAccumulatedDelay: Int = 0,
        val circleRadius: Float = 0.28f,
        val polygonSides: Int = 6,
        val pathAngleDeg: Float = 0f,
        val showPeaks: Boolean = true,
        val alternatingDirection: Boolean = false,
        val barCount: Int = 0, // 0 follows provider output count
        val barSpacing: Float = 0f,
        val roundness: Float = 1f,
        val creativePath: CreativeSpectrumPath = CreativeSpectrumPath.LEGACY,
        val creativeStyle: CreativeSpectrumStyle = CreativeSpectrumStyle.BARS,
        val pathTurns: Float = 2f,
        val pathSpread: Float = .3f,
        val stepCount: Int = 8,
        val secondaryCreativeStyle: CreativeSpectrumStyle = CreativeSpectrumStyle.BARS,
        val creativeStrokeWidth: Float = 2f,
        val creativeFillAlpha: Float = .65f,
        val creativeTipSize: Float = 1f,
        val sampleOffset: Float = 0f,
        val sampleRepeats: Int = 1,
        val amplitudeCurve: Float = 1f,
        val baselineOffset: Float = 0f,
        val signedWaveform: Boolean = false,
        val curveSubdivisions: Int = 1,
        val gradientRepeats: Int = 1,
        val customPathEnabled: Boolean = false,
        val customPathClosed: Boolean = false,
        val customPathPoints: List<SpectrumPathPoint> = emptyList(),
        val pathStretchX: Float = 1f,
        val pathStretchY: Float = 1f,
        val barStrokeWidth: Float = 0f,
        val barStrokeColorArgb: Int = 0xFFFFFFFF.toInt(),
        val barFillOpacity: Float = 1f,
        val barTipWidth: Float = 1f,
        val barGradientMode: SpectrumGradientMode = SpectrumGradientMode.PATH,
        val segment2StrokeWidth: Float = 0f,
        val segment2StrokeColorArgb: Int = 0xFFFFFFFF.toInt(),
        val segment2FillOpacity: Float = 1f,
        val waveRibbonWidth: Float = 1f,
        val waveLineGap: Float = 2.5f,
        val waveCurveTension: Float = .5f,
        val waveSmoothJoins: Boolean = true,
        val waveDashRatio: Float = 1f,
        val waveDashCount: Int = 12,
        val gradientSpeed: Float = 0f,
        val reactiveSource: SpectrumReactiveSource = SpectrumReactiveSource.BEAT,
        val reactiveStroke: Float = 0f,
        val reactiveColorMix: Float = 0f,
        val reactiveColorArgb: Int = 0xFFFFFFFF.toInt(),
        val peakMarkerStyle: SpectrumPeakStyle = SpectrumPeakStyle.LINE,
        val peakMarkerWidth: Float = 1f,
        val peakMarkerThickness: Float = 1f,
        val peakMarkerOffset: Float = 0f,
        val peakMarkerColorArgb: Int? = null,
        val gradientCycle: Boolean = false,
        val segment2AlternatingDirection: Boolean? = null,
        val barWidthAffectedByShape:Boolean?=null,
        val segment2WidthAffectedByShape:Boolean?=null
    ) : LayerConfig

    @Serializable
    data class Image(
        val sourceType: ImageSourceType = ImageSourceType.INTERNAL,
        val internalRes: String = "internalres:vignette80",
        val customUri: String? = null,
        val sourceCompositionIndex: Int = 0,
        val colorArgb: Int = 0xFFFFFFFF.toInt(),
        val colorToArgb: Int = 0xFFFFFFFF.toInt(),
        val measureColorBlend: MeasureConfig = MeasureConfig(),
        val shape: ImageShape = ImageShape.NONE,
        val keepAspectRatio: Boolean = true,
        val keepAspectRatioAndCropToFit: Boolean = false,
        val flipU: Boolean = false,
        val flipV: Boolean = false,
        val saturation: Float = 1f,
        val colorKeyEnabled: Boolean = false,
        val colorKeyArgb: Int = 0xFF00FF00.toInt(),
        val autoDetectColorKey: Boolean = true,
        val transparencyStrength: Float = 1f,
        val opacityStrength: Float = 1f,
        val blurEnabled: Boolean = false,
        val blurRadius: Int = 7,
        val blurDivider: Int = 5,
        val blurQuality: BlurQuality = BlurQuality.BALANCED,
        val blurredBorder: Boolean = false,
        val maskImage: String = "",
        val maskMode: MaskMode = MaskMode.TRANSPARENCY,
        val videoEnabled: Boolean = false,
        val videoLoop: Boolean = true,
        val videoSpeed: Float = 1f,
        val videoOffsetMs: Long = 0L,
        val videoSampleFps: Int = 12,
        val videoDecodeMode: VideoDecodeMode = VideoDecodeMode.AUTO,
        val videoTextureLimit: Int = 1280,
        val creative: CreativeImageConfig = CreativeImageConfig(),
        val animationSpeedMeasure:MeasureConfig=MeasureConfig(),
        val imageAnimationSpeed:Float=1f
    ) : LayerConfig

    @Serializable
    data class Text(
        val text: String = "{Title}\n{Artist}",
        val colorArgb: Int = 0xFFFFFFFF.toInt(),
        val fontSize: Int = 44,
        val bold: Boolean = true,
        val italic: Boolean = false,
        val fontAssetUri: String? = null,
        val alignment: Int = 0, // -1 left, 0 center, 1 right
        val letterSpacing: Float = 0.04f,
        val lineSpacing: Float = 1.2f,
        val strokeWidth: Float = 0f,
        val strokeColorArgb: Int = 0xFF000000.toInt(),
        val shadowRadius: Float = 4f,
        val animation: TextMotion = TextMotion.NONE,
        val animationDuration: Float = 2f,
        val animationDelay: Float = 0f,
        val animationLoop: Boolean = true,
        val animationAmount: Float = .2f,
        val animationAudio: MeasureConfig = MeasureConfig(measureWhat=MeasureWhat.BEAT,amountA=.3f,amountB=.3f),
        val shadowColorArgb: Int = 0xCC000000.toInt(),
        val fontFamily:String="Poppins",
        val fontWeight:Int=0,
        val legacyTypeface:String?=null
    ) : LayerConfig

    @Serializable
    data class Shape(
        val kind: ShapeKind = ShapeKind.POLYGON,
        val sides: Int = 6,
        val fillColorArgb: Int = 0x33FFFFFF,
        val strokeColorArgb: Int = 0xFFFFFFFF.toInt(),
        val strokeWidth: Float = 3f,
        val cornerRadius: Float = 0f,
        val gradientEndArgb: Int = 0x33C6F36A,
        val radius: Float = .32f,
        val starInnerRatio: Float = .48f,
        val spin: Float = 0f,
        val pulse: MeasureConfig = MeasureConfig(),
        val strokePulse: MeasureConfig = MeasureConfig()
    ) : LayerConfig

    @Serializable
    data class Particles(
        val countLimit: Int = 650,
        val spawnArea: SpawnAreaType = SpawnAreaType.RECTANGLE,
        val forceField: ForceFieldType = ForceFieldType.NONE,
        val vectorAngleDeg: Float = 270f,
        val vectorAngleZ: Float = 0f,
        val useRandomVectorInstead: Boolean = false,
        val vectorRelativeToCenter: Boolean = false,
        val speed: Float = 100f,
        val speedRandom: Float = 80f,
        val spawnTimeSec: Float = 0.015f,
        val lifetimeSec: Float = 6.0f,
        val gravityX: Float = 0f,
        val gravityY: Float = 0f,
        val colorArgb: Int = 0xFFFFFFFF.toInt(),
        val colorFromArgb: Int = 0xFFFFFFFF.toInt(),
        val colorToArgb: Int = 0x38C6F36A,
        val startSize: Float = 2f,
        val endSize: Float = 4f,
        val endSizeRandom: Float = 0f,
        val fadeInSize: Float = 3f,
        val fadeInAndOutTime: Float = 0.15f,
        val particleScale: Float = 0.65f,
        val perspectiveDepth: Float = 900f,
        val perspectiveFov: Float = 48.75f,
        val nearCameraFadeOutDistance: Float = 75f,
        val velocityAngle: Boolean = true,
        val mirrorX: Boolean = false,
        val mirrorY: Boolean = false,
        val trailLength: Float = 0f,
        val sideSineWaveFreq: Float = 0f,
        val sideSineWaveMag: Float = 0f,
        val measureOverallSpeed: MeasureConfig = MeasureConfig(
            measureWhat = MeasureWhat.BEAT,
            amountA = 0.4f,
            amountB = 0.4f
        ),
        val customImage: String = "internalres:particle_circle_blur4",
        val seed: Int = 42,
        val emitter: ParticleEmitter = ParticleEmitter.LEGACY,
        val emitterX: Float = .5f,
        val emitterY: Float = .5f,
        val emitterWidth: Float = 1f,
        val emitterHeight: Float = 1f,
        val directionSpread: Float = 37f,
        val drag: Float = 0f,
        val burstCount: Int = 0,
        val burstThreshold: Float = .65f,
        val burstProvider: Int = 1,
        val burstSource: ParticleBurstSource = ParticleBurstSource.BEAT_ENERGY,
        val burstCooldownMs: Int = 80,
        val burstSpeedMultiplier: Float = 1f,
        val reactiveEmission: Float = 0f,
        val turbulence: Float = 0f,
        val centerForce: Float = 0f,
        val sideSineWaveFreqRandom: Float = 0f,
        val sideSineWaveMagRandom: Float = 0f,
        val sizeAspectRatio: Float = 1f
    ) : LayerConfig

    @Serializable
    data class BlurEffect(
        val sourceCompositionIndex: Int = 1,
        val blurRadius: Float = 2.0f,
        val blurMultiplier: Float = 3.5f,
        val layer1Scale: Float = 1.0f,
        val layer1ColorArgb: Int = 0xFFFFFFFF.toInt(),
        val layer2Scale: Float = 0f,
        val layer2ColorArgb: Int = 0xFFFFFFFF.toInt(),
        val layer3Scale: Float = 0f,
        val layer3ColorArgb: Int = 0xFFFFFFFF.toInt(),
        val colorArgb: Int = 0xFF000000.toInt(),
        val blendModeContent: BlendMode = BlendMode.PRE_MUL_ALPHA,
        val showUnblurredContent: Boolean = false,
        val showUnblurredContentUnder: Boolean = true,
        val maskImage: String = "internalres:transparent",
        val measureBlur: MeasureConfig = MeasureConfig(),
        val unblurredBlendMode: BlendMode? = null,
        val glowBlendMode: BlendMode? = null,
        val blurAngleDeg: Float = 0f,
        val blurCrossAxis: Float = 1f,
        val filterMode: BlurFilterMode = BlurFilterMode.GAUSSIAN,
        val radialCenterX: Float = .5f,
        val radialCenterY: Float = .5f,
        val radialStrength: Float = .12f,
        val bloomThreshold: Float = 0f,
        val bloomKnee: Float = .1f,
        val bloomIntensity: Float = 1f,
        val blurQuality: BlurQuality = BlurQuality.BALANCED,
        val layer1AxisRatio: Float = 1f,
        val layer2AxisRatio: Float = 1f,
        val layer3AxisRatio: Float = 1f
    ) : LayerConfig

    @Serializable
    data class MotionBlurEffect(
        val targetCompositionIndex: Int = 1,
        val blurAmountMultiplier: Float = 0.85f,
        val motionSource: MotionSource = MotionSource.EFFECT_TRANSFORM,
        val relativeMotionMode: Boolean = true,
        val manualMotionX: Float = 0.025f,
        val manualMotionY: Float = 0f,
        val trailPersistence: Float = 0.65f,
        val blurAmountMeasure: MeasureConfig = MeasureConfig(),
        val effectBlendMode: BlendMode? = null,
        val manualRotationDeg: Float = 0f,
        val manualZoom: Float = 0f,
        val includeRotationAndZoom: Boolean = false,
        val blendModeContent: BlendMode = BlendMode.ADD,
        val colorArgb: Int = 0xFFFFFFFF.toInt(),
        val showUnblurredContent: Boolean = false,
        val showUnblurredContentUnder: Boolean = false
    ) : LayerConfig

    @Serializable
    data class MirrorEffect(
        val targetCompositionIndex: Int = 1,
        val mirrorMode: MirrorMode = MirrorMode.HORIZONTAL,
        val flipMirror: Boolean = false,
        val colorArgb: Int = 0xFFFFFFFF.toInt(),
        val repeatCount: Int = 1
    ) : LayerConfig

    @Serializable
    data class RgbSplitEffect(
        val targetCompositionIndex: Int = 1,
        val splitMultiplier: Float = 3.0f,
        val splitAmountX: Float = 0.015f,
        val splitAmountY: Float = 0.008f,
        val splitMeasure: MeasureConfig = MeasureConfig(
            measureWhat = MeasureWhat.BEAT_RANDOM_SHAKE,
            amountA = 0.5f,
            amountB = 0.5f
        ),
        val splitColor0Argb: Int = 0xFFFF0000.toInt(),
        val splitColor1Argb: Int = 0xFF00FF00.toInt(),
        val splitColor2Argb: Int = 0xFF0000FF.toInt(),
        val aberrationMode: AberrationMode = AberrationMode.LEGACY,
        val aberrationMix: Float = 1f,
        val aberrationCenterX: Float = .5f,
        val aberrationCenterY: Float = .5f,
        val blendModeContent: BlendMode = BlendMode.ADD,
        val showUnblurredContent: Boolean = false,
        val showUnblurredContentUnder: Boolean = false,
        val splitMultiplierMeasure: MeasureConfig = MeasureConfig(),
        val effectBlendMode: BlendMode? = null
    ) : LayerConfig
}

@Serializable
data class LayerNode(
    val type: LayerType,
    val common: LayerCommon,
    val config: LayerConfig
)

@Serializable
data class CompositionNode(
    val id: String = UUID.randomUUID().toString(),
    val index: Int = 0, // 0 = Final Composition
    val name: String = if (index == 0) "Final Composition" else "Composition $index",
    val layers: List<LayerNode> = emptyList(),
    val aveeMetadata: Map<String, String> = emptyMap(),
    val preservedElements: Map<Int, String> = emptyMap(),
    val providerIndices: List<Int> = emptyList(),
    val providerElementPositions: Map<Int, Int> = emptyMap()
)

@Serializable
data class AudioTrackItem(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val title: String,
    val artist: String = "Unknown Artist",
    val album: String = "Unknown Album",
    val durationMs: Long = 0L,
    val artworkHash: String? = null
)

@Serializable
data class ProjectCamera(
    val enabled:Boolean=false,
    val providerIndex:Int=1,
    val source:SpectrumReactiveSource=SpectrumReactiveSource.BEAT,
    val baseZoom:Float=1f,
    val pulseZoom:Float=.12f,
    val shake:Float=.02f,
    val rollDeg:Float=2f,
    val drift:Float=0f,
    val frequency:Float=3f,
    val seed:Int=1337,
    val autoOverscan:Boolean=true
)

@Serializable
data class CanvasConfig(
    val aspectRatio: AspectRatioPreset = AspectRatioPreset.RATIO_16_9,
    val backgroundColorArgb: Int = 0xFF000000.toInt(),
    val showSafeAreaGuides: Boolean = false,
    val camera: ProjectCamera = ProjectCamera()
)

@Serializable
data class ExportSettings(
    val aspectRatio: AspectRatioPreset = AspectRatioPreset.RATIO_16_9,
    val resolution: VideoResolution = VideoResolution.FHD_1080,
    val fps: Int = 30,
    val videoBitrateMbps: Int = 12,
    val audioBitrateKbps: Int = 192,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L, // 0 = full track
    val useHevcIfSupported: Boolean = false,
    val smoothEdges: Boolean = true,
    val offscreenSmoothEdges: Boolean = true,
    val highResolutionTextures: Boolean = true,
    val customOutputUri: String? = null,
    val adaptiveVideoBitrate:Boolean=false
)

@Serializable
data class VexoProject(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Untitled Studio",
    val schemaVersion: Int = 2,
    val updatedAtMs: Long = System.currentTimeMillis(),
    val canvas: CanvasConfig = CanvasConfig(),
    val audioUri: String? = null,
    val audioProviders: List<AudioProviderConfig> = listOf(AudioProviderConfig()),
    val compositions: List<CompositionNode> = listOf(
        CompositionNode(index = 0, name = "Final Composition", layers = emptyList())
    ),
    val keyframes: List<KeyframeTrack> = emptyList(),
    val exportSettings: ExportSettings = ExportSettings(),
    val aveeRootMetadata: Map<String, String> = emptyMap()
)

@Serializable
enum class ThemePreset(val label: String) {
    MONOCHROME_DARK("Monochrome Dark"),
    LIME_STUDIO("Signal Lime"),
    AMBER_CONSOLE("Amber Console"),
    CLEAN_LIGHT("Studio Light"),
    CITRUS_LIGHT("Citrus Light"),
    CITRUS_DARK("Citrus Dark")
}

@Serializable
enum class PauseBehavior(val label: String) {
    DECAY("Decay to zero"),
    FREEZE("Freeze frame")
}

@Serializable
data class AppSettings(
    val themePreset: ThemePreset = ThemePreset.LIME_STUDIO,
    val customAccentArgb: Int = 0xFFC5F84A.toInt(),
    val defaultFftSize: Int = 1024,
    val defaultHopSize: Int = 256,
    val previewScale: Float = 1.0f,
    val adaptivePreview: Boolean = false,
    val showPreviewDiagnostics: Boolean = false,
    val pauseBehavior: PauseBehavior = PauseBehavior.DECAY,
    val showPropertyHelpHints: Boolean = true,
    val glassEnabled: Boolean = true,
    val glassOpacity: Float = 0.82f,
    val backdropBlurDp: Float = 24f,
    val cornerRadiusDp: Float = 12f,
    val controlScale: Float = 1f,
    val uiTextScale: Float = 1f,
    val previewFps: Int = 30,
    val previewShare: Float = 0.41f,
    val portraitOverlay: Boolean = false,
    val wallpaperUri: String = "",
    val wallpaperBlurDp: Float = 16f,
    val wallpaperDim: Float = .72f,
    val customAccentAllThemes: Boolean = false
)

@Serializable
data class PropertyDescriptor(
    val key: String,
    val label: String,
    val units: String,
    val defaultValue: Float,
    val min: Float,
    val max: Float,
    val step: Float,
    val help: String
)

object PropertyRegistry {
    val descriptors: Map<String, PropertyDescriptor> = listOf(
        PropertyDescriptor("opacity", "OPACITY", "%", 1f, 0f, 1f, 0.01f, "Layer alpha multiplier (0 to 1)."),
        PropertyDescriptor("posX", "POSITION X", "norm", 0.5f, -1f, 2f, 0.01f, "Normalized horizontal anchor position."),
        PropertyDescriptor("posY", "POSITION Y", "norm", 0.5f, -1f, 2f, 0.01f, "Normalized vertical anchor position."),
        PropertyDescriptor("scaleX", "SCALE X", "x", 1f, -2f, 4f, 0.01f, "Horizontal layer scale."),
        PropertyDescriptor("scaleY", "SCALE Y", "x", 1f, -2f, 4f, 0.01f, "Vertical layer scale."),
        PropertyDescriptor("rotationDeg", "ROTATION", "deg", 0f, -360f, 360f, 1f, "Layer rotation in degrees."),
        PropertyDescriptor("barWidth", "BAR WIDTH", "ratio", 0.72f, 0.05f, 1.5f, 0.01f, "Segment width relative to slot spacing."),
        PropertyDescriptor("heightScale", "HEIGHT GAIN", "x", 2.4f, -10f, 10f, 0.05f, "Vertical amplitude multiplier."),
        PropertyDescriptor("minHeightScale", "MIN HEIGHT", "norm", 0.012f, -0.05f, 0.25f, 0.002f, "Minimum baseline bar height."),
        PropertyDescriptor("maxHeightScale", "MAX HEIGHT", "norm", 1f, 0.1f, 2f, 0.01f, "Maximum bar height ceiling."),
        PropertyDescriptor("circleRadius", "CIRCLE RADIUS", "norm", 0.28f, 0.05f, 0.9f, 0.01f, "Radius for circular or polygon paths."),
        PropertyDescriptor("sampleOutCount", "BAR COUNT", "bars", 96f, 8f, 512f, 4f, "Number of resampled output spectrum bars."),
        PropertyDescriptor("outputMultiplier", "GAIN", "x", 4f, 0.1f, 32f, 0.1f, "Audio provider output amplitude multiplier."),
        PropertyDescriptor("releaseMs", "RELEASE", "ms", 75f, 0f, 2000f, 5f, "Time-based release decay independent of FPS."),
        PropertyDescriptor("attackMs", "ATTACK", "ms", 8f, 0f, 500f, 2f, "Time-based attack rise independent of FPS."),
        PropertyDescriptor("lowerHz", "LOWER HZ", "Hz", 32f, 10f, 500f, 1f, "Lowest mapped spectrum frequency."),
        PropertyDescriptor("higherHz", "HIGHER HZ", "Hz", 16000f, 300f, 22050f, 50f, "Highest mapped spectrum frequency (clamped to Nyquist)."),
        PropertyDescriptor("beatSensitivity", "BEAT SENS", "x", 1.2f, 0.1f, 4f, 0.05f, "Multiplier for beat energy detection."),
        PropertyDescriptor("blurRadius", "BLUR RADIUS", "", 2f, 0f, 24f, 0.1f, "Gaussian spread at a 720-pixel reference size."),
        PropertyDescriptor("blurMultiplier", "BLUR MULT", "x", 3.5f, 0.5f, 8f, 0.1f, "Bloom/blur intensity scale."),
        PropertyDescriptor("splitMultiplier", "SPLIT MULT", "x", 3f, 0f, 10f, 0.1f, "Chromatic aberration RGB offset multiplier."),
        PropertyDescriptor("countLimit", "PARTICLES", "count", 650f, 10f, 3000f, 10f, "Maximum active pooled particles."),
        PropertyDescriptor("speed", "SPEED", "px/s", 100f, -300f, 300f, 5f, "Base particle velocity."),
        PropertyDescriptor("fontSize", "FONT SIZE", "sp", 44f, 10f, 120f, 1f, "Rendered text glyph size.")
    ).associateBy { it.key }
}

object VexoTemplates {
    fun allTemplates(): List<VexoProject> = listOf(
        monochromeStudio(),
        circularPulse(),
        neonParticleRing(),
        rgbSplitMinimal(),
        styledSpectrum("Silver Wave", BarsShapePath.HORIZONTAL_LINE, 0xFFFFFFFF.toInt(), 0xFF8794A5.toInt(), AspectRatioPreset.RATIO_16_9),
        styledSpectrum("Crimson Orbit", BarsShapePath.CIRCLE, 0xFFFF345D.toInt(), 0xFFFFB28B.toInt(), AspectRatioPreset.RATIO_1_1),
        styledSpectrum("Electric Violet", BarsShapePath.CIRCLE, 0xFFB18AFF.toInt(), 0xFF5CD8FF.toInt(), AspectRatioPreset.RATIO_9_16),
        styledSpectrum("Golden Polygon", BarsShapePath.SIDED_POLYGON, 0xFFFFCE70.toInt(), 0xFFFFF0CC.toInt(), AspectRatioPreset.RATIO_1_1),
        styledSpectrum("Vertical Signal", BarsShapePath.VERTICAL_LINE, 0xFF7CFAD2.toInt(), 0xFFFFFFFF.toInt(), AspectRatioPreset.RATIO_9_16),
        styledSpectrum("Ocean Frequency", BarsShapePath.HORIZONTAL_LINE, 0xFF168BFF.toInt(), 0xFF5FF0FF.toInt(), AspectRatioPreset.RATIO_16_9),
        styledSpectrum("Polar Ring", BarsShapePath.CIRCLE, 0xFFFFFFFF.toInt(), 0xFF90B5FF.toInt(), AspectRatioPreset.RATIO_4_5),
        styledSpectrum("Rose Spectrum", BarsShapePath.HORIZONTAL_LINE, 0xFFFF7BA9.toInt(), 0xFFFFE1E9.toInt(), AspectRatioPreset.RATIO_9_16)
    )

    private fun styledSpectrum(name: String, path: BarsShapePath, from: Int, to: Int, ratio: AspectRatioPreset): VexoProject {
        val base = if(path == BarsShapePath.CIRCLE || path == BarsShapePath.SIDED_POLYGON) circularPulse() else monochromeStudio()
        return base.copy(name = name, canvas = base.canvas.copy(aspectRatio = ratio), exportSettings = base.exportSettings.copy(aspectRatio = ratio),
            compositions = base.compositions.map { comp -> comp.copy(layers = comp.layers.map { layer ->
                val cfg = layer.config
                if (cfg is LayerConfig.Bars) layer.copy(config = cfg.copy(shapePath = path, colorFromArgb = from, colorToArgb = to, segment2 = BarsSegmentType.NONE)) else layer
            }) })
    }

    fun monochromeStudio(): VexoProject {
        val comp1 = CompositionNode(
            index = 1,
            name = "Scene Layer Bus",
            layers = listOf(
                LayerNode(
                    type = LayerType.IMAGE,
                    common = LayerCommon(name = "Album Backdrop", opacity = 0.28f),
                    config = LayerConfig.Image(
                        sourceType = ImageSourceType.ALBUM_ART,
                        keepAspectRatioAndCropToFit = true,
                        saturation = 0.2f
                    )
                ),
                LayerNode(
                    type = LayerType.BARS,
                    common = LayerCommon(name = "Studio Bars", posX = 0.5f, posY = 0.52f),
                    config = LayerConfig.Bars(
                        shapePath = BarsShapePath.HORIZONTAL_LINE,
                        segment1 = BarsSegmentType.BARS,
                        barWidth = 0.72f,
                        mirror = true,
                        colorFromArgb = 0xFFFFFFFF.toInt(),
                        colorToArgb = 0xFFC6F36A.toInt(),
                        heightScale = 2.6f
                    )
                ),
                LayerNode(
                    type = LayerType.TEXT,
                    common = LayerCommon(name = "Track Title", posX = 0.5f, posY = 0.16f),
                    config = LayerConfig.Text(
                        text = "{Title}\n{Artist}",
                        fontSize = 40,
                        bold = true,
                        colorArgb = 0xFFFFFFFF.toInt()
                    )
                )
            )
        )
        val finalComp = CompositionNode(
            index = 0,
            name = "Final Composition",
            layers = listOf(
                LayerNode(
                    type = LayerType.IMAGE,
                    common = LayerCommon(name = "Master Bus"),
                    config = LayerConfig.Image(
                        sourceType = ImageSourceType.COMPOSITION,
                        sourceCompositionIndex = 1
                    )
                ),
                LayerNode(
                    type = LayerType.IMAGE,
                    common = LayerCommon(name = "Studio Vignette", opacity = 0.65f, blendMode = BlendMode.ALPHA),
                    config = LayerConfig.Image(
                        sourceType = ImageSourceType.INTERNAL,
                        internalRes = "internalres:vignette80"
                    )
                )
            )
        )
        return VexoProject(
            name = "Monochrome Studio",
            compositions = listOf(finalComp, comp1)
        )
    }

    fun circularPulse(): VexoProject {
        val comp1 = CompositionNode(
            index = 1,
            name = "Ring Bus",
            layers = listOf(
                LayerNode(
                    type = LayerType.PARTICLES,
                    common = LayerCommon(name = "Stardust", blendMode = BlendMode.ADD, opacity = 0.8f),
                    config = LayerConfig.Particles(countLimit = 450, speed = 80f)
                ),
                LayerNode(
                    type = LayerType.BARS,
                    common = LayerCommon(
                        name = "Radial Ring",
                        measureScale = MeasureConfig(MeasureWhat.BEAT, 0.14f, 0.14f)
                    ),
                    config = LayerConfig.Bars(
                        shapePath = BarsShapePath.CIRCLE,
                        segment1 = BarsSegmentType.ROUND_BARS,
                        circleRadius = 0.27f,
                        mirror = true,
                        colorFromArgb = 0xFFC6F36A.toInt(),
                        colorToArgb = 0xFFFFFFFF.toInt(),
                        heightScale = 2.0f
                    )
                ),
                LayerNode(
                    type = LayerType.IMAGE,
                    common = LayerCommon(
                        name = "Center Art",
                        scaleX = 0.38f,
                        scaleY = 0.38f,
                        measureScale = MeasureConfig(MeasureWhat.BEAT, 0.06f, 0.06f)
                    ),
                    config = LayerConfig.Image(
                        sourceType = ImageSourceType.ALBUM_ART,
                        shape = ImageShape.CIRCLE,
                        keepAspectRatioAndCropToFit = true
                    )
                ),
                LayerNode(
                    type = LayerType.TEXT,
                    common = LayerCommon(name = "Track Info", posY = 0.87f),
                    config = LayerConfig.Text(text = "{Artist} — {Title}", fontSize = 32)
                )
            )
        )
        val finalComp = CompositionNode(
            index = 0,
            name = "Final Composition",
            layers = listOf(
                LayerNode(
                    type = LayerType.BLUR_EFFECT,
                    common = LayerCommon(name = "Glow Bloom", blendMode = BlendMode.ADD),
                    config = LayerConfig.BlurEffect(
                        sourceCompositionIndex = 1,
                        blurRadius = 2.2f,
                        blurMultiplier = 3.2f,
                        showUnblurredContentUnder = true
                    )
                )
            )
        )
        return VexoProject(
            name = "Circular Pulse",
            canvas = CanvasConfig(aspectRatio = AspectRatioPreset.RATIO_1_1),
            compositions = listOf(finalComp, comp1)
        )
    }

    fun neonParticleRing(): VexoProject {
        val base = circularPulse()
        val comp1 = base.compositions[1].copy(
            layers = base.compositions[1].layers.map { layer ->
                if (layer.type == LayerType.BARS) {
                    layer.copy(
                        config = (layer.config as LayerConfig.Bars).copy(
                            shapePath = BarsShapePath.SIDED_POLYGON,
                            polygonSides = 6,
                            segment1 = BarsSegmentType.SHARP_BARS,
                            colorFromArgb = 0xFF38BDF8.toInt(),
                            colorToArgb = 0xFFC6F36A.toInt()
                        )
                    )
                } else if (layer.type == LayerType.PARTICLES) {
                    layer.copy(
                        config = (layer.config as LayerConfig.Particles).copy(
                            forceField = ForceFieldType.VORTEX,
                            countLimit = 700
                        )
                    )
                } else layer
            }
        )
        return base.copy(
            name = "Polygon Vortex",
            canvas = CanvasConfig(aspectRatio = AspectRatioPreset.RATIO_9_16),
            compositions = listOf(base.compositions[0], comp1)
        )
    }

    fun rgbSplitMinimal(): VexoProject {
        val comp1 = CompositionNode(
            index = 1,
            name = "Minimal Bus",
            layers = listOf(
                LayerNode(
                    type = LayerType.SHAPE,
                    common = LayerCommon(
                        name = "Pulse Frame",
                        scaleX = 0.7f,
                        scaleY = 0.7f,
                        measureRot = MeasureConfig(MeasureWhat.TOTAL_TIME, 8f, 8f)
                    ),
                    config = LayerConfig.Shape(
                        kind = ShapeKind.POLYGON,
                        sides = 4,
                        fillColorArgb = 0x00000000,
                        strokeColorArgb = 0x88FFFFFF.toInt(),
                        strokeWidth = 3f
                    )
                ),
                LayerNode(
                    type = LayerType.BARS,
                    common = LayerCommon(name = "Sharp Spectrum"),
                    config = LayerConfig.Bars(
                        shapePath = BarsShapePath.HORIZONTAL_LINE,
                        segment1 = BarsSegmentType.SHARP_BARS,
                        mirror = false,
                        colorFromArgb = 0xFFFFFFFF.toInt(),
                        colorToArgb = 0xFFFFFFFF.toInt(),
                        heightScale = 3.0f
                    )
                ),
                LayerNode(
                    type = LayerType.TEXT,
                    common = LayerCommon(name = "Timecode", posY = 0.84f),
                    config = LayerConfig.Text(text = "{Title} · {Time}", fontSize = 30)
                )
            )
        )
        val finalComp = CompositionNode(
            index = 0,
            name = "Final Composition",
            layers = listOf(
                LayerNode(
                    type = LayerType.RGB_SPLIT_EFFECT,
                    common = LayerCommon(name = "Beat RGB Split"),
                    config = LayerConfig.RgbSplitEffect(
                        targetCompositionIndex = 1,
                        splitMultiplier = 2.6f,
                        showUnblurredContentUnder = true
                    )
                ),
                LayerNode(
                    type = LayerType.MIRROR_EFFECT,
                    common = LayerCommon(name = "Subtle Mirror", visible = false),
                    config = LayerConfig.MirrorEffect(targetCompositionIndex = 1)
                )
            )
        )
        return VexoProject(
            name = "RGB Split Minimal",
            compositions = listOf(finalComp, comp1)
        )
    }
}

@Serializable
enum class CreativeGradient { NONE, LINEAR, RADIAL, CONIC }

@Serializable
enum class CreativeDistortion { NONE, WAVE, KALEIDOSCOPE, PIXELATE, ZOOM_PULSE }

@Serializable
data class CreativeImageConfig(
    val gradient: CreativeGradient = CreativeGradient.NONE,
    val gradientFrom: Int = 0xFF121229.toInt(),
    val gradientTo: Int = 0xFFBFFF00.toInt(),
    val gradientAngle: Float = 0f,
    val gradientCenterX: Float = .5f,
    val gradientCenterY: Float = .5f,
    val gradientScale: Float = 1f,
    val gradientMix: Float = 1f,
    val distortion: CreativeDistortion = CreativeDistortion.NONE,
    val amount: Float = .15f,
    val frequency: Float = 6f,
    val speed: Float = 1f,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val temperature: Float = 0f,
    val hue: Float = 0f,
    val vignette: Float = 0f,
    val scanlines: Float = 0f,
    val grain: Float = 0f,
    val audio: MeasureConfig = MeasureConfig(),
    val pattern: ProceduralPattern = ProceduralPattern.NONE,
    val patternDensity: Float = 8f,
    val patternIntensity: Float = .5f,
    val patternSpeed: Float = .25f,
    val aperture: Aperture = Aperture.NONE,
    val apertureSize: Float = .75f,
    val apertureFeather: Float = .05f,
    val apertureInvert: Boolean = false,
    val apertureX: Float = .5f,
    val apertureY: Float = .5f
)

@Serializable data class SpectrumPathPoint(val x: Float, val y: Float)
@Serializable enum class CreativeSpectrumPath { LEGACY, ARC, SPIRAL, HEART, SINE, CIRCLE, OVAL, TRIANGLE, SQUARE, HEXAGON, STAR, FLOWER, FIGURE_EIGHT, BUTTERFLY, LISSAJOUS, DIAGONAL, ZIGZAG }
@Serializable enum class BlurQuality { FAST, BALANCED, HIGH }
@Serializable enum class BlurFilterMode { GAUSSIAN, RADIAL_ZOOM, RADIAL_SPIN }
@Serializable enum class AberrationMode { LEGACY, CHANNEL_SPLIT, RADIAL }
@Serializable enum class SpectrumReactiveSource { BEAT, BASS, MID, TREBLE, RMS, PEAK }
@Serializable enum class SpectrumPeakStyle { LINE, DOT, DIAMOND }
@Serializable enum class SpectrumGradientMode { PATH, HEIGHT, AMPLITUDE, ALTERNATING }
@Serializable enum class CreativeSpectrumStyle { BARS, DOTS, STEPS, LINE, FILL, OUTLINE, TRIANGLE, DIAMOND, CAPSULE, DASH, DOUBLE_LINE, RIBBON, ZIGZAG, LOLLIPOP, CROSS, CHEVRON, TAPERED, CAPPED, STACK_DOTS, HOLLOW_DOTS, WAVE_POINTS, SAWTOOTH, STRIPES, FAN, TICKS, PEAKS }
@Serializable enum class ParticleBurstSource { BEAT_ENERGY, BEAT_PULSE, BASS, MID, TREBLE, RMS, PEAK }
@Serializable enum class ParticleEmitter { LEGACY, POINT, LINE, BOX, RING, DISC }
@Serializable enum class TextMotion { NONE, FADE, SLIDE, PULSE, REVEAL, BEAT_SCALE }

@Serializable enum class ProceduralPattern { NONE, GRID, CHECKER, RINGS, PLASMA, STARFIELD }
@Serializable enum class Aperture { NONE, CIRCLE, RECTANGLE, DIAMOND, LINEAR }
@Serializable data class LayerTiming(
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L
)

@Serializable enum class VideoDecodeMode { AUTO, FRAME_SAMPLING }
