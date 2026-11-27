package com.vexo.studio.engine.scene

/** Preserve independent imported glow axes while keeping the native scalar slider usable. */
object LegacyGlowMath {
    private fun safe(v:Float)=v.takeIf{it.isFinite()}?.coerceIn(0f,10f)?:0f
    fun meanScale(x:Float,y:Float)=(safe(x)+safe(y))*.5f
    fun axisRatio(x:Float,y:Float):Float {
        val sum=safe(x)+safe(y)
        return if(sum<=0f)1f else 2f*safe(x)/sum
    }
    fun axes(scale:Float,ratio:Float):Pair<Float,Float>{
        val s=safe(scale)
        val r=ratio.takeIf{it.isFinite()}?.coerceIn(0f,2f)?:1f
        return (s*r).coerceIn(0f,10f) to (s*(2f-r)).coerceIn(0f,10f)
    }
}
