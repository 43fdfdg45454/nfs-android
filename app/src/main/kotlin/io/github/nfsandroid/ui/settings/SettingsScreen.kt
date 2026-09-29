package io.github.nfsandroid.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
import android.provider.Settings.EXTRA_APP_PACKAGE
import android.provider.Settings.EXTRA_CHANNEL_ID
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nfsandroid.R
import io.github.nfsandroid.service.ConnectionService
import io.github.nfsandroid.ui.common.Figure
import io.github.nfsandroid.ui.common.Section

/** What applies to every server: the local cache, the notification, and about the app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_settings)) }) }, contentWindowInsets = WindowInsets(0)) { padding ->
        androidx.compose.foundation.layout.Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).wrapContentWidth().widthIn(max = 720.dp).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppearanceSection()
            CacheSection()
            Section(stringResource(R.string.section_notification), rememberVectorPainter(Icons.Outlined.Notifications), stringResource(R.string.section_notification_help)) {
                OutlinedButton(onClick = {
                    context.startActivity(Intent(ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(EXTRA_APP_PACKAGE, context.packageName).putExtra(EXTRA_CHANNEL_ID, ConnectionService.CHANNEL))
                }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.notification_settings)) }
            }
            Section(stringResource(R.string.section_about), rememberVectorPainter(Icons.Outlined.Info)) {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                Figure(stringResource(R.string.version), "${info.versionName} (${info.longVersionCode})")
                Figure(stringResource(R.string.license), "GPL-3.0")
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE))) }) {
                    Text(stringResource(R.string.source_code))
                }
            }
        }
    }
}

private const val SOURCE = "https://github.com/43fdfdg45454/nfs-android"
