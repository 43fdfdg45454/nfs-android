package io.github.nfsandroid.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.nfsandroid.R
import io.github.nfsandroid.core.Networks
import io.github.nfsandroid.data.Server
import io.github.nfsandroid.ui.common.Help
import io.github.nfsandroid.ui.common.Section
import io.github.nfsandroid.ui.common.Segmented
import io.github.nfsandroid.ui.common.TextInput

/** Where the server is reached: any network, or only a VPN or a Wi-Fi (that one, by its subnet). */
@Composable
fun NetworkSection(s: Server, problem: Int?, onChange: (Server) -> Unit) =
    Section(stringResource(R.string.section_network), painterResource(R.drawable.ic_network), stringResource(R.string.section_network_help)) {
        val links by Networks.state.collectAsState()
        val options = listOf("any" to stringResource(R.string.network_any), "vpn" to "VPN", "lan" to stringResource(R.string.network_lan))
        Segmented(options, s.networkKind) { onChange(s.copy(networkKind = it)) }
        if (s.networkKind == "any") {
            Help(stringResource(R.string.network_any_help))
            return@Section
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextInput(
                stringResource(R.string.network_subnet), s.networkSubnet, Modifier.weight(1f), placeholder = stringResource(R.string.network_subnet_any),
                error = problem?.let { stringResource(it) }, keyboard = KeyboardType.Uri,
            ) { onChange(s.copy(networkSubnet = it.trim())) }
            val now = links.let { Networks.subnetNow(s.networkKind) }
            OutlinedButton(onClick = { now?.let { onChange(s.copy(networkSubnet = it)) } }, enabled = now != null) {
                Text(stringResource(R.string.network_use_current))
            }
        }
        val now = links.filter { it.kind == s.networkKind }.flatMap { it.subnets }
        Help(stringResource(R.string.network_subnet_help))
        Help(if (now.isEmpty()) stringResource(R.string.network_none_now) else stringResource(R.string.network_now, now.joinToString(", ")))
    }
