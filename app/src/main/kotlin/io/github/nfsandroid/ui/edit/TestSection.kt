package io.github.nfsandroid.ui.edit

import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nfsandroid.R
import io.github.nfsandroid.core.Connector
import io.github.nfsandroid.data.Server
import io.github.nfsandroid.log.NfsLog
import io.github.nfsandroid.ui.common.Format
import io.github.nfsandroid.ui.common.Section
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

private val TIMEOUT = 20.seconds

/** Connects with the settings as they are on screen, before saving them, and tells what happened. */
@Composable
fun TestSection(server: Server, enabled: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Result<String>?>(null) }
    Section(stringResource(R.string.section_test), rememberVectorPainter(Icons.Outlined.CheckCircle), stringResource(R.string.section_test_help)) {
        FilledTonalButton(
            onClick = { running = true; result = null; scope.launch { result = test(context, server); running = false } },
            enabled = enabled && !running, modifier = Modifier.fillMaxWidth(),
        ) {
            if (running) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.test))
        }
        result?.let { r ->
            val ok = r.isSuccess
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                color = if (ok) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.Warning, null)
                    Text(r.getOrElse { stringResource(R.string.test_failed, it.message.orEmpty().removePrefix("reason=")) },
                        Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private suspend fun test(context: Context, server: Server): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
        val (mount, took) = measureTimedValue {
            // An address that never answers (a closed UDP port gets no reply) must not hang the test.
            withTimeoutOrNull(TIMEOUT) { Connector.connect(context, server) } ?: error(context.getString(R.string.test_timeout, TIMEOUT.inWholeSeconds))
        }
        try {
            val (space, entries) = mount.space() to mount.list("").size
            context.getString(R.string.test_ok, took.inWholeMilliseconds, entries,
                Format.bytes(space.available.toLong()), Format.bytes(space.total.toLong()))
        } finally {
            mount.disconnect()
        }
    }.also { NfsLog.line("test of ${server.title}: ${it.getOrElse { e -> e.message }}") }
}
