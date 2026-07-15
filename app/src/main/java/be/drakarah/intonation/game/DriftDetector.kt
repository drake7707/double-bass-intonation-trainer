package be.drakarah.intonation.game

import kotlin.math.abs

/** Detects systematic pitch drift (user's idea): when everything the player lands is
 * consistently sharp or consistently flat, they've drifted off their inner reference and
 * are training the wrong pitches — better to stop and recalibrate the ear than continue.
 *
 * Rule: over the last [window] scored attempts (needs a full window), at least
 * [minSameSign] must share one sign and their median magnitude must reach [thresholdCents].
 *
 * Two guards keep the warning meaningful, both learned from replaying her real game traces
 * (see docs/DETECTION.md and FeedbackRegressionTest):
 *  - **[trendBandCents]** — only notes recognizably *near* the target count as drift. A capture
 *    more than this far off is a wrong/missed note or a detector artifact (octave/adjacent-note
 *    mis-reads showed up as −375c, −201c "flat" attempts even though WRONG_NOTE_CENTS=450 let
 *    them through as "the right note"). Drift is a small *systematic* bias among notes she's
 *    actually hitting — gross deviations are excluded from the trend entirely, not just clamped.
 *  - **agreement + deadband** — the banner appears on the reveal of the note she just scored, so
 *    it must agree with that note. Firing "you're flat" the instant she nails a sharp or in-tune
 *    note (her report: scored +20c and got a flat warning) is confusing and arguably wrong — she
 *    may be correcting. The warning is held until the triggering note is itself off by at least
 *    [thresholdCents] *and* on the same side as the trend. The window still records the note; only
 *    the banner is deferred, so a genuine drift surfaces on her next same-side note.
 */
class DriftDetector(
    private val window: Int = 6,
    private val minSameSign: Int = 5,
    private val thresholdCents: Float = 8f,
    private val trendBandCents: Float = 60f,
    private val triggerDeadbandCents: Float = 10f,
) {
    private val recent = ArrayDeque<Float>()

    /** Feed a scored attempt; returns the median signed drift when drifting, else null. */
    fun onAttempt(signedCents: Float?): Float? {
        if (signedCents == null) return null // timeouts/wrong notes don't say anything about drift
        if (abs(signedCents) > trendBandCents) return null // gross miss/artifact: not intonation drift
        recent.addLast(signedCents)
        while (recent.size > window) recent.removeFirst()
        if (recent.size < window) return null

        val sharp = recent.count { it > 0 }
        val flat = recent.count { it < 0 }
        if (maxOf(sharp, flat) < minSameSign) return null

        val median = recent.sorted().let {
            if (it.size % 2 == 1) it[it.size / 2] else (it[it.size / 2 - 1] + it[it.size / 2]) / 2f
        }
        if (abs(median) < thresholdCents) return null
        // Don't warn against the note she just saw: it must be clearly off and on the trend's side.
        if (abs(signedCents) < triggerDeadbandCents) return null
        if ((signedCents > 0) != (median > 0)) return null
        return median
    }

    fun reset() = recent.clear()
}
