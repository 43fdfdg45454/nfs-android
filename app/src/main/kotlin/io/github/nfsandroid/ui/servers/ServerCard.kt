package io.github.nfsandroid.ui.servers

import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nfsandroid.R
import io.github.nfsandroid.core.Live
import io.github.nfsandroid.data.Server
import io.github.nfsandroid.provider.Spaces
import io.github.nfsandroid.ui.common.Format
import io.github.nfsandroid.ui.common.Tag
import io.github.nfsandroid.ui.theme.Brand

/** A server: where it is, how it connects, whether it is on, and whether it is connected now. */
@Composable
fun ServerCard(
    server: Server, live: Live.Server?, unreachable: String?, networkUp: Boolean, space: Pair<Long, Long>?,
    onToggle: (Boolean) -> Unit, onEdit: () -> Unit,
) {
    val context = LocalContext.current
    val dim by animateFloatAsState(if (server.enabled) 1f else 0.55f, label = "enabled")
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 8.dp).alpha(dim)) {
                    Text(server.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${server.host}:${server.port} · ${server.export}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // The last space known (asked when a picker shows the server, while connected).
                    space?.let { (available, total) ->
                        Text(Spaces.describe(context, available, total), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    }
                }
                Switch(server.enabled, onToggle, Modifier.padding(end = 8.dp))
            }
            Row(Modifier.alpha(dim), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag(if (server.transport == "quic") "QUIC" else "TCP", strong = true)
                if (server.security != "none") Tag(if (server.security == "mtls") "mTLS" else "TLS")
                if (server.readOnly) Tag(stringResource(R.string.tag_read_only))
            }
            Row(Modifier.padding(end = 8.dp).alpha(dim), verticalAlignment = Alignment.CenterVertically) {
                val dot = when {
                    live != null -> Brand.success
                    unreachable != null && server.enabled -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.outline
                }
                Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                Text(
                    when {
                        !server.enabled -> stringResource(R.string.disabled)
                        live != null -> Format.connections(context, live.stats)
                        !networkUp -> stringResource(R.string.waiting_network, Format.network(context, server))
                        unreachable != null -> stringResource(R.string.unreachable, unreachable)
                        else -> stringResource(R.string.not_connected)
                    },
                    Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.edit), Modifier.padding(start = 8.dp))
                }
                FilledTonalButton(onClick = { browse(context, server) }, enabled = server.enabled) {
                    Icon(painterResource(R.drawable.ic_folder), null, Modifier.size(18.dp))
                    Text(stringResource(R.string.browse), Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/** The system's Files app at this server's root: not another app that says it views roots (a file manager may take it as "save as"). */
private fun browse(context: Context, server: Server) = runCatching {
    val root = DocumentsContract.buildRootUri("${context.packageName}.documents", server.id)
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(root, DocumentsContract.Root.MIME_TYPE_ITEM)
    context.packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }
        .firstOrNull { it.endsWith(".documentsui") }?.let(intent::setPackage)
    context.startActivity(intent)
}
