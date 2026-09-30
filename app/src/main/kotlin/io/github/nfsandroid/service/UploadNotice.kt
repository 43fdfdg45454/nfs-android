package io.github.nfsandroid.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import io.github.nfsandroid.R
import io.github.nfsandroid.ui.common.Format
import io.github.nfsandroid.ui.theme.Palettes
import java.util.concurrent.ConcurrentHashMap

/**
 * Uploads, through the file proxy or a local copy: their progress once one takes more than a
 * second (then each second; through the proxy, what was sent, its size unknown until the end),
 * a note when it ends that goes by itself, and a failure.
 */
object UploadNotice {
    /** Progress and ends, quietly; failures, heard: each can be silenced on its own. */
    private const val PROGRESS = "upload_progress"
    private const val FAILED = "upload_failed"
    private const val SECOND = 1000L
    private val started = ConcurrentHashMap<String, Long>()
    private val shown = ConcurrentHashMap<String, Long>()

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java).apply {
        if (getNotificationChannel(FAILED) == null) {
            createNotificationChannel(NotificationChannel(PROGRESS, context.getString(R.string.channel_upload_progress), NotificationManager.IMPORTANCE_LOW))
            createNotificationChannel(NotificationChannel(FAILED, context.getString(R.string.channel_upload_failed), NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    private fun builder(context: Context, channel: String, path: String) = Notification.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_stat).setColor(Palettes.accent(context)).setContentTitle(path.substringAfterLast('/'))

    /** [sent] bytes so far, of [size] when known (0 when not). */
    fun progress(context: Context, path: String, sent: Long, size: Long) {
        val now = SystemClock.elapsedRealtime()
        if (now - started.getOrPut(path) { now } < SECOND || now - (shown[path] ?: 0) < SECOND) return
        shown[path] = now
        val builder = builder(context, PROGRESS, path).setOngoing(true).setOnlyAlertOnce(true)
        if (size > 0) {
            builder.setContentText(context.getString(R.string.upload_progress, Format.bytes(sent), Format.bytes(size)))
                .setProgress(100, (sent * 100 / size).coerceAtMost(100).toInt(), false)
        } else {
            builder.setContentText(context.getString(R.string.upload_sent, Format.bytes(sent))).setProgress(0, 0, true)
        }
        manager(context).notify(path.hashCode(), builder.build())
    }

    /** Ended: what was shown becomes a note of how it went, gone in 10 s. */
    fun done(context: Context, path: String, summary: String) {
        started.remove(path)
        if (shown.remove(path) == null) return
        val text = context.getString(R.string.upload_done, summary)
        manager(context).notify(path.hashCode(), builder(context, PROGRESS, path).setContentText(text).setAutoCancel(true).setTimeoutAfter(10 * SECOND).build())
    }

    fun failed(context: Context, path: String, reason: String) {
        started.remove(path)
        shown.remove(path)
        val text = context.getString(R.string.upload_failed, reason)
        manager(context).notify(path.hashCode(), builder(context, FAILED, path).setContentText(text).setStyle(Notification.BigTextStyle().bigText(text)).build())
    }
}
