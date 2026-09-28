package io.github.nfsandroid.ui.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nfsandroid.R
import io.github.nfsandroid.core.Live
import io.github.nfsandroid.ui.common.Format
import io.github.nfsandroid.ui.theme.Brand

/** All servers together: throughput both ways, connections up and calls in flight. */
@Composable
fun Summary(live: Live.State) {
    val context = LocalContext.current
    val soft = Color.White.copy(alpha = 0.85f)
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Brand.gradient).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val servers = live.servers
        Text(
            if (servers.isEmpty()) stringResource(R.string.summary_idle) else pluralStringResource(R.plurals.summary_connected, servers.size, servers.size),
            style = MaterialTheme.typography.labelLarge, color = soft,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Text("↓ ${Format.rate(context, live.down)}", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text("↑ ${Format.rate(context, live.up)}", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        }
        Text(
            if (servers.isEmpty()) {
                stringResource(R.string.summary_idle_help)
            } else {
                stringResource(
                    R.string.summary_detail, servers.sumOf { it.stats.alive.toInt() }, servers.sumOf { it.stats.lanes.toInt() },
                    servers.sumOf { it.stats.inFlight.toInt() }, servers.sumOf { it.stats.openFiles.toInt() },
                )
            },
            style = MaterialTheme.typography.bodySmall, color = soft,
        )
    }
}
