package com.abhishek.ame.music.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Downloads the update APK inside a persistent Foreground Service.
 */
class UpdateService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL)
        val version = intent?.getStringExtra(EXTRA_VERSION).orEmpty()
        val bytes = intent?.getLongExtra(EXTRA_BYTES, 0L) ?: 0L

        if (url.isNullOrEmpty() || running) return START_NOT_STICKY
        running = true

        channel()
        startForeground(NOTIFICATION_ID, notification(version, null))

        val updater = Updater(applicationContext)
        scope.launch {
            updater.fetchAndInstall(url, version, bytes) { progress ->
                notify(version, progress)
            }
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notify(version: String, progress: Float?) {
        manager().notify(NOTIFICATION_ID, notification(version, progress))
    }

    private fun notification(version: String, progress: Float?): Notification {
        val percent = progress?.let { (it * 100).toInt().coerceIn(0, 100) }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("Downloading update")
            .setContentText(
                listOfNotNull(version.takeIf { it.isNotEmpty() }, percent?.let { "$it%" })
                    .joinToString(" · "),
            )
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, percent ?: 0, percent == null)
            .build()
    }

    private fun channel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "App Updates",
            NotificationManager.IMPORTANCE_LOW,
        )
        manager().createNotificationChannel(channel)
    }

    private fun manager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val CHANNEL_ID = "app_update_channel"
        private const val NOTIFICATION_ID = 4_201

        private const val EXTRA_URL = "url"
        private const val EXTRA_VERSION = "version"
        private const val EXTRA_BYTES = "bytes"

        fun start(context: Context, update: Updater.State.Available) {
            val intent = Intent(context, UpdateService::class.java)
                .putExtra(EXTRA_URL, update.url)
                .putExtra(EXTRA_VERSION, update.version)
                .putExtra(EXTRA_BYTES, update.bytes)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
