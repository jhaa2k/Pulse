package com.vexo.studio.engine.gl
object VideoDecodeMath{
    fun shouldSeek(previous:Long,target:Long)=previous<0||target<previous||target-previous>2_000_000L
    fun uv(rotation:Int):FloatArray{
        val p=floatArrayOf(0f,0f,1f,0f,0f,1f,1f,1f)
        for(i in 0..3){
            val x=p[2*i];val y=p[2*i+1]
            val (sx,sy)=when((rotation%360+360)%360){90->y to (1f-x);180->(1f-x) to (1f-y);270->(1f-y) to x;else->x to y}
            p[2*i]=sx;p[2*i+1]=1f-sy
        }
        return p
    }
}
