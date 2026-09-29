package io.github.nfsandroid.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A server as the user configured it. */
data class Server(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val host: String = "",
    val port: Int = 2049,
    val export: String = "/",
    /** "tcp" to nfsd, or "quic" to the gateway (then [host] and [port] are the gateway's, UDP). */
    val transport: String = "tcp",
    /** "none", "tls" or "mtls" (with the KeyChain certificate [certificateAlias]). */
    val security: String = "none",
    val certificateAlias: String = "",
    val uid: Int = 0,
    val gid: Int = 0,
    val gids: List<Int> = emptyList(),
    /** Permissions new files and folders do not get: [UMASK_STANDARD], [UMASK_GROUP] or [UMASK_PRIVATE]. */
    val umask: Int = UMASK_STANDARD,
    /** 0: the transport's default. */
    val connections: Int = 0,
    val readAheadMb: Int = 256,
    val useCache: Boolean = true,
    val readOnly: Boolean = false,
    /** Off: kept with its settings, but out of the file pickers and never connected. */
    val enabled: Boolean = true,
    /** The network it is reached through: "any", "vpn" or "lan" (Wi-Fi or cable). */
    val networkKind: String = "any",
    /** Optionally, the subnet that network gives the phone (which VPN, which Wi-Fi). */
    val networkSubnet: String = "",
    /** Links to their own folder or one above it followed: loops for whatever walks folders. */
    val followParentLinks: Boolean = false,
    /** Copies within the server made by it (CLONE, COPY); off, the file manager copies, with progress. */
    val serverCopies: Boolean = true,
    /** Minutes unused before the connection closes; 0: never (while the app runs). */
    val disconnectMinutes: Int = 5,
    /** Files written whole: "local" (a local copy, uploaded as it grows) or "proxy" (Android's file proxy). */
    val writeMode: String = "local",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("host", host); put("port", port); put("export", export)
        put("transport", transport); put("security", security)
        put("certificateAlias", certificateAlias); put("uid", uid); put("gid", gid)
        put("gids", JSONArray(gids)); put("umask", umask); put("connections", connections); put("readAheadMb", readAheadMb)
        put("useCache", useCache); put("readOnly", readOnly); put("enabled", enabled)
        put("networkKind", networkKind); put("networkSubnet", networkSubnet); put("followParentLinks", followParentLinks)
        put("serverCopies", serverCopies); put("disconnectMinutes", disconnectMinutes); put("writeMode", writeMode)
    }

    val title get() = name.ifBlank { host }

    /** The same server over the other transport, with that one's default port if it had its own. */
    fun over(transport: String) = copy(transport = transport, port = if (port == defaultPort(this.transport)) defaultPort(transport) else port)

    companion object {
        const val UMASK_STANDARD = 18 // 022: files 644, folders 755
        const val UMASK_GROUP = 2 // 002: files 664, folders 775
        const val UMASK_PRIVATE = 63 // 077: files 600, folders 700

        fun defaultPort(transport: String) = if (transport == "quic") 443 else 2049

        fun fromJson(o: JSONObject) = Server(
            id = o.getString("id"), name = o.optString("name"), host = o.getString("host"),
            port = o.optInt("port", 2049), export = o.optString("export", "/"),
            transport = o.optString("transport", "tcp"),
            security = o.optString("security", "none"), certificateAlias = o.optString("certificateAlias"),
            uid = o.optInt("uid"), gid = o.optInt("gid"),
            gids = o.optJSONArray("gids")?.let { a -> List(a.length()) { a.getInt(it) } } ?: emptyList(),
            umask = o.optInt("umask", UMASK_STANDARD), connections = o.optInt("connections"), readAheadMb = o.optInt("readAheadMb", 256),
            useCache = o.optBoolean("useCache", true), readOnly = o.optBoolean("readOnly"),
            enabled = o.optBoolean("enabled", true),
            networkKind = o.optString("networkKind", "any"), networkSubnet = o.optString("networkSubnet"),
            followParentLinks = o.optBoolean("followParentLinks"),
            serverCopies = o.optBoolean("serverCopies", true), disconnectMinutes = o.optInt("disconnectMinutes", 5),
            writeMode = o.optString("writeMode", "local"),
        ).let { server ->
            // Before, QUIC servers kept the gateway (host:port) apart from nfsd's host and port.
            val gateway = o.optString("gateway").takeIf { server.transport == "quic" && it.isNotBlank() } ?: return@let server
            val (host, port) = gateway.substringBeforeLast(':') to gateway.substringAfterLast(':').toIntOrNull()
            server.copy(host = host.takeIf { port != null } ?: gateway, port = port ?: 443)
        }
    }
}
