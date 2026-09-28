package io.github.nfsandroid.ui.edit

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import io.github.nfsandroid.R
import io.github.nfsandroid.data.Server
import io.github.nfsandroid.ui.common.Help
import io.github.nfsandroid.ui.common.Segmented
import io.github.nfsandroid.ui.common.TextInput

private const val OTHER = -1
private val PRESETS = listOf(Server.UMASK_STANDARD, Server.UMASK_GROUP, Server.UMASK_PRIVATE)

private fun octal(n: Int) = n.toString(8).padStart(3, '0')

/** What new files and folders may not get (a umask): three usual ones, or any typed in octal. */
@Composable
fun Permissions(umask: Int, onChange: (Int) -> Unit) {
    var other by remember { mutableStateOf(umask !in PRESETS) }
    var text by remember { mutableStateOf(octal(umask)) }
    Text(stringResource(R.string.umask), style = MaterialTheme.typography.bodyLarge)
    val options = listOf(
        Server.UMASK_STANDARD to stringResource(R.string.umask_standard),
        Server.UMASK_GROUP to stringResource(R.string.umask_group),
        Server.UMASK_PRIVATE to stringResource(R.string.umask_private),
        OTHER to stringResource(R.string.umask_other),
    )
    Segmented(options, if (other) OTHER else umask) { v ->
        other = v == OTHER
        if (!other) { text = octal(v); onChange(v) }
    }
    if (other) {
        val typed = text.toIntOrNull(8)?.takeIf { it <= "777".toInt(8) }
        val result = typed?.let { stringResource(R.string.umask_result, octal("666".toInt(8) and it.inv()), octal("777".toInt(8) and it.inv())) }
        TextInput(stringResource(R.string.umask_mask), text, help = result, error = stringResource(R.string.umask_invalid).takeIf { typed == null },
            keyboard = KeyboardType.Number) { v ->
            text = v.filter { it in '0'..'7' }.take(4)
            text.toIntOrNull(8)?.takeIf { it <= "777".toInt(8) }?.let(onChange)
        }
    }
    Help(stringResource(R.string.umask_help))
}
