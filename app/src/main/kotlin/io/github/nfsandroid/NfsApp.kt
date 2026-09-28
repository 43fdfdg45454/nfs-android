package io.github.nfsandroid

import android.app.Application
import io.github.nfsandroid.core.Mounts
import io.github.nfsandroid.data.ServerStore
import io.github.nfsandroid.log.CrashLog
import io.github.nfsandroid.log.NfsLog
import io.github.nfsandroid.service.ConnectionService

class NfsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        NfsLog.init(this)
        ServerStore.init(this)
        Mounts.init(this)
        io.github.nfsandroid.core.NetworkMonitor.init(this)
        io.github.nfsandroid.core.Networks.init(this)
        ConnectionService.channel(this)
    }
}
