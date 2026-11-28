package com.vexo.studio.data

import android.content.Context
import com.vexo.studio.model.AspectRatioPreset
import com.vexo.studio.model.CanvasConfig
import com.vexo.studio.model.ExportSettings
import com.vexo.studio.model.VexoProject
import com.vexo.studio.model.VexoTemplates
import com.vexo.studio.model.VideoResolution
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.util.UUID

data class ProjectSummary(
    val id: String,
    val name: String,
    val updatedAtMs: Long,
    val aspectRatio: AspectRatioPreset,
    val layerCount: Int
)

/**
 * Atomic file-based project repository with autosave crash recovery and bounded undo/redo stack.
 */
class ProjectRepository(context: Context) {
    private val projectsDir = File(context.filesDir, "vexo_projects").apply { mkdirs() }
    private val autosaveFile = File(projectsDir, "_autosave.json")
    private val activeFile=File(projectsDir,"_active.txt")
    private fun validId(id:String)=runCatching{UUID.fromString(id).toString()==id}.getOrDefault(false)
    private fun projectFile(id:String):File { require(validId(id)){"Invalid project ID"};return File(projectsDir,"$id.json") }
    private fun backup(file:File)=File(projectsDir,file.name+".previous")
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    @Synchronized
    fun listProjects(): List<ProjectSummary> {
        val ids=projectsDir.listFiles().orEmpty().mapNotNull{f->
            val id=f.name.removeSuffix(".previous").removeSuffix(".bak").removeSuffix(".json")
            id.takeIf{validId(it)}
        }.distinct()
        return ids.mapNotNull{id->
            loadProject(id)?.let{p->ProjectSummary(p.id,p.name,p.updatedAtMs,p.canvas.aspectRatio,p.compositions.sumOf{it.layers.size})}
        }.sortedByDescending{it.updatedAtMs}
    }

    @Synchronized
    fun loadProject(id:String):VexoProject? {
        if(!validId(id))return null
        val file=projectFile(id)
        return listOf(file,backup(file)).firstNotNullOfOrNull{candidate->readValid(candidate)?.takeIf{it.id==id}}
    }

    @Synchronized
    fun recoverAutosaveOrDefault():VexoProject {
        val active=runCatching{android.util.AtomicFile(activeFile).readFully().toString(Charsets.UTF_8).trim()}.getOrNull()
        val activeDraft=readValid(autosaveFile)?.takeIf{it.id==active}
        if(activeDraft!=null)return activeDraft
        val saved=active?.let(::loadProject)
        if(saved!=null){
            val draft=readValid(autosaveFile)?.takeIf{it.id==saved.id}
            return if(draft!=null&&draft.updatedAtMs>saved.updatedAtMs)draft else saved
        }
        // Old installations have no active marker. Only restore a draft for a surviving project.
        val recovered=readValid(autosaveFile)?.takeIf{validId(it.id)&&loadProject(it.id)!=null}
        if(recovered!=null)return recovered
        val existing=listProjects().firstOrNull()?.let{loadProject(it.id)}
        if(existing!=null)return existing
        return VexoTemplates.monochromeStudio().also{autosave(it)}
    }

    @Synchronized
    fun saveProject(project:VexoProject,markActive:Boolean=true){
        val updated=project.copy(updatedAtMs=System.currentTimeMillis())
        val file=projectFile(updated.id)
        val payload=encodeEnvelope(updated)
        require(payload.toByteArray(Charsets.UTF_8).size<=NativeVexCodec.MAX_JSON_BYTES){"Project exceeds save limit"}
        // Retain the last known-good version before replacing the primary.
        readValid(file)?.takeIf{it.id==updated.id}?.let{previous->atomicWrite(backup(file),encodeEnvelope(previous))}
        atomicWrite(file,payload)
        if(markActive){
            atomicWrite(activeFile,updated.id)
            atomicWrite(autosaveFile,payload)
        }
    }

    @Synchronized
    fun markActive(id:String){
        if(validId(id))atomicWrite(activeFile,id)
    }

    @Synchronized
    fun autosave(project:VexoProject){
        val data=encodeEnvelope(project.copy(updatedAtMs=System.currentTimeMillis()))
        require(data.toByteArray(Charsets.UTF_8).size<=NativeVexCodec.MAX_JSON_BYTES){"Draft exceeds save limit"}
        atomicWrite(autosaveFile,data)
        markActive(project.id)
    }

    @Synchronized
    fun duplicateProject(id:String):VexoProject?{
        val src=loadProject(id)?:return null
        val copy=src.copy(id=UUID.randomUUID().toString(),name=src.name+" (Copy)",updatedAtMs=System.currentTimeMillis())
        saveProject(copy,false)
        return copy
    }

