package com.vexo.studio.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

/** Bounded UI-only decoding. Does not enter the visualizer scene or video export. */
object UiWallpaper {
    fun decode(context:Context,source:String):Bitmap? {
        if(source.isBlank())return null
        return runCatching{
            val uri=Uri.parse(source)
            val resolver=context.contentResolver
            val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
            resolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,bounds)}
            if(bounds.outWidth<=0||bounds.outHeight<=0)return null
            var sample=1
            while(maxOf(bounds.outWidth,bounds.outHeight)/sample>1920)sample*=2
            val options=BitmapFactory.Options().apply{inSampleSize=sample;inPreferredConfig=Bitmap.Config.ARGB_8888}
            resolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,options)}
        }.getOrNull()
    }
}
