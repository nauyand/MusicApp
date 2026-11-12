package com.nada.music.ui.effects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nada.music.audio.AudioSettings
import com.nada.music.audio.AudioSettingsStore
import com.nada.music.audio.EQ_BANDS_HZ
import com.nada.music.audio.EQ_MAX_DB
import com.nada.music.audio.EqPreset
import com.nada.music.audio.VocalMode
import com.nada.music.ui.LocalPlayer
import com.nada.music.ui.glass.ArtworkBackdrop
import com.nada.music.ui.glass.ArtworkColors
import com.nada.music.ui.glass.GlassDarkTheme
import com.nada.music.ui.glass.glass
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

private data class TempoPreset(
    val label: String,
    val speed: Float,
    val pitch: Float,
    val reverb: Boolean? = null,
)

private fun semitonesFor(factor: Float) = (12 * ln(factor) / ln(2f) * 10).roundToInt() / 10f

private val tempoPresets = listOf(
    TempoPreset("Normal", 1f, 0f, reverb = false),
    TempoPreset("Slowed", 0.85f, semitonesFor(0.85f)),
    TempoPreset("Slowed + Reverb", 0.85f, semitonesFor(0.85f), reverb = true),
    TempoPreset("Nightcore", 1.25f, semitonesFor(1.25f)),
    TempoPreset("Sped Up", 1.2f, 0f),
)

@Composable
fun EffectsScreen(colors: ArtworkColors, onClose: () -> Unit) {
    val song by LocalPlayer.current.currentSong.collectAsState()
    val settings by AudioSettingsStore.settings.collectAsState()

    GlassDarkTheme(colors) {
        Box(Modifier.fillMaxSize()) {
            ArtworkBackdrop(song?.thumbnailUrl, colors)
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Tutup") }
                    Text("Efek Audio", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                }
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.navigationBarsPadding(),
                ) {
                    item { TempoCard(settings) }
                    item { EqualizerCard(settings) }
                    item { ReverbCard(settings) }
                    item { VocalCard(settings) }
                }
            }
        }
    }
}

