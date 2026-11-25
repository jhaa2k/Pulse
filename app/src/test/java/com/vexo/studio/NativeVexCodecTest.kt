package com.vexo.studio

import com.vexo.studio.data.NativeVexCodec
import com.vexo.studio.model.*
import org.junit.Assert.*
import org.junit.Test

class NativeVexCodecTest {
    @Test fun allBuiltInProjectsRoundTripExactly() {
        VexoTemplates.allTemplates().forEach { project ->
            assertEquals(project, NativeVexCodec.decode(NativeVexCodec.encode(project)))
        }
    }

    @Test fun fullLayerConfigsAndIdsSurvive() {
        val layers = listOf(
            LayerNode(LayerType.BARS, LayerCommon(name="bars"), LayerConfig.Bars(segment2=BarsSegmentType.LINE, segment2Width=0.27f)),
            LayerNode(LayerType.IMAGE, LayerCommon(name="image", locked=true), LayerConfig.Image(maskMode=MaskMode.INVERTED_TRANSPARENCY, flipU=true, customUri="content://test/img")),
            LayerNode(LayerType.TEXT, LayerCommon(), LayerConfig.Text(bold=false, italic=true, letterSpacing=0.13f)),
            LayerNode(LayerType.SHAPE, LayerCommon(), LayerConfig.Shape(cornerRadius=0.23f)),
            LayerNode(LayerType.PARTICLES, LayerCommon(), LayerConfig.Particles(seed=88, lifetimeSec=9f)),
            LayerNode(LayerType.BLUR_EFFECT, LayerCommon(), LayerConfig.BlurEffect(layer2Scale=2.3f)),
            LayerNode(LayerType.MOTION_BLUR_EFFECT, LayerCommon(), LayerConfig.MotionBlurEffect(relativeMotionMode=false)),
            LayerNode(LayerType.MIRROR_EFFECT, LayerCommon(), LayerConfig.MirrorEffect(repeatCount=4)),
            LayerNode(LayerType.RGB_SPLIT_EFFECT, LayerCommon(), LayerConfig.RgbSplitEffect(splitAmountY=0.02f))
        )
        val project = VexoProject(name="Native", compositions=listOf(CompositionNode(index=0,layers=layers)),
            audioProviders=listOf(AudioProviderConfig(fftSize=4096, hopSize=512, channelMode=ChannelMode.RIGHT)),
            exportSettings=ExportSettings(aspectRatio=AspectRatioPreset.RATIO_9_16, audioBitrateKbps=256, fps=60),
            audioUri="content://test/audio")
        assertEquals(project, NativeVexCodec.decode(NativeVexCodec.encode(project)))
    }

    @Test(expected=IllegalArgumentException::class) fun rejectsMissingFinalComposition() {
        NativeVexCodec.decode(NativeVexCodec.encode(VexoProject(compositions=listOf(CompositionNode(index=2)))))
    }
}
