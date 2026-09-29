package io.github.nfsandroid.ui.edit

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import io.github.nfsandroid.R
import io.github.nfsandroid.data.Server
import io.github.nfsandroid.ui.common.Help
import io.github.nfsandroid.ui.common.Section
import io.github.nfsandroid.ui.common.Segmented

/** The export's security (none, TLS, mutual TLS) and, with mutual TLS, the certificate nfsd asks for. */
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
        if (s.security == "mtls") {
            CertificatePicker(stringResource(R.string.certificate), s.certificateAlias, stringResource(R.string.certificate_help)) {
                onChange(s.copy(certificateAlias = it))
            }
        }
    }
