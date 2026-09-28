package io.github.nfsandroid.ui.edit

import io.github.nfsandroid.R
import io.github.nfsandroid.core.Subnet
import io.github.nfsandroid.data.Server

/** What keeps a server from being saved, by field (string resources). */
data class Problems(val host: Int?, val port: Int?, val export: Int?, val subnet: Int? = null) {
    val none get() = host == null && port == null && export == null && subnet == null

    /** Only on the fields already touched, or all once saving was tried: not before anything was typed. */
    fun shown(touched: Set<String>, all: Boolean) = Problems(
        host.takeIf { all || "host" in touched }, port.takeIf { all || "port" in touched }, export.takeIf { all || "export" in touched },
        subnet.takeIf { all || "subnet" in touched },
    )

    companion object {
        /** The fields with a problem to show that changed from [a] to [b]. */
        fun touched(a: Server, b: Server) = buildSet {
            if (a.host != b.host) add("host")
            if (a.port != b.port) add("port")
            if (a.export != b.export) add("export")
            if (a.networkSubnet != b.networkSubnet) add("subnet")
        }

        fun of(s: Server) = Problems(
            host = R.string.error_required.takeIf { s.host.isBlank() || s.host.any(Char::isWhitespace) },
            port = R.string.error_port.takeIf { s.port !in 1..65535 },
            export = R.string.error_export.takeIf { !s.export.startsWith("/") },
            subnet = R.string.error_subnet.takeIf { s.networkKind != "any" && s.networkSubnet.isNotBlank() && !Subnet.valid(s.networkSubnet) },
        )
    }
}
