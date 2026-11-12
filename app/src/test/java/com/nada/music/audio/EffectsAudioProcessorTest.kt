package com.nada.music.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class EffectsAudioProcessorTest {

    private val rate = 44100

    private fun processor(settings: AudioSettings): EffectsAudioProcessor =
        EffectsAudioProcessor().apply {
            configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_16BIT))
            flush()
            apply(settings)
        }

    /** Sinyal stereo interleaved: [left] dan [right] berupa fungsi waktu (detik) → -1..1. */
    private fun stereo(seconds: Double, left: (Double) -> Double, right: (Double) -> Double): ShortArray {
        val n = (rate * seconds).toInt()
        return ShortArray(n * 2) { i ->
            val t = (i / 2).toDouble() / rate
            val v = if (i % 2 == 0) left(t) else right(t)
            (v * 32767 * 0.5).toInt().toShort()
        }
    }

    private fun run(p: EffectsAudioProcessor, samples: ShortArray): ShortArray {
        val input = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder())
        samples.forEach { input.putShort(it) }
        input.flip()
        val out = mutableListOf<Short>()
        // Kirim dalam beberapa potong, seperti ExoPlayer.
        val chunk = 4096
        while (input.hasRemaining()) {
            val slice = input.duplicate().order(ByteOrder.nativeOrder())
            slice.limit(minOf(input.position() + chunk, input.limit()))
            p.queueInput(slice)
            input.position(slice.position())
            val o = p.output
            while (o.hasRemaining()) out += o.short
        }
        return out.toShortArray()
    }

    private fun rms(samples: ShortArray, channel: Int, from: Int = 0): Double {
        var sum = 0.0
        var count = 0
        var i = from * 2 + channel
        while (i < samples.size) {
            sum += samples[i].toDouble() * samples[i]
            count++
            i += 2
        }
        return sqrt(sum / count)
    }

    @Test
    fun passthroughWhenAllEffectsOff() {
        val input = stereo(0.2, { sin(2 * PI * 440 * it) }, { sin(2 * PI * 660 * it) })
        val output = run(processor(AudioSettings()), input)
        assertArrayEquals(input, output)
    }

    @Test
    fun flatEqualizerKeepsSignal() {
        val input = stereo(0.3, { sin(2 * PI * 1000 * it) }, { sin(2 * PI * 1000 * it) })
        val output = run(processor(AudioSettings(eqEnabled = true)), input)
        assertEquals(input.size, output.size)
        val maxDiff = input.indices.drop(2000).maxOf { abs(input[it] - output[it]) }
        assertTrue("selisih EQ datar terlalu besar: $maxDiff", maxDiff <= 2)
    }

    @Test
    fun bassBoostRaisesLowFrequencies() {
        val input = stereo(0.5, { sin(2 * PI * 60 * it) * 0.5 }, { sin(2 * PI * 60 * it) * 0.5 })
        val output = run(processor(AudioSettings(eqEnabled = true, eqGainsDb = EqPreset.BASS.gains.toList())), input)
        assertTrue(rms(output, 0, 5000) > rms(input, 0, 5000) * 1.8)
    }

    @Test
    fun removeVocalsCancelsCenterButKeepsSidesAndBass() {
        // "Vokal" 1 kHz identik di L & R (tengah), gitar 3 kHz hanya di kiri (samping).
        val vocal = stereo(0.5, { sin(2 * PI * 1000 * it) }, { sin(2 * PI * 1000 * it) })
        val vocalOut = run(processor(AudioSettings(vocalMode = VocalMode.REMOVE_VOCALS)), vocal)
        assertTrue("vokal tengah masih terdengar", rms(vocalOut, 0, 2000) < rms(vocal, 0, 2000) * 0.05)

        val guitar = stereo(0.5, { sin(2 * PI * 3000 * it) }, { 0.0 })
        val guitarOut = run(processor(AudioSettings(vocalMode = VocalMode.REMOVE_VOCALS)), guitar)
        assertTrue("instrumen samping ikut hilang", rms(guitarOut, 0, 2000) > rms(guitar, 0, 2000) * 0.5)

        val bass = stereo(0.5, { sin(2 * PI * 50 * it) }, { sin(2 * PI * 50 * it) })
        val bassOut = run(processor(AudioSettings(vocalMode = VocalMode.REMOVE_VOCALS)), bass)
        assertTrue("bass tengah ikut hilang", rms(bassOut, 0, 5000) > rms(bass, 0, 5000) * 0.6)
    }

    @Test
    fun vocalsOnlyKeepsCenterAndDropsSides() {
        val vocal = stereo(0.5, { sin(2 * PI * 1000 * it) }, { sin(2 * PI * 1000 * it) })
        val vocalOut = run(processor(AudioSettings(vocalMode = VocalMode.VOCALS_ONLY)), vocal)
        assertTrue(rms(vocalOut, 0, 2000) > rms(vocal, 0, 2000) * 0.7)

        val side = stereo(0.5, { sin(2 * PI * 1000 * it) }, { -sin(2 * PI * 1000 * it) })
        val sideOut = run(processor(AudioSettings(vocalMode = VocalMode.VOCALS_ONLY)), side)
        assertTrue(rms(sideOut, 0, 2000) < rms(side, 0, 2000) * 0.05)
    }

    @Test
    fun reverbAddsTailAfterSoundStops() {
        val burst = stereo(1.0, { t -> if (t < 0.1) sin(2 * PI * 440 * t) else 0.0 }, { t -> if (t < 0.1) sin(2 * PI * 440 * t) else 0.0 })
        val dryOut = run(processor(AudioSettings()), burst)
        val wetOut = run(processor(AudioSettings(reverbEnabled = true, reverbMix = 0.5f)), burst)
        val tailStart = (rate * 0.3).toInt()
        assertEquals(0.0, rms(dryOut, 0, tailStart), 0.0)
        assertTrue("tidak ada gema", rms(wetOut, 0, tailStart) > 50)
    }

    @Test
    fun softClipNeverExceedsFullScale() {
        listOf(-10f, -1.5f, -0.9f, 0f, 0.5f, 0.9f, 1.5f, 10f).forEach {
            assertTrue(abs(EffectsAudioProcessor.softClip(it)) < 1f)
        }
        assertEquals(0.5f, EffectsAudioProcessor.softClip(0.5f))
    }
}
