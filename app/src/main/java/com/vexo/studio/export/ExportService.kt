package com.vexo.studio.export

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.vexo.studio.VexoApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Foreground export service displaying live progress notification and cancel control.
 */
class ExportService : Service() {
    private val renderDispatcher = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "vexo-encoder") }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + renderDispatcher)
    private var activeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            if(activeJob!=null){_exportState.value=_exportState.value.copy(isRunning=true,statusText="Cancelling export…");activeJob?.cancel()}
            else {pendingRequest=null;_exportState.value=ExportProgress(statusText="Cancelled");busy.set(false);stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
            return START_NOT_STICKY
        }

        if (activeJob?.isCompleted == false) return START_NOT_STICKY
        ensureChannel()
        val req = pendingRequest ?: run {
            busy.set(false)
            stopSelf()
            return START_NOT_STICKY
        }
        pendingRequest = null

        try{startForeground(NOTIF_ID, buildNotification(0, "Starting export...", 0L))}catch(e:Exception){_exportState.value=ExportProgress(statusText="Cannot start foreground export",errorMessage=e.message);busy.set(false);stopSelf();return START_NOT_STICKY}
        if (activeJob?.isActive == true) return START_NOT_STICKY
        activeJob = scope.launch {
            var terminal:ExportProgress?=null
            var lastNotificationMs = 0L
            var lastNotificationText = ""
            val exporter = VexoVideoExporter(applicationContext)
            try {
                exporter.export(
                    project = req.project,
                    audioUriStr = req.audioUri,
                    trackMeta = req.trackMeta,
                    onProgress = progress@{ prog ->
                        if(!prog.isRunning){terminal=prog;return@progress}
                        _exportState.value = prog
                        val pct = (prog.progress * 100).toInt().coerceIn(0, 100)
                        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        val now = android.os.SystemClock.elapsedRealtime()
                        val stage = prog.statusText.substringBefore(" frame ")
                        if (now - lastNotificationMs >= 500L || stage != lastNotificationText) {
                            lastNotificationMs = now
                            lastNotificationText = stage
                            // A notification failure must not invalidate an otherwise valid video.
                            runCatching { nm.notify(NOTIF_ID, buildNotification(pct, prog.statusText, prog.etaSeconds)) }
                        }
                    }
                )
            } catch(e:kotlinx.coroutines.CancellationException){
                terminal=terminal?.takeIf{it.publishedUri!=null}?:ExportProgress(statusText="Cancelled")
            } catch(e:Exception){
                terminal=ExportProgress(statusText="Export failed",errorMessage=e.message)
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                _exportState.value=terminal?:ExportProgress(statusText="Export finished")
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        val job=activeJob
        if(job==null||job.isCompleted){
            renderDispatcher.close()
            busy.set(false)
        }else job.invokeOnCompletion{
            renderDispatcher.close()
            busy.set(false)
        }
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(CHANNEL_ID, "Vexo Video Export", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(percent: Int, status: String, etaSec: Long) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Exporting Video ($percent%)")
            .setContentText(if (etaSec > 0) "$status · ~${etaSec}s left" else status)
            .setProgress(100, percent, percent == 0)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                PendingIntent.getService(
                    this,
                    1,
                    Intent(this, ExportService::class.java).apply { action = ACTION_CANCEL },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    data class ExportRequest(
        val project: com.vexo.studio.model.VexoProject,
        val audioUri: String,
        val trackMeta: com.vexo.studio.engine.gl.TrackMetadataSnapshot
    )

    companion object {
        private const val CHANNEL_ID = "vexo_export"
        private const val NOTIF_ID = 4201
        private const val ACTION_CANCEL = "com.vexo.studio.CANCEL_EXPORT"

        @Volatile
        var pendingRequest: ExportRequest? = null

        private val busy=java.util.concurrent.atomic.AtomicBoolean(false)
        private val _exportState = MutableStateFlow(ExportProgress())
        val exportState: StateFlow<ExportProgress> = _exportState.asStateFlow()

        fun startExport(context: Context, request: ExportRequest) {
            if(!busy.compareAndSet(false,true))return
            pendingRequest = request
            _exportState.value=ExportProgress(isRunning=true,statusText="Preparing export")
            val intent = Intent(context, ExportService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
            }catch(e:Exception){pendingRequest=null;busy.set(false);_exportState.value=ExportProgress(statusText="Cannot start export",errorMessage=e.message)}
        }

        fun cancelExport(context: Context) {
            val intent = Intent(context, ExportService::class.java).apply { action = ACTION_CANCEL }
            runCatching{context.startService(intent)}
        }

        fun resetStatus() {
            if(!busy.get())_exportState.value = ExportProgress()
        }
    }
}
