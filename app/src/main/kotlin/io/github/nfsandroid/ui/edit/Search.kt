package io.github.nfsandroid.ui.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nfsandroid.R
import io.github.nfsandroid.ui.common.Help
import java.text.Normalizer

/** Every option by page, as its name and its help: what the search looks through. */
private val OPTIONS: Map<Page, List<Pair<Int, Int?>>> = mapOf(
    Page.General to listOf(
        R.string.name to R.string.name_help, R.string.transport_tcp to R.string.tcp_help, R.string.transport_quic to R.string.quic_help,
        R.string.host to null, R.string.host_gateway to null, R.string.port to null, R.string.export to R.string.export_help,
        R.string.gateway_security to null, R.string.gateway_certificate to R.string.gateway_certificate_help,
    ),
    Page.Network to listOf(R.string.section_network to R.string.network_any_help, R.string.network_subnet to R.string.network_subnet_help),
    Page.Security to listOf(R.string.section_security to R.string.tls_help, R.string.certificate to R.string.certificate_help),
    Page.Identity to listOf(R.string.uid to R.string.uid_help, R.string.gid to null, R.string.gids to R.string.gids_help, R.string.umask to R.string.umask_help),
    Page.Access to listOf(R.string.enabled to R.string.enabled_help, R.string.read_only to R.string.read_only_help),
    Page.Performance to listOf(R.string.connections to R.string.connections_help, R.string.down_limit to R.string.rate_help, R.string.up_limit to R.string.rate_help),
    Page.Cache to listOf(R.string.use_cache to R.string.use_cache_help, R.string.read_ahead to R.string.read_ahead_help),
    Page.Thumbnails to THUMBNAIL_SOURCES.values.map { (title, help) -> title to help } + (R.string.thumbnail_max to R.string.thumbnail_max_help),
    Page.Advanced to listOf(
        R.string.follow_parent_links to R.string.follow_parent_links_help, R.string.server_copies to R.string.server_copies_help,
        R.string.write_mode to R.string.write_mode_help, R.string.disconnect_after to R.string.disconnect_after_help,
    ),
    Page.Log to listOf(R.string.log_enabled to R.string.log_enabled_help, R.string.log_level to R.string.log_categories),
    Page.Test to listOf(R.string.section_test to R.string.section_test_help),
)

private data class Hit(val page: Page, val where: String, val title: String, val help: String)

/** Lower case and without accents: "cache" finds "caché". */
private fun fold(text: String) = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}"), "")

/** The options with every word typed in their name, help or page; tapping one opens its page. */
@Composable
fun SearchResults(query: String, onOpen: (Page) -> Unit) {
    val words = fold(query).split(' ').filter { it.isNotBlank() }
    val hits = OPTIONS.flatMap { (page, options) ->
        val where = stringResource(page.title)
        options.map { (title, help) -> Hit(page, where, stringResource(title), help?.let { stringResource(it) }.orEmpty()) }
    }.filter { hit -> fold("${hit.title} ${hit.help} ${hit.where}").let { text -> words.all { it in text } } }
    if (hits.isEmpty()) Help(stringResource(R.string.search_none), Modifier.clip(MaterialTheme.shapes.large))
    hits.forEach { (page, where, title, help) ->
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text("$where · $help", maxLines = 2, overflow = TextOverflow.Ellipsis) },
            leadingContent = { Icon(page.icon(), null, Modifier.size(20.dp)) },
            modifier = Modifier.clip(MaterialTheme.shapes.large).clickable { onOpen(page) },
        )
    }
}
