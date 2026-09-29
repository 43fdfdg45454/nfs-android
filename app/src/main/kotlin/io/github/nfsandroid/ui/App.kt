package io.github.nfsandroid.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
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

/**
 * The tabs: upright, a bar below the screen; sideways (a phone on its side, a tablet), a rail
 * beside it, and the screen clear of the system's bars and the camera's cutout.
 */
@Composable
private fun Tabs(tab: Tab, onTab: (Tab) -> Unit, onEdit: (Server?) -> Unit) = BoxWithConstraints {
    if (maxWidth >= 600.dp && maxWidth > maxHeight) {
        val bars = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        Row {
            NavigationRail(windowInsets = bars.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)) {
                Tab.entries.forEach { t ->
                    NavigationRailItem(tab == t, { onTab(t) }, icon = { Icon(t.icon(), null) }, label = { Text(stringResource(t.label)) })
                }
            }
            Screens(tab, Modifier.weight(1f).windowInsetsPadding(bars.only(WindowInsetsSides.Bottom + WindowInsetsSides.End)), onEdit)
        }
    } else {
        Scaffold(bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(tab == t, { onTab(t) }, icon = { Icon(t.icon(), null) }, label = { Text(stringResource(t.label)) })
                }
            }
        }) { padding -> Screens(tab, Modifier.padding(bottom = padding.calculateBottomPadding()), onEdit) }
    }
}

@Composable
private fun Tab.icon(): Painter = when (this) {
    Tab.Servers -> painterResource(R.drawable.ic_servers)
    Tab.Activity -> painterResource(R.drawable.ic_activity)
    Tab.Settings -> rememberVectorPainter(Icons.Outlined.Settings)
}

/** The tab's screen, sliding towards the tab picked: right for one further on, left for one before. */
@Composable
private fun Screens(tab: Tab, modifier: Modifier, onEdit: (Server?) -> Unit) = AnimatedContent(tab, modifier, transitionSpec = {
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

/** The id of a server not saved yet. */
private const val NEW = ""
