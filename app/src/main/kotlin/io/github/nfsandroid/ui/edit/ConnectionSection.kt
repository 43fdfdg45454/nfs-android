package io.github.nfsandroid.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.nfsandroid.R
import io.github.nfsandroid.data.Server
import io.github.nfsandroid.ui.common.Help
import io.github.nfsandroid.ui.common.NumberInput
import io.github.nfsandroid.ui.common.Section
import io.github.nfsandroid.ui.common.Segmented
import io.github.nfsandroid.ui.common.TextInput

/** Its name, how it is reached (TCP to nfsd, or QUIC to the gateway), where, and which export. */
@Composable
fun ConnectionSection(s: Server, problems: Problems, onChange: (Server) -> Unit) =
    Section(stringResource(R.string.section_connection), painterResource(R.drawable.ic_servers), stringResource(R.string.section_connection_help)) {
        TextInput(
            stringResource(R.string.name), s.name, help = stringResource(R.string.name_help),
            placeholder = s.host.ifBlank { stringResource(R.string.name_placeholder) },
        ) { onChange(s.copy(name = it)) }
        Segmented(listOf("tcp" to stringResource(R.string.transport_tcp), "quic" to stringResource(R.string.transport_quic)), s.transport) {
            onChange(s.over(it))
        }
        Help(stringResource(if (s.transport == "quic") R.string.quic_help else R.string.tcp_help))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextInput(
                stringResource(if (s.transport == "quic") R.string.host_gateway else R.string.host), s.host, Modifier.weight(1f),
                error = problems.host?.let { stringResource(it) }, placeholder = "nas.example.net", keyboard = KeyboardType.Uri,
            ) { onChange(s.copy(host = it.trim())) }
            NumberInput(stringResource(R.string.port), s.port, Modifier.width(112.dp), error = problems.port?.let { stringResource(it) }) {
                onChange(s.copy(port = it))
            }
        }
        Help(stringResource(if (s.transport == "quic") R.string.host_help_quic else R.string.host_help_tcp))
        TextInput(
            stringResource(R.string.export), s.export, help = stringResource(R.string.export_help),
            error = problems.export?.let { stringResource(it) }, keyboard = KeyboardType.Uri,
        ) { onChange(s.copy(export = it.trim())) }
    }
