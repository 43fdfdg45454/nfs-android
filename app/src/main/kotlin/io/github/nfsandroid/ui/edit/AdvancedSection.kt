package io.github.nfsandroid.ui.edit

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import io.github.nfsandroid.R
import io.github.nfsandroid.data.Server
import io.github.nfsandroid.ui.common.Section
import io.github.nfsandroid.ui.common.SwitchRow

/** Edge cases allowed on purpose: off by default, what is sensible for most servers. */
@Composable
fun AdvancedSection(s: Server, onChange: (Server) -> Unit) =
    Section(stringResource(R.string.section_advanced), rememberVectorPainter(Icons.Outlined.Build), stringResource(R.string.section_advanced_help)) {
        SwitchRow(stringResource(R.string.follow_parent_links), stringResource(R.string.follow_parent_links_help), s.followParentLinks) {
            onChange(s.copy(followParentLinks = it))
        }
    }
