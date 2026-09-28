package io.github.nfsandroid.ui.common

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp

/** A group of related settings on a card: an icon, a title, what the group is for, its controls. */
@Composable
fun Section(title: String, icon: Painter, help: String? = null, content: @Composable ColumnScope.() -> Unit) = Card(
    Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
) {
    Column(Modifier.animateContentSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            Column(Modifier.padding(start = 14.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                help?.let { Help(it) }
            }
        }
        content()
    }
}

/** An explanation under a control or a title. */
@Composable
fun Help(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** A short label on a server: its transport, its security. */
@Composable
fun Tag(text: String, strong: Boolean = false) = Text(
    text,
    Modifier.clip(MaterialTheme.shapes.small)
        .background(if (strong) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
        .padding(horizontal = 10.dp, vertical = 4.dp),
    style = MaterialTheme.typography.labelMedium,
    color = if (strong) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
)

/** A label and its value, on one line (the activity screen's figures). */
@Composable
fun Figure(label: String, value: String) = Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
}
