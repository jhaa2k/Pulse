package com.vexo.studio.export
object ExportBudget{
    fun encodedBytes(durationUs:Long,videoBps:Int,audioBps:Int):Long =
        (durationUs.coerceAtLeast(0).toDouble()/1_000_000*(videoBps.toLong()+audioBps)/8).toLong()
    fun workingBytes(durationUs:Long,videoBps:Int,audioBps:Int,sampleRate:Int,channels:Int):Long{
        val pcm=(durationUs.coerceAtLeast(0).toDouble()/1_000_000*sampleRate*channels*2).toLong()
        return (encodedBytes(durationUs,videoBps,audioBps)*1.35).toLong()+pcm+32L*1024*1024
    }
}
