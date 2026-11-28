package com.vexo.studio.playback

import android.content.Context
import android.media.audiofx.Equalizer
import android.media.audiofx.BassBoost
import android.media.audiofx.Virtualizer
import android.media.audiofx.PresetReverb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.Closeable

data class SoundBand(val index: Int,val hz: Int,val level: Int)
data class SoundState(
    val enabled: Boolean=false,val bands: List<SoundBand> = emptyList(),
    val minLevel: Int=-1500,val maxLevel: Int=1500,
    val presets: List<String> = emptyList(),val preset: Int=-1,
    val bass: Int=0,val virtual: Int=0,val reverb: Int=0,
    val bassSupported: Boolean=false,val virtualSupported: Boolean=false,val reverbSupported: Boolean=false,
    val message: String?=null
)

/** Session-scoped optional Android effects. No output-mix/global effect. */
class SoundEffects(context: Context): Closeable {
    private val prefs=context.getSharedPreferences("sound_effects",Context.MODE_PRIVATE)
    private var eq: Equalizer?=null
    private var bass: BassBoost?=null
    private var virtual: Virtualizer?=null
    private var reverb: PresetReverb?=null
    private val _state=MutableStateFlow(SoundState(enabled=prefs.getBoolean("enabled",false)))
    val state=_state.asStateFlow()
    fun attach(id: Int) {
        close()
        if(id<=0) return
        eq=runCatching{Equalizer(0,id)}.getOrNull()
        bass=runCatching{BassBoost(0,id)}.getOrNull()
        virtual=runCatching{Virtualizer(0,id)}.getOrNull()
        reverb=runCatching{PresetReverb(0,id)}.getOrNull()
        val q=eq
        val range=runCatching{q?.bandLevelRange}.getOrNull()
        val bands=runCatching{(0 until (q?.numberOfBands?.toInt()?:0)).map{i->
            val level=prefs.getInt("band_$i",0).coerceIn(range?.get(0)?.toInt()?:-1500,range?.get(1)?.toInt()?:1500)
            q?.setBandLevel(i.toShort(),level.toShort())
            SoundBand(i,(q?.getCenterFreq(i.toShort())?:0)/1000,level)
        }}.getOrDefault(emptyList())
        val presets=runCatching{(0 until (q?.numberOfPresets?.toInt()?:0)).map{i->q?.getPresetName(i.toShort()).orEmpty()}}.getOrDefault(emptyList())
        _state.value=SoundState(prefs.getBoolean("enabled",false),bands,range?.get(0)?.toInt()?:-1500,range?.get(1)?.toInt()?:1500,presets,
            bass=prefs.getInt("bass",0),virtual=prefs.getInt("virtual",0),reverb=prefs.getInt("reverb",0),
            bassSupported=runCatching{bass?.strengthSupported==true}.getOrDefault(false),
            virtualSupported=runCatching{virtual?.strengthSupported==true}.getOrDefault(false),reverbSupported=reverb!=null,
            message=if(q==null)"Device equalizer unavailable" else null)
        apply()
    }
    private fun apply() {
        val s=_state.value
        val results=listOf(
            runCatching{eq?.setEnabled(s.enabled)},
            runCatching{bass?.setStrength(s.bass.toShort());bass?.setEnabled(s.enabled&&s.bass>0)},
            runCatching{virtual?.setStrength(s.virtual.toShort());virtual?.setEnabled(s.enabled&&s.virtual>0)},
            runCatching{reverb?.setPreset(s.reverb.toShort());reverb?.setEnabled(s.enabled&&s.reverb>0)}
        )
        if(results.any{it.isFailure || (it.getOrNull()?:0)!=0}) _state.value=s.copy(message="One or more audio effects are unsupported or unavailable")
    }
    fun enable(value: Boolean){_state.value=_state.value.copy(enabled=value,message=null);prefs.edit().putBoolean("enabled",value).apply();apply()}
    fun band(index:Int,level:Int){
        val s=_state.value;val v=level.coerceIn(s.minLevel,s.maxLevel)
        if(s.bands.none{it.index==index})return
        runCatching{eq?.setBandLevel(index.toShort(),v.toShort())}.onSuccess{
            _state.value=s.copy(bands=s.bands.map{if(it.index==index)it.copy(level=v) else it},preset=-1,message=null)
            prefs.edit().putInt("band_$index",v).apply()
        }.onFailure{_state.value=s.copy(message=it.message)}
    }
    fun preset(index:Int){
        val s=_state.value;val q=eq?:return
        if(index !in s.presets.indices)return
        runCatching{q.usePreset(index.toShort());s.bands.map{it.copy(level=q.getBandLevel(it.index.toShort()).toInt())}}.onSuccess{bands->
            _state.value=s.copy(bands=bands,preset=index,message=null)
            val edit=prefs.edit();bands.forEach{edit.putInt("band_"+it.index,it.level)};edit.apply()
        }.onFailure{_state.value=s.copy(message=it.message)}
    }
    fun bass(value:Int){val v=value.coerceIn(0,1000);_state.value=_state.value.copy(bass=v,message=null);prefs.edit().putInt("bass",v).apply();apply()}
    fun virtual(value:Int){val v=value.coerceIn(0,1000);_state.value=_state.value.copy(virtual=v,message=null);prefs.edit().putInt("virtual",v).apply();apply()}
    fun reverb(value:Int){val v=value.coerceIn(0,6);_state.value=_state.value.copy(reverb=v,message=null);prefs.edit().putInt("reverb",v).apply();apply()}
    override fun close(){listOf(eq,bass,virtual,reverb).forEach{runCatching{it?.release()}};eq=null;bass=null;virtual=null;reverb=null}
}
