package io.github.nfsandroid.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.nfsandroid.R
import io.github.nfsandroid.data.Settings
import io.github.nfsandroid.ui.common.Format
import io.github.nfsandroid.ui.common.Help
import io.github.nfsandroid.ui.common.Section
import io.github.nfsandroid.ui.common.AmountInput
import io.github.nfsandroid.ui.edit.Confirm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SIZES_GB = listOf(0, 1, 2, 4, 8, 16, 32, 64, 128)

/** The disk cache for every server: its size (for connections made after), how full it is, emptying it. */
@Composable
fun CacheSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var gb by remember { mutableIntStateOf(Settings.cacheGb(context)) }
    var used by remember { mutableLongStateOf(0L) }
    var confirm by remember { mutableStateOf(false) }
    suspend fun measure() { used = withContext(Dispatchers.IO) { uniffi.nfscore.cacheUsed(Settings.cacheDir(context)).toLong() } }
    LaunchedEffect(Unit) { measure() }
    Section(stringResource(R.string.section_cache), painterResource(R.drawable.ic_servers), stringResource(R.string.section_cache_help)) {
        AmountInput(stringResource(R.string.cache_size), gb, SIZES_GB, "GB", stringResource(R.string.cache_size_help)) {
            gb = it
            Settings.setCacheGb(context, it)
        }
        val size = gb.toLong() shl 30
        LinearProgressIndicator({ if (size > 0) (used.toFloat() / size).coerceIn(0f, 1f) else 0f }, Modifier.fillMaxWidth())
        Help(stringResource(R.string.cache_used, Format.bytes(used)))
        OutlinedButton(onClick = { confirm = true }, Modifier.fillMaxWidth(), enabled = used > 0) {
            Icon(rememberVectorPainter(Icons.Outlined.Delete), null)
            Text(stringResource(R.string.cache_clear))
        }
    }
    if (confirm) Confirm(R.string.cache_clear_title, R.string.cache_clear_text, R.string.cache_clear, onYes = {
        scope.launch { withContext(Dispatchers.IO) { uniffi.nfscore.cacheClear(Settings.cacheDir(context)) }; measure() }
    }) { confirm = false }
}
