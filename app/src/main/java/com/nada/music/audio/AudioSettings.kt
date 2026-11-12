package com.nada.music.audio

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.pow

enum class VocalMode { NORMAL, REMOVE_VOCALS, VOCALS_ONLY }

/** Frekuensi tengah 5 band equalizer (Hz). */
val EQ_BANDS_HZ = floatArrayOf(60f, 230f, 910f, 3600f, 14000f)
const val EQ_MAX_DB = 12f

enum class EqPreset(val label: String, val gains: FloatArray) {
    FLAT("Normal", floatArrayOf(0f, 0f, 0f, 0f, 0f)),
    BASS("Bass Boost", floatArrayOf(7f, 4f, 0f, 0f, 1f)),
    POP("Pop", floatArrayOf(-1f, 2f, 4f, 2f, -1f)),
    ROCK("Rock", floatArrayOf(5f, 2f, -2f, 3f, 5f)),
    DANGDUT("Dangdut", floatArrayOf(5f, 3f, 1f, 2f, 3f)),
    VOCAL("Vokal", floatArrayOf(-2f, 0f, 4f, 4f, 1f)),
    JAZZ("Jazz", floatArrayOf(3f, 1f, -1f, 2f, 4f)),
    TREBLE("Treble", floatArrayOf(-1f, 0f, 0f, 4f, 7f)),
}

data class AudioSettings(
    val eqEnabled: Boolean = false,
    val eqGainsDb: List<Float> = List(EQ_BANDS_HZ.size) { 0f },
    val reverbEnabled: Boolean = false,
    /** 0..1, campuran suara reverb. */
    val reverbMix: Float = 0.3f,
    /** 0..1, ukuran ruangan (panjang gema). */
    val reverbRoom: Float = 0.6f,
    /** 0.5..2.0 */
    val speed: Float = 1f,
    /** Nada dalam semitone, -12..+12. */
    val pitchSemitones: Float = 0f,
    val vocalMode: VocalMode = VocalMode.NORMAL,
) {
    val pitchFactor: Float get() = 2f.pow(pitchSemitones / 12f)
    val isTempoModified: Boolean get() = speed != 1f || pitchSemitones != 0f
    val anyEffectOn: Boolean get() = eqEnabled || reverbEnabled || vocalMode != VocalMode.NORMAL
}

/**
 * Penyimpanan pengaturan audio. Aplikasi berjalan di satu proses, jadi UI dan
 * [com.nada.music.playback.PlaybackService] berbagi StateFlow yang sama.
 */
object AudioSettingsStore {
    private lateinit var prefs: SharedPreferences
    private val _settings = MutableStateFlow(AudioSettings())
    val settings: StateFlow<AudioSettings> = _settings.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("audio_settings", Context.MODE_PRIVATE)
        _settings.value = load()
    }

    fun update(transform: (AudioSettings) -> AudioSettings) {
        _settings.update(transform)
        if (::prefs.isInitialized) save(_settings.value)
    }

    fun resetTempo() = update { it.copy(speed = 1f, pitchSemitones = 0f) }

    private fun load(): AudioSettings {
        val d = AudioSettings()
        return AudioSettings(
            eqEnabled = prefs.getBoolean("eqEnabled", d.eqEnabled),
            eqGainsDb = prefs.getString("eqGains", null)
                ?.split(",")?.mapNotNull { it.toFloatOrNull() }
                ?.takeIf { it.size == EQ_BANDS_HZ.size } ?: d.eqGainsDb,
            reverbEnabled = prefs.getBoolean("reverbEnabled", d.reverbEnabled),
            reverbMix = prefs.getFloat("reverbMix", d.reverbMix),
            reverbRoom = prefs.getFloat("reverbRoom", d.reverbRoom),
            speed = prefs.getFloat("speed", d.speed),
            pitchSemitones = prefs.getFloat("pitch", d.pitchSemitones),
            vocalMode = runCatching { VocalMode.valueOf(prefs.getString("vocalMode", null)!!) }
                .getOrDefault(d.vocalMode),
        )
    }

    private fun save(s: AudioSettings) {
        prefs.edit()
            .putBoolean("eqEnabled", s.eqEnabled)
            .putString("eqGains", s.eqGainsDb.joinToString(","))
            .putBoolean("reverbEnabled", s.reverbEnabled)
            .putFloat("reverbMix", s.reverbMix)
            .putFloat("reverbRoom", s.reverbRoom)
            .putFloat("speed", s.speed)
            .putFloat("pitch", s.pitchSemitones)
            .putString("vocalMode", s.vocalMode.name)
            .apply()
    }
}
