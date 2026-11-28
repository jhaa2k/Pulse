package com.vexo.studio.export
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** An editable display name, never a filesystem path. */
object ExportFileName {
    fun defaultName(now:Long=System.currentTimeMillis()):String =
        "VEXO_"+SimpleDateFormat("yyyy-MM-dd_HH-mm-ss",Locale.US).format(Date(now))+".mp4"
    fun normalize(raw:String,now:Long=System.currentTimeMillis()):String{
        var base=raw.trim()
        if(base.endsWith(".mp4",ignoreCase=true))base=base.dropLast(4)
        base=base.map{c->if(c.isISOControl()||c in "/\\:*?\"<>|") '_' else c}.joinToString("").trim().trim('.').trim()
        // Cap UTF-8 bytes rather than chopping a Unicode surrogate pair.
        while(base.toByteArray(Charsets.UTF_8).size>200){
            val end=base.length-Character.charCount(base.codePointBefore(base.length))
            base=base.substring(0,end)
        }
        base=base.trim().trim('.')
        return if(base.isBlank())defaultName(now) else base+".mp4"
    }
}
