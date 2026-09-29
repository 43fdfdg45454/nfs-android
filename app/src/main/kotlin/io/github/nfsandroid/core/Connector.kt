package io.github.nfsandroid.core

import android.content.Context
import io.github.nfsandroid.data.Server
import io.github.nfsandroid.data.Settings
import uniffi.nfscore.Mount
import uniffi.nfscore.Security
import uniffi.nfscore.Transport
import java.util.UUID

/** A stored server turned into the client core's terms, and connected. */
object Connector {
    suspend fun connect(context: Context, server: Server): Mount {
        val core = uniffi.nfscore.Server(
            host = server.host, port = server.port.toUShort(), export = server.export,
            transport = if (server.transport == "quic") Transport.QUIC else Transport.TCP,
            security = when (server.security) { "tls" -> Security.TLS; "mtls" -> Security.MUTUAL_TLS; else -> Security.NONE },
            uid = server.uid.toUInt(), gid = server.gid.toUInt(), gids = server.gids.map { it.toUInt() },
            umask = server.umask.toUInt(), followParentLinks = server.followParentLinks,
            owner = "${installation(context)}-${server.id}", connections = server.connections.toUInt(),
            readAheadMb = server.readAheadMb.toUInt(), useCache = server.useCache,
        )
        val identity = server.certificateAlias.takeIf { it.isNotBlank() }?.let { KeyChainIdentity(context, it) }
        val cacheBytes = Settings.cacheGb(context).toLong() shl 30
        return Mount.connect(core, Trust.roots(), identity, Settings.cacheDir(context), cacheBytes.toULong())
    }

    /** Names this installation to servers, for good. */
    private fun installation(context: Context): String {
        val preferences = context.getSharedPreferences("installation", Context.MODE_PRIVATE)
        return preferences.getString("id", null) ?: UUID.randomUUID().toString().also { preferences.edit().putString("id", it).apply() }
    }
}
