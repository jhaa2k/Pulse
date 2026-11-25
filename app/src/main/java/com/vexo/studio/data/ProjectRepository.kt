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
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun listProjects(): List<ProjectSummary> {
        val files = projectsDir.listFiles { f ->
            f.isFile && f.extension == "json" && !f.name.startsWith("_")
        } ?: emptyArray()
        return files.mapNotNull { file ->
            runCatching {
                val p = loadFromFile(file)
                ProjectSummary(
                    id = p.id,
                    name = p.name,
                    updatedAtMs = p.updatedAtMs,
                    aspectRatio = p.canvas.aspectRatio,
                    layerCount = p.compositions.sumOf { it.layers.size }
                )
            }.getOrNull()
        }.sortedByDescending { it.updatedAtMs }
    }

    fun loadProject(id: String): VexoProject? {
        val file = File(projectsDir, "$id.json")
        if (!file.exists()) return null
        return runCatching { loadFromFile(file) }.getOrNull()
    }

    fun recoverAutosaveOrDefault(): VexoProject {
        if (autosaveFile.exists()) {
            val recovered = runCatching { loadFromFile(autosaveFile) }.getOrNull()
            if (recovered != null) return recovered
        }
        val existing = listProjects().firstOrNull()?.let { loadProject(it.id) }
        if (existing != null) return existing
        val starter = VexoTemplates.monochromeStudio()
        saveProject(starter)
        return starter
    }

    fun saveProject(project: VexoProject) {
        val updated = project.copy(updatedAtMs = System.currentTimeMillis())
        val payload = encodeEnvelope(updated)
        atomicWrite(File(projectsDir, "${updated.id}.json"), payload)
        atomicWrite(autosaveFile, payload)
    }

    fun autosave(project: VexoProject) {
        val updated = project.copy(updatedAtMs = System.currentTimeMillis())
        atomicWrite(autosaveFile, encodeEnvelope(updated))
    }

    fun duplicateProject(id: String): VexoProject? {
        val src = loadProject(id) ?: return null
        val copy = src.copy(
            id = UUID.randomUUID().toString(),
            name = "${src.name} (Copy)",
            updatedAtMs = System.currentTimeMillis()
        )
        saveProject(copy)
        return copy
    }

    fun deleteProject(id: String) {
        File(projectsDir, "$id.json").delete()
    }

    private fun atomicWrite(target: File, content: String) {
        val tmp = File(projectsDir, "${target.name}.tmp")
        tmp.writeText(content, Charsets.UTF_8)
        if (!tmp.renameTo(target)) {
            target.writeText(content, Charsets.UTF_8)
            tmp.delete()
        }
    }

    private fun encodeEnvelope(project: VexoProject): String = NativeVexCodec.encode(project)

    private fun loadFromFile(file: File): VexoProject {
        val text = file.readText(Charsets.UTF_8)
        if (NativeVexCodec.isNative(text)) return NativeVexCodec.decode(text)
        val env = json.parseToJsonElement(text) as JsonObject
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
