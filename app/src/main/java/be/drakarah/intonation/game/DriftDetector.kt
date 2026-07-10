package be.drakarah.intonation.game

import kotlin.math.abs

/** Detects systematic pitch drift (user's idea): when everything the player lands is
 * consistently sharp or consistently flat, they've drifted off their inner reference and
 * are training the wrong pitches — better to stop and recalibrate the ear than continue.
 *
 * Rule: over the last [window] scored attempts (needs a full window), at least
 * [minSameSign] must share one sign and their median magnitude must reach [thresholdCents].
 */
class DriftDetector(
    private val window: Int = 6,
    private val minSameSign: Int = 5,
    private val thresholdCents: Float = 8f,
) {
    private val recent = ArrayDeque<Float>()

    /** Feed a scored attempt; returns the median signed drift when drifting, else null. */
    fun onAttempt(signedCents: Float?): Float? {
        if (signedCents == null) return null // timeouts/wrong notes don't say anything about drift
        recent.addLast(signedCents)
        while (recent.size > window) recent.removeFirst()
        if (recent.size < window) return null

        val sharp = recent.count { it > 0 }
        val flat = recent.count { it < 0 }
        if (maxOf(sharp, flat) < minSameSign) return null

        val median = recent.sorted().let {
            if (it.size % 2 == 1) it[it.size / 2] else (it[it.size / 2 - 1] + it[it.size / 2]) / 2f
        }
        return if (abs(median) >= thresholdCents) median else null
    }

    fun reset() = recent.clear()
}
