package com.nada.music.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.nada.music.data.MusicRepository
import com.nada.music.playback.PlayerConnection

val LocalPlayer = staticCompositionLocalOf<PlayerConnection> { error("PlayerConnection belum disediakan") }
val LocalRepository = staticCompositionLocalOf<MusicRepository> { error("MusicRepository belum disediakan") }
