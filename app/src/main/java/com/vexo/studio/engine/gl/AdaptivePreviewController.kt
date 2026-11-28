package com.vexo.studio.engine.gl

/** Changes resolution only after sustained load; never exceeds the user's chosen ceiling. */
class AdaptivePreviewController(maxScale:Float,targetFps:Int){
    private val ceiling=maxScale.takeIf{it.isFinite()}?.coerceIn(.5f,1f)?:1f
    private val levels=(listOf(ceiling,.85f,.7f,.5f).filter{it<=ceiling}).distinct().sortedDescending()
    private val target=targetFps.coerceIn(15,60)
    private var index=0
    private var slow=0
    private var fast=0
    private var cooldown=0
    val scale:Float get()=levels[index]
    fun observe(fps:Float,cpuMs:Float,error:Boolean=false):Float{
        if(!fps.isFinite()||!cpuMs.isFinite()||fps<=0f)return scale
        if(cooldown>0){cooldown--;return scale}
        val budget=1000f/target
        val overloaded=error||fps<target*.78f||cpuMs>budget*.92f
        val comfortable=!error&&fps>=target*.9f&&cpuMs<budget*.55f
        slow=if(overloaded)slow+1 else 0
        fast=if(comfortable)fast+1 else 0
        if(slow>=3&&index<levels.lastIndex){index++;slow=0;fast=0;cooldown=4}
        else if(fast>=10&&index>0){index--;slow=0;fast=0;cooldown=4}
        return scale
    }
}
object TextureDecodePolicy{
    fun limit(export:Boolean,lowRam:Boolean,highResolution:Boolean,outputMax:Int,gpuMax:Int):Int{
        val requested=when{
            !export&&lowRam->1024
            export&&highResolution&&!lowRam&&outputMax>2048->4096
            else->2048
        }
        return minOf(requested,gpuMax.coerceAtLeast(16))
    }
    fun sampleSize(width:Int,height:Int,limit:Int):Int{
        val largest=maxOf(width,height).coerceAtLeast(1).toLong()
        val bound=limit.coerceAtLeast(1).toLong()
        var sample=1
        while((largest+sample-1)/sample>bound&&sample<(1 shl 30))sample*=2
        return sample
    }
}
