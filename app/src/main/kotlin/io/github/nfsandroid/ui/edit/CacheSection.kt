package io.github.nfsandroid.ui.edit

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.nfsandroid.R
import io.github.nfsandroid.data.Server
import io.github.nfsandroid.ui.common.AmountInput
import io.github.nfsandroid.ui.common.Help
import io.github.nfsandroid.ui.common.Section
import io.github.nfsandroid.ui.common.SwitchRow

private val READ_AHEAD_MB = listOf(16, 32, 64, 128, 256, 512, 768, 1024)

/** What of this server is kept on the phone: the local cache, and how far ahead a player is read. */
@Composable
fun CacheSection(s: Server, onChange: (Server) -> Unit) =
    Section(stringResource(R.string.section_server_cache), painterResource(R.drawable.ic_folder), stringResource(R.string.section_server_cache_help)) {
        SwitchRow(stringResource(R.string.use_cache), stringResource(R.string.use_cache_help), s.useCache) { onChange(s.copy(useCache = it)) }
        AmountInput(stringResource(R.string.read_ahead), s.readAheadMb, READ_AHEAD_MB, "MB", stringResource(R.string.read_ahead_help)) {
            onChange(s.copy(readAheadMb = it))
        }
        Help(stringResource(R.string.cache_size_where))
    }
