package com.nada.music.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.nada.music.ui.LocalPlayer
import kotlinx.coroutines.delay

/** Posisi & durasi pemutaran (ms), diperbarui tiap 200 ms. */
@Composable
fun rememberPlaybackPosition(): Pair<Long, Long> {
    val player = LocalPlayer.current
    val state by produceState(0L to 0L, player) {
        while (true) {
            value = player.position to player.duration
            delay(200)
        }
    }
    return state
}
