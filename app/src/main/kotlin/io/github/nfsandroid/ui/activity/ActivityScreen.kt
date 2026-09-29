package io.github.nfsandroid.ui.activity

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nfsandroid.R
import io.github.nfsandroid.core.Live
import io.github.nfsandroid.core.NetworkMonitor
import io.github.nfsandroid.log.NfsLog
import io.github.nfsandroid.ui.common.Figure
import io.github.nfsandroid.ui.common.Format
import io.github.nfsandroid.ui.common.Help
import io.github.nfsandroid.ui.common.Section

/** What helps after a problem: the network, every connected server in detail, and the log. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen() {
    val context = LocalContext.current
    val live by Live.state.collectAsState()
    val network by NetworkMonitor.state.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_activity)) }) }, contentWindowInsets = WindowInsets(0)) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).wrapContentWidth().widthIn(max = 720.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Section(stringResource(R.string.network), painterResource(R.drawable.ic_activity)) {
                    Figure(stringResource(R.string.network_default), network)
                    Figure(stringResource(R.string.download), Format.rate(context, live.down))
                    Figure(stringResource(R.string.upload), Format.rate(context, live.up))
                }
            }
            if (live.servers.isEmpty()) item { Help(stringResource(R.string.no_connections), Modifier.padding(horizontal = 8.dp)) }
            items(live.servers, key = { it.id }) { ServerDetail(it) }
            item {
                OutlinedButton(onClick = {
                    val log = NfsLog.file(context).takeIf { it.exists() }?.readText()?.takeLast(64 shl 10).orEmpty()
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, log)
                    context.startActivity(Intent.createChooser(send, context.getString(R.string.share_log)))
                }, Modifier.fillMaxWidth()) {
                    Icon(rememberVectorPainter(Icons.Outlined.Share), null, Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.share_log))
                }
                Help(stringResource(R.string.share_log_help), Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp))
            }
        }
    }
}
