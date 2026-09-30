package io.github.nfsandroid.ui.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.nfsandroid.R
import io.github.nfsandroid.ui.common.Help

/** The app's mark, as in its icon, in the look's colours. */
@Composable
fun Logo(size: Dp) = Box(
    Modifier.size(size).clip(MaterialTheme.shapes.medium).background(accent()),
    contentAlignment = Alignment.Center,
) {
    Icon(
        painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(size).graphicsLayer(scaleX = 1.45f, scaleY = 1.45f),
        tint = MaterialTheme.colorScheme.onPrimary,
    )
}

/** The look's gradient: its primary colour to its secondary. */
@Composable
fun accent() = Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))

@Composable
fun Wordmark() = Row(verticalAlignment = Alignment.CenterVertically) {
    Logo(34.dp)
    Spacer(Modifier.width(12.dp))
    Text(stringResource(R.string.app_name))
}

/** No server yet: what the app does and how to start. */
@Composable
fun EmptyState() = Column(Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Logo(72.dp)
    Text(stringResource(R.string.empty_title), Modifier.padding(top = 20.dp, bottom = 8.dp), style = MaterialTheme.typography.titleLarge)
    Help(stringResource(R.string.empty_help))
    Text(stringResource(R.string.hint_files_app), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}
