package com.nada.music

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import android.graphics.Color
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
        // Ikon status bar & navigasi selalu putih (tema gelap).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        requestMaxRefreshRate()

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

    /**
     * Minta layar berjalan di refresh rate tertinggi yang didukung (90/120/144 Hz)
     * dengan resolusi yang sama. Beberapa pabrikan tetap bisa membatasi ini lewat
     * pengaturan sistem atau mode hemat baterai.
     */
    @Suppress("DEPRECATION")
    private fun requestMaxRefreshRate() {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else windowManager.defaultDisplay
        display ?: return
        val current = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
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
