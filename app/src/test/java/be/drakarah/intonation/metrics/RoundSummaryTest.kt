package be.drakarah.intonation.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundSummaryTest {

    private val ctx = RoundContext(a4Hz = 440f, micSensitivity = 55, difficulty = "STANDARD", roundLength = 10)

    private fun attempt(
        cents: Float? = 5f,
        stars: Int = 2,
        wrongNote: Boolean = false,
        wrongOctave: Boolean = false,
        timedOut: Boolean = false,
        extrasJson: String? = null,
    ) = AttemptRecord(
        promptIndex = 0, targetMidi = 40, targetFreqHz = 82f, positionId = "1st",
        centsError = cents, score = 50, stars = stars,
        quality = if (timedOut) AttemptQuality.TIMEOUT else AttemptQuality.CLEAN,
        wrongNote = wrongNote, wrongOctave = wrongOctave, timedOut = timedOut,
        extrasJson = extrasJson,
    )

    private fun round(
        attempts: List<AttemptRecord>,
        exerciseType: String = EXERCISE_TYPE_NOTE_ACCURACY,
        mode: String = "arco",
    ) = RoundRecord(
        exerciseType = exerciseType, mode = mode, configKey = "cfg",
        startedAt = 1_700_000_000_000L, endedAt = 1_700_000_050_000L,
        totalScore = 500, maxScore = 1000, context = ctx, attempts = attempts,
    )

    // --- chart points ---

    @Test
    fun `chart points carry cents and classify misses and wrong octaves`() {
        val s = buildRoundSummary(round(listOf(
            attempt(cents = 8f, stars = 2),
            attempt(cents = null, stars = 0, timedOut = true),
            attempt(cents = 400f, stars = 0, wrongNote = true),
            attempt(cents = -12f, stars = 0, wrongOctave = true),
        )))
        assertEquals(4, s.chartPoints.size)
        assertTrue(s.chartPoints[0].isPitched)
        assertEquals(8f, s.chartPoints[0].signedCents)
        assertTrue(s.chartPoints[1].missed)
        assertTrue(s.chartPoints[2].missed)
        assertFalse(s.chartPoints[3].missed)
        assertTrue(s.chartPoints[3].wrongOctave)
        assertFalse(s.chartPoints[3].isPitched)
        assertEquals(1, s.scoredCount)
        assertEquals(4, s.attemptCount)
        assertEquals(25, s.hitRatePct)
        assertTrue(s.hasMisses)
    }

    @Test
    fun `stars sum over attempts against three per attempt`() {
        val s = buildRoundSummary(round(listOf(
            attempt(stars = 3), attempt(stars = 1), attempt(stars = 0, timedOut = true, cents = null),
        )))
        assertEquals(4, s.starsEarned)
        assertEquals(9, s.starsPossible)
    }

    // --- the hit-rate-capped band (her "Solid felt wrong" fix) ---

    @Test
    fun `tight cents on a half-missed round cannot read solid`() {
        // 5 of 10 landed, all beautifully — but 50% hit rate caps the band at DEVELOPING.
        val landed = List(5) { attempt(cents = 4f, stars = 3) }
        val missed = List(5) { attempt(cents = null, stars = 0, timedOut = true) }
        val s = buildRoundSummary(round(landed + missed))
        assertEquals(MasteryBand.DEVELOPING, s.band)
    }

    @Test
    fun `locked cents with one miss in ten reads solid not locked`() {
        val landed = List(9) { attempt(cents = 4f, stars = 3) }
        val missed = List(1) { attempt(cents = null, stars = 0, timedOut = true) }
        val s = buildRoundSummary(round(landed + missed))
        assertEquals(MasteryBand.SOLID, s.band)
    }

    @Test
    fun `full round keeps the pure cents band`() {
        assertEquals(
            MasteryBand.LOCKED,
            buildRoundSummary(round(List(10) { attempt(cents = 4f, stars = 3) })).band,
        )
    }

    @Test
    fun `the cap never upgrades a genuinely developing round`() {
        assertEquals(
            MasteryBand.DEVELOPING,
            buildRoundSummary(round(List(10) { attempt(cents = 40f, stars = 0) })).band,
        )
    }

    @Test
    fun `nothing scored means no band`() {
        val s = buildRoundSummary(round(List(3) { attempt(cents = null, stars = 0, timedOut = true) }))
        assertNull(s.band)
        assertNull(s.avgAbsCents)
    }

    // --- verdict routing ---

    @Test
    fun `note accuracy verdict comes from scored cents`() {
        val s = buildRoundSummary(round(List(5) { attempt(cents = 3f, stars = 3) }))
        assertEquals(RoundCoachVerdict.LOCKED, s.verdict)
        assertNull(s.sustainVerdict)
    }

    @Test
    fun `sustain has hold verdict and no chart, band or trend`() {
        val holds = List(4) { attempt(cents = 6f, stars = 2) } +
            attempt(cents = null, stars = 0, timedOut = true)
        val s = buildRoundSummary(round(holds, exerciseType = EXERCISE_TYPE_SUSTAIN), previousBlockAvgCents = 20f)
        assertEquals(SustainCoachVerdict.MOST_HELD, s.sustainVerdict)
        assertNull(s.verdict)
        assertNull(s.band)
        assertTrue(s.chartPoints.isEmpty())
        assertNull(s.trend) // sustain's cents are hold medians, not the trend metric
    }

    @Test
    fun `shift verdict grades the interval from extras not the landing`() {
        // Landing (centsError) is way off, but the shift interval itself was clean: startCents
        // pushed it. The verdict grades the interval; the start flag names the real culprit.
        val attempts = List(5) {
            attempt(cents = 30f, stars = 1, extrasJson = ShiftAttemptExtras(startCents = 25f, shiftCents = 4f).encode())
        }
        val s = buildRoundSummary(round(attempts, exerciseType = EXERCISE_TYPE_SHIFT))
        assertEquals(RoundCoachVerdict.LOCKED, s.verdict) // SHIFT thresholds, interval ≤20¢
        assertEquals(true, s.shiftStartFlagged)
    }

    @Test
    fun `old shift rounds without extras stay silent instead of guessing`() {
        val s = buildRoundSummary(round(List(5) { attempt(cents = 10f, stars = 2) }, exerciseType = EXERCISE_TYPE_SHIFT))
        assertNull(s.verdict)
        assertNull(s.shiftStartFlagged)
        // But the chart and band still work from the persisted landing cents.
        assertEquals(5, s.chartPoints.size)
        assertEquals(MasteryBand.LOCKED, s.band) // SHIFT: locked ≤20¢
    }

    @Test
    fun `clean shift start is not flagged`() {
        val attempts = List(5) {
            attempt(cents = 5f, stars = 3, extrasJson = ShiftAttemptExtras(startCents = 2f, shiftCents = 4f).encode())
        }
        assertEquals(false, buildRoundSummary(round(attempts, exerciseType = EXERCISE_TYPE_SHIFT)).shiftStartFlagged)
    }

    // --- trend ---

    @Test
    fun `trend silent without previous block data`() {
        assertNull(buildRoundSummary(round(List(5) { attempt() }), previousBlockAvgCents = null).trend)
    }

    @Test
    fun `trend classifies improvement within the steady band`() {
        val s = buildRoundSummary(round(List(5) { attempt(cents = 10f) }), previousBlockAvgCents = 20f)
        val trend = s.trend!!
        assertEquals(10f, trend.thisRoundAvgAbsCents, 1e-4f)
        assertTrue(trend.improved)
        assertFalse(trend.worse)

        val steady = RoundTrend(thisRoundAvgAbsCents = 19f, previousBlockAvgAbsCents = 20f)
        assertFalse(steady.improved)
        assertFalse(steady.worse)

        val worse = RoundTrend(thisRoundAvgAbsCents = 25f, previousBlockAvgAbsCents = 20f)
        assertTrue(worse.worse)
    }

    @Test
    fun `withTrend fills in after the store query returns`() {
        val s = buildRoundSummary(round(List(5) { attempt(cents = 10f) }))
        assertNull(s.trend)
        assertEquals(20f, s.withTrend(20f).trend?.previousBlockAvgAbsCents)
    }
}
