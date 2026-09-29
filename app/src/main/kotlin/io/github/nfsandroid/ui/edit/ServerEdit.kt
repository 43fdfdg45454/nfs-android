package io.github.nfsandroid.ui.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nfsandroid.R
import io.github.nfsandroid.core.Mounts
import io.github.nfsandroid.data.Server
import io.github.nfsandroid.data.ServerStore
import kotlinx.coroutines.launch

/**
 * A server's settings as a list of pages and the page open: side by side when there is room (in
 * landscape, on a tablet), else one at a time. Saved only when asked; saving with a problem opens
 * its page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerEdit(initial: Server, isNew: Boolean, onDone: () -> Unit) = BoxWithConstraints {
    val wide = maxWidth >= 720.dp
    var s by remember { mutableStateOf(initial) }
    var open by rememberSaveable { mutableStateOf<Page?>(null) }
    var ask by remember { mutableStateOf<Ask?>(null) }
    var touched by remember { mutableStateOf(emptySet<String>()) }
    var tried by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val problems = Problems.of(s)
    val shown = problems.shown(touched, tried)
    val page = open ?: Page.General.takeIf { wide }
    fun update(changed: Server) {
        touched = touched + Problems.touched(s, changed)
        s = changed
    }
    fun back() {
        when {
            !wide && open != null -> open = null
            s != initial -> ask = Ask.Discard
            else -> onDone()
        }
    }
    fun save() {
        if (!problems.none) {
            tried = true
            open = Page.entries.first { it.wrong(problems) }
            return
        }
        scope.launch { Mounts.forget(s.id); ServerStore.put(s); onDone() }
    }
    BackHandler { back() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(page?.takeIf { !wide }?.title ?: if (isNew) R.string.new_server else R.string.edit_server)) },
                navigationIcon = { IconButton(::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = { TextButton(onClick = ::save, enabled = isNew || s != initial) { Text(stringResource(R.string.save)) } },
            )
        },
    ) { padding ->
        val remove = { ask = Ask.Remove }.takeIf { !isNew }
        val content = Modifier.padding(padding).fillMaxSize()
        when {
            wide -> Row(content) {
                PageList(s, shown, page, remove, Modifier.width(320.dp).fillMaxHeight()) { open = it }
                VerticalDivider()
                PageContent(page ?: Page.General, s, shown, Modifier.weight(1f).fillMaxHeight(), ::update)
            }
            page == null -> PageList(s, shown, null, remove, content) { open = it }
            else -> PageContent(page, s, shown, content, ::update)
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
