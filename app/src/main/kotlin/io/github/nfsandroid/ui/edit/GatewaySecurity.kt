package io.github.nfsandroid.ui.edit

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.nfsandroid.R
import io.github.nfsandroid.data.Server
import io.github.nfsandroid.ui.common.Help
import io.github.nfsandroid.ui.common.Segmented

/**
 * The QUIC tunnel's own security, apart from the export's: always encrypted and the gateway's
 * certificate always checked (QUIC is TLS 1.3); with mTLS the phone presents one too.
 */
@Composable
fun GatewaySecurity(s: Server, onChange: (Server) -> Unit) {
    Text(stringResource(R.string.gateway_security), style = MaterialTheme.typography.bodyLarge)
    Segmented(listOf("tls" to "TLS", "mtls" to "mTLS"), s.gatewaySecurity) { onChange(s.copy(gatewaySecurity = it)) }
    Help(stringResource(if (s.gatewaySecurity == "mtls") R.string.gateway_mtls_help else R.string.gateway_tls_help))
    if (s.gatewaySecurity == "mtls") {
        CertificatePicker(stringResource(R.string.gateway_certificate), s.gatewayCertificateAlias,
            stringResource(R.string.gateway_certificate_help)) { onChange(s.copy(gatewayCertificateAlias = it)) }
    }
}
