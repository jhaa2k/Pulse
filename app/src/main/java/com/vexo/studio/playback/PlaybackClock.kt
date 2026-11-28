package com.vexo.studio.playback
object PlaybackClock{
    fun remainingSeconds(deadline:Long,now:Long):Long{
        if(deadline<=now||deadline<=0)return 0L
        val remain=deadline-now
        return remain/1000L+if(remain%1000L!=0L)1L else 0L
    }
}
