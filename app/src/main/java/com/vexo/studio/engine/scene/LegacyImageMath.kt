package com.vexo.studio.engine.scene

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import java.util.Locale

object LegacyImageMath {
    /** Native colors are ARGB; legacy chsla properties use normalized HSLA. */
    fun argbToHsla(argb:Int):String {
        val a=(argb ushr 24 and 255)/255f
        val r=(argb ushr 16 and 255)/255f
        val g=(argb ushr 8 and 255)/255f
        val b=(argb and 255)/255f
        val hi=max(r,max(g,b));val lo=min(r,min(g,b))
        val d=hi-lo;val l=(hi+lo)*.5f
        val s=if(d==0f)0f else d/(1f-abs(2f*l-1f))
        val hue=if(d==0f)0f else when(hi){
            r->((g-b)/d)%6f
            g->(b-r)/d+2f
            else->(r-g)/d+4f
        }
        val h=((hue/6f)%1f+1f)%1f
        return String.format(Locale.ROOT,"%.6f %.6f %.6f %.6f",h,s.coerceIn(0f,1f),l,a)
    }

    private val token=Regex("\\{(Title|Artist|Album|Time|TimeCurrent|Duration|TimeLength|Progress)\\}",RegexOption.IGNORE_CASE)
    private fun time(ms:Long):String{
        val sec=ms.coerceAtLeast(0L)/1000
        return String.format(Locale.ROOT,"%d:%02d",sec/60,sec%60)
    }
    /** Resolve only tokens in the original text, never tokens appearing inside metadata. */
    fun resolveTrackText(text:String,title:String,artist:String,album:String,position:Long,duration:Long):String =
        token.replace(text){match->
            when(match.groupValues[1].lowercase(Locale.ROOT)){
                "title"->title
                "artist"->artist
                "album"->album
                "time","timecurrent"->time(position)
                "duration","timelength"->time(duration)
                else->if(duration>0)((position.toDouble()/duration).coerceIn(0.0,1.0)*100).toInt().toString()+"%" else "0%"
            }
        }
}
