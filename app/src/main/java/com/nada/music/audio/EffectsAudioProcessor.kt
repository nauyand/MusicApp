package com.nada.music.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Prosesor audio buatan sendiri yang dipasang di pipeline ExoPlayer, berurutan:
 * mode vokal → equalizer 5 band → reverb → limiter.
 *
 * Parameter bisa diubah kapan saja lewat [apply] tanpa menghentikan lagu.
 * Kecepatan & pitch tidak di sini: itu ditangani Sonic bawaan ExoPlayer.
 */
@UnstableApi
class EffectsAudioProcessor : BaseAudioProcessor() {

    @Volatile private var settings = AudioSettings()
    @Volatile private var dirty = true

    private var sampleRate = 0
    private var channels = 0

    private val eq = Array(2) { Array(EQ_BANDS_HZ.size) { Biquad() } }
    private val midLow = Biquad()      // bass dari kanal tengah (untuk mode tanpa vokal)
    private val midBandHp = Biquad()   // band-pass kanal tengah (untuk mode vokal saja)
    private val midBandLp = Biquad()
    private var reverb: Reverb? = null
    private val wet = FloatArray(2)

    fun apply(newSettings: AudioSettings) {
        settings = newSettings
        dirty = true
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..2) {
            // Format lain: lewati prosesor ini (tidak aktif).
            return AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount
        reverb = Reverb(sampleRate)
        dirty = true
        return inputAudioFormat
    }

    private fun updateFilters(s: AudioSettings) {
        for (ch in 0 until 2) {
            EQ_BANDS_HZ.forEachIndexed { band, freq ->
                val gain = s.eqGainsDb.getOrElse(band) { 0f }
                val f = freq.coerceAtMost(sampleRate * 0.45f)
                // Titik belok shelf digeser supaya frekuensi label band mendapat gain penuh.
                when (band) {
                    0 -> eq[ch][band].setLowShelf(sampleRate, f * 1.65f, gain)
                    EQ_BANDS_HZ.lastIndex -> eq[ch][band].setHighShelf(sampleRate, (f * 0.7f).coerceAtMost(sampleRate * 0.45f), gain)
                    else -> eq[ch][band].setPeaking(sampleRate, f, 1.0, gain)
                }
            }
        }
        midLow.setLowPass(sampleRate, 150f)
        midBandHp.setHighPass(sampleRate, 180f)
        midBandLp.setLowPass(sampleRate, 7000f)
        reverb?.configure(room = s.reverbRoom, damping = 0.5f)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val s = settings
        if (dirty) {
            dirty = false
            updateFilters(s)
        }

        val out = replaceOutputBuffer(remaining)
        val input = inputBuffer.order(ByteOrder.nativeOrder())

        if (!s.anyEffectOn) {
            out.put(input)
            out.flip()
            return
        }

        val stereo = channels == 2
        val vocalMode = if (stereo) s.vocalMode else VocalMode.NORMAL
        val reverbOn = s.reverbEnabled
        val mix = s.reverbMix.coerceIn(0f, 1f)
        val dry = 1f - mix * 0.5f
        val wetGain = mix * 3f
        val rev = reverb

        while (input.remaining() >= 2 * channels) {
            var l = input.short / 32768f
            var r = if (stereo) input.short / 32768f else l

            when (vocalMode) {
                VocalMode.NORMAL -> Unit
                VocalMode.REMOVE_VOCALS -> {
                    // Vokal biasanya ada di tengah (sama di L dan R). Buang bagian tengah,
                    // tapi pertahankan bass-nya supaya musik tidak terdengar tipis.
                    val mid = (l + r) * 0.5f
                    val side = (l - r) * 0.5f
                    val bass = midLow.process(mid)
                    l = bass + side * 1.4f
                    r = bass - side * 1.4f
                }
                VocalMode.VOCALS_ONLY -> {
                    // Ambil bagian tengah di rentang frekuensi suara manusia.
                    val mid = (l + r) * 0.5f
                    val v = midBandLp.process(midBandHp.process(mid)) * 1.3f
                    l = v
                    r = v
                }
            }

            if (s.eqEnabled) {
                for (band in eq[0].indices) {
                    l = eq[0][band].process(l)
                    r = eq[1][band].process(r)
                }
            }

            if (reverbOn && rev != null) {
                rev.process(l, r, wet)
                l = l * dry + wet[0] * wetGain
                r = r * dry + wet[1] * wetGain
            }

            out.putShort(toPcm(softClip(l)))
            if (stereo) out.putShort(toPcm(softClip(r)))
        }
        // Sisa byte yang tidak lengkap satu frame (seharusnya tidak terjadi).
        inputBuffer.position(inputBuffer.limit())
        out.flip()
    }

    override fun onFlush() {
        eq.forEach { bands -> bands.forEach(Biquad::reset) }
        midLow.reset()
        midBandHp.reset()
        midBandLp.reset()
        reverb?.clear()
    }

    override fun onReset() {
        reverb = null
        sampleRate = 0
        channels = 0
    }

    companion object {
        /** Limiter halus supaya EQ/reverb tidak membuat suara pecah. */
        fun softClip(x: Float): Float {
            val a = abs(x)
            if (a <= 0.8f) return x
            val over = a - 0.8f
            val compressed = 0.8f + over / (1f + over / 0.2f)
            return if (x < 0) -compressed else compressed
        }

        private fun toPcm(x: Float): Short =
            (x * 32767f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
}
