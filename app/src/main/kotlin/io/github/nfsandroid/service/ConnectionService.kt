package io.github.nfsandroid.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.PowerManager
import io.github.nfsandroid.R
import io.github.nfsandroid.core.Live
import io.github.nfsandroid.log.NfsLog

/**
 * Runs in the foreground while any server is connected. Android blocks new network connections
 * of an app in the background (always since Android 15), and this app is in the background while a
 * player shows a video from it: without the service, every reconnection and every seek that needs
 * a new connection would fail at once. Its notification shows what the connections are doing.
 */
class ConnectionService : Service() {
    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(ID, StatusNotification.build(this, Live.state.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        running = true
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        posted = null
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "connection"
        private const val ID = 1

        @Volatile
        private var running = false

        fun channel(context: Context) {
            val channel = NotificationChannel(CHANNEL, context.getString(R.string.channel_connection), NotificationManager.IMPORTANCE_LOW)
            channel.description = context.getString(R.string.channel_connection_help)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        fun start(context: Context) {
            if (running) return
            runCatching { context.startForegroundService(Intent(context, ConnectionService::class.java)) }
                .onFailure { NfsLog.line("foreground service refused: ${it.message}") }
        }

        private var posted: Pair<Int, Triple<String, String, String>>? = null

        /**
         * The notification with the connections as they are now (every 2 s at most), posted only
         * when it changed; with the screen off, only when the servers connected did (nobody sees
         * the rest, and each post wakes the system's UI).
         */
        fun show(context: Context, state: Live.State) {
            if (!running) return
            val now = state.servers.size to StatusNotification.texts(context, state)
            val screenOn = context.getSystemService(PowerManager::class.java).isInteractive
            if (now == posted || (!screenOn && now.first == posted?.first)) return
            posted = now
            context.getSystemService(NotificationManager::class.java).notify(ID, StatusNotification.build(context, state))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ConnectionService::class.java))
        }
    }
}
