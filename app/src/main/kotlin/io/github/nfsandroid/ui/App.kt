package io.github.nfsandroid.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.nfsandroid.R
import io.github.nfsandroid.data.Server
import io.github.nfsandroid.data.ServerStore
import io.github.nfsandroid.ui.activity.ActivityScreen
import io.github.nfsandroid.ui.edit.ServerEdit
import io.github.nfsandroid.ui.servers.Home
import io.github.nfsandroid.ui.settings.SettingsScreen

private enum class Tab(val label: Int) { Servers(R.string.tab_servers), Activity(R.string.tab_activity), Settings(R.string.tab_settings) }

/** Three tabs (servers, activity, settings); editing a server slides in over them. */
@Composable
fun App() {
    var tab by rememberSaveable { mutableStateOf(Tab.Servers) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    AnimatedContent(editing, transitionSpec = {
        if (targetState != null) {
            (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 } + fadeOut())
        } else {
            (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
        }
    }, label = "edit") { id ->
        if (id != null) {
            // A new server starts as the usual first user of a Linux server.
            val server = remember(id) { ServerStore.get(id) ?: Server(uid = 1000, gid = 1000) }
            ServerEdit(server, isNew = ServerStore.get(id) == null, onDone = { editing = null })
        } else {
            Tabs(tab, { tab = it }) { editing = it?.id ?: NEW }
        }
    }
}

@Composable
private fun Tabs(tab: Tab, onTab: (Tab) -> Unit, onEdit: (Server?) -> Unit) = Scaffold(bottomBar = {
    NavigationBar {
        Tab.entries.forEach { t ->
            val icon = when (t) {
                Tab.Servers -> painterResource(R.drawable.ic_servers)
                Tab.Activity -> painterResource(R.drawable.ic_activity)
                Tab.Settings -> rememberVectorPainter(Icons.Outlined.Settings)
            }
            NavigationBarItem(selected = tab == t, onClick = { onTab(t) }, icon = { Icon(icon, null) }, label = { Text(stringResource(t.label)) })
        }
    }
}) { padding ->
    // Towards the tab picked: right for one further on, left for one before.
    AnimatedContent(tab, Modifier.padding(bottom = padding.calculateBottomPadding()), transitionSpec = {
        val way = if (targetState.ordinal > initialState.ordinal) 1 else -1
        (slideInHorizontally { way * it / 6 } + fadeIn()) togetherWith (slideOutHorizontally { -way * it / 6 } + fadeOut())
    }, label = "tab") { shown ->
        Box {
            when (shown) {
                Tab.Servers -> Home(onEdit)
                Tab.Activity -> ActivityScreen()
                Tab.Settings -> SettingsScreen()
            }
        }
    }
}

/** The id of a server not saved yet. */
private const val NEW = ""
