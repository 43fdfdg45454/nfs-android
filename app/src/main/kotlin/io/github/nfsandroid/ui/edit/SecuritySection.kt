package io.github.nfsandroid.ui.edit

import android.app.Activity
import android.security.KeyChain
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nfsandroid.R
import io.github.nfsandroid.data.Server
import io.github.nfsandroid.ui.common.Help
import io.github.nfsandroid.ui.common.Section
import io.github.nfsandroid.ui.common.Segmented

/** The export's security (none, TLS, mutual TLS) and the phone's certificate, when one is needed. */
@Composable
fun SecuritySection(s: Server, onChange: (Server) -> Unit) =
    Section(stringResource(R.string.section_security), rememberVectorPainter(Icons.Outlined.Lock), stringResource(R.string.section_security_help)) {
        val options = listOf("none" to stringResource(R.string.security_none), "tls" to "TLS", "mtls" to "mTLS")
        Segmented(options, s.security) { onChange(s.copy(security = it)) }
        Help(stringResource(when (s.security) {
            "tls" -> R.string.tls_help
            "mtls" -> R.string.mtls_help
            else -> if (s.transport == "quic") R.string.none_help_quic else R.string.none_help
        }))
        if (s.security == "mtls" || s.transport == "quic") Certificate(s, onChange)
    }

/** The client certificate from Android's key store: the key never leaves it. */
@Composable
private fun Certificate(s: Server, onChange: (Server) -> Unit) {
    val activity = LocalContext.current as Activity
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.certificate), style = MaterialTheme.typography.bodyLarge)
                Text(
                    s.certificateAlias.ifBlank { stringResource(R.string.certificate_none) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (s.certificateAlias.isBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                Help(stringResource(if (s.transport == "quic") R.string.certificate_help_quic else R.string.certificate_help))
            }
            TextButton(onClick = {
                KeyChain.choosePrivateKeyAlias(activity, { alias -> alias?.let { onChange(s.copy(certificateAlias = it)) } },
                    null, null, null, s.certificateAlias.ifBlank { null })
            }) { Text(stringResource(if (s.certificateAlias.isBlank()) R.string.choose else R.string.change)) }
        }
    }
}
