package io.github.nfsandroid.ui.edit

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.nfsandroid.R
import io.github.nfsandroid.data.Server
import io.github.nfsandroid.log.LogLevel
import io.github.nfsandroid.ui.common.Help
import io.github.nfsandroid.ui.common.Segmented
import io.github.nfsandroid.ui.common.SwitchRow

/** Each level with its name and what it adds to the ones before it. */
private val LEVELS = mapOf(
    LogLevel.ERROR to (R.string.log_error to R.string.log_error_help),
    LogLevel.WARN to (R.string.log_warn to R.string.log_warn_help),
    LogLevel.INFO to (R.string.log_info to R.string.log_info_help),
    LogLevel.DEBUG to (R.string.log_debug to R.string.log_debug_help),
)

/** The server's lines in nfs-log.txt: on or off, the level, and what each category holds. */
@Composable
fun LogSettings(s: Server, onChange: (Server) -> Unit) {
    SwitchRow(stringResource(R.string.log_enabled), stringResource(R.string.log_enabled_help), s.logEnabled) {
        onChange(s.copy(logEnabled = it))
    }
    if (!s.logEnabled) return
    Text(stringResource(R.string.log_level), style = MaterialTheme.typography.bodyLarge)
    val level = LogLevel.of(s.logLevel)
    Segmented(LEVELS.map { (value, names) -> value to stringResource(names.first) }, level) { onChange(s.copy(logLevel = it.key)) }
    Help(stringResource(LEVELS.getValue(level).second))
    Help(stringResource(R.string.log_categories))
}
