package com.dany.fusionbot.spam

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
import com.dany.fusionbot.MainActivity
import com.dany.fusionbot.R
import com.dany.fusionbot.core.LogBus
import com.dany.fusionbot.core.Prefs
import com.dany.fusionbot.core.ScooterClient

class SpamService : Service() {

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            if (!ScooterClient.spamActive.value && !ScooterClient.spamWanted) {
                stopForegroundCompat()
                stopSelf()
                return
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, buildNotification())
            handler.postDelayed(this, REFRESH_MS)
        }
    }

    companion object {
        const val ACTION_START = "com.dany.fusionbot.spam.START"
        const val ACTION_STOP = "com.dany.fusionbot.spam.STOP"
        private const val CHANNEL_ID = "fusionbot_spam"
        private const val NOTIFICATION_ID = 0x5A1
        private const val REFRESH_MS = 1000L

        fun start(context: Context) {
            val intent = Intent(context, SpamService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SpamService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                handler.removeCallbacks(refresh)
                ScooterClient.stopSpam()
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                ensureChannel()
                startForeground(NOTIFICATION_ID, buildNotification())
                ScooterClient.init(applicationContext)
                if (!ScooterClient.spamActive.value) {
                    ScooterClient.startSpam()
                }
                if (!ScooterClient.spamActive.value && !ScooterClient.spamWanted) {
                    LogBus.warn("SPAM", "Спам не запущен: нет авторизации")
                    stopForegroundCompat()
                    stopSelf()
                    return START_NOT_STICKY
                }
                handler.removeCallbacks(refresh)
                handler.postDelayed(refresh, REFRESH_MS)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(refresh)
        ScooterClient.stopSpam()
        stopForegroundCompat()
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.description = getString(R.string.notification_channel_desc)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, SpamService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val interval = Prefs.get(this).spamIntervalMs
        val text = if (ScooterClient.spamActive.value) {
            getString(R.string.notification_text, ScooterClient.spamSent.value, interval)
        } else {
            getString(R.string.notification_waiting)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notification_action_stop),
                stopIntent,
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }
}
