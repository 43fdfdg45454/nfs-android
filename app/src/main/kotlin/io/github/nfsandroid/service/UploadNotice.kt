package io.github.nfsandroid.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import io.github.nfsandroid.R
import io.github.nfsandroid.ui.common.Format
import java.util.concurrent.ConcurrentHashMap

/**
 * Uploads that go on after the app that wrote them let go: their progress (once one takes more
 * than a second, then each second), and a failure, which that app can no longer be told.
 */
object UploadNotice {
    /** Progress, quietly; failures, heard: each can be silenced on its own. */
    private const val PROGRESS = "upload_progress"
    private const val FAILED = "upload_failed"
    private val shown = ConcurrentHashMap<String, Long>()

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java).apply {
        if (getNotificationChannel(FAILED) == null) {
            createNotificationChannel(NotificationChannel(PROGRESS, context.getString(R.string.channel_upload_progress), NotificationManager.IMPORTANCE_LOW))
            createNotificationChannel(NotificationChannel(FAILED, context.getString(R.string.channel_upload_failed), NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    private fun builder(context: Context, channel: String, path: String) = Notification.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_stat).setColor(context.getColor(R.color.brand)).setContentTitle(path.substringAfterLast('/'))

    fun progress(context: Context, path: String, sent: Long, size: Long) {
        val now = SystemClock.elapsedRealtime()
        val last = shown.putIfAbsent(path, now) ?: return
        if (now - last < 1000) return
        shown[path] = now
        val text = context.getString(R.string.upload_progress, Format.bytes(sent), Format.bytes(size))
        val percent = (sent * 100 / maxOf(size, 1)).toInt()
        manager(context).notify(path.hashCode(), builder(context, PROGRESS, path).setContentText(text).setProgress(100, percent, false)
            .setOngoing(true).setOnlyAlertOnce(true).build())
    }

    fun done(context: Context, path: String) {
        shown.remove(path)
        manager(context).cancel(path.hashCode())
    }

    fun failed(context: Context, path: String, reason: String) {
        shown.remove(path)
        val text = context.getString(R.string.upload_failed, reason)
        manager(context).notify(path.hashCode(), builder(context, FAILED, path).setContentText(text).setStyle(Notification.BigTextStyle().bigText(text)).build())
    }
}
