package io.github.nfsandroid.ui.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nfsandroid.R
import io.github.nfsandroid.core.Mounts
import io.github.nfsandroid.data.Server
import io.github.nfsandroid.data.ServerStore
import kotlinx.coroutines.launch

/** A server's settings by section, each with its help; saved only when asked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerEdit(initial: Server, isNew: Boolean, onDone: () -> Unit) {
    var s by remember { mutableStateOf(initial) }
    var ask by remember { mutableStateOf<Ask?>(null) }
    var touched by remember { mutableStateOf(emptySet<String>()) }
    var tried by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val problems = Problems.of(s)
    fun update(changed: Server) {
        touched = touched + Problems.touched(s, changed)
        s = changed
    }
    fun back() {
        if (s != initial) ask = Ask.Discard else onDone()
    }
    fun save() = scope.launch { Mounts.forget(s.id); ServerStore.put(s); onDone() }
    BackHandler { back() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isNew) R.string.new_server else R.string.edit_server)) },
                navigationIcon = { IconButton(::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    TextButton(onClick = { if (problems.none) save() else tried = true }, enabled = isNew || s != initial) {
                        Text(stringResource(R.string.save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ConnectionSection(s, problems.shown(touched, tried), ::update)
            NetworkSection(s, problems.shown(touched, tried).subnet, ::update)
            SecuritySection(s, ::update)
            IdentitySection(s, ::update)
            PerformanceSection(s, ::update)
            TestSection(s, enabled = problems.none)
            if (!isNew) {
                TextButton(onClick = { ask = Ask.Remove }, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.remove), color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    when (ask) {
        Ask.Discard -> Confirm(R.string.discard_title, R.string.discard_text, R.string.discard, onYes = onDone) { ask = null }
        Ask.Remove -> Confirm(R.string.remove_title, R.string.remove_text, R.string.remove, onYes = {
            scope.launch { Mounts.forget(s.id); ServerStore.remove(s.id); onDone() }
        }) { ask = null }
        null -> {}
    }
}

private enum class Ask { Discard, Remove }
