package io.github.nfsandroid.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.nfsandroid.R
import io.github.nfsandroid.core.Live
import io.github.nfsandroid.data.ServerStore
import io.github.nfsandroid.ui.MainActivity
import io.github.nfsandroid.ui.common.Format

/**
 * The service's notification, a technical summary: throughput both ways as the title; servers,
 * connections up and calls in flight as the text; expanded, one line per server (transport,
 * connections, in flight, round trip and loss over QUIC, files being read, reconnections).
 */
object StatusNotification {
    /** The title, the text and the expanded text; the same ones mean nothing to post again. */
    fun texts(context: Context, state: Live.State): Triple<String, String, String> {
        val servers = state.servers
        val title = if (servers.isEmpty()) {
            context.getString(R.string.notification_connecting)
        } else {
            context.getString(R.string.notification_title, Format.rate(context, state.down), Format.rate(context, state.up))
        }
        val text = context.resources.getQuantityString(
            R.plurals.notification_text, servers.size, servers.size,
            servers.sumOf { it.stats.alive.toInt() }, servers.sumOf { it.stats.lanes.toInt() }, servers.sumOf { it.stats.inFlight.toInt() },
        )
        val lines = servers.joinToString("\n") { "${ServerStore.get(it.id)?.title ?: it.id}: ${Format.connections(context, it.stats)}" }
        return Triple(title, text, if (lines.isEmpty()) text else "$text\n$lines")
    }

    fun build(context: Context, state: Live.State): Notification {
        val (title, text, expanded) = texts(context, state)
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(context, ConnectionService.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(io.github.nfsandroid.ui.theme.Palettes.accent(context))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(expanded))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
