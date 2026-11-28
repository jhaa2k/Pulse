package com.vexo.studio
import com.vexo.studio.engine.audio.AudioProviderEngine
import com.vexo.studio.model.*
import org.junit.Assert.*
import org.junit.Test
class Phase16AudioTest {
 @Test fun hopCachesAnalysisUntilEnoughNewSamples(){
  val e=AudioProviderEngine(AudioProviderConfig(fftSize=256,hopSize=128))
  fun run(v:Float,pos:Long)=e.process(FloatArray(256){v},44100,16f,1,true,false,sampleEndFrame=pos)
  assertEquals(.5f,run(.5f,256).rms,.001f)
  assertEquals(.5f,run(.1f,300).rms,.001f)
  assertEquals(.1f,run(.1f,384).rms,.001f)
 }
 @Test fun pauseDecaysWaveform(){
  val e=AudioProviderEngine(AudioProviderConfig(fftSize=256,releaseMs=10f))
  e.process(FloatArray(256){.5f},44100,16f,1,true,false)
  assertTrue(e.process(null,44100,100f,1,false,false).waveform.all{kotlin.math.abs(it)<.001f})
 }
 @Test fun invertedBeatThresholdStaysFinite(){
  val e=AudioProviderEngine(AudioProviderConfig(beatRangeValueLower=50f,beatRangeValueHigher=0f))
  assertTrue(e.process(FloatArray(1024){.5f},44100,16f,1,true,false).beat.isFinite())
 }
}
