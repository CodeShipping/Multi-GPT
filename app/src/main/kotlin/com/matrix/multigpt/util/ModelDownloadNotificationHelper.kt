package com.matrix.multigpt.util

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Helper class to show download progress notifications.
 * Works directly in the app module without needing dynamic feature reflection.
 */
object ModelDownloadNotificationHelper {
    
    private const val CHANNEL_ID = "model_download_channel"
    private const val CHANNEL_NAME = "Model Downloads"
    
    /**
     * Check if notification permission is granted (Android 13+)
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    
    /**
     * Create notification channel (required for Android 8+)
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows download progress for AI models"
                setShowBadge(false)
            }
            
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
    
    /**
     * Show download progress notification
     */
    fun showProgressNotification(
        context: Context,
        modelId: String,
        modelName: String,
        progress: Float,
        downloadedBytes: Long,
        totalBytes: Long
    ) {
        if (!hasNotificationPermission(context)) {
            return
        }
        
        createNotificationChannel(context)
        
        val progressPercent = (progress * 100).toInt()
        val contentText = if (downloadedBytes > 0 && totalBytes > 0) {
            "${formatSize(downloadedBytes)} / ${formatSize(totalBytes)}"
        } else {
            "Downloading... $progressPercent%"
        }
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Downloading $modelName")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progressPercent, progressPercent == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        
        val notificationManager = NotificationManagerCompat.from(context)
        try {
            notificationManager.notify(modelId.hashCode(), notification)
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }
    
    /**
     * Show download complete notification
     */
    fun showCompleteNotification(context: Context, modelId: String, modelName: String) {
        if (!hasNotificationPermission(context)) {
            return
        }
        
        createNotificationChannel(context)
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Download Complete")
            .setContentText("$modelName is ready to use")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = NotificationManagerCompat.from(context)
        try {
            // Cancel progress notification
            notificationManager.cancel(modelId.hashCode())
            // Show completion notification with different ID
            notificationManager.notify(modelId.hashCode() + 1000, notification)
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }
    
    /**
     * Show download failed notification
     */
    fun showFailedNotification(context: Context, modelId: String, modelName: String, error: String) {
        if (!hasNotificationPermission(context)) {
            return
        }
        
        createNotificationChannel(context)
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Download Failed")
            .setContentText("$modelName: $error")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = NotificationManagerCompat.from(context)
        try {
            // Cancel progress notification
            notificationManager.cancel(modelId.hashCode())
            // Show failure notification
            notificationManager.notify(modelId.hashCode() + 2000, notification)
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }
    
    /**
     * Cancel download notification
     */
    fun cancelNotification(context: Context, modelId: String) {
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(modelId.hashCode())
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
