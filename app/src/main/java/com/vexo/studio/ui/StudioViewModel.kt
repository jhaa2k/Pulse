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
import com.vexo.studio.model.BlendMode
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

sealed interface ProjectSwitch {
    data class Open(val id:String):ProjectSwitch
    data class Create(val project:VexoProject):ProjectSwitch
}

data class StudioUiState(
    val navDestination: NavDestination = NavDestination.STUDIO,
    val studioTab: StudioTab = StudioTab.LAYERS,
    val project: VexoProject = VexoTemplates.monochromeStudio(),
    val selectedCompositionIndex: Int = 0,
    val previewSelectedComposition: Boolean = false,
    val selectedLayerId: String? = null,
    val clipboardLayerType: LayerType? = null,
    val selectedProviderIndex: Int = 1,
    val projects: List<ProjectSummary> = emptyList(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isFullscreenPreview: Boolean = false,
    val previewZoom: Float = 1f,
    val previewPanX: Float = 0f,
    val previewPanY: Float = 0f,
    val statusBanner: String? = null,
    val unsaved:Boolean=false,
    val pendingPreset:VexoProject?=null,
    val pendingProjectSwitch:ProjectSwitch?=null
)

@UnstableApi
class StudioViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as VexoApplication
    val playbackController = app.playbackController
    private val projectRepo = app.projectRepository
    private val settingsRepo = app.settingsRepository
    private val history = ProjectHistoryStack(50)
    private val pendingSaves = Channel<Unit>(Channel.CONFLATED)
    private val saveLock = Any()
    private val latestSave = java.util.concurrent.ConcurrentHashMap<String, VexoProject>()

    private fun enqueueSave(project: VexoProject) {
        latestSave[project.id] = project
        pendingSaves.trySend(Unit)
    }

    private fun updateProjectSummary(project: VexoProject): List<ProjectSummary> {
        return _uiState.value.projects
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
            projects = projectRepo.listProjects(),
            unsaved=projectRepo.loadProject(recovered.id)?.let{it.copy(updatedAtMs=recovered.updatedAtMs)!=recovered}?:true
        )
        playbackController.loadProjectAudio(recovered.id,recovered.audioUri)
        viewModelScope.launch(Dispatchers.IO) {
            for(signal in pendingSaves){
                for(project in latestSave.values.toList()){
                    if(latestSave[project.id]!==project)continue
                    val result=runCatching{
                        synchronized(saveLock){
                            if(latestSave[project.id]===project){
                                if(_uiState.value.project.id==project.id)projectRepo.autosave(project)
                                latestSave.remove(project.id,project)
                            }
                        }
                    }
                    result.onFailure{err->withContext(Dispatchers.Main){_uiState.value=_uiState.value.copy(statusBanner="Save failed: "+err.message)}}
                }
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
        pendingSaves.close()
        // Final synchronous flush only for still-pending projects when the editor is destroyed.
        synchronized(saveLock){latestSave.values.toList().forEach{p->if(p.id==_uiState.value.project.id)runCatching{projectRepo.autosave(p)}};latestSave.clear()}
        realtimeFftEngine.close()
        super.onCleared()
    }

    fun navigateTo(dest: NavDestination) {
        _uiState.value = _uiState.value.copy(navDestination = dest,isFullscreenPreview = dest == NavDestination.STUDIO && _uiState.value.isFullscreenPreview)
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
            previewSelectedComposition=_uiState.value.previewSelectedComposition&&index!=0,
            selectedLayerId = comp.layers.firstOrNull()?.common?.id
        )
    }

    fun selectLayer(layerId: String) {
        _uiState.value = _uiState.value.copy(selectedLayerId = layerId)
    }

    fun toggleCompositionPreview(){val s=_uiState.value;_uiState.value=s.copy(previewSelectedComposition=s.selectedCompositionIndex!=0&&!s.previewSelectedComposition)}

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

    private val motionPrefs=app.getSharedPreferences("motion_presets",android.content.Context.MODE_PRIVATE)
    private val motionSerializer=kotlinx.serialization.builtins.ListSerializer(com.vexo.studio.engine.scene.SavedMotionPreset.serializer())
    private val _motionPresets=kotlinx.coroutines.flow.MutableStateFlow(runCatching{
        kotlinx.serialization.json.Json.decodeFromString(motionSerializer,motionPrefs.getString("presets","[]")?:"[]")
    }.getOrDefault(emptyList()).take(20))
    val motionPresets=_motionPresets.asStateFlow()

    fun saveMotionPreset(layerId:String,name:String){
        if(name.isBlank())return
        val preset=com.vexo.studio.engine.scene.MotionPresets.capture(name,_uiState.value.project.keyframes.filter{it.targetLayerId==layerId})
        if(preset==null){_uiState.value=_uiState.value.copy(statusBanner="Create transform keyframes first");return}
        val list=(_motionPresets.value.filterNot{it.name.equals(preset.name,true)}+preset).takeLast(20)
        motionPrefs.edit().putString("presets",kotlinx.serialization.json.Json.encodeToString(motionSerializer,list)).apply()
        _motionPresets.value=list
        _uiState.value=_uiState.value.copy(statusBanner="Animation preset saved")
    }
    fun deleteMotionPreset(name:String){
        val list=_motionPresets.value.filterNot{it.name==name}
        motionPrefs.edit().putString("presets",kotlinx.serialization.json.Json.encodeToString(motionSerializer,list)).apply()
        _motionPresets.value=list
    }
    fun applyMotionPreset(layerId:String,recipe:com.vexo.studio.engine.scene.MotionRecipe?,savedName:String?,start:Long,duration:Long,strength:Float){
        val p=_uiState.value.project
        val layer=p.compositions.flatMap{it.layers}.firstOrNull{it.common.id==layerId&&!it.common.locked}?:return
        val tracks=if(recipe!=null)com.vexo.studio.engine.scene.MotionPresets.builtin(recipe,layer.common,start,duration,strength)
        else _motionPresets.value.firstOrNull{it.name==savedName}?.let{com.vexo.studio.engine.scene.MotionPresets.applySaved(it,layer.common,start,duration)}.orEmpty()
        if(tracks.isEmpty())return
        commitProjectChange(p.copy(keyframes=com.vexo.studio.engine.scene.MotionPresets.replace(p.keyframes,tracks)))
    }

    fun putTransformKeyframe(layerId:String,key:String,time:Long,value:Float,mode:com.vexo.studio.model.InterpolationType){
        if(!value.isFinite())return
        val p=_uiState.value.project
        val layer=p.compositions.flatMap{it.layers}.firstOrNull{it.common.id==layerId&&!it.common.locked}?:return
        if(key !in com.vexo.studio.engine.scene.LayerPropertyAnimation.keys(layer))return
        val property=com.vexo.studio.engine.scene.LayerPropertyAnimation.trackKey(key)
        val safeValue=com.vexo.studio.engine.scene.LayerPropertyAnimation.clamp(key,value)
        val track=p.keyframes.firstOrNull{it.targetLayerId==layerId&&it.propertyKey==property}
        val t=time.coerceIn(0L,86400000L)
        val frames=(track?.keyframes.orEmpty().filterNot{it.timeMs==t}+com.vexo.studio.model.Keyframe(t,safeValue,mode)).sortedBy{it.timeMs}
        if(frames.size>500)return
        commitProjectChange(p.copy(keyframes=p.keyframes.filterNot{it.targetLayerId==layerId&&it.propertyKey==property}+com.vexo.studio.model.KeyframeTrack(layerId,property,frames)))
    }
    fun moveTransformKeyframe(layerId:String,key:String,oldTime:Long,newTime:Long):Boolean{
        val p=_uiState.value.project
        if(p.compositions.none{c->c.layers.any{it.common.id==layerId&&!it.common.locked}})return false
        val property=com.vexo.studio.engine.scene.LayerPropertyAnimation.trackKey(key)
        val track=p.keyframes.firstOrNull{it.targetLayerId==layerId&&it.propertyKey==property}?:return false
        val frame=track.keyframes.firstOrNull{it.timeMs==oldTime}?:return false
        val t=newTime.coerceIn(0L,86400000L)
        if(t==oldTime)return true
        if(track.keyframes.any{it.timeMs==t}){_uiState.value=_uiState.value.copy(statusBanner="A keyframe already exists at that time");return false}
        val edited=track.copy(keyframes=(track.keyframes.filterNot{it.timeMs==oldTime}+frame.copy(timeMs=t)).sortedBy{it.timeMs})
        commitProjectChange(p.copy(keyframes=p.keyframes.map{if(it==track)edited else it}))
        return true
    }

    fun removeTransformKeyframe(layerId:String,key:String,time:Long?){
        val p=_uiState.value.project
        if(p.compositions.none{c->c.layers.any{it.common.id==layerId&&!it.common.locked}})return
        val property=com.vexo.studio.engine.scene.LayerPropertyAnimation.trackKey(key)
        val tracks=p.keyframes.mapNotNull{t->
            if(t.targetLayerId!=layerId||t.propertyKey!=property)t
            else if(time==null)null else t.copy(keyframes=t.keyframes.filterNot{it.timeMs==time}).takeIf{it.keyframes.isNotEmpty()}
        }
        commitProjectChange(p.copy(keyframes=tracks))
    }

    private val layerPresetPrefs=app.getSharedPreferences("layer_presets",android.content.Context.MODE_PRIVATE)
    private val layerPresetSerializer=kotlinx.serialization.builtins.ListSerializer(com.vexo.studio.engine.scene.SavedLayerPreset.serializer())
    private val _layerPresets=kotlinx.coroutines.flow.MutableStateFlow(runCatching{
        kotlinx.serialization.json.Json.decodeFromString(layerPresetSerializer,layerPresetPrefs.getString("presets","[]")?:"[]")
    }.getOrDefault(emptyList()).take(32))
    val layerPresets=_layerPresets.asStateFlow()

    private fun persistLayerPresets(list:List<com.vexo.studio.engine.scene.SavedLayerPreset>){
        val encoded=kotlinx.serialization.json.Json.encodeToString(layerPresetSerializer,list)
        if(encoded.length>2_000_000){_uiState.value=_uiState.value.copy(statusBanner="Layer preset library size limit reached");return}
        layerPresetPrefs.edit().putString("presets",encoded).apply()
        _layerPresets.value=list
    }
    fun saveLayerPreset(layerId:String,name:String){
        val p=_uiState.value.project
        val layer=p.compositions.flatMap{it.layers}.firstOrNull{it.common.id==layerId}?:return
        val saved=com.vexo.studio.engine.scene.LayerPresets.capture(name,layer,p.keyframes)
        if(saved==null){_uiState.value=_uiState.value.copy(statusBanner="Standalone presets cannot contain composition references or invalid animation");return}
        persistLayerPresets((_layerPresets.value.filterNot{it.name.equals(saved.name,true)}+saved).takeLast(32))
    }
    fun deleteLayerPreset(id:String){persistLayerPresets(_layerPresets.value.filterNot{it.id==id})}
    fun addSavedLayerPreset(id:String){
        val preset=_layerPresets.value.firstOrNull{it.id==id}?:return
        val p=_uiState.value.project
        if(p.compositions.sumOf{it.layers.size}>=2048)return
        val idx=_uiState.value.selectedCompositionIndex
        if(p.compositions.none{it.index==idx})return
        val (layer,tracks)=com.vexo.studio.engine.scene.LayerPresets.instantiate(preset,p.audioProviders.map{it.index}.toSet(),p.audioProviders.first().index)
        if(com.vexo.studio.engine.scene.SceneGraph.referencesOf(layer.config).isNotEmpty())return
        commitProjectChange(p.copy(compositions=p.compositions.map{if(it.index==idx)it.copy(layers=it.layers+layer) else it},keyframes=p.keyframes+tracks))
        _uiState.value=_uiState.value.copy(selectedLayerId=layer.common.id,studioTab=StudioTab.PROPERTIES,statusBanner="Layer preset added")
    }

    private data class LayerClipboard(val projectId:String,val layer:LayerNode,val keyframes:List<com.vexo.studio.model.KeyframeTrack>)
    private var layerClipboard:LayerClipboard?=null

    fun copyLayer(layerId:String){
        val p=_uiState.value.project
        val layer=p.compositions.flatMap{it.layers}.firstOrNull{it.common.id==layerId}?:return
        layerClipboard=LayerClipboard(p.id,layer,p.keyframes.filter{it.targetLayerId==layerId})
        _uiState.value=_uiState.value.copy(clipboardLayerType=layer.type,statusBanner="Copied "+layer.common.name)
    }

    fun pasteLayer(){
        val clip=layerClipboard?:return
        val p=_uiState.value.project
        if(clip.projectId!=p.id){_uiState.value=_uiState.value.copy(statusBanner="Copy a layer from the current project first");return}
        if(p.compositions.sumOf{it.layers.size}>=2048)return
        val idx=_uiState.value.selectedCompositionIndex
        val clone=clip.layer.copy(common=clip.layer.common.copy(id=UUID.randomUUID().toString(),name=clip.layer.common.name+" Copy",locked=false))
        val next=p.copy(compositions=p.compositions.map{if(it.index==idx)it.copy(layers=it.layers+clone) else it},keyframes=p.keyframes+clip.keyframes.map{it.copy(targetLayerId=clone.common.id)})
        if(com.vexo.studio.engine.scene.SceneGraph.resolve(next).hasCycle){_uiState.value=_uiState.value.copy(statusBanner="Cannot paste: circular composition reference");return}
        commitProjectChange(next)
        _uiState.value=_uiState.value.copy(selectedLayerId=clone.common.id,studioTab=StudioTab.PROPERTIES,statusBanner="Layer pasted")
    }

    fun pasteLayerStyle(layerId:String,transformOnly:Boolean){
        val clip=layerClipboard?:return
        val p=_uiState.value.project
        val target=p.compositions.flatMap{it.layers}.firstOrNull{it.common.id==layerId&&!it.common.locked}?:return
        if(!transformOnly&&(clip.projectId!=p.id||clip.layer.type!=target.type)){_uiState.value=_uiState.value.copy(statusBanner="Style requires the same layer type in the current project");return}
        val source=clip.layer.common
        val common=target.common.copy(posX=source.posX,posY=source.posY,anchorX=source.anchorX,anchorY=source.anchorY,alignmentX=source.alignmentX,alignmentY=source.alignmentY,scaleX=source.scaleX,scaleY=source.scaleY,scaleIsUniform=source.scaleIsUniform,rotationDeg=source.rotationDeg,opacity=source.opacity,blendMode=source.blendMode)
        val replacement=target.copy(common=common,config=if(transformOnly)target.config else clip.layer.config)
        val next=p.copy(compositions=p.compositions.map{c->c.copy(layers=c.layers.map{if(it.common.id==layerId)replacement else it})})
        if(com.vexo.studio.engine.scene.SceneGraph.resolve(next).hasCycle){_uiState.value=_uiState.value.copy(statusBanner="Cannot paste style: circular composition reference");return}
        commitProjectChange(next)
        _uiState.value=_uiState.value.copy(statusBanner=if(transformOnly)"Transform pasted" else "Style pasted")
    }

    fun resetLayerTransform(layerId:String){
        updateLayer(layerId){l->if(l.common.locked)l else l.copy(common=l.common.copy(posX=.5f,posY=.5f,anchorX=com.vexo.studio.model.AnchorMode.UNSET,anchorY=com.vexo.studio.model.AnchorMode.UNSET,alignmentX=.5f,alignmentY=.5f,scaleX=1f,scaleY=1f,scaleIsUniform=false,rotationDeg=0f))}
    }

    fun alignLayer(layerId:String,x:Float?,y:Float?){
        updateLayer(layerId){l->
            if(l.common.locked)l else l.copy(common=l.common.copy(posX=x?:l.common.posX,posY=y?:l.common.posY,anchorX=if(x!=null)com.vexo.studio.model.AnchorMode.UNSET else l.common.anchorX,anchorY=if(y!=null)com.vexo.studio.model.AnchorMode.UNSET else l.common.anchorY))
        }
    }

    fun setProjectCamera(camera:com.vexo.studio.model.ProjectCamera){
        val p=_uiState.value.project
        commitProjectChange(p.copy(canvas=p.canvas.copy(camera=camera)))
    }

    fun setCanvasColor(argb:Int){
        val p=_uiState.value.project
        commitProjectChange(p.copy(canvas=p.canvas.copy(backgroundColorArgb=argb)))
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

    /** Isolate the selected layer on a transparent bus so glow has an actual source. */
    fun addGlowLayer() {
        val state = _uiState.value
        val p = state.project
        val comp = p.compositions.firstOrNull { it.index == state.selectedCompositionIndex } ?: return
        val selected = comp.layers.firstOrNull { it.common.id == state.selectedLayerId }
        if (selected == null || selected.common.locked || !selected.common.visible || p.compositions.size >= 64 || p.compositions.sumOf { it.layers.size } >= 2048) {
            _uiState.value = state.copy(statusBanner = "Select a visible, unlocked layer and ensure composition capacity before adding glow")
            return
        }
        val idx = (p.compositions.maxOfOrNull { it.index } ?: 0) + 1
        val bus = CompositionNode(index=idx, name="Glow source: " + selected.common.name, layers=listOf(selected))
        val glow = LayerNode(LayerType.BLUR_EFFECT, LayerCommon(name="Glow • " + selected.common.name, blendMode=BlendMode.ADD),
            LayerConfig.BlurEffect(sourceCompositionIndex=idx, blurRadius=5f, blurMultiplier=2f,
                layer1Scale=1f, layer1ColorArgb=0xAAFFFFFF.toInt(), layer2Scale=1.025f, layer2ColorArgb=0x55FFFFFF,
                blendModeContent=BlendMode.ADD, showUnblurredContentUnder=false, showUnblurredContent=true, unblurredBlendMode=selected.common.blendMode))
        val next = p.copy(compositions=p.compositions.map { if(it.index==comp.index)it.copy(layers=it.layers.map { l->if(l.common.id==selected.common.id)glow else l }) else it }+bus)
        if(com.vexo.studio.engine.scene.SceneGraph.resolve(next).hasCycle) {
            _uiState.value=state.copy(statusBanner="Glow source would create a composition cycle")
            return
        }
        commitProjectChange(next)
        _uiState.value=_uiState.value.copy(selectedLayerId=glow.common.id,studioTab=StudioTab.PROPERTIES,statusBanner="Glow added. Original layer is editable in its source composition.")
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

    fun batchEditLayers(ids:Set<String>,action:com.vexo.studio.engine.scene.BatchLayerAction){
        val p=_uiState.value.project
        val next=com.vexo.studio.engine.scene.BatchLayerEdits.apply(p,_uiState.value.selectedCompositionIndex,ids,action)
        if(next==p)return
        commitProjectChange(next)
        val comp=next.compositions.first{it.index==_uiState.value.selectedCompositionIndex}
        _uiState.value=_uiState.value.copy(selectedLayerId=comp.layers.firstOrNull{it.common.id==_uiState.value.selectedLayerId}?.common?.id?:comp.layers.firstOrNull()?.common?.id,statusBanner="Batch edit applied")
    }

    fun setTimelineTiming(layerId:String,timing:com.vexo.studio.model.LayerTiming){
        updateLayer(layerId){l->if(l.common.locked)l else l.copy(common=l.common.copy(timing=timing))}
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

    fun moveLayerToComposition(layerId:String,target:Int){
        val p=_uiState.value.project
        val source=p.compositions.firstOrNull{c->c.layers.any{it.common.id==layerId}}?:return
        if(source.index==target||p.compositions.none{it.index==target})return
        val layer=source.layers.first{it.common.id==layerId}
        if(layer.common.locked)return
        val next=p.copy(compositions=p.compositions.map{c->when(c.index){
            source.index->c.copy(layers=c.layers.filterNot{it.common.id==layerId})
            target->c.copy(layers=c.layers+layer)
            else->c
        }})
        // Validate even hidden references, so enabling a hidden layer cannot introduce a cycle.
        val allVisible=next.copy(compositions=next.compositions.map{c->c.copy(layers=c.layers.map{it.copy(common=it.common.copy(visible=true))})})
        if(com.vexo.studio.engine.scene.SceneGraph.resolve(allVisible).hasCycle){
            _uiState.value=_uiState.value.copy(statusBanner="Cannot move: circular composition reference")
            return
        }
        commitProjectChange(next)
        _uiState.value=_uiState.value.copy(selectedCompositionIndex=target,selectedLayerId=layerId,statusBanner="Layer moved")
    }

    fun moveLayer(layerId: String, direction: Int) {
        val p = _uiState.value.project
        val compIdx = _uiState.value.selectedCompositionIndex
        val comp=p.compositions.firstOrNull{it.index==compIdx}?:return
        val index=comp.layers.indexOfFirst{it.common.id==layerId}
        if(index !in comp.layers.indices || comp.layers[index].common.locked || index+direction !in comp.layers.indices || direction !in listOf(-1,1))return
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
        if(recordUndo){
            val old=p.compositions.flatMap{it.layers}.firstOrNull{it.common.id==layerId}
            val changed=next.compositions.flatMap{it.layers}.firstOrNull{it.common.id==layerId}
            if(old!=null&&changed!=null && (com.vexo.studio.engine.scene.SceneGraph.referencesOf(old.config)!=com.vexo.studio.engine.scene.SceneGraph.referencesOf(changed.config)||old.common.visible!=changed.common.visible)){
                if(com.vexo.studio.engine.scene.SceneGraph.resolve(next).hasCycle&&!com.vexo.studio.engine.scene.SceneGraph.resolve(p).hasCycle){_uiState.value=_uiState.value.copy(statusBanner="Circular composition reference blocked");return}
            }
        }
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
            projects = updateProjectSummary(cur),unsaved=true
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
        val next = p.copy(audioProviders = p.audioProviders + com.vexo.studio.engine.scene.ProviderBindings.defaults(appSettings.value,nextIdx,"Provider $nextIdx"))
        commitProjectChange(next)
        _uiState.value = _uiState.value.copy(selectedProviderIndex = nextIdx)
    }

    fun duplicateAudioProvider(index:Int){
        val p=_uiState.value.project
        if(p.audioProviders.size>=16)return
        val source=p.audioProviders.firstOrNull{it.index==index}?:return
        val next=(p.audioProviders.maxOfOrNull{it.index}?:0)+1
        commitProjectChange(p.copy(audioProviders=p.audioProviders+source.copy(id=UUID.randomUUID().toString(),index=next,tag=(source.tag+" Copy").take(80))))
        _uiState.value=_uiState.value.copy(selectedProviderIndex=next)
    }
    fun deleteAudioProvider(index:Int){
        val p=_uiState.value.project
        val next=com.vexo.studio.engine.scene.ProviderBindings.remove(p,index)
        if(next==p)return
        commitProjectChange(next)
        _uiState.value=_uiState.value.copy(selectedProviderIndex=next.audioProviders.first().index,statusBanner="Provider removed; its bindings moved to the first remaining provider")
    }
    fun resetAudioProvider(index:Int){
        updateAudioProvider(index){old->com.vexo.studio.engine.scene.ProviderBindings.defaults(appSettings.value,index,old.tag).copy(id=old.id)}
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
            projects = updateProjectSummary(prev),unsaved=true
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
            projects = updateProjectSummary(next),unsaved=true
        )
    }

    fun availableTemplates(): List<VexoProject> = com.vexo.studio.model.StudioSceneCatalog.all()

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

    val sleepRemaining=playbackController.sleepRemaining
    fun setSleepTimer(minutes:Int)=playbackController.setSleepTimer(minutes)

    fun saveCurrentProject():Boolean{
        val p=_uiState.value.project.copy(audioUri=playbackController.currentAudioUri())
        runCatching{synchronized(saveLock){latestSave.remove(p.id);projectRepo.saveProject(p)}}.onSuccess{
            _uiState.value=_uiState.value.copy(project=p,projects=projectRepo.listProjects(),unsaved=false,statusBanner="Project saved")
        }.onFailure{_uiState.value=_uiState.value.copy(statusBanner="Save failed: "+it.message)}.let{return it.isSuccess}
    }

    fun applyPreset(template:VexoProject){
        val state=_uiState.value
        // A recovered or newly created draft can be unsaved with no undo history.
        if(state.unsaved){_uiState.value=state.copy(pendingPreset=template);return}
        applyPresetNow(template)
    }
    fun resolvePresetSwitch(save:Boolean,discard:Boolean){
        val template=_uiState.value.pendingPreset?:return
        if(!save&&!discard){_uiState.value=_uiState.value.copy(pendingPreset=null);return}
        if(save&&!saveCurrentProject())return
        _uiState.value=_uiState.value.copy(pendingPreset=null)
        applyPresetNow(template)
    }
    private fun applyPresetNow(template:VexoProject){
        val current=_uiState.value.project
        val fresh=com.vexo.studio.data.PresetDraft.create(current,template,playbackController.currentAudioUri())
        replaceWithDraft(fresh,true)
        val diagnostics=com.vexo.studio.data.TemplateDiagnostics.inspect(fresh)
        _uiState.value=_uiState.value.copy(statusBanner=diagnostics.banner(fresh))
    }

    fun createNewProject(name:String,template:VexoProject=VexoTemplates.monochromeStudio()){
        val fresh=template.copy(id=UUID.randomUUID().toString(),name=name.ifBlank{"Untitled Studio"},updatedAtMs=System.currentTimeMillis())
        requestProjectSwitch(ProjectSwitch.Create(fresh))
    }

    private fun requestProjectSwitch(target:ProjectSwitch){
        if(_uiState.value.unsaved){_uiState.value=_uiState.value.copy(pendingProjectSwitch=target);return}
        performProjectSwitch(target)
    }
    fun resolveProjectSwitch(save:Boolean,discard:Boolean){
        val target=_uiState.value.pendingProjectSwitch?:return
        if(!save&&!discard){_uiState.value=_uiState.value.copy(pendingProjectSwitch=null);return}
        if(save&&!saveCurrentProject())return
        _uiState.value=_uiState.value.copy(pendingProjectSwitch=null)
        performProjectSwitch(target)
    }
    private fun performProjectSwitch(target:ProjectSwitch){
        when(target){
            is ProjectSwitch.Open->openProjectNow(target.id)
            is ProjectSwitch.Create->replaceWithDraft(target.project,false)
        }
    }

    private fun replaceWithDraft(fresh:VexoProject,keepMusic:Boolean){
        val prior=_uiState.value.project.id
        history.clear()
        val comp=fresh.compositions.firstOrNull{it.index==1}?:fresh.compositions.first()
        synchronized(saveLock){
            latestSave.remove(prior)
            projectRepo.autosave(fresh)
            _uiState.value=_uiState.value.copy(project=fresh,selectedCompositionIndex=comp.index,selectedLayerId=comp.layers.firstOrNull()?.common?.id,selectedProviderIndex=fresh.audioProviders.first().index,previewSelectedComposition=false,previewZoom=1f,previewPanX=0f,previewPanY=0f,canUndo=false,canRedo=false,unsaved=true,navDestination=NavDestination.STUDIO,statusBanner="Unsaved draft · "+fresh.name)
        }
        if(keepMusic)playbackController.rebindProjectQueue(fresh.id)
        else playbackController.loadProjectAudio(fresh.id,fresh.audioUri)
    }

    fun openProject(id:String){
        if(id==_uiState.value.project.id){navigateTo(NavDestination.STUDIO);return}
        requestProjectSwitch(ProjectSwitch.Open(id))
    }

    private fun openProjectNow(id: String) {
        val loaded = synchronized(saveLock) { projectRepo.loadProject(id) } ?: run{
            _uiState.value=_uiState.value.copy(statusBanner="Project unavailable or unreadable")
            return
        }
        synchronized(saveLock){latestSave.remove(_uiState.value.project.id);projectRepo.autosave(loaded)}
        history.clear()
        val comp = loaded.compositions.firstOrNull { it.index == 1 } ?: loaded.compositions.first()
        _uiState.value = _uiState.value.copy(
            project = loaded,
            selectedCompositionIndex = comp.index,
            selectedLayerId = comp.layers.firstOrNull()?.common?.id,
            canUndo = false,
            canRedo = false,
            unsaved=false,previewSelectedComposition=false,previewZoom=1f,previewPanX=0f,previewPanY=0f,selectedProviderIndex=loaded.audioProviders.first().index,
            navDestination = NavDestination.STUDIO
        )
        synchronized(saveLock){projectRepo.markActive(loaded.id)}
        playbackController.loadProjectAudio(loaded.id,loaded.audioUri)
    }

    fun duplicateProject(id: String) {
        val duplicate=synchronized(saveLock){projectRepo.duplicateProject(id)}
        if(duplicate!=null)playbackController.copyProjectQueue(id,duplicate.id)
        _uiState.value = _uiState.value.copy(projects = projectRepo.listProjects(),statusBanner=if(duplicate!=null)"Project and audio queue duplicated" else "Unable to duplicate project")
    }

    fun restorePreviousProject(id:String){
        val restored=synchronized(saveLock){projectRepo.restorePrevious(id)}
        _uiState.value=_uiState.value.copy(projects=projectRepo.listProjects(),statusBanner=if(restored!=null)"Recovered as a separate project" else "No previous saved version available")
    }

    fun renameCurrentProject(newName: String) {
        val clean = newName.trim().ifBlank { return }
        commitProjectChange(_uiState.value.project.copy(name = clean))
    }

    fun deleteProject(id: String) {
        synchronized(saveLock) {latestSave.remove(id);projectRepo.deleteProject(id)}
        playbackController.forgetProjectQueue(id)
        val remaining = projectRepo.listProjects()
        if (_uiState.value.project.id == id) {
            val fallback = remaining.firstOrNull()?.let { projectRepo.loadProject(it.id) }
                ?: VexoTemplates.monochromeStudio()
            history.clear()
            val comp=fallback.compositions.firstOrNull{it.index==0}?:fallback.compositions.first()
            _uiState.value = _uiState.value.copy(
                project = fallback,
                selectedCompositionIndex=comp.index,selectedLayerId=comp.layers.firstOrNull()?.common?.id,
                selectedProviderIndex=fallback.audioProviders.first().index,canUndo=false,canRedo=false,previewSelectedComposition=false,
                projects = projectRepo.listProjects(),unsaved=remaining.isEmpty()
            )
            projectRepo.autosave(fallback)
            playbackController.loadProjectAudio(fallback.id,fallback.audioUri)
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
                    applyPreset(imported)
                    if(_uiState.value.pendingPreset!=null){
                        val diagnostics=com.vexo.studio.data.TemplateDiagnostics.inspect(imported)
                        _uiState.value=_uiState.value.copy(statusBanner="Awaiting preset confirmation · "+diagnostics.banner(imported))
                    }
                }
            }.onFailure { err ->
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(statusBanner = "Preset import failed: ${err.message}")
                }
            }
        }
    }

    fun exportPresetToUri(uri: Uri) {
        val project = _uiState.value.project.copy(audioUri = playbackController.currentAudioUri())
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

    fun clearProjectAudioQueue(){
        playbackController.clearQueue()
        val p=_uiState.value.project
        commitProjectChange(p.copy(audioUri=null))
    }

    fun importAudioTracks(uris: List<Uri>) {
        val projectId=_uiState.value.project.id
        playbackController.importTracks(uris,playFirstImmediately=true){item->
            val current=_uiState.value.project
            if(current.id==projectId)commitProjectChange(current.copy(audioUri=item.uri))
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
            target.onSuccess{path->
                val editable=_uiState.value.project.compositions.any{c->c.layers.any{it.common.id==layerId&&!it.common.locked&&it.config is LayerConfig.Text}}
                if(editable)updateLayer(layerId){l->if(l.config is LayerConfig.Text&&!l.common.locked)l.copy(config=l.config.copy(fontAssetUri=path)) else l}
                else {runCatching{Uri.parse(path).path?.let{java.io.File(it).delete()}};_uiState.value=_uiState.value.copy(statusBanner="Font target is locked or missing")}
            }
                .onFailure{_uiState.value=_uiState.value.copy(statusBanner="Font import failed: "+it.message)}
        }
    }

    fun attachLayerAsset(layerId:String,uri:Uri,role:String){
        if(_uiState.value.project.compositions.none{c->c.layers.any{it.common.id==layerId&&!it.common.locked}}){_uiState.value=_uiState.value.copy(statusBanner="Unlock the target layer before importing assets");return}
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
        if(selected?.type==LayerType.IMAGE&&selected.common.locked){_uiState.value=_uiState.value.copy(statusBanner="Unlock the image layer before replacing media");return}
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
        if(playbackState.value.importTotal>0){_uiState.value=_uiState.value.copy(statusBanner="Finish audio import before exporting");return}
        val audioUri = playbackController.currentAudioUri()
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
            projects = updateProjectSummary(next),unsaved=true
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
