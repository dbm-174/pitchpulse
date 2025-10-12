package de.dbmlab.pitchpulse.core.pitch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.min

data class PitchResult(
    val hz: Float,
    val confidence: Float,   // 0..1 (1 = sehr sicher)
    val voiced: Boolean
)

class YinDetector(
    private val sampleRate: Int,
    private val frameSize: Int = 2048,
    private val threshold: Float = 0.15f
) {
    private val diff = FloatArray(frameSize / 2)
    private val cmnd = FloatArray(frameSize / 2)

    fun detect(frame: FloatArray): PitchResult {
        // 1) Difference function d(tau)
        val n = min(frame.size, frameSize)
        val half = n / 2
        java.util.Arrays.fill(diff, 0f)
        for (tau in 1 until half) {
            var sum = 0f
            var i = 0
            while (i + tau < n) {
                val d = frame[i] - frame[i + tau]
                sum += d * d
                i++
            }
            diff[tau] = sum
        }

        // 2) CMND
        var runningSum = 0f
        cmnd[0] = 1f
        for (tau in 1 until half) {
            runningSum += diff[tau]
            cmnd[tau] = if (runningSum == 0f) 1f else diff[tau] * tau / runningSum
        }

        // 3) Threshold crossing
        var tau = -1
        for (t in 2 until half) {
            if (cmnd[t] < threshold) {
                while (t + 1 < half && cmnd[t + 1] < cmnd[t]) {
                    // lokales Minimum suchen
                    tau = t + 1
                    break
                }
                tau = if (tau == -1) t else tau
                break
            }
        }
        if (tau == -1) {
            // kein valider Peak – unvoiced
            return PitchResult(0f, 0f, false)
        }

        // 4) Parabolic interpolation around tau
        val x0 = if (tau <= 1) tau else tau - 1
        val x2 = if (tau + 1 < half) tau + 1 else tau
        val s0 = cmnd[x0]
        val s1 = cmnd[tau]
        val s2 = cmnd[x2]
        val denom = (s0 + s2 - 2f * s1)
        val betterTau = if (denom != 0f) (tau + (s0 - s2) / (2f * denom)) else tau.toFloat()

        val freq = sampleRate / betterTau
        val conf = 1f - s1.coerceIn(0f, 1f)
        val voiced = conf >= 0.6f && freq in 60f..1500f
        return if (voiced) PitchResult(freq, conf, true) else PitchResult(0f, conf, false)
    }
}
