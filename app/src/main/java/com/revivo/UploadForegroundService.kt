package com.revivo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat

data class UploadProgressInfo(
    val fileName: String,
    val percent: Int,
    val detail: String,
    val isComplete: Boolean
)

class UploadForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "revivo_upload_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_UPDATE_PROGRESS = "com.revivo.action.UPDATE_UPLOAD_PROGRESS"
        const val ACTION_STOP_SERVICE = "com.revivo.action.STOP_UPLOAD_SERVICE"

        const val EXTRA_FILE_NAME = "extra_file_name"
        const val EXTRA_PERCENT = "extra_percent"
        const val EXTRA_DETAIL = "extra_detail"
        const val EXTRA_IS_COMPLETE = "extra_is_complete"

        fun parseUploadLog(message: String): UploadProgressInfo? {
            if (!message.contains("[upload]", ignoreCase = true)) return null

            val cleaned = message.substringAfter("[upload]", "").trim()
            if (cleaned.isEmpty()) return null

            if (cleaned.contains("complete", ignoreCase = true) ||
                cleaned.contains("completato", ignoreCase = true) ||
                cleaned.contains("success", ignoreCase = true) ||
                cleaned.contains("finish", ignoreCase = true) ||
                cleaned.contains("finito", ignoreCase = true)
            ) {
                val candidateName = cleaned.substringBefore(":").trim()
                val fileName = if (!candidateName.contains("complete", ignoreCase = true) &&
                    !candidateName.contains("completato", ignoreCase = true) &&
                    !candidateName.contains("success", ignoreCase = true)
                ) candidateName else ""
                return UploadProgressInfo(
                    fileName = fileName,
                    percent = 100,
                    detail = "",
                    isComplete = true
                )
            }

            val regexWithDetails = Regex("""^(?:(.*?):\s*)?(\d{1,3})%(?:\s*\((.*?)\))?.*$""")
            val match = regexWithDetails.find(cleaned)
            if (match != null) {
                val fileName = match.groupValues.getOrNull(1)?.trim() ?: ""
                val percent = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
                val detail = match.groupValues.getOrNull(3)?.trim() ?: ""
                val isComplete = percent >= 100
                return UploadProgressInfo(
                    fileName = fileName,
                    percent = percent.coerceIn(0, 100),
                    detail = detail,
                    isComplete = isComplete
                )
            }

            val percentRegex = Regex("""(\d{1,3})%""")
            val percentMatch = percentRegex.find(cleaned)
            if (percentMatch != null) {
                val percent = percentMatch.groupValues[1].toIntOrNull() ?: 0
                val fileName = cleaned.substringBefore(":").trim()
                val isComplete = percent >= 100
                return UploadProgressInfo(
                    fileName = fileName,
                    percent = percent.coerceIn(0, 100),
                    detail = "",
                    isComplete = isComplete
                )
            }

            return null
        }

        fun updateProgress(
            context: Context,
            fileName: String,
            percent: Int,
            detail: String,
            isComplete: Boolean
        ) {
            val intent = Intent(context, UploadForegroundService::class.java).apply {
                action = ACTION_UPDATE_PROGRESS
                putExtra(EXTRA_FILE_NAME, fileName)
                putExtra(EXTRA_PERCENT, percent)
                putExtra(EXTRA_DETAIL, detail)
                putExtra(EXTRA_IS_COMPLETE, isComplete)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, UploadForegroundService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isForegroundActive = false
    private var lastFileName = ""

    private val stopRunnable = Runnable {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireLocks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPDATE_PROGRESS -> {
                val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: lastFileName
                if (fileName.isNotEmpty()) {
                    lastFileName = fileName
                }
                val percent = intent.getIntExtra(EXTRA_PERCENT, 0)
                val detail = intent.getStringExtra(EXTRA_DETAIL) ?: ""
                val isComplete = intent.getBooleanExtra(EXTRA_IS_COMPLETE, false)

                handleProgress(fileName, percent, detail, isComplete)
            }
            ACTION_STOP_SERVICE -> {
                releaseLocks()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                val initialNotification = buildNotification(
                    fileName = lastFileName.ifEmpty { getString(R.string.upload_in_progress) },
                    percent = 0,
                    detail = "",
                    isComplete = false
                )
                startForegroundCompat(initialNotification)
            }
        }
        return START_NOT_STICKY
    }

    private fun handleProgress(
        fileName: String,
        percent: Int,
        detail: String,
        isComplete: Boolean
    ) {
        handler.removeCallbacks(stopRunnable)
        acquireLocks()

        val notification = buildNotification(fileName, percent, detail, isComplete)

        if (!isForegroundActive) {
            startForegroundCompat(notification)
        } else {
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
        }

        if (isComplete || percent >= 100) {
            handler.postDelayed(stopRunnable, 4000)
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isForegroundActive = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildNotification(
        fileName: String,
        percent: Int,
        detail: String,
        isComplete: Boolean
    ): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (fileName.isNotEmpty()) fileName else getString(R.string.upload_in_progress)

        val contentText = when {
            isComplete || percent >= 100 -> getString(R.string.upload_complete)
            detail.isNotEmpty() -> "$percent% ($detail)"
            else -> "$percent%"
        }

            val iconRes = try {
                R.drawable.ic_notification
            } catch (e: Exception) {
                android.R.drawable.stat_sys_upload
            }

            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(contentText)
                .setSmallIcon(iconRes)
                .setContentIntent(pendingIntent)
                .setOngoing(!isComplete && percent < 100)
                .setAutoCancel(isComplete || percent >= 100)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)

        if (!isComplete && percent < 100) {
            builder.setProgress(100, percent, false)
        } else {
            builder.setProgress(0, 0, false)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = getString(R.string.upload_notification_channel_name)
            val channelDesc = getString(R.string.upload_notification_channel_desc)
            val channel = NotificationChannel(
                CHANNEL_ID,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = channelDesc
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    @Suppress("DEPRECATION")
    private fun acquireLocks() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Revivo:UploadWakeLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire(4 * 60 * 60 * 1000L)
                }
            }

            if (wifiLock == null) {
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                wifiLock = wifiManager.createWifiLock(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                    } else {
                        WifiManager.WIFI_MODE_FULL_HIGH_PERF
                    },
                    "Revivo:UploadWifiLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null

            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
            wifiLock = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(stopRunnable)
        releaseLocks()
        isForegroundActive = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
