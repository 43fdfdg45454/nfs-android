package io.github.nfsandroid.ui.edit

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.nfsandroid.R
import io.github.nfsandroid.data.Server
import io.github.nfsandroid.ui.common.Section
import io.github.nfsandroid.ui.common.AmountInput
import io.github.nfsandroid.ui.common.SwitchRow

private val CONNECTIONS = listOf(0, 2, 4, 8, 12, 16, 24, 32, 48, 64)
private val READ_AHEAD_MB = listOf(16, 32, 64, 128, 256, 512, 768, 1024)

/** How much the server is asked at once, and how far ahead a player is read. */
@Composable
fun PerformanceSection(s: Server, onChange: (Server) -> Unit) {
    Section(stringResource(R.string.section_performance), painterResource(R.drawable.ic_activity), stringResource(R.string.section_performance_help)) {
        AmountInput(
            stringResource(if (s.transport == "quic") R.string.streams else R.string.connections), s.connections, CONNECTIONS, "",
            stringResource(R.string.connections_help, if (s.transport == "quic") 4 else 8),
        ) { onChange(s.copy(connections = it)) }
        AmountInput(stringResource(R.string.read_ahead), s.readAheadMb, READ_AHEAD_MB, "MB", stringResource(R.string.read_ahead_help)) {
            onChange(s.copy(readAheadMb = it))
        }
        SwitchRow(stringResource(R.string.use_cache), stringResource(R.string.use_cache_help), s.useCache) { onChange(s.copy(useCache = it)) }
    }
    Section(stringResource(R.string.section_access), rememberVectorPainter(Icons.Outlined.Edit)) {
        SwitchRow(stringResource(R.string.enabled), stringResource(R.string.enabled_help), s.enabled) { onChange(s.copy(enabled = it)) }
        SwitchRow(stringResource(R.string.read_only), stringResource(R.string.read_only_help), s.readOnly) { onChange(s.copy(readOnly = it)) }
    }
}
