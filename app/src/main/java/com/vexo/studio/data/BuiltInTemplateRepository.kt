package com.vexo.studio.data

import android.content.Context
import com.vexo.studio.model.VexoProject

object BuiltInTemplateRepository {
    private val filenames=listOf("neon-trap-circle.json","minimalist-wave.json","retro-synthwave.json","bass-reactor.json","aurora-particles.json")
    fun load(context: Context): List<VexoProject> = filenames.map { name ->
        context.assets.open("templates/$name").bufferedReader().use { NativeVexCodec.decode(it.readText()) }
    }
}
