package io.github.nfsandroid.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nfsandroid.R
import io.github.nfsandroid.data.Server
import io.github.nfsandroid.ui.common.Help
import io.github.nfsandroid.ui.common.NumberInput
import io.github.nfsandroid.ui.common.Section
import io.github.nfsandroid.ui.common.Segmented
import io.github.nfsandroid.ui.common.TextInput

/** Who the phone is to the server (AUTH_SYS): the numbers its permissions apply to. */
@Composable
fun IdentitySection(s: Server, onChange: (Server) -> Unit) =
    Section(stringResource(R.string.section_identity), rememberVectorPainter(Icons.Outlined.Person), stringResource(R.string.section_identity_help)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberInput(stringResource(R.string.uid), s.uid, Modifier.weight(1f)) { onChange(s.copy(uid = it)) }
            NumberInput(stringResource(R.string.gid), s.gid, Modifier.weight(1f)) { onChange(s.copy(gid = it)) }
        }
        Help(stringResource(R.string.uid_help))
        TextInput(stringResource(R.string.gids), s.gids.joinToString(", "), help = stringResource(R.string.gids_help), placeholder = "100, 1001") { v ->
            onChange(s.copy(gids = v.split(',', ' ').mapNotNull { it.trim().toIntOrNull() }))
        }
        Text(stringResource(R.string.umask), style = MaterialTheme.typography.bodyLarge)
        val options = listOf(
            Server.UMASK_STANDARD to stringResource(R.string.umask_standard),
            Server.UMASK_GROUP to stringResource(R.string.umask_group),
            Server.UMASK_PRIVATE to stringResource(R.string.umask_private),
        )
        Segmented(options, s.umask) { onChange(s.copy(umask = it)) }
        Help(stringResource(R.string.umask_help))
    }
