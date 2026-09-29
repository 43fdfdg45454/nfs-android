package io.github.nfsandroid.ui.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import io.github.nfsandroid.core.Mounts
import io.github.nfsandroid.core.Networks
import io.github.nfsandroid.provider.Spaces
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nfsandroid.R
import io.github.nfsandroid.core.Live
import io.github.nfsandroid.data.Server
import io.github.nfsandroid.data.ServerStore
import io.github.nfsandroid.ui.common.Help

/** The servers and what they are doing, and adding one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Home(onEdit: (Server?) -> Unit) {
    val servers by ServerStore.servers.collectAsState()
    val live by Live.state.collectAsState()
    val scope = rememberCoroutineScope()
    val links by Networks.state.collectAsState()
    val spaces by Spaces.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    Scaffold(
        topBar = { TopAppBar(title = { Wordmark() }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEdit(null) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.add_server)) },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        // As many columns as fit: one upright on a phone, two or more sideways or on a tablet.
        LazyVerticalGrid(
            GridCells.Adaptive(340.dp), Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { Summary(live) }
            if (servers.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyState() }
            items(servers, key = { it.id }) { server ->
                Box(Modifier.animateItem()) {
                    val space = spaces[server.id] ?: Spaces.get(context, server.id)
                    ServerCard(server, live.of(server.id), live.unreachable[server.id], Networks.allows(server, links), space, onToggle = { on ->
                        // Off: its connections close now, and the pickers drop its root.
                        scope.launch { if (!on) Mounts.forget(server.id); ServerStore.put(server.copy(enabled = on)) }
                    }) { onEdit(server) }
                }
            }
            if (servers.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Help(stringResource(R.string.hint_files_app), Modifier.padding(horizontal = 8.dp)) }
        }
    }
}
