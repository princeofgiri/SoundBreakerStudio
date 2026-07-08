package id.soundbreaker.studio.audio

import id.soundbreaker.studio.data.MasterEqPresets
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

class MasterEqProcessor(private val sampleRate: Int) {

    private val gains = FloatArray(MasterEqPresets.bandFrequencies.size) { 0f }

    // Biquad coefficients per band (stereo: left + right share same filter state)
    private data class BiquadCoeffs(val b0: Float, val b1: Float, val b2: Float, val a1: Float, val a2: Float)
    private var leftFilters = Array(MasterEqPresets.bandFrequencies.size) { BiquadCoeffs(1f, 0f, 0f, 0f, 0f) }
    private var rightFilters = Array(MasterEqPresets.bandFrequencies.size) { BiquadCoeffs(1f, 0f, 0f, 0f, 0f) }
    private var leftState = Array(MasterEqPresets.bandFrequencies.size) { floatArrayOf(0f, 0f, 0f, 0f) }
    private var rightState = Array(MasterEqPresets.bandFrequencies.size) { floatArrayOf(0f, 0f, 0f, 0f) }

    init {
        recalcFilters()
    }

    fun setGains(newGains: List<Float>) {
        for (i in gains.indices) {
            gains[i] = newGains.getOrElse(i) { 0f }
        }
        recalcFilters()
    }

    private fun recalcFilters() {
        for (i in MasterEqPresets.bandFrequencies.indices) {
            val freq = MasterEqPresets.bandFrequencies[i].toFloat()
            val gain = gains[i]
            leftFilters[i] = calcPeaking(freq, gain, 1.0f, sampleRate.toFloat())
            rightFilters[i] = calcPeaking(freq, gain, 1.0f, sampleRate.toFloat())
        }
    }

    private fun calcPeaking(f0: Float, gain: Float, q: Float, sr: Float): BiquadCoeffs {
        if (kotlin.math.abs(gain) < 0.01f) return BiquadCoeffs(1f, 0f, 0f, 0f, 0f)
        val A = Math.pow(10.0, gain / 40.0).toFloat()
        val w0 = (2.0 * PI * f0 / sr).toFloat()
        val sinW0 = sin(w0.toDouble()).toFloat()
        val cosW0 = cos(w0.toDouble()).toFloat()
        val alpha = sinW0 / (2f * q)
        val b0 = 1f + alpha * A
        val b1 = -2f * cosW0
        val b2 = 1f - alpha * A
        val a0 = 1f + alpha / A
        val a1 = -2f * cosW0
        val a2 = 1f - alpha / A
        return BiquadCoeffs(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    }

    fun processStereo(output: ShortArray, frames: Int) {
        for (i in 0 until frames) {
            var left = output[i * 2].toFloat()
            var right = output[i * 2 + 1].toFloat()
            for (b in gains.indices) {
                if (kotlin.math.abs(gains[b]) < 0.01f) continue
                val lc = leftFilters[b]
                val ls = leftState[b]
                val lo = lc.b0 * left + lc.b1 * ls[0] + lc.b2 * ls[1] - lc.a1 * ls[2] - lc.a2 * ls[3]
                ls[1] = ls[0]; ls[0] = left; ls[3] = ls[2]; ls[2] = lo
                left = lo
                val rc = rightFilters[b]
                val rs = rightState[b]
                val ro = rc.b0 * right + rc.b1 * rs[0] + rc.b2 * rs[1] - rc.a1 * rs[2] - rc.a2 * rs[3]
                rs[1] = rs[0]; rs[0] = right; rs[3] = rs[2]; rs[2] = ro
                right = ro
            }
            output[i * 2] = left.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            output[i * 2 + 1] = right.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }
}
