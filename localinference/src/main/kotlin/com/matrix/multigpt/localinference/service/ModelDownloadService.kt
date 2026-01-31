package com.matrix.multigpt.localinference.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.matrix.multigpt.localinference.LocalInferenceProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground service for downloading AI models in the background.
 * Shows a notification with download progress that persists even when app is minimized.
 */
class ModelDownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "model_download_channel"
        const val NOTIFICATION_ID = 1001
        
        const val ACTION_START_DOWNLOAD = "com.matrix.multigpt.START_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.matrix.multigpt.CANCEL_DOWNLOAD"
        const val ACTION_DOWNLOAD_PROGRESS = "com.matrix.multigpt.DOWNLOAD_PROGRESS"
        const val ACTION_DOWNLOAD_COMPLETE = "com.matrix.multigpt.DOWNLOAD_COMPLETE"
        const val ACTION_DOWNLOAD_FAILED = "com.matrix.multigpt.DOWNLOAD_FAILED"
        
        const val EXTRA_MODEL_ID = "model_id"
        const val EXTRA_MODEL_NAME = "model_name"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_DOWNLOADED_BYTES = "downloaded_bytes"
        const val EXTRA_TOTAL_BYTES = "total_bytes"
        const val EXTRA_ERROR_MESSAGE = "error_message"
        
        // Active downloads tracking (shared across instances)
        private val _activeDownloads = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
        val activeDownloads: StateFlow<Map<String, DownloadState>> = _activeDownloads.asStateFlow()
        
        fun startDownload(context: Context, modelId: String, modelName: String) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_MODEL_ID, modelId)
                putExtra(EXTRA_MODEL_NAME, modelName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun cancelDownload(context: Context, modelId: String) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                action = ACTION_CANCEL_DOWNLOAD
                putExtra(EXTRA_MODEL_ID, modelId)
            }
            context.startService(intent)
        }
        
        fun isDownloading(modelId: String): Boolean {
            return _activeDownloads.value[modelId]?.isActive == true
        }
    }
    
    data class DownloadState(
        val modelId: String,
        val modelName: String,
        val progress: Float = 0f,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = 0L,
        val isActive: Boolean = true,
        val error: String? = null
    )
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadJobs = mutableMapOf<String, Job>()
    private lateinit var notificationManager: NotificationManager
    
    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val modelId = intent.getStringExtra(EXTRA_MODEL_ID) ?: return START_NOT_STICKY
                val modelName = intent.getStringExtra(EXTRA_MODEL_NAME) ?: modelId
                startModelDownload(modelId, modelName)
            }
            ACTION_CANCEL_DOWNLOAD -> {
                val modelId = intent.getStringExtra(EXTRA_MODEL_ID)
                if (modelId != null) {
                    cancelModelDownload(modelId)
                }
            }
        }
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Model Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows download progress for AI models"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun startModelDownload(modelId: String, modelName: String) {
        // Don't start if already downloading
        if (downloadJobs.containsKey(modelId)) return
        
        // Update state
        _activeDownloads.value = _activeDownloads.value + (modelId to DownloadState(
            modelId = modelId,
            modelName = modelName,
            isActive = true
        ))
        
        // Start foreground with initial notification
        startForeground(NOTIFICATION_ID, createProgressNotification(modelName, 0f, 0L, 0L))
        
        val job = serviceScope.launch {
            try {
                val provider = LocalInferenceProvider.getInstance(this@ModelDownloadService)
                
                // Start download
                provider.downloadModel(modelId)
                
                // Poll for progress
                var lastProgress = 0f
                val downloadManager = provider.downloadManager
                
                repeat(3600) { // Max 30 minutes
                    delay(500)
                    
                    // Check if cancelled
                    if (!isActive) return@launch
                    
                    // Check if completed
                    if (downloadManager.isModelDownloaded(modelId)) {
                        onDownloadComplete(modelId, modelName)
                        return@launch
                    }
                    
                    // Get progress
                    val states = downloadManager.modelStates.value
                    val state = states[modelId]
                    if (state != null) {
                        val progress = state.downloadProgress
                        val downloaded = state.downloadedBytes
                        val total = state.totalBytes
                        
                        if (progress != lastProgress) {
                            lastProgress = progress
                            onProgressUpdate(modelId, modelName, progress, downloaded, total)
                        }
                    }
                }
                
                // Timeout
                onDownloadFailed(modelId, modelName, "Download timed out")
                
            } catch (e: CancellationException) {
                // Cancelled - cleanup handled in finally
            } catch (e: Exception) {
                onDownloadFailed(modelId, modelName, e.message ?: "Download failed")
            } finally {
                downloadJobs.remove(modelId)
                _activeDownloads.value = _activeDownloads.value - modelId
                
                // Stop service if no more downloads
                if (downloadJobs.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        
        downloadJobs[modelId] = job
    }
    
    private fun cancelModelDownload(modelId: String) {
        downloadJobs[modelId]?.cancel()
        downloadJobs.remove(modelId)
        _activeDownloads.value = _activeDownloads.value - modelId
        
        // Cancel in download manager too
        try {
            val provider = LocalInferenceProvider.getInstance(this)
            provider.downloadManager.cancelDownload(modelId)
        } catch (e: Exception) {
            // Ignore
        }
        
        // Update notification or stop service
        if (downloadJobs.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            // Update notification for remaining downloads
            val firstDownload = _activeDownloads.value.values.firstOrNull()
            if (firstDownload != null) {
                notificationManager.notify(
                    NOTIFICATION_ID,
                    createProgressNotification(firstDownload.modelName, firstDownload.progress, firstDownload.downloadedBytes, firstDownload.totalBytes)
                )
            }
        }
    }
    
    private fun onProgressUpdate(modelId: String, modelName: String, progress: Float, downloadedBytes: Long, totalBytes: Long) {
        // Update state
        _activeDownloads.value = _activeDownloads.value + (modelId to DownloadState(
            modelId = modelId,
            modelName = modelName,
            progress = progress,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            isActive = true
        ))
        
        // Update notification
        notificationManager.notify(
            NOTIFICATION_ID,
            createProgressNotification(modelName, progress, downloadedBytes, totalBytes)
        )
        
        // Broadcast for UI update
        sendBroadcast(Intent(ACTION_DOWNLOAD_PROGRESS).apply {
            putExtra(EXTRA_MODEL_ID, modelId)
            putExtra(EXTRA_PROGRESS, progress)
            putExtra(EXTRA_DOWNLOADED_BYTES, downloadedBytes)
            putExtra(EXTRA_TOTAL_BYTES, totalBytes)
            setPackage(packageName)
        })
    }
    
    private fun onDownloadComplete(modelId: String, modelName: String) {
        // Show completion notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Download Complete")
            .setContentText("$modelName is ready to use")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(modelId.hashCode(), notification)
        
        // Broadcast completion
        sendBroadcast(Intent(ACTION_DOWNLOAD_COMPLETE).apply {
            putExtra(EXTRA_MODEL_ID, modelId)
            setPackage(packageName)
        })
    }
    
    private fun onDownloadFailed(modelId: String, modelName: String, error: String) {
        // Update state
        _activeDownloads.value = _activeDownloads.value + (modelId to DownloadState(
            modelId = modelId,
            modelName = modelName,
            isActive = false,
            error = error
        ))
        
        // Show error notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Download Failed")
            .setContentText("$modelName: $error")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(modelId.hashCode(), notification)
        
        // Broadcast failure
        sendBroadcast(Intent(ACTION_DOWNLOAD_FAILED).apply {
            putExtra(EXTRA_MODEL_ID, modelId)
            putExtra(EXTRA_ERROR_MESSAGE, error)
            setPackage(packageName)
        })
    }
    
    private fun createProgressNotification(modelName: String, progress: Float, downloadedBytes: Long, totalBytes: Long): Notification {
        val progressPercent = (progress * 100).toInt()
        
        // Cancel action
        val cancelIntent = Intent(this, ModelDownloadService::class.java).apply {
            action = ACTION_CANCEL_DOWNLOAD
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, 0, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val contentText = if (downloadedBytes > 0 && totalBytes > 0) {
            "${formatSize(downloadedBytes)} / ${formatSize(totalBytes)}"
        } else {
            "Downloading... $progressPercent%"
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading $modelName")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progressPercent, progressPercent == 0)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, "Cancel", cancelPendingIntent)
            .build()
    }
    
    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
