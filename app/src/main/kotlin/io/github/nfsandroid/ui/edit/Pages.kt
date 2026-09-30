package io.github.nfsandroid.ui.edit

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.nfsandroid.R
import io.github.nfsandroid.data.Server
import io.github.nfsandroid.log.LogLevel
import io.github.nfsandroid.ui.edit.Page.Access
import io.github.nfsandroid.ui.edit.Page.Advanced
import io.github.nfsandroid.ui.edit.Page.Cache
import io.github.nfsandroid.ui.edit.Page.General
import io.github.nfsandroid.ui.edit.Page.Identity
import io.github.nfsandroid.ui.edit.Page.Log
import io.github.nfsandroid.ui.edit.Page.Network
import io.github.nfsandroid.ui.edit.Page.Performance
import io.github.nfsandroid.ui.edit.Page.Security
import io.github.nfsandroid.ui.edit.Page.Test
import io.github.nfsandroid.ui.edit.Page.Thumbnails

/** A server's settings, by page: each with its title and what it holds now, at a glance. */
enum class Page(val title: Int) {
    General(R.string.section_connection), Network(R.string.section_network), Security(R.string.section_security),
    Identity(R.string.section_identity), Access(R.string.section_access), Performance(R.string.section_performance),
    Cache(R.string.section_server_cache), Thumbnails(R.string.section_thumbnails), Advanced(R.string.section_advanced),
    Log(R.string.section_log), Test(R.string.section_test);

    /** Whether a problem that keeps the server from being saved is on this page. */
    fun wrong(p: Problems) = when (this) {
        General -> p.host != null || p.port != null || p.export != null
        Network -> p.subnet != null
        else -> false
    }
}

@Composable
fun Page.icon(): Painter = when (this) {
    General -> painterResource(R.drawable.ic_servers)
    Network -> painterResource(R.drawable.ic_network)
    Security -> rememberVectorPainter(Icons.Outlined.Lock)
    Identity -> rememberVectorPainter(Icons.Outlined.Person)
    Access -> rememberVectorPainter(Icons.Outlined.Edit)
    Performance -> painterResource(R.drawable.ic_activity)
    Cache -> painterResource(R.drawable.ic_folder)
    Thumbnails -> painterResource(R.drawable.ic_image)
    Advanced -> rememberVectorPainter(Icons.Outlined.Build)
    Log -> rememberVectorPainter(Icons.AutoMirrored.Outlined.List)
    Test -> rememberVectorPainter(Icons.Outlined.PlayArrow)
}

@Composable
fun Page.summary(s: Server): String = when (this) {
    General -> listOf(
        stringResource(if (s.transport == "quic") R.string.transport_quic else R.string.transport_tcp),
        s.host.ifBlank { "—" } + ":" + s.port, s.export,
    ).joinToString(" · ")
    Network -> when (s.networkKind) {
        "vpn" -> "VPN"
        "lan" -> stringResource(R.string.network_lan)
        else -> stringResource(R.string.network_any)
    } + s.networkSubnet.takeIf { s.networkKind != "any" && it.isNotBlank() }?.let { " · $it" }.orEmpty()
    Security -> when (s.security) {
        "tls" -> "TLS"
        "mtls" -> "mTLS"
        else -> stringResource(R.string.security_none)
    }
    Identity -> "UID ${s.uid} · GID ${s.gid}"
    Access -> listOf(
        stringResource(if (s.enabled) R.string.summary_on else R.string.summary_off),
        stringResource(if (s.readOnly) R.string.read_only else R.string.summary_read_write),
    ).joinToString(" · ")
    Performance -> stringResource(R.string.summary_connections, if (s.connections == 0) stringResource(R.string.summary_auto) else "${s.connections}") +
        if (s.downLimit > 0 || s.upLimit > 0) " · " + stringResource(R.string.summary_limited) else ""
    Cache -> stringResource(if (s.useCache) R.string.summary_on else R.string.summary_off) + " · " +
        stringResource(R.string.summary_performance, s.readAheadMb)
    Thumbnails -> io.github.nfsandroid.provider.thumbnail.Thumbnails.strategy(s)
        .map { stringResource(THUMBNAIL_SOURCES.getValue(it).first) }.joinToString(", ")
        .ifEmpty { stringResource(R.string.summary_none) }
    Advanced -> stringResource(if (s.writeMode == "local") R.string.write_local else R.string.write_proxy)
    Log -> if (!s.logEnabled) stringResource(R.string.summary_off) else stringResource(
        when (LogLevel.of(s.logLevel)) {
            LogLevel.ERROR -> R.string.log_error
            LogLevel.WARN -> R.string.log_warn
            LogLevel.INFO -> R.string.log_info
            LogLevel.DEBUG -> R.string.log_debug
        },
    )
    Test -> stringResource(R.string.section_test_help)
}
