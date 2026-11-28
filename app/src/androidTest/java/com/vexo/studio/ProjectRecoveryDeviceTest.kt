package com.vexo.studio

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vexo.studio.data.ProjectRepository
import com.vexo.studio.model.AudioTrackItem
import com.vexo.studio.model.VexoTemplates
import com.vexo.studio.playback.ProjectQueueStore
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProjectRecoveryDeviceTest {
    private fun withRepository(action:(ProjectRepository,File)->Unit){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val dir=File(context.cacheDir,"project-test-"+UUID.randomUUID()).apply{mkdirs()}
        val isolated=object:ContextWrapper(context){override fun getFilesDir():File=dir}
        try{action(ProjectRepository(isolated),dir)}finally{dir.deleteRecursively()}
    }

    @Test fun savedProjectAndDraftSurviveRepositoryRestart()=withRepository{repo,dir->
        val original=VexoTemplates.monochromeStudio().copy(name="Saved",audioUri="content://test/audio")
        repo.saveProject(original)
        val draft=original.copy(name="Unsaved draft")
        repo.autosave(draft)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val reopened=ProjectRepository(object:ContextWrapper(context){override fun getFilesDir():File=dir})
        assertEquals("Unsaved draft",reopened.recoverAutosaveOrDefault().name)
        assertEquals(original.audioUri,reopened.recoverAutosaveOrDefault().audioUri)
        assertEquals("Saved",reopened.loadProject(original.id)?.name)
    }

    @Test fun corruptPrimaryFallsBackToPreviousVersion()=withRepository{repo,dir->
        val p=VexoTemplates.monochromeStudio().copy(name="First")
        repo.saveProject(p)
        repo.saveProject(p.copy(name="Second"))
        File(dir,"vexo_projects/"+p.id+".json").writeText("invalid-json")
        assertEquals("First",repo.loadProject(p.id)?.name)
        val recovered=checkNotNull(repo.restorePrevious(p.id))
        assertNotEquals(p.id,recovered.id)
        assertTrue(recovered.name.contains("Recovered"))
        assertEquals("First",repo.loadProject(p.id)?.name)
    }

    @Test fun duplicateQueueRetainsSelectedTrackAndPosition(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val from=UUID.randomUUID().toString()
        val to=UUID.randomUUID().toString()
        val store=ProjectQueueStore(context)
        val a=AudioTrackItem(id=UUID.randomUUID().toString(),uri="content://test/a",title="A",artist="Artist",album="Album",durationMs=10000L)
        val b=a.copy(id=UUID.randomUUID().toString(),uri="content://test/b",title="B")
        try{
            store.save(from,listOf(a,b),b.id,4200L,true)
            store.copy(from,to)
            val restored=checkNotNull(store.read(to))
            assertEquals(listOf(a,b),restored.tracks)
            assertEquals(1,restored.index)
            assertEquals(4200L,restored.positionMs)
            store.delete(from)
            assertEquals(restored,store.read(to))
        }finally{store.delete(from);store.delete(to)}
    }
}
