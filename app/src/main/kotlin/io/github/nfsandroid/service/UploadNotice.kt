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
    /**
     * Progress and ends: off until turned on in Android's settings (the app that copies shows its
     * own). A new channel: the one before was on, and an app cannot turn down one that exists.
     */
    const val PROGRESS = "uploads"
    private const val BEFORE = "upload_progress"
    /** Failures, heard: the app that wrote the file may no longer be told. */
    const val FAILED = "upload_failed"
    private const val SECOND = 1000L
    private val started = ConcurrentHashMap<String, Long>()
    private val shown = ConcurrentHashMap<String, Long>()

    /** The last text posted for each file, whether its channel shows it or not (diagnosis, tests). */
    val posted = ConcurrentHashMap<String, String>()

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java).apply {
        if (getNotificationChannel(PROGRESS) == null) {
            deleteNotificationChannel(BEFORE)
            createNotificationChannel(
                NotificationChannel(PROGRESS, context.getString(R.string.channel_upload_progress), NotificationManager.IMPORTANCE_NONE)
                    .apply { description = context.getString(R.string.channel_upload_progress_help) },
            )
        }
        if (getNotificationChannel(FAILED) == null) {
            createNotificationChannel(NotificationChannel(FAILED, context.getString(R.string.channel_upload_failed), NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    private fun post(context: Context, path: String, text: String, notification: Notification) {
        posted[path] = text
        manager(context).notify(path.hashCode(), notification)
    }

    private fun builder(context: Context, channel: String, path: String) = Notification.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_stat).setColor(Palettes.accent(context)).setContentTitle(path.substringAfterLast('/'))

    /** [sent] bytes so far, of [size] when known (0 when not). */
    fun progress(context: Context, path: String, sent: Long, size: Long) {
        val now = SystemClock.elapsedRealtime()
        if (now - started.getOrPut(path) { now } < SECOND || now - (shown[path] ?: 0) < SECOND) return
        shown[path] = now
        val builder = builder(context, PROGRESS, path).setOngoing(true).setOnlyAlertOnce(true)
        val text = if (size > 0) {
            builder.setProgress(100, (sent * 100 / size).coerceAtMost(100).toInt(), false)
            context.getString(R.string.upload_progress, Format.bytes(sent), Format.bytes(size))
        } else {
            builder.setProgress(0, 0, true)
            context.getString(R.string.upload_sent, Format.bytes(sent))
        }
        post(context, path, text, builder.setContentText(text).build())
    }

    /** Ended: what was shown becomes a note of how it went, gone in 10 s. */
    fun done(context: Context, path: String, summary: String) {
        started.remove(path)
        if (shown.remove(path) == null) return
        val text = context.getString(R.string.upload_done, summary)
        post(context, path, text, builder(context, PROGRESS, path).setContentText(text).setAutoCancel(true).setTimeoutAfter(10 * SECOND).build())
    }

    fun failed(context: Context, path: String, reason: String) {
        started.remove(path)
        shown.remove(path)
        val text = context.getString(R.string.upload_failed, reason)
        post(context, path, text, builder(context, FAILED, path).setContentText(text).setStyle(Notification.BigTextStyle().bigText(text)).build())
    }
}