@Composable
private fun GlassCard(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .glass()
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TempoCard(s: AudioSettings) {
    GlassCard(
        title = "Kecepatan & Nada",
        trailing = {
            if (s.isTempoModified) {
                TextButton(onClick = AudioSettingsStore::resetTempo) {
                    Icon(Icons.Rounded.RestartAlt, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Reset")
                }
            }
        },
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tempoPresets.forEach { preset ->
                val selected = abs(s.speed - preset.speed) < 0.001f && abs(s.pitchSemitones - preset.pitch) < 0.05f &&
                    (preset.reverb == null || preset.reverb == s.reverbEnabled)
                GlassChip(preset.label, selected) {
                    AudioSettingsStore.update {
                        it.copy(
                            speed = preset.speed,
                            pitchSemitones = preset.pitch,
                            reverbEnabled = preset.reverb ?: it.reverbEnabled,
                            reverbMix = if (preset.reverb == true) 0.45f else it.reverbMix,
                            reverbRoom = if (preset.reverb == true) 0.85f else it.reverbRoom,
                        )
                    }
                }
            }
        }
        LabeledSlider(
            label = "Kecepatan",
            valueText = "%.2fx".format(s.speed),
            value = s.speed,
            range = 0.5f..2f,
            steps = 29,
            onChange = { v -> AudioSettingsStore.update { it.copy(speed = (v * 20).roundToInt() / 20f) } },
        )
        LabeledSlider(
            label = "Nada (pitch)",
            valueText = when {
                s.pitchSemitones > 0 -> "+%.1f".format(s.pitchSemitones)
                s.pitchSemitones < 0 -> "%.1f".format(s.pitchSemitones)
                else -> "0"
            } + " semitone",
            value = s.pitchSemitones,
            range = -12f..12f,
            steps = 47,
            onChange = { v -> AudioSettingsStore.update { it.copy(pitchSemitones = (v * 2).roundToInt() / 2f) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EqualizerCard(s: AudioSettings) {
    GlassCard(
        title = "Equalizer",
        trailing = {
            Switch(checked = s.eqEnabled, onCheckedChange = { on -> AudioSettingsStore.update { it.copy(eqEnabled = on) } })
        },
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EqPreset.entries.forEach { preset ->
                val selected = s.eqEnabled && preset.gains.toList() == s.eqGainsDb
                GlassChip(preset.label, selected) {
                    AudioSettingsStore.update { it.copy(eqEnabled = true, eqGainsDb = preset.gains.toList()) }
                }
            }
        }
        EQ_BANDS_HZ.forEachIndexed { band, hz ->
            val gain = s.eqGainsDb.getOrElse(band) { 0f }
            LabeledSlider(
                label = if (hz >= 1000) "${(hz / 1000).let { if (it % 1f == 0f) it.toInt().toString() else "%.1f".format(it) }} kHz" else "${hz.toInt()} Hz",
                valueText = (if (gain > 0) "+" else "") + "${gain.roundToInt()} dB",
                value = gain,
                range = -EQ_MAX_DB..EQ_MAX_DB,
                steps = (EQ_MAX_DB * 2).toInt() - 1,
                enabled = s.eqEnabled,
                onChange = { v ->
                    AudioSettingsStore.update {
                        it.copy(eqGainsDb = it.eqGainsDb.toMutableList().also { g -> g[band] = v.roundToInt().toFloat() })
                    }
                },
            )
        }
    }
}

@Composable
private fun ReverbCard(s: AudioSettings) {
    GlassCard(
        title = "Reverb",
        trailing = {
            Switch(checked = s.reverbEnabled, onCheckedChange = { on -> AudioSettingsStore.update { it.copy(reverbEnabled = on) } })
        },
    ) {
        Text(
            "Menambahkan gema seperti di dalam ruangan atau aula.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LabeledSlider(
            label = "Jumlah",
            valueText = "${(s.reverbMix * 100).roundToInt()}%",
            value = s.reverbMix,
            range = 0f..1f,
            enabled = s.reverbEnabled,
            onChange = { v -> AudioSettingsStore.update { it.copy(reverbMix = v) } },
        )
        LabeledSlider(
            label = "Ukuran ruang",
            valueText = when {
                s.reverbRoom < 0.35f -> "Kamar"
                s.reverbRoom < 0.7f -> "Studio"
                s.reverbRoom < 0.9f -> "Aula"
                else -> "Katedral"
            },
            value = s.reverbRoom,
            range = 0f..1f,
            enabled = s.reverbEnabled,
            onChange = { v -> AudioSettingsStore.update { it.copy(reverbRoom = v) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VocalCard(s: AudioSettings) {
    GlassCard(title = "Vokal & Instrumental") {
        val options = listOf(
            VocalMode.NORMAL to "Normal",
            VocalMode.REMOVE_VOCALS to "Instrumental",
            VocalMode.VOCALS_ONLY to "Vokal saja",
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (mode, label) ->
                SegmentedButton(
                    selected = s.vocalMode == mode,
                    onClick = { AudioSettingsStore.update { it.copy(vocalMode = mode) } },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        activeContentColor = Color.White,
                        inactiveContainerColor = Color.Transparent,
                        inactiveContentColor = Color.White.copy(alpha = 0.8f),
                    ),
                ) { Text(label, maxLines = 1) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Bekerja dengan memisahkan bagian tengah stereo (tempat vokal biasanya berada). " +
                "Hasil terbaik pada lagu studio stereo. Tidak sempurna: sisa vokal atau musik masih bisa terdengar, " +
                "dan tidak berfungsi pada audio mono.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GlassChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(16.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.White.copy(alpha = 0.08f),
            labelColor = Color.White,
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            selectedLabelColor = Color.White,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = Color.White.copy(alpha = 0.2f),
            selectedBorderColor = Color.White.copy(alpha = 0.45f),
        ),
    )
}

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    steps: Int = 0,
    enabled: Boolean = true,
) {
    Column(Modifier.padding(top = 6.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps, enabled = enabled)
    }
}
