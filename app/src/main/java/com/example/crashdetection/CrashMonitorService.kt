package com.example.crashdetection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class CrashMonitorService : Service() {
    private var countdownActive = false
    private var countdownSeconds = 10
    private var countdownThread: Thread? = null
    private lateinit var crashDetector: CrashDetector
    private lateinit var locationHelper: LocationHelper
    private var wakeLock: PowerManager.WakeLock? = null
    private var toneGenerator: ToneGenerator? = null
    private var currentSeverity: String = ""

    override fun onCreate() {
        super.onCreate()

        toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        locationHelper = LocationHelper(this)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CrashDetection:SensorWakeLock").apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 1000L)
        }

        createNotificationChannel()
        
        val notification = buildNotification("Crash monitoring active")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(1, notification)
        }

        crashDetector = CrashDetector(this) { gForce ->
            if (!countdownActive) {
                startCrashCountdown(gForce)
            }
        }

        crashDetector.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL_COUNTDOWN) {
            cancelCountdown()
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Stop the service when the app is swiped away from the recents list
        stopSelf()
    }

    override fun onDestroy() {
        crashDetector.stop()
        stopSound()
        toneGenerator?.release()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(text: String, isWarning: Boolean = false): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Crash Detection")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (isWarning) {
            builder.setPriority(NotificationCompat.PRIORITY_MAX)
            builder.setCategory(NotificationCompat.CATEGORY_ALARM)
            builder.setFullScreenIntent(pendingIntent, true)
        } else {
            builder.setPriority(NotificationCompat.PRIORITY_LOW)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Crash Monitor",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Monitors for accidents in the background"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "crash_monitor_channel"
        const val ACTION_CRASH_UPDATE = "com.example.crashdetection.CRASH_UPDATE"
        const val ACTION_CANCEL_COUNTDOWN = "com.example.crashdetection.CANCEL_COUNTDOWN"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_COUNTDOWN = "extra_countdown"
        const val EXTRA_IS_CONFIRMED = "extra_confirmed"
        const val EXTRA_SEVERITY = "extra_severity"
    }

    private fun classifySeverity(gForce: Float): String {
        return when {
            gForce >= 25.0f -> "EXTREME"
            gForce >= 12.0f -> "SEVERE"
            else -> "MINOR"
        }
    }

    private fun startCrashCountdown(gForce: Float) {
        countdownActive = true
        countdownSeconds = 10
        currentSeverity = classifySeverity(gForce)

        countdownThread = Thread {
            while (countdownSeconds > 0 && countdownActive) {
                val msg = "Possible $currentSeverity crash detected — confirming in $countdownSeconds s"
                updateNotification(msg, true)
                sendUpdateBroadcast(msg, countdownSeconds, false, currentSeverity)
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200)
                try {
                    Thread.sleep(1000)
                } catch (e: InterruptedException) {
                    break
                }
                countdownSeconds--
            }

            if (countdownActive) {
                onCrashConfirmed(gForce)
            }
        }
        countdownThread?.start()
    }

    private fun onCrashConfirmed(gForce: Float) {
        val msg = "$currentSeverity crash CONFIRMED (G=$gForce)"
        updateNotification(msg, true)
        sendUpdateBroadcast(msg, 0, true, currentSeverity)
        
        stopSound()
        toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 10000)
        
        val emergencyNumber = ContactStore.get(this)
        
        if (emergencyNumber != null) {
            locationHelper.get { lat, lon ->
                val locationText = if (lat != null && lon != null) {
                    "Location: https://www.google.com/maps?q=$lat,$lon"
                } else {
                    "Location: Unknown"
                }
                
                val smsText = "EMERGENCY: A $currentSeverity vehicle crash (G-Force: $gForce) has been detected. $locationText"
                
                try {
                    SmsHelper.send(emergencyNumber, smsText)
                    android.util.Log.d("CrashService", "SMS sent to $emergencyNumber")
                } catch (e: Exception) {
                    android.util.Log.e("CrashService", "Failed to send SMS: ${e.message}")
                }
            }
        }
    }

    private fun sendUpdateBroadcast(message: String, seconds: Int, confirmed: Boolean, severity: String) {
        val intent = Intent(ACTION_CRASH_UPDATE).apply {
            putExtra(EXTRA_MESSAGE, message)
            putExtra(EXTRA_COUNTDOWN, seconds)
            putExtra(EXTRA_IS_CONFIRMED, confirmed)
            putExtra(EXTRA_SEVERITY, severity)
            setPackage(packageName)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
        sendBroadcast(intent)
    }

    private fun updateNotification(text: String, isWarning: Boolean = false) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(1, buildNotification(text, isWarning))
    }

    private fun stopSound() {
        toneGenerator?.stopTone()
    }

    fun cancelCountdown() {
        countdownActive = false
        stopSound()
        updateNotification("Crash monitoring active")
        sendUpdateBroadcast("Monitoring...", -1, false, "")
    }
}