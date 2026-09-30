package io.github.nfsandroid.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nfsandroid.R
import io.github.nfsandroid.data.Server

/** Every page with what it holds now; the one open stands out, one with a problem is flagged. */
@Composable
fun PageList(s: Server, problems: Problems, open: Page?, onRemove: (() -> Unit)?, modifier: Modifier = Modifier, onOpen: (Page) -> Unit) =
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)) {
        var query by rememberSaveable { mutableStateOf("") }
        OutlinedTextField(
            query, { query = it }, Modifier.fillMaxWidth().padding(bottom = 8.dp), singleLine = true,
            placeholder = { Text(stringResource(R.string.search_settings)) }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = if (query.isEmpty()) null else {
                { IconButton({ query = "" }) { Icon(Icons.Outlined.Clear, stringResource(R.string.search_clear)) } }
            },
            shape = MaterialTheme.shapes.extraLarge,
        )
        if (query.isNotBlank()) SearchResults(query, onOpen) else Pages(s, problems, open, onRemove, onOpen)
    }

@Composable
private fun Pages(s: Server, problems: Problems, open: Page?, onRemove: (() -> Unit)?, onOpen: (Page) -> Unit) {
    Page.entries.forEach { page ->
        val selected = page == open
        ListItem(
            headlineContent = { Text(stringResource(page.title)) },
            supportingContent = { Text(page.summary(s), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingContent = {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Icon(page.icon(), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            },
            trailingContent = if (page.wrong(problems)) {
                { Icon(Icons.Outlined.Warning, stringResource(R.string.has_problem), tint = MaterialTheme.colorScheme.error) }
            } else {
                null
            },
            colors = ListItemDefaults.colors(
                containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
            ),
            modifier = Modifier.clip(MaterialTheme.shapes.large).clickable { onOpen(page) },
        )
    }
    onRemove?.let {
        TextButton(onClick = it, Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error)
        }
    }
}
