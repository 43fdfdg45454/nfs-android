package io.github.nfsandroid.ui

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.nfsandroid.ui.theme.NfsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        setContent { NfsTheme { App() } }
    }
}
