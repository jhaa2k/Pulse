package com.vexo.studio.data

import com.vexo.studio.model.VexoProject
import java.util.UUID

object PresetDraft {
    fun create(current:VexoProject,template:VexoProject,audioUri:String?,id:String=UUID.randomUUID().toString(),now:Long=System.currentTimeMillis()):VexoProject=
        template.copy(id=id,updatedAtMs=now,audioUri=audioUri,
            canvas=template.canvas.copy(aspectRatio=current.canvas.aspectRatio),
            exportSettings=current.exportSettings.copy(aspectRatio=current.canvas.aspectRatio,trimStartMs=0L,trimEndMs=0L,customOutputUri=null))
}
