package com.nada.music.audio

/**
 * Reverb stereo berbasis algoritma Freeverb (Jezar at Dreampoint, public domain):
 * 8 comb filter paralel + 4 allpass seri per kanal.
 */
class Reverb(sampleRate: Int) {

    private class Comb(size: Int) {
        private val buffer = FloatArray(size)
        private var index = 0
        private var store = 0f
        var feedback = 0.84f
        var damp = 0.2f

        fun process(input: Float): Float {
            val output = buffer[index]
            store = output * (1 - damp) + store * damp
            buffer[index] = input + store * feedback
            if (++index >= buffer.size) index = 0
            return output
        }

        fun clear() {
            buffer.fill(0f)
            store = 0f
        }
    }

    private class AllPass(size: Int) {
        private val buffer = FloatArray(size)
        private var index = 0

        fun process(input: Float): Float {
            val buffered = buffer[index]
            val output = -input + buffered
            buffer[index] = input + buffered * 0.5f
            if (++index >= buffer.size) index = 0
            return output
        }

        fun clear() = buffer.fill(0f)
    }

    private val scale = sampleRate / 44100f
    private fun scaled(n: Int) = (n * scale).toInt().coerceAtLeast(1)

    private val combTunings = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
    private val allPassTunings = intArrayOf(556, 441, 341, 225)
    private val stereoSpread = 23

    private val combsL = combTunings.map { Comb(scaled(it)) }
    private val combsR = combTunings.map { Comb(scaled(it + stereoSpread)) }
    private val allPassL = allPassTunings.map { AllPass(scaled(it)) }
    private val allPassR = allPassTunings.map { AllPass(scaled(it + stereoSpread)) }

    /** [room] 0..1 (panjang gema), [damping] 0..1 (meredam frekuensi tinggi). */
    fun configure(room: Float, damping: Float) {
        val feedback = 0.7f + room.coerceIn(0f, 1f) * 0.28f
        val damp = damping.coerceIn(0f, 1f) * 0.4f
        (combsL + combsR).forEach {
            it.feedback = feedback
            it.damp = damp
        }
    }

    /** Mengembalikan sinyal "wet" untuk satu frame stereo, ditulis ke [out] (L, R). */
    fun process(left: Float, right: Float, out: FloatArray) {
        val input = (left + right) * 0.015f
        var l = 0f
        var r = 0f
        for (i in combsL.indices) {
            l += combsL[i].process(input)
            r += combsR[i].process(input)
        }
        for (i in allPassL.indices) {
            l = allPassL[i].process(l)
            r = allPassR[i].process(r)
        }
        out[0] = l
        out[1] = r
    }

    fun clear() {
        (combsL + combsR).forEach { it.clear() }
        (allPassL + allPassR).forEach { it.clear() }
    }
}
