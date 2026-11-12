package com.nada.music

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.nada.music.playback.PlayerConnection
import com.nada.music.ui.LocalPlayer
import com.nada.music.ui.LocalRepository
import com.nada.music.ui.NadaRoot
import com.nada.music.ui.theme.NadaTheme

class MainActivity : ComponentActivity() {

    private lateinit var playerConnection: PlayerConnection

    /** Diset true saat activity dibuka dari notifikasi media. */
    private val openPlayerRequest = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        playerConnection = PlayerConnection(this)
        handleIntent(intent)
        requestNotificationPermission()

        setContent {
            NadaTheme {
                CompositionLocalProvider(
                    LocalPlayer provides playerConnection,
                    LocalRepository provides (application as NadaApp).repository,
                ) {
                    NadaRoot(openPlayerRequest)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_PLAYER, false) == true) {
            openPlayerRequest.value = true
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
    }

    override fun onDestroy() {
        playerConnection.release()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "open_player"
    }
}
