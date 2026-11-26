package com.vexo.studio.playback

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LibraryTrack(val id: Long,val uri: String,val title: String,val artist: String,
    val album: String,val folder: String,val durationMs: Long)

class MediaLibrary(private val context: Context) {
    /** Call after READ_MEDIA_AUDIO (33+) or READ_EXTERNAL_STORAGE (24–32) is granted. */
    suspend fun tracks(): List<LibraryTrack> = withContext(Dispatchers.IO) {
        val table=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val cols=arrayOf(MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DATA,MediaStore.Audio.Media.DURATION)
        val out=ArrayList<LibraryTrack>()
        context.contentResolver.query(table,cols,
            MediaStore.Audio.Media.IS_MUSIC+" != 0",null,MediaStore.Audio.Media.TITLE+" ASC")?.use { c ->
            while(c.moveToNext()) {
                val id=c.getLong(0)
                out.add(LibraryTrack(id,ContentUris.withAppendedId(table,id).toString(),
                    c.getString(1).orEmpty(),c.getString(2).orEmpty(),c.getString(3).orEmpty(),
                    c.getString(4).orEmpty().substringBeforeLast('/',"Unknown Folder"),c.getLong(5)))
            }
        }
        out
    }
}
