package com.kaenova.m.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.kaenova.m.MainActivity
import com.kaenova.m.R
import com.kaenova.m.data.api.MifiApiClient
import com.kaenova.m.data.model.MifiMetrics
import com.kaenova.m.data.repository.MifiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MifiMonitorService : Service() {

    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private val apiClient by lazy { MifiApiClient() }
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private var isMonitoring = false
    private var lastMetrics: MifiMetrics? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MifiMonitorService created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called with action: ${intent?.action}")

        when (intent?.action) {
            ACTION_START -> startMonitoring()
            ACTION_STOP -> stopMonitoring()
        }

        return START_STICKY
    }

    private fun startMonitoring() {
        if (isMonitoring) return

        isMonitoring = true
        Log.d(TAG, "Starting MiFi monitoring")

        scope.launch {
            while (isMonitoring) {
                try {
                    val result = apiClient.getMifiMetrics()
                    result.onSuccess { metrics ->
                        lastMetrics = metrics
                        MifiRepository.updateMetrics(metrics)
                        updateNotification(metrics)
                        Log.d(TAG, "Metrics updated: ${metrics.signalStrength}")
                    }.onFailure { error ->
                        Log.e(TAG, "Failed to fetch metrics: ${error.message}")
                        val errorMetrics = MifiMetrics(
                            isConnected = false,
                            error = "Connection error: ${error.message}"
                        )
                        MifiRepository.updateMetrics(errorMetrics)
                        updateNotification(errorMetrics)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during polling: ${e.message}", e)
                }

                delay(POLLING_INTERVAL_MS)
            }
        }

        // Start as foreground service
        val notification = createInitialNotification()
        startForegroundService(notification)
    }

    private fun stopMonitoring() {
        Log.d(TAG, "Stopping MiFi monitoring")
        isMonitoring = false
        scope.launch {
            job.cancel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun updateNotification(metrics: MifiMetrics) {
        val notification = buildNotification(metrics)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun createInitialNotification(): Notification {
        return buildNotification(
            MifiMetrics(
                isConnected = false,
                signalStrength = "Connecting..."
            )
        )
    }

    private fun buildNotification(metrics: MifiMetrics): Notification {
        val contentText = if (metrics.isConnected) {
            buildString {
                append("🔋 ${metrics.batteryPercent}%  ")
                append("👥 ${metrics.connectedDevices}  ")
                append("↓${metrics.downloadSpeed} ↑${metrics.uploadSpeed}")
            }
        } else {
            metrics.error ?: "Offline"
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, MifiMonitorService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MiFi Monitor")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MiFi Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows MiFi device metrics"
                enableVibration(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    @Suppress("DEPRECATION")
    private fun startForegroundService(notification: Notification) {
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MifiMonitorService destroyed")
        isMonitoring = false
        job.cancel()
    }

    companion object {
        private const val TAG = "MifiMonitorService"
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "mifi_monitoring"
        const val ACTION_START = "com.kaenova.m.service.START"
        const val ACTION_STOP = "com.kaenova.m.service.STOP"
        const val POLLING_INTERVAL_MS = 1000L
    }
}
