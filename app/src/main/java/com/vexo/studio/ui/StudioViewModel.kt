package com.vexo.studio.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.vexo.studio.VexoApplication
import com.vexo.studio.data.AveeJsonCodec
import com.vexo.studio.data.NativeVexCodec
import com.vexo.studio.data.VexArchive
import com.vexo.studio.data.ProjectHistoryStack
import com.vexo.studio.data.ProjectSummary
import kotlinx.coroutines.channels.Channel
import com.vexo.studio.engine.audio.AudioProviderSnapshot
import com.vexo.studio.engine.audio.RealtimeFftEngine
import com.vexo.studio.engine.gl.RenderSceneSnapshot
import com.vexo.studio.export.ExportProgress
import com.vexo.studio.export.ExportService
import com.vexo.studio.model.AppSettings
import com.vexo.studio.model.AspectRatioPreset
import com.vexo.studio.model.AudioProviderConfig
import com.vexo.studio.model.ChannelMode
import com.vexo.studio.model.CompositionNode
import com.vexo.studio.model.LayerCommon
import com.vexo.studio.model.LayerConfig
import com.vexo.studio.model.LayerNode
import com.vexo.studio.model.LayerType
import com.vexo.studio.model.NavDestination
import com.vexo.studio.model.PauseBehavior
import com.vexo.studio.model.StudioTab
import com.vexo.studio.model.VexoProject
import com.vexo.studio.model.VexoTemplates
import com.vexo.studio.playback.PlaybackUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class StudioUiState(
    val navDestination: NavDestination = NavDestination.STUDIO,
    val studioTab: StudioTab = StudioTab.LAYERS,
    val project: VexoProject = VexoTemplates.monochromeStudio(),
    val selectedCompositionIndex: Int = 0,
    val previewSelectedComposition: Boolean = false,
    val selectedLayerId: String? = null,
    val selectedProviderIndex: Int = 1,
    val projects: List<ProjectSummary> = emptyList(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isFullscreenPreview: Boolean = false,
    val previewZoom: Float = 1f,
    val previewPanX: Float = 0f,
    val previewPanY: Float = 0f,
    val statusBanner: String? = null
)

@UnstableApi
class StudioViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as VexoApplication
    val playbackController = app.playbackController
    private val projectRepo = app.projectRepository
    private val settingsRepo = app.settingsRepository
    private val history = ProjectHistoryStack(50)
    private val pendingSaves = Channel<VexoProject>(Channel.UNLIMITED)
    private val saveLock = Any()
    private val latestSave = java.util.concurrent.ConcurrentHashMap<String, VexoProject>()

    private fun enqueueSave(project: VexoProject) {
        latestSave[project.id] = project
        pendingSaves.trySend(project)
    }

    private fun updateProjectSummary(project: VexoProject): List<ProjectSummary> {
        val entry = ProjectSummary(project.id, project.name, System.currentTimeMillis(), project.canvas.aspectRatio, project.compositions.sumOf { it.layers.size })
        return listOf(entry) + _uiState.value.projects.filterNot { it.id == project.id }
    }

    val playbackState: StateFlow<PlaybackUiState> = playbackController.state
    val exportState: StateFlow<ExportProgress> = ExportService.exportState
    val appSettings: StateFlow<AppSettings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    private val _sceneSnapshot = MutableStateFlow<RenderSceneSnapshot?>(null)
    val sceneSnapshot: StateFlow<RenderSceneSnapshot?> = _sceneSnapshot.asStateFlow()

    private val realtimeFftEngine = RealtimeFftEngine(app.pcmRingBuffer, Dispatchers.Default, 16L)
    val audioSnapshots: StateFlow<Map<Int, AudioProviderSnapshot>> = realtimeFftEngine.snapshotsFlow
    val primaryAudioSnapshot: StateFlow<AudioProviderSnapshot?> = realtimeFftEngine.primarySnapshotFlow

    private var totalTimeSec = 0f
    private var playingTimeSec = 0f

    init {
        val recovered = projectRepo.recoverAutosaveOrDefault()
        val firstComp = recovered.compositions.firstOrNull { it.index == 1 } ?: recovered.compositions.first()
        _uiState.value = _uiState.value.copy(
            project = recovered,
            selectedCompositionIndex = firstComp.index,
            selectedLayerId = firstComp.layers.firstOrNull()?.common?.id,
            projects = projectRepo.listProjects()
        )
        playbackController.loadProjectAudio(recovered.audioUri)
        viewModelScope.launch(Dispatchers.IO) {
            for (project in pendingSaves) {
                if (latestSave[project.id] !== project) continue
                runCatching { synchronized(saveLock) { if (latestSave[project.id] === project) projectRepo.saveProject(project) } }
                    .onFailure { err -> withContext(Dispatchers.Main) { _uiState.value = _uiState.value.copy(statusBanner = "Save failed: ${err.message}") } }
                latestSave.remove(project.id, project)
            }
        }
        startAnalysisLoop()
    }

    private fun startAnalysisLoop() {
        viewModelScope.launch(Dispatchers.Main) {
            while (isActive) {
                playbackController.pollPosition()
                delay(32L)
            }
        }

        realtimeFftEngine.start(
            providersProvider = {
                val ui = _uiState.value
                ui.project.audioProviders
            },
            isPlayingProvider = { playbackState.value.isPlaying },
            freezeOnPauseProvider = { appSettings.value.pauseBehavior == PauseBehavior.FREEZE },
            onFrameProcessed = { snapshots, deltaSec ->
                val pState = playbackState.value
                val ui = _uiState.value
                totalTimeSec += deltaSec
                if (pState.isPlaying) {
                    playingTimeSec += deltaSec
                }
                _sceneSnapshot.value = RenderSceneSnapshot(
                    project = ui.project,
                    audioSnapshots = snapshots,
                    trackMeta = pState.trackMeta,
                    isPlaying = pState.isPlaying,
                    totalTimeSec = totalTimeSec,
                    playingTimeSec = playingTimeSec,
                    zoom = ui.previewZoom,
                    panX = ui.previewPanX,
                    panY = ui.previewPanY,
                    showSafeArea = ui.project.canvas.showSafeAreaGuides,
                    previewCompositionIndex = if(ui.previewSelectedComposition)ui.selectedCompositionIndex else 0
                )
            }
        )
    }

    override fun onCleared() {
        realtimeFftEngine.close()
        super.onCleared()
    }

    fun navigateTo(dest: NavDestination) {
        _uiState.value = _uiState.value.copy(navDestination = dest)
    }

    fun selectStudioTab(tab: StudioTab) {
        _uiState.value = _uiState.value.copy(studioTab = tab)
    }

    fun toggleFullscreenPreview() {
        _uiState.value = _uiState.value.copy(isFullscreenPreview = !_uiState.value.isFullscreenPreview)
    }

    fun updatePreviewTransform(zoomDelta: Float, panX: Float, panY: Float) {
        val cur = _uiState.value
        val nextZoom = (cur.previewZoom * zoomDelta).coerceIn(0.5f, 3.5f)
        _uiState.value = cur.copy(
            previewZoom = nextZoom,
            previewPanX = (cur.previewPanX + panX).coerceIn(-400f, 400f),
            previewPanY = (cur.previewPanY + panY).coerceIn(-400f, 400f)
        )
    }

    fun resetPreviewCamera() {
        _uiState.value = _uiState.value.copy(previewZoom = 1f, previewPanX = 0f, previewPanY = 0f)
    }

    fun toggleSafeAreaGuides() {
        commitProjectChange(_uiState.value.project.let { p ->
            p.copy(canvas = p.canvas.copy(showSafeAreaGuides = !p.canvas.showSafeAreaGuides))
        })
    }

    fun setAspectRatio(ratio: AspectRatioPreset) {
        val p = _uiState.value.project
        commitProjectChange(
            p.copy(
                canvas = p.canvas.copy(aspectRatio = ratio),
                exportSettings = p.exportSettings.copy(aspectRatio = ratio)
            )
        )
    }

    fun selectComposition(index: Int) {
        val comp = _uiState.value.project.compositions.firstOrNull { it.index == index } ?: return
        _uiState.value = _uiState.value.copy(
            selectedCompositionIndex = index,
            selectedLayerId = comp.layers.firstOrNull()?.common?.id
        )
    }

    fun selectLayer(layerId: String) {
        _uiState.value = _uiState.value.copy(selectedLayerId = layerId)
    }

    fun toggleCompositionPreview(){_uiState.value=_uiState.value.copy(previewSelectedComposition=!_uiState.value.previewSelectedComposition)}

    fun addComposition() {
        val p = _uiState.value.project
        if(p.compositions.size>=64){_uiState.value=_uiState.value.copy(statusBanner="Maximum 64 compositions");return}
        val nextIdx = (p.compositions.maxOfOrNull { it.index } ?: 0) + 1
        val newComp = CompositionNode(index = nextIdx, name = "Composition $nextIdx")
        commitProjectChange(p.copy(compositions = p.compositions + newComp))
        _uiState.value = _uiState.value.copy(selectedCompositionIndex = nextIdx, selectedLayerId = null)
    }

    fun renameComposition(index:Int,name:String){
        val clean=name.trim().take(80)
        if(clean.isEmpty())return
        val p=_uiState.value.project
        commitProjectChange(p.copy(compositions=p.compositions.map{if(it.index==index)it.copy(name=clean) else it}))
    }
    fun duplicateComposition(index:Int){
        val p=_uiState.value.project
        if(p.compositions.size>=64)return
        val src=p.compositions.firstOrNull{it.index==index}?:return
        if(p.compositions.sumOf{it.layers.size}+src.layers.size>2048)return
        val ids=src.layers.associate{it.common.id to UUID.randomUUID().toString()}
        val next=(p.compositions.maxOfOrNull{it.index}?:0)+1
        val copy=src.copy(id=UUID.randomUUID().toString(),index=next,name=src.name+" Copy",layers=src.layers.map{it.copy(common=it.common.copy(id=ids.getValue(it.common.id)))},providerIndices=emptyList())
        commitProjectChange(p.copy(compositions=p.compositions+copy,keyframes=p.keyframes+p.keyframes.filter{it.targetLayerId in ids}.map{it.copy(targetLayerId=ids.getValue(it.targetLayerId))}))
        selectComposition(next)
    }
    fun deleteComposition(index:Int){
        if(index==0)return
        val p=_uiState.value.project
        val target=p.compositions.firstOrNull{it.index==index}?:return
        val refs=p.compositions.filter{it.index!=index}.filter{comp->
            comp.layers.any{l->index in com.vexo.studio.engine.scene.SceneGraph.referencesOf(l.config)}
        }
        if(refs.isNotEmpty()){
            _uiState.value=_uiState.value.copy(statusBanner="Remove references from "+refs.joinToString{it.name}+" before deleting")
            return
        }
        val ids=target.layers.map{it.common.id}.toSet()
        commitProjectChange(p.copy(compositions=p.compositions.filterNot{it.index==index},keyframes=p.keyframes.filterNot{it.targetLayerId in ids}))
        selectComposition(0)
    }

    fun putTransformKeyframe(layerId:String,key:String,time:Long,value:Float,mode:com.vexo.studio.model.InterpolationType){
        if(key !in com.vexo.studio.engine.scene.TransformAnimation.keys||!value.isFinite())return
        val p=_uiState.value.project
        if(p.compositions.none{c->c.layers.any{it.common.id==layerId&&!it.common.locked}})return
        val property="transform."+key
        val track=p.keyframes.firstOrNull{it.targetLayerId==layerId&&it.propertyKey==property}
        val t=time.coerceAtLeast(0)
        val frames=(track?.keyframes.orEmpty().filterNot{it.timeMs==t}+com.vexo.studio.model.Keyframe(t,value,mode)).sortedBy{it.timeMs}
        if(frames.size>500)return
        commitProjectChange(p.copy(keyframes=p.keyframes.filterNot{it.targetLayerId==layerId&&it.propertyKey==property}+com.vexo.studio.model.KeyframeTrack(layerId,property,frames)))
    }
    fun removeTransformKeyframe(layerId:String,key:String,time:Long?){
        val p=_uiState.value.project
        val property="transform."+key
        val tracks=p.keyframes.mapNotNull{t->
            if(t.targetLayerId!=layerId||t.propertyKey!=property)t
            else if(time==null)null else t.copy(keyframes=t.keyframes.filterNot{it.timeMs==time}).takeIf{it.keyframes.isNotEmpty()}
        }
        commitProjectChange(p.copy(keyframes=tracks))
    }

    fun addCreativeLayer(name:String){
        val p=_uiState.value.project
        if(p.compositions.sumOf{it.layers.size}>=2048)return
        val idx=_uiState.value.selectedCompositionIndex
        val source=p.compositions.firstOrNull{it.index!=0&&it.index!=idx&&idx !in com.vexo.studio.engine.scene.SceneGraph.dependenciesOf(it)}?.index
        val layer=LayerNode(LayerType.IMAGE,LayerCommon(name=name),com.vexo.studio.model.CreativeRecipes.config(name,source))
        val next=p.copy(compositions=p.compositions.map{if(it.index==idx)it.copy(layers=it.layers+layer) else it})
        if(com.vexo.studio.engine.scene.SceneGraph.resolve(next).hasCycle){
            val fallback=layer.copy(config=com.vexo.studio.model.CreativeRecipes.config(name,null))
            commitProjectChange(p.copy(compositions=p.compositions.map{if(it.index==idx)it.copy(layers=it.layers+fallback) else it}))
        }else commitProjectChange(next)
        _uiState.value=_uiState.value.copy(selectedLayerId=layer.common.id,studioTab=StudioTab.PROPERTIES)
    }

    fun addLayer(type: LayerType) {
        val p = _uiState.value.project
        val compIdx = _uiState.value.selectedCompositionIndex
        val defaultLayer = createDefaultLayer(type)
        val target = p.compositions.firstOrNull { it.index != compIdx && it.index != 0 }?.index ?: p.compositions.firstOrNull { it.index != compIdx }?.index
        val newLayer = if(target == null) defaultLayer else defaultLayer.copy(config = when(val cfg = defaultLayer.config) {
            is LayerConfig.BlurEffect -> cfg.copy(sourceCompositionIndex = target)
            is LayerConfig.MotionBlurEffect -> cfg.copy(targetCompositionIndex = target)
            is LayerConfig.MirrorEffect -> cfg.copy(targetCompositionIndex = target)
            is LayerConfig.RgbSplitEffect -> cfg.copy(targetCompositionIndex = target)
            else -> cfg
        })
        val updatedComps = p.compositions.map { comp ->
            if (comp.index == compIdx) comp.copy(layers = comp.layers + newLayer) else comp
        }
        commitProjectChange(p.copy(compositions = updatedComps))
        _uiState.value = _uiState.value.copy(selectedLayerId = newLayer.common.id)
    }

    fun duplicateLayer(layerId: String) {
        val p = _uiState.value.project
        val compIdx = _uiState.value.selectedCompositionIndex
        var clonedId: String? = null
        val updatedComps = p.compositions.map { comp ->
            if (comp.index != compIdx) comp else {
                val list = mutableListOf<LayerNode>()
                for (l in comp.layers) {
                    list.add(l)
                    if (l.common.id == layerId) {
                        val copy = l.copy(
                            common = l.common.copy(
                                id = UUID.randomUUID().toString(),
                                name = "${l.common.name} Copy"
                            )
                        )
                        clonedId = copy.common.id
                        list.add(copy)
                    }
                }
                comp.copy(layers = list)
            }
        }
        val clonedTracks=p.keyframes.filter{it.targetLayerId==layerId}.mapNotNull{t->clonedId?.let{t.copy(targetLayerId=it)}}
        commitProjectChange(p.copy(compositions = updatedComps,keyframes=p.keyframes+clonedTracks))
        if (clonedId != null) _uiState.value = _uiState.value.copy(selectedLayerId = clonedId)
    }

    fun deleteLayer(layerId: String) {
        val p = _uiState.value.project
        val compIdx = _uiState.value.selectedCompositionIndex
        val updatedComps = p.compositions.map { comp ->
            if (comp.index == compIdx) {
                comp.copy(layers = comp.layers.filterNot { it.common.id == layerId })
            } else comp
        }
        commitProjectChange(p.copy(compositions = updatedComps,keyframes=p.keyframes.filterNot{it.targetLayerId==layerId}))
        val remaining = updatedComps.firstOrNull { it.index == compIdx }?.layers
        _uiState.value = _uiState.value.copy(selectedLayerId = remaining?.firstOrNull()?.common?.id)
    }

    fun moveLayer(layerId: String, direction: Int) {
        val p = _uiState.value.project
        val compIdx = _uiState.value.selectedCompositionIndex
        val updatedComps = p.compositions.map { comp ->
            if (comp.index != compIdx) comp else {
                val list = comp.layers.toMutableList()
                val idx = list.indexOfFirst { it.common.id == layerId }
                val target = idx + direction
                if (idx in list.indices && target in list.indices) {
                    val item = list.removeAt(idx)
                    list.add(target, item)
                }
                comp.copy(layers = list)
            }
        }
        commitProjectChange(p.copy(compositions = updatedComps))
    }

    fun updateLayer(layerId: String, recordUndo: Boolean = true, transform: (LayerNode) -> LayerNode) {
        val p = _uiState.value.project
        val updatedComps = p.compositions.map { comp ->
            comp.copy(layers = comp.layers.map { l -> if (l.common.id == layerId) transform(l) else l })
        }
        val next = p.copy(compositions = updatedComps)
        if (recordUndo) {
            commitProjectChange(next)
        } else {
            _uiState.value = _uiState.value.copy(project = next)
        }
    }

    fun commitUndoSnapshot(previousProject: VexoProject) {
        val cur = _uiState.value.project
        if (previousProject == cur || previousProject.id != cur.id) return
        history.pushState(previousProject)
        enqueueSave(cur)
        _uiState.value = _uiState.value.copy(
            canUndo = history.canUndo,
            canRedo = history.canRedo,
            projects = updateProjectSummary(cur)
        )
    }

    fun updateAudioProvider(index: Int, recordUndo: Boolean = true, transform: (AudioProviderConfig) -> AudioProviderConfig) {
        val p = _uiState.value.project
        val updated = p.audioProviders.map { if (it.index == index) transform(it) else it }
        val next = p.copy(audioProviders = updated)
        if (recordUndo) commitProjectChange(next) else _uiState.value = _uiState.value.copy(project = next)
    }

    fun addAudioProvider() {
        val p = _uiState.value.project
        if(p.audioProviders.size>=16){_uiState.value=_uiState.value.copy(statusBanner="Maximum 16 audio providers");return}
        val nextIdx = (p.audioProviders.maxOfOrNull { it.index } ?: 0) + 1
        val next = p.copy(audioProviders = p.audioProviders + AudioProviderConfig(index = nextIdx, tag = "Provider $nextIdx"))
        commitProjectChange(next)
        _uiState.value = _uiState.value.copy(selectedProviderIndex = nextIdx)
    }

    fun renameAudioProvider(index:Int,name:String){
        val clean=name.trim().take(80)
        if(clean.isNotBlank())updateAudioProvider(index){it.copy(tag=clean)}
    }
    fun selectAudioProvider(index: Int) {
        _uiState.value = _uiState.value.copy(selectedProviderIndex = index)
    }

    fun undo() {
        val prev = history.undo(_uiState.value.project) ?: return
        enqueueSave(prev)
        val comp = prev.compositions.firstOrNull { it.index == _uiState.value.selectedCompositionIndex } ?: prev.compositions.first()
        _uiState.value = _uiState.value.copy(
            project = prev,
            selectedCompositionIndex = comp.index,
            selectedLayerId = comp.layers.firstOrNull { it.common.id == _uiState.value.selectedLayerId }?.common?.id ?: comp.layers.firstOrNull()?.common?.id,
            selectedProviderIndex = prev.audioProviders.firstOrNull { it.index == _uiState.value.selectedProviderIndex }?.index ?: prev.audioProviders.first().index,
            canUndo = history.canUndo,
            canRedo = history.canRedo,
            projects = updateProjectSummary(prev)
        )
    }

    fun redo() {
        val next = history.redo(_uiState.value.project) ?: return
        enqueueSave(next)
        val comp = next.compositions.firstOrNull { it.index == _uiState.value.selectedCompositionIndex } ?: next.compositions.first()
        _uiState.value = _uiState.value.copy(
            project = next,
            selectedCompositionIndex = comp.index,
            selectedLayerId = comp.layers.firstOrNull { it.common.id == _uiState.value.selectedLayerId }?.common?.id ?: comp.layers.firstOrNull()?.common?.id,
            selectedProviderIndex = next.audioProviders.firstOrNull { it.index == _uiState.value.selectedProviderIndex }?.index ?: next.audioProviders.first().index,
            canUndo = history.canUndo,
            canRedo = history.canRedo,
            projects = updateProjectSummary(next)
        )
    }

    fun availableTemplates(): List<VexoProject> = runCatching {
        com.vexo.studio.data.BuiltInTemplateRepository.load(app)
    }.getOrDefault(emptyList()) + VexoTemplates.allTemplates()

    fun applyArtworkPalette() {
        val bitmap = playbackState.value.trackMeta.artwork
        if (bitmap == null) {
            _uiState.value = _uiState.value.copy(statusBanner = "Import a track with album art first")
            return
        }
        val projectId = _uiState.value.project.id
        viewModelScope.launch {
            runCatching { com.vexo.studio.engine.scene.ArtworkPaletteExtractor.extract(bitmap) }
                .onSuccess { colors ->
                    if (_uiState.value.project.id == projectId) {
                        commitProjectChange(com.vexo.studio.engine.scene.ArtworkPaletteExtractor.apply(_uiState.value.project, colors))
                        _uiState.value = _uiState.value.copy(statusBanner = "Album-art palette applied")
                    }
                }.onFailure { _uiState.value = _uiState.value.copy(statusBanner = "Palette extraction failed: "+it.message) }
        }
    }

    private var sleepJob: kotlinx.coroutines.Job? = null
    private val _sleepRemaining = MutableStateFlow(0L)
    val sleepRemaining = _sleepRemaining.asStateFlow()
    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        val deadline = android.os.SystemClock.elapsedRealtime() + minutes.coerceIn(0,180)*60_000L
        _sleepRemaining.value = (minutes.coerceIn(0,180)*60L)
        if (minutes <= 0) return
        sleepJob = viewModelScope.launch {
            while (isActive) {
                val remain = (deadline-android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                _sleepRemaining.value = (remain+999)/1000
                if (remain == 0L) { playbackController.player.pause(); break }
                delay(minOf(1000L,remain))
            }
        }
    }

    fun createNewProject(name: String, template: VexoProject = VexoTemplates.monochromeStudio()) {
        val fresh = template.copy(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Untitled Studio" },
            updatedAtMs = System.currentTimeMillis()
        )
        history.clear()
        synchronized(saveLock) { projectRepo.saveProject(fresh) }
        val comp = fresh.compositions.firstOrNull { it.index == 1 } ?: fresh.compositions.first()
        _uiState.value = _uiState.value.copy(
            project = fresh,
            selectedCompositionIndex = comp.index,
            selectedLayerId = comp.layers.firstOrNull()?.common?.id,
            projects = projectRepo.listProjects(),
            canUndo = false,
            canRedo = false,
            navDestination = NavDestination.STUDIO,
            statusBanner = "Loaded ${fresh.name}"
        )
        playbackController.loadProjectAudio(fresh.audioUri)
    }

    fun openProject(id: String) {
        val loaded = latestSave[id] ?: synchronized(saveLock) { projectRepo.loadProject(id) } ?: return
        history.clear()
        val comp = loaded.compositions.firstOrNull { it.index == 1 } ?: loaded.compositions.first()
        _uiState.value = _uiState.value.copy(
            project = loaded,
            selectedCompositionIndex = comp.index,
            selectedLayerId = comp.layers.firstOrNull()?.common?.id,
            canUndo = false,
            canRedo = false,
            navDestination = NavDestination.STUDIO
        )
        playbackController.loadProjectAudio(loaded.audioUri)
    }

    fun duplicateProject(id: String) {
        projectRepo.duplicateProject(id)
        _uiState.value = _uiState.value.copy(projects = projectRepo.listProjects())
    }

    fun renameCurrentProject(newName: String) {
        val clean = newName.trim().ifBlank { return }
        commitProjectChange(_uiState.value.project.copy(name = clean))
    }

    fun deleteProject(id: String) {
        latestSave.remove(id)
        synchronized(saveLock) { projectRepo.deleteProject(id) }
        val remaining = projectRepo.listProjects()
        if (_uiState.value.project.id == id) {
            val fallback = remaining.firstOrNull()?.let { projectRepo.loadProject(it.id) }
                ?: VexoTemplates.monochromeStudio().also { projectRepo.saveProject(it) }
            _uiState.value = _uiState.value.copy(
                project = fallback,
                projects = projectRepo.listProjects()
            )
            playbackController.loadProjectAudio(fallback.audioUri)
        } else {
            _uiState.value = _uiState.value.copy(projects = remaining)
        }
    }

    fun importPresetFromUri(uri: Uri, displayName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val stream = app.contentResolver.openInputStream(uri) ?: error("Cannot open preset file")
                val imported = stream.use { s -> VexArchive.read(app, s, displayName.substringBeforeLast('.')) }
                withContext(Dispatchers.Main) {
                    createNewProject(imported.name, imported)
                    val unsupported = imported.compositions.sumOf { it.preservedElements.size }
                    _uiState.value = _uiState.value.copy(statusBanner = "Imported ${imported.compositions.size} compositions · ${imported.compositions.sumOf { it.layers.size }} editable layers" + if (unsupported > 0) " · $unsupported unsupported elements preserved" else "")
                }
            }.onFailure { err ->
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(statusBanner = "Preset import failed: ${err.message}")
                }
            }
        }
    }

    fun exportPresetToUri(uri: Uri) {
        val project = _uiState.value.project.copy(audioUri = playbackController.currentAudioUri() ?: _uiState.value.project.audioUri)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                app.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    VexArchive.write(app, project, out)
                } ?: error("Cannot write to target URI")
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(statusBanner = "VEX project exported")
                }
            }.onFailure { err ->
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(statusBanner = "Preset export failed: ${err.message}")
                }
            }
        }
    }

    fun importAudioTracks(uris: List<Uri>) {
        playbackController.importTracks(uris, playFirstImmediately = true)
        val first = uris.firstOrNull()?.toString()
        if (first != null) {
            val p = _uiState.value.project
            commitProjectChange(p.copy(audioUri = first))
        }
    }

    fun attachFont(layerId:String,uri:Uri) {
        runCatching { app.contentResolver.takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        viewModelScope.launch {
            val target=withContext(Dispatchers.IO) { runCatching {
                val dir=java.io.File(app.filesDir,"fonts").apply{mkdirs()}
                val file=java.io.File(dir,UUID.randomUUID().toString()+".ttf")
                try {
                    app.contentResolver.openInputStream(uri)?.use{input->file.outputStream().use{output->
                        val buffer=ByteArray(8192);var total=0L
                        while(true){val n=input.read(buffer);if(n<0)break;total+=n;require(total<=16L*1024*1024);output.write(buffer,0,n)}
                    }}?:error("Cannot open font")
                    android.graphics.Typeface.createFromFile(file)
                    Uri.fromFile(file).toString()
                }catch(e:Exception){file.delete();throw e}
            }}
            target.onSuccess{path->updateLayer(layerId){l->if(l.config is LayerConfig.Text)l.copy(config=l.config.copy(fontAssetUri=path)) else l}}
                .onFailure{_uiState.value=_uiState.value.copy(statusBanner="Font import failed: "+it.message)}
        }
    }

    fun attachLayerAsset(layerId:String,uri:Uri,role:String){
        runCatching{app.contentResolver.takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)}
        updateLayer(layerId){layer->layer.copy(config=when(val c=layer.config){
            is LayerConfig.Image -> if(role=="mask")c.copy(maskImage=uri.toString()) else c
            is LayerConfig.BlurEffect -> if(role=="mask")c.copy(maskImage=uri.toString()) else c
            is LayerConfig.Particles -> if(role=="particle")c.copy(customImage=uri.toString()) else c
            else -> layer.config
        })}
    }

    fun attachImageToSelectedLayer(uri: Uri) {
        val video = app.contentResolver.getType(uri)?.startsWith("video/") == true
        runCatching {
            app.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val selected = currentSelectedLayer()
        if (selected != null && selected.type == LayerType.IMAGE) {
            val cfg = selected.config as LayerConfig.Image
            updateLayer(selected.common.id) {
                it.copy(
                    config = cfg.copy(
                        sourceType = com.vexo.studio.model.ImageSourceType.IMPORTED,
                        customUri = uri.toString(), videoEnabled = video
                    )
                )
            }
            _uiState.value = _uiState.value.copy(statusBanner = (if(video) "Video" else "Image") + " applied to ${selected.common.name}")
        } else {
            val p = _uiState.value.project
            val compIdx = _uiState.value.selectedCompositionIndex
            val newLayer = LayerNode(
                type = LayerType.IMAGE,
                common = LayerCommon(name = if(video) "Video Background" else "Imported Image"),
                config = LayerConfig.Image(
                    sourceType = com.vexo.studio.model.ImageSourceType.IMPORTED,
                    customUri = uri.toString(), videoEnabled = video, keepAspectRatioAndCropToFit = video
                )
            )
            val updated = p.compositions.map {
                if (it.index == compIdx) it.copy(layers = it.layers + newLayer) else it
            }
            commitProjectChange(p.copy(compositions = updated))
            _uiState.value = _uiState.value.copy(selectedLayerId = newLayer.common.id)
        }
    }

    fun updateExportSettings(transform: (com.vexo.studio.model.ExportSettings) -> com.vexo.studio.model.ExportSettings) {
        val p = _uiState.value.project
        commitProjectChange(p.copy(exportSettings = transform(p.exportSettings)))
    }

    fun startVideoExport() {
        val audioUri = playbackController.currentAudioUri() ?: _uiState.value.project.audioUri
        if (exportState.value.isRunning) return
        if (audioUri.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(statusBanner = "Import an audio track before exporting video")
            return
        }
        val exp = _uiState.value.project.exportSettings
        if (exp.trimEndMs > 0L && exp.trimEndMs <= exp.trimStartMs) {
            _uiState.value = _uiState.value.copy(statusBanner = "Trim end must be after trim start")
            return
        }
        if (android.os.Build.VERSION.SDK_INT < 29 && exp.customOutputUri.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(statusBanner = "Choose a custom MP4 destination on Android 7–9")
            return
        }
        if (playbackState.value.isPlaying) {
            playbackController.togglePlayPause()
        }
        ExportService.startExport(
            app,
            ExportService.ExportRequest(
                project = _uiState.value.project,
                audioUri = audioUri,
                trackMeta = playbackState.value.trackMeta
            )
        )
    }

    fun cancelVideoExport() {
        ExportService.cancelExport(app)
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            settingsRepo.update(transform)
        }
    }

    fun clearBanner() {
        _uiState.value = _uiState.value.copy(statusBanner = null)
    }

    fun currentSelectedLayer(): LayerNode? {
        val comp = _uiState.value.project.compositions.firstOrNull {
            it.index == _uiState.value.selectedCompositionIndex
        } ?: return null
        return comp.layers.firstOrNull { it.common.id == _uiState.value.selectedLayerId }
    }

    private fun commitProjectChange(next: VexoProject) {
        if (next == _uiState.value.project) return
        history.pushState(_uiState.value.project)
        enqueueSave(next)
        _uiState.value = _uiState.value.copy(
            project = next,
            canUndo = history.canUndo,
            canRedo = history.canRedo,
            projects = updateProjectSummary(next)
        )
    }

    private fun createDefaultLayer(type: LayerType): LayerNode = when (type) {
        LayerType.BARS -> LayerNode(type, LayerCommon(name = "Spectrum Bars"), LayerConfig.Bars())
        LayerType.IMAGE -> LayerNode(type, LayerCommon(name = "Image"), LayerConfig.Image())
        LayerType.TEXT -> LayerNode(type, LayerCommon(name = "Text Label"), LayerConfig.Text())
        LayerType.SHAPE -> LayerNode(type, LayerCommon(name = "Polygon Shape"), LayerConfig.Shape())
        LayerType.PARTICLES -> LayerNode(type, LayerCommon(name = "Particles"), LayerConfig.Particles())
        LayerType.BLUR_EFFECT -> LayerNode(type, LayerCommon(name = "Blur Effect"), LayerConfig.BlurEffect())
        LayerType.MOTION_BLUR_EFFECT -> LayerNode(type, LayerCommon(name = "Motion Blur"), LayerConfig.MotionBlurEffect())
        LayerType.MIRROR_EFFECT -> LayerNode(type, LayerCommon(name = "Mirror Effect"), LayerConfig.MirrorEffect())
        LayerType.RGB_SPLIT_EFFECT -> LayerNode(type, LayerCommon(name = "RGB Split"), LayerConfig.RgbSplitEffect())
    }
}
