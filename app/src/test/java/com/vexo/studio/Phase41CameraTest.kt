package com.vexo.studio
import com.vexo.studio.engine.scene.*
import com.vexo.studio.engine.audio.AudioProviderSnapshot
import com.vexo.studio.model.ProjectCamera
import com.vexo.studio.model.SpectrumReactiveSource
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class Phase41CameraTest{
    private val audio=AudioProviderSnapshot(floatArrayOf(.5f),floatArrayOf(0f),.8f,1f,.6f,.2f,.1f,.3f,.7f)
    @Test fun disabledCameraDoesNotChangeProject(){
        assertEquals(CameraPose(1f,0f,0f,0f),CinematicCamera.evaluate(ProjectCamera(),audio,1234,720,1280))
    }
    @Test fun reactsToSelectedSignalWithoutClockDrift(){
        val c=ProjectCamera(enabled=true,source=SpectrumReactiveSource.BASS,pulseZoom=.5f,shake=0f,rollDeg=0f,autoOverscan=false)
        val a=CinematicCamera.evaluate(c,audio,500,1920,1080)
        assertEquals(1.3f,a.zoom,.001f)
        assertEquals(a,CinematicCamera.evaluate(c,audio,500,1920,1080))
        assertEquals(1f,CinematicCamera.evaluate(c,null,500,1920,1080).zoom,.001f)
    }
    @Test fun overscanCoversCanvasInBothOrientations(){
        val c=ProjectCamera(enabled=true,baseZoom=1f,pulseZoom=0f,shake=.1f,rollDeg=20f,drift=.08f)
        for((w,h) in listOf(720 to 1280,1280 to 720,1080 to 1080)){
            for(t in 0L..2000L step 40L){
                val p=CinematicCamera.evaluate(c,audio,t,w,h)
                val angle=p.rotationDeg*PI/180.0
                for(x in listOf(-w*.5,w*.5))for(y in listOf(-h*.5,h*.5)){
                    val dx=x-p.offsetX;val dy=y-p.offsetY
                    assertTrue(abs(cos(angle)*dx+sin(angle)*dy)/p.zoom<=w*.5+.001)
                    assertTrue(abs(-sin(angle)*dx+cos(angle)*dy)/p.zoom<=h*.5+.001)
                }
            }
        }
    }
}
