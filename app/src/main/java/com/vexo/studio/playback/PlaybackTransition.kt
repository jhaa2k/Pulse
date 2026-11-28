package com.vexo.studio.playback
object PlaybackTransition {
    fun gain(fadeMs:Int,remainingMs:Long,sinceTransitionMs:Long,hasNext:Boolean,playing:Boolean):Float {
        if(fadeMs<=0||!playing)return 1f
        val out=if(hasNext)(remainingMs.toFloat()/fadeMs).coerceIn(0f,1f) else 1f
        val input=(sinceTransitionMs.toFloat()/fadeMs).coerceIn(0f,1f)
        return minOf(input,out)
    }
}
