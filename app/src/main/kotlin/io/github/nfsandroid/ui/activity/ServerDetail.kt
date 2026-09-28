package io.github.nfsandroid.ui.activity

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.nfsandroid.R
import io.github.nfsandroid.core.Live
import io.github.nfsandroid.data.ServerStore
import io.github.nfsandroid.ui.common.Figure
import io.github.nfsandroid.ui.common.Format
import io.github.nfsandroid.ui.common.Section

/** One connected server's connections, figure by figure. */
@Composable
fun ServerDetail(server: Live.Server) {
    val context = LocalContext.current
    val s = server.stats
    val title = ServerStore.get(server.id)?.title ?: server.id
    Section(title, painterResource(R.drawable.ic_servers), pluralStringResource(R.plurals.used_ago, server.idleMinutes.toInt(), server.idleMinutes.toInt())) {
        Figure(stringResource(R.string.figure_transport), if (s.quic) stringResource(R.string.transport_quic) else stringResource(R.string.transport_tcp))
        Figure(stringResource(R.string.figure_connections), "${s.alive} / ${s.lanes}")
        Figure(stringResource(R.string.figure_in_flight), "${s.inFlight}")
        Figure(stringResource(R.string.figure_reconnections), "${Format.reconnections(s)}")
        if (s.quic) {
            Figure(stringResource(R.string.figure_rtt), if (s.rttMs > 0u) "${s.rttMs} ms" else "—")
            Figure(stringResource(R.string.figure_loss), Format.loss(s)?.let { "%.2f %%".format(it) + " (${s.packetsLost}/${s.packetsSent})" } ?: "—")
            Figure(stringResource(R.string.figure_cwnd), Format.bytes(s.cwnd.toLong()))
        }
        Figure(stringResource(R.string.figure_files), "${s.openFiles}")
        Figure(stringResource(R.string.figure_memory), Format.bytes(s.memory.toLong()))
        Figure(stringResource(R.string.figure_callbacks_down), "${s.callbacksDown}")
    }
}
