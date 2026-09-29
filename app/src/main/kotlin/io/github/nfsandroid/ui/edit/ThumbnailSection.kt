package io.github.nfsandroid.ui.edit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nfsandroid.R
import io.github.nfsandroid.data.Server
import io.github.nfsandroid.data.ThumbnailSources
import io.github.nfsandroid.ui.common.AmountInput
import io.github.nfsandroid.ui.common.Help
import io.github.nfsandroid.ui.common.Reorderable
import io.github.nfsandroid.ui.common.Section

private val MAX_MB = listOf(1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024)

/** Each source's name and what it looks for. */
val THUMBNAIL_SOURCES = mapOf(
    "sidecar" to (R.string.thumb_sidecar to R.string.thumb_sidecar_help),
    "embedded" to (R.string.thumb_embedded to R.string.thumb_embedded_help),
    "attachment" to (R.string.thumb_attachment to R.string.thumb_attachment_help),
    "exif" to (R.string.thumb_exif to R.string.thumb_exif_help),
    "decode" to (R.string.thumb_decode to R.string.thumb_decode_help),
)

/** Where thumbnails come from, in the order dragged, each on or off; and the most one may read. */
@Composable
fun ThumbnailSection(s: Server, onChange: (Server) -> Unit) =
    Section(stringResource(R.string.section_thumbnails), painterResource(R.drawable.ic_image), stringResource(R.string.section_thumbnails_help)) {
        Reorderable(ThumbnailSources.order(s.thumbnailOrder), { onChange(s.copy(thumbnailOrder = it)) }) { key, handle ->
            val (title, help) = THUMBNAIL_SOURCES.getValue(key)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Menu, stringResource(R.string.drag_to_order), handle.padding(8.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Checkbox(key !in s.thumbnailOff, { on -> onChange(s.copy(thumbnailOff = if (on) s.thumbnailOff - key else s.thumbnailOff + key)) })
                Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                    Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
                    Help(stringResource(help))
                }
            }
        }
        AmountInput(stringResource(R.string.thumbnail_max), s.thumbnailMaxMb, MAX_MB, "MB", stringResource(R.string.thumbnail_max_help)) {
            onChange(s.copy(thumbnailMaxMb = it))
        }
    }
