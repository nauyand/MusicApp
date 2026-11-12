package com.nada.music.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Filter biquad (rumus "Audio EQ Cookbook" dari Robert Bristow-Johnson).
 * Satu instance memproses satu kanal.
 */
class Biquad {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private var z1 = 0.0
    private var z2 = 0.0

    fun process(x: Float): Float {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y.toFloat()
    }

    fun reset() {
        z1 = 0.0
        z2 = 0.0
    }

    fun setPeaking(sampleRate: Int, freq: Float, q: Double, gainDb: Float) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2 * PI * freq / sampleRate
        val alpha = sin(w0) / (2 * q)
        set(
            1 + alpha * a, -2 * cos(w0), 1 - alpha * a,
            1 + alpha / a, -2 * cos(w0), 1 - alpha / a,
        )
    }

    fun setLowShelf(sampleRate: Int, freq: Float, gainDb: Float) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2 * PI * freq / sampleRate
        val cw = cos(w0)
        val alpha = sin(w0) / 2 * sqrt(2.0)
        val sa = 2 * sqrt(a) * alpha
        set(
            a * ((a + 1) - (a - 1) * cw + sa), 2 * a * ((a - 1) - (a + 1) * cw), a * ((a + 1) - (a - 1) * cw - sa),
            (a + 1) + (a - 1) * cw + sa, -2 * ((a - 1) + (a + 1) * cw), (a + 1) + (a - 1) * cw - sa,
        )
    }

    fun setHighShelf(sampleRate: Int, freq: Float, gainDb: Float) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2 * PI * freq / sampleRate
        val cw = cos(w0)
        val alpha = sin(w0) / 2 * sqrt(2.0)
        val sa = 2 * sqrt(a) * alpha
        set(
            a * ((a + 1) + (a - 1) * cw + sa), -2 * a * ((a - 1) + (a + 1) * cw), a * ((a + 1) + (a - 1) * cw - sa),
            (a + 1) - (a - 1) * cw + sa, 2 * ((a - 1) - (a + 1) * cw), (a + 1) - (a - 1) * cw - sa,
        )
    }

    fun setLowPass(sampleRate: Int, freq: Float, q: Double = 0.7071) {
        val w0 = 2 * PI * freq / sampleRate
        val alpha = sin(w0) / (2 * q)
        val cw = cos(w0)
        set((1 - cw) / 2, 1 - cw, (1 - cw) / 2, 1 + alpha, -2 * cw, 1 - alpha)
    }

    fun setHighPass(sampleRate: Int, freq: Float, q: Double = 0.7071) {
        val w0 = 2 * PI * freq / sampleRate
        val alpha = sin(w0) / (2 * q)
        val cw = cos(w0)
        set((1 + cw) / 2, -(1 + cw), (1 + cw) / 2, 1 + alpha, -2 * cw, 1 - alpha)
    }

    private fun set(nb0: Double, nb1: Double, nb2: Double, na0: Double, na1: Double, na2: Double) {
        b0 = nb0 / na0
        b1 = nb1 / na0
        b2 = nb2 / na0
        a1 = na1 / na0
        a2 = na2 / na0
    }
}
