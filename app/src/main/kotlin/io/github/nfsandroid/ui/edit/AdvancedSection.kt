package io.github.nfsandroid.ui.edit

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import io.github.nfsandroid.R
import io.github.nfsandroid.data.Server
import io.github.nfsandroid.ui.common.Help
import io.github.nfsandroid.ui.common.Section
import io.github.nfsandroid.ui.common.Segmented
import io.github.nfsandroid.ui.common.SwitchRow

private val DISCONNECT_MINUTES = listOf(1, 5, 15, 60, 0)

/** Edge cases allowed on purpose: off by default, what is sensible for most servers. */
@Composable
fun AdvancedSection(s: Server, onChange: (Server) -> Unit) =
    Section(stringResource(R.string.section_advanced), rememberVectorPainter(Icons.Outlined.Build), stringResource(R.string.section_advanced_help)) {
        SwitchRow(stringResource(R.string.follow_parent_links), stringResource(R.string.follow_parent_links_help), s.followParentLinks) {
            onChange(s.copy(followParentLinks = it))
        }
        SwitchRow(stringResource(R.string.server_copies), stringResource(R.string.server_copies_help), s.serverCopies) {
            onChange(s.copy(serverCopies = it))
        }
        Text(stringResource(R.string.write_mode), style = MaterialTheme.typography.bodyLarge)
        val ways = listOf("local" to R.string.write_local, "proxy" to R.string.write_proxy)
        Segmented(ways.map { (value, label) -> value to stringResource(label) }, s.writeMode) { onChange(s.copy(writeMode = it)) }
        Help(stringResource(R.string.write_mode_help))
        Text(stringResource(R.string.disconnect_after), style = MaterialTheme.typography.bodyLarge)
        val never = stringResource(R.string.disconnect_never)
        Segmented(DISCONNECT_MINUTES.map { it to if (it == 0) never else if (it < 60) "$it min" else "${it / 60} h" }, s.disconnectMinutes) {
            onChange(s.copy(disconnectMinutes = it))
        }
        Help(stringResource(R.string.disconnect_after_help))
    }