    @Synchronized
    fun restorePrevious(id:String):VexoProject?{
        if(!validId(id))return null
        val previous=readValid(backup(projectFile(id)))?.takeIf{it.id==id}?:return null
        // Create a separate recovered project; current version is never overwritten.
        val recovered=previous.copy(id=UUID.randomUUID().toString(),name=previous.name+" (Recovered)",updatedAtMs=System.currentTimeMillis())
        saveProject(recovered,false)
        return recovered
    }

    @Synchronized
    fun deleteProject(id:String){
        if(!validId(id))return
        val f=projectFile(id)
        android.util.AtomicFile(f).delete()
        android.util.AtomicFile(backup(f)).delete()
        if(readValid(autosaveFile)?.id==id)android.util.AtomicFile(autosaveFile).delete()
        val active=runCatching{android.util.AtomicFile(activeFile).readFully().toString(Charsets.UTF_8)}.getOrNull()
        if(active==id)android.util.AtomicFile(activeFile).delete()
    }

    private fun readValid(file:File):VexoProject?=runCatching{
        val data=android.util.AtomicFile(file).openRead().use{input->
            require(file.length()<=NativeVexCodec.MAX_JSON_BYTES){"Project too large"}
            input.readBytes()
        }
        require(data.size<=NativeVexCodec.MAX_JSON_BYTES)
        loadText(data.toString(Charsets.UTF_8))
    }.getOrNull()

    private fun atomicWrite(target:File,content:String){
        val atomic=android.util.AtomicFile(target)
        val stream=atomic.startWrite()
        try{stream.write(content.toByteArray(Charsets.UTF_8));atomic.finishWrite(stream)}
        catch(e:Exception){atomic.failWrite(stream);throw e}
    }

    private fun encodeEnvelope(project: VexoProject): String = NativeVexCodec.encode(project)

    private fun loadText(text:String):VexoProject {
        if (NativeVexCodec.isNative(text)) return NativeVexCodec.decode(text)
        val env = json.parseToJsonElement(text) as JsonObject
        require(env["format"]==null){"Unrecognized project envelope"}
        require(env["sceneJson"]!=null || env["compositions"]!=null || env["elements"]!=null){"Invalid legacy project"}
        val sceneJson = (env["sceneJson"] as? JsonPrimitive)?.content ?: text
        val base = AveeJsonCodec.importFromText(
            sceneJson,
            (env["name"] as? JsonPrimitive)?.content ?: "Untitled Studio"
        )
        val ratioName = (env["aspectRatio"] as? JsonPrimitive)?.content
        val ratio = AspectRatioPreset.entries.firstOrNull { it.name == ratioName }
            ?: AspectRatioPreset.RATIO_16_9
        val resName = (env["exportResolution"] as? JsonPrimitive)?.content
        val res = VideoResolution.entries.firstOrNull { it.name == resName }
            ?: VideoResolution.FHD_1080
        val audio = (env["audioUri"] as? JsonPrimitive)?.content?.ifBlank { null }

        return base.copy(
            id = (env["id"] as? JsonPrimitive)?.content ?: base.id,
            name = (env["name"] as? JsonPrimitive)?.content ?: base.name,
            updatedAtMs = (env["updatedAtMs"] as? JsonPrimitive)?.longOrNull ?: System.currentTimeMillis(),
            audioUri = audio,
            canvas = CanvasConfig(
                aspectRatio = ratio,
                backgroundColorArgb = (env["backgroundColorArgb"] as? JsonPrimitive)?.intOrNull ?: 0xFF000000.toInt(),
                showSafeAreaGuides = (env["showSafeAreaGuides"] as? JsonPrimitive)?.content == "true"
            ),
            exportSettings = ExportSettings(
                aspectRatio = ratio,
                resolution = res,
                fps = (env["exportFps"] as? JsonPrimitive)?.intOrNull ?: 30,
                videoBitrateMbps = (env["exportBitrateMbps"] as? JsonPrimitive)?.intOrNull ?: 12,
                trimStartMs = (env["exportTrimStartMs"] as? JsonPrimitive)?.longOrNull ?: 0L,
                trimEndMs = (env["exportTrimEndMs"] as? JsonPrimitive)?.longOrNull ?: 0L
            )
        )
    }
}

/**
 * Bounded 50-step Undo/Redo history stack for project edits.
 */
class ProjectHistoryStack(private val maxDepth: Int = 50) {
    private val undoStack = ArrayDeque<VexoProject>()
    private val redoStack = ArrayDeque<VexoProject>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun pushState(previous: VexoProject) {
        if (undoStack.size >= maxDepth) {
            undoStack.removeFirst()
        }
        undoStack.addLast(previous)
        redoStack.clear()
    }

    fun undo(current: VexoProject): VexoProject? {
        if (undoStack.isEmpty()) return null
        redoStack.addLast(current)
        return undoStack.removeLast()
    }

    fun redo(current: VexoProject): VexoProject? {
        if (redoStack.isEmpty()) return null
        undoStack.addLast(current)
        return redoStack.removeLast()
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
