package io.github.nfsandroid.ui.edit

import android.app.Activity
import android.security.KeyChain
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nfsandroid.R
import io.github.nfsandroid.ui.common.Help

/** A client certificate from Android's key store (the key never leaves it), chosen or changed. */
@Composable
fun CertificatePicker(title: String, alias: String, help: String, onChange: (String) -> Unit) {
    val activity = LocalContext.current as Activity
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    alias.ifBlank { stringResource(R.string.certificate_none) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (alias.isBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                Help(help)
            }
            TextButton(onClick = {
                KeyChain.choosePrivateKeyAlias(activity, { chosen -> chosen?.let(onChange) }, null, null, null, alias.ifBlank { null })
            }) { Text(stringResource(if (alias.isBlank()) R.string.choose else R.string.change)) }
        }
    }
}
