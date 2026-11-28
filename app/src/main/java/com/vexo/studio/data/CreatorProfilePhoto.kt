package com.vexo.studio.data
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.UUID

internal object CreatorProfilePhoto {
    private const val PREFIX="local-profile:"
    private fun directory(context:Context)=File(context.filesDir,"creator-profile")
    fun save(context:Context,uri:Uri):String?=runCatching{
        val bitmap=UiWallpaper.decode(context,uri.toString())?:return null
        val scale=(512f/maxOf(bitmap.width,bitmap.height)).coerceAtMost(1f)
        val scaled=Bitmap.createScaledBitmap(bitmap,(bitmap.width*scale).toInt().coerceAtLeast(1),(bitmap.height*scale).toInt().coerceAtLeast(1),true)
        val dir=directory(context)
        check(dir.isDirectory||dir.mkdirs())
        val name=UUID.randomUUID().toString()+".jpg"
        val file=File(dir,name)
        try{
            file.outputStream().use{check(scaled.compress(Bitmap.CompressFormat.JPEG,90,it))}
            PREFIX+name
        }catch(e:Exception){file.delete();throw e}
    }.getOrNull()
    private fun resolve(context:Context,source:String):File?{
        if(!source.startsWith(PREFIX))return null
        val name=source.removePrefix(PREFIX)
        if(!Regex("[0-9a-f-]{36}\\.jpg").matches(name))return null
        return File(directory(context),name)
    }
    fun decode(context:Context,source:String):Bitmap?=
        if(source.startsWith(PREFIX))runCatching{resolve(context,source)?.let{BitmapFactory.decodeFile(it.absolutePath)}}.getOrNull()
        else UiWallpaper.decode(context,source)
    fun remove(context:Context,source:String){runCatching{resolve(context,source)?.delete()}}
}
