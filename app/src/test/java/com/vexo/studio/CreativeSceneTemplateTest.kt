package com.vexo.studio
import com.vexo.studio.data.NativeVexCodec
import com.vexo.studio.model.CreativeSceneTemplates
import org.junit.Assert.*
import org.junit.Test
class CreativeSceneTemplateTest{
 @Test fun templatesRoundTrip(){
    val scenes=CreativeSceneTemplates.all()
    assertEquals(3,scenes.size)
    scenes.forEach{assertEquals(it,NativeVexCodec.decode(NativeVexCodec.encode(it)))}
 }
}
