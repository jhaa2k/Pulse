package com.vexo.studio
import com.vexo.studio.engine.scene.CreativeGeometry
import com.vexo.studio.model.*
import org.junit.Assert.*
import org.junit.Test
class CreativeGeometryTest{
 @Test fun spiralStartsInsideAndEndsOutside(){
   val a=CreativeGeometry.path(CreativeSpectrumPath.SPIRAL,0f,2f,.3f,.3f,0f,0f)
   val b=CreativeGeometry.path(CreativeSpectrumPath.SPIRAL,1f,2f,.3f,.3f,0f,0f)
   assertEquals(.036f,a.x,.001f);assertEquals(.3f,b.x,.001f)
 }
 @Test fun emitterPointAndRing(){
   val p=CreativeGeometry.emitter(ParticleEmitter.POINT,.1f,.9f,.4f,.6f,1f,1f)
   assertEquals(.4f,p.x,.001f);assertEquals(.6f,p.y,.001f)
   val r=CreativeGeometry.emitter(ParticleEmitter.RING,0f,.5f,.5f,.5f,1f,1f)
   assertEquals(1f,r.x,.001f);assertEquals(.5f,r.y,.001f)
 }
 @Test fun animationTiming(){
   assertEquals(0f,CreativeGeometry.animationProgress(500,1f,2f,false),.001f)
   assertEquals(.5f,CreativeGeometry.animationProgress(2000,1f,2f,false),.001f)
   assertEquals(.5f,CreativeGeometry.animationProgress(4000,1f,2f,true),.001f)
 }
}
