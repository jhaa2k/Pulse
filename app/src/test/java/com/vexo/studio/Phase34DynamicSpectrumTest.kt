package com.vexo.studio
import com.vexo.studio.engine.scene.*
import com.vexo.studio.engine.audio.AudioProviderSnapshot
import com.vexo.studio.model.*
import org.junit.Assert.*
import org.junit.Test
class Phase34DynamicSpectrumTest {
    private val audio=AudioProviderSnapshot(floatArrayOf(.4f,.6f),floatArrayOf(-.2f,.2f),.2f,.8f,.5f,.3f,.1f,.4f,.7f)
    @Test fun defaultDoesNotAlterLegacyConfig(){
        val c=LayerConfig.Bars()
        assertSame(c,DynamicSpectrum.evaluate(c,audio,1000))
    }
    @Test fun timestampAndAudioDriveStyleDeterministically(){
        val c=LayerConfig.Bars(gradientSpeed=-.5f,reactiveSource=SpectrumReactiveSource.BASS,reactiveStroke=8f,reactiveColorMix=1f,colorFromArgb=0xFF000000.toInt())
        val a=DynamicSpectrum.evaluate(c,audio,500)
        assertEquals(.75f,a.colorBlendOffset,.001f)
        assertEquals(4f,a.barStrokeWidth,.001f)
        assertEquals(0xFF7F7F7F.toInt(),a.colorFromArgb)
        assertEquals(a,DynamicSpectrum.evaluate(c,audio,500))
        assertTrue(a.gradientCycle)
        assertEquals(0f,DynamicSpectrum.evaluate(c,null,500).barStrokeWidth,.001f)
    }
    @Test fun customPeakStylesEmitDifferentFiniteGeometry(){
        fun mesh(style:SpectrumPeakStyle):List<Float>{
            val points=mutableListOf<Float>()
            SpectrumStudioMesh.emit(LayerConfig.Bars(peakMarkerStyle=style,showPeaks=true),floatArrayOf(.2f,.3f),floatArrayOf(.6f,.7f),720,1280){x,y,_->points.add(x);points.add(y)}
            assertTrue(points.all{it.isFinite()})
            return points
        }
        assertNotEquals(mesh(SpectrumPeakStyle.LINE),mesh(SpectrumPeakStyle.DOT))
        assertNotEquals(mesh(SpectrumPeakStyle.LINE),mesh(SpectrumPeakStyle.DIAMOND))
    }
}
