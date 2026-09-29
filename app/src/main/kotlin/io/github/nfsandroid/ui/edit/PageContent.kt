package io.github.nfsandroid.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nfsandroid.data.Server

/** One page's sections, never wider than reads well. */
@Composable
fun PageContent(page: Page, s: Server, problems: Problems, modifier: Modifier = Modifier, onChange: (Server) -> Unit) =
    Box(modifier.imePadding().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (page) {
                Page.General -> {
                    ConnectionSection(s, problems, onChange)
                    AccessSection(s, onChange)
                }
                Page.Network -> NetworkSection(s, problems.subnet, onChange)
                Page.Security -> SecuritySection(s, onChange)
                Page.Identity -> IdentitySection(s, onChange)
                Page.Performance -> PerformanceSection(s, onChange)
                Page.Thumbnails -> ThumbnailSection(s, onChange)
                Page.Advanced -> AdvancedSection(s, onChange)
                Page.Log -> LogSection(s, onChange)
                Page.Test -> TestSection(s, enabled = problems.none)
            }
        }
    }
