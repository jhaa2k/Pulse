package com.vexo.studio.data
import android.content.Context
import android.os.Build
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Local-only minimal fatal-error report. No exception message, user URI or media metadata. */
object LocalCrashReport{
    private val installed=AtomicBoolean(false)
    private fun file(context:Context)=File(context.filesDir,"last_crash.txt")
    fun install(context:Context){
        if(!installed.compareAndSet(false,true))return
        val app=context.applicationContext
        val prior=Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler{thread,error->
            runCatching{
                val report=buildString{
                    appendLine("VEXO Studio fatal error")
                    appendLine("Android SDK "+Build.VERSION.SDK_INT)
                    appendLine("Recorded UTC epoch ms: "+System.currentTimeMillis())
                    appendLine("Type: "+error.javaClass.name)
                    error.stackTrace.filter{it.className.startsWith("com.vexo.studio.")}.take(24).forEach{appendLine(it.className+"."+it.methodName+":"+it.lineNumber)}
                }
                file(app).writeText(report.take(8192))
            }
            if(prior!=null)prior.uncaughtException(thread,error)
            else {android.os.Process.killProcess(android.os.Process.myPid());kotlin.system.exitProcess(10)}
        }
    }
    fun read(context:Context):String=runCatching{file(context).takeIf{it.isFile&&it.length()<=8192}?.readText().orEmpty()}.getOrDefault("")
    fun clear(context:Context){runCatching{file(context).delete()}}
}
