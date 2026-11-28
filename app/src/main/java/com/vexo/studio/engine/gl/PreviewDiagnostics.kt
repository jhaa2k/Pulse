package com.vexo.studio.engine.gl

data class PreviewDiagnostics(
    val fps:Float=0f,
    val cpuFrameMs:Float=0f,
    val width:Int=0,
    val height:Int=0,
    val compositions:Int=0,
    val layers:Int=0,
    val textureBytes:Long=0L,
    val framebufferBytes:Long=0L,
    val compositionSlots:Int=0
)
internal class FrameDiagnosticsCounter{
    private var start=0L
    private var count=0
    private var sum=0.0
    fun reset(){start=0;count=0;sum=0.0}
    fun frame(now:Long,cpuMs:Float):Pair<Float,Float>?{
        if(start==0L){start=now;return null}
        count++;sum+=cpuMs.toDouble()
        val elapsed=now-start
        if(elapsed<500_000_000L)return null
        val result=(count*1_000_000_000.0/elapsed).toFloat() to (sum/count).toFloat()
        start=now;count=0;sum=0.0
        return result
    }
}
