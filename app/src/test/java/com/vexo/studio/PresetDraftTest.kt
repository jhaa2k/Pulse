package com.vexo.studio
import com.vexo.studio.model.*
import com.vexo.studio.data.PresetDraft
import org.junit.Assert.*
import org.junit.Test
class PresetDraftTest{
 @Test fun preservesSelectedCanvasAndCurrentAudio(){
    val current=VexoProject(canvas=CanvasConfig(aspectRatio=AspectRatioPreset.RATIO_9_16),audioUri="content://old",exportSettings=ExportSettings(trimStartMs=200,trimEndMs=2000,customOutputUri="content://destination"))
    val template=VexoProject(name="Example",canvas=CanvasConfig(aspectRatio=AspectRatioPreset.RATIO_16_9),audioUri="content://template")
    val draft=PresetDraft.create(current,template,"content://current",id="draft",now=1L)
    assertEquals(AspectRatioPreset.RATIO_9_16,draft.canvas.aspectRatio)
    assertEquals(draft.canvas.aspectRatio,draft.exportSettings.aspectRatio)
    assertEquals("content://current",draft.audioUri)
    assertEquals("draft",draft.id)
    assertEquals("Example",draft.name)
    assertEquals(0L,draft.exportSettings.trimStartMs)
    assertNull(draft.exportSettings.customOutputUri)
    assertEquals(template.compositions,draft.compositions)
 }
}
