package be.drakarah.intonation.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gauge domain (presentation redesign 2026-07-17). Covers the universal pitch-accuracy gauge (one
 * absolute scale, hit-rate capped), the game-specific gauges, and the per-exercise gauge sets —
 * plus a replay of the real Shifts round (session 41) that started the redesign.
 */
class RoundGaugeTest {

    private val ctx = RoundContext(a4Hz = 440f, micSensitivity = 55, difficulty = "STANDARD", roundLength = 10)

    private fun attempt(
        cents: Float? = 5f,
        timedOut: Boolean = false,
        wrongNote: Boolean = false,
        wrongOctave: Boolean = false,
        steadiness: Float? = null,
        heldMs: Long? = null,
        extrasJson: String? = null,
    ) = AttemptRecord(
        promptIndex = 0, targetMidi = 40, targetFreqHz = 82f, positionId = "1st",
        centsError = cents, score = 50, stars = 2,
        quality = if (timedOut) AttemptQuality.TIMEOUT else AttemptQuality.CLEAN,
        wrongNote = wrongNote, wrongOctave = wrongOctave, timedOut = timedOut,
        steadinessCents = steadiness, sustainHeldMs = heldMs, extrasJson = extrasJson,
    )

    private fun round(attempts: List<AttemptRecord>, exerciseType: String, mode: String = "arco") =
        RoundRecord(
            exerciseType = exerciseType, mode = mode, configKey = "cfg",
            startedAt = 1_700_000_000_000L, endedAt = 1_700_000_050_000L,
            totalScore = 500, maxScore = 1000, context = ctx, attempts = attempts,
        )

    private fun List<RoundGauge>.of(kind: GaugeKind) = firstOrNull { it.kind == kind }

    // --- pitch accuracy: one absolute scale, hit-rate capped ---

    @Test
    fun `pitch gauge grades mean absolute cents on the NOTE scale`() {
        assertEquals(GaugeLevel.GOOD, pitchAccuracyGauge(List(10) { 4f }).level)   // ≤10
        assertEquals(GaugeLevel.OK, pitchAccuracyGauge(List(10) { 18f }).level)    // ≤25
        assertEquals(GaugeLevel.DEVELOPING, pitchAccuracyGauge(List(10) { 40f }).level)
    }

    @Test
    fun `pitch gauge is hit-rate capped like the round band`() {
        // 5 landed perfectly, 5 gaps → 50% caps to DEVELOPING despite tight cents.
        val g = pitchAccuracyGauge(List(5) { 4f } + List(5) { null })
        assertEquals(GaugeLevel.DEVELOPING, g.level)
        assertEquals(5, g.points.count { it.zone == GaugeZone.MISS })
    }

    @Test
    fun `pitch gauge points colour by the band they land in, gaps are MISS`() {
        val g = pitchAccuracyGauge(listOf(3f, -18f, 40f, null))
        assertEquals(GaugeZone.GOOD, g.points[0].zone)        // ≤10
        assertEquals(GaugeZone.OK, g.points[1].zone)          // ≤25
        assertEquals(GaugeZone.DEVELOPING, g.points[2].zone)  // >25
        assertEquals(GaugeZone.MISS, g.points[3].zone)
        assertNull(g.points[3].value)
    }

    // --- pitch accuracy is game-independent (the core of the redesign) ---

    @Test
    fun `a shift round's pitch gauge uses the NOTE scale, not the lenient shift scale`() {
        // 30¢ landings would read SOLID on the old SHIFT band (≤45) but must read DEVELOPING here.
        val g = buildGauges(round(List(10) { attempt(cents = 30f) }, EXERCISE_TYPE_SHIFT))
        assertEquals(GaugeLevel.DEVELOPING, g.of(GaugeKind.PITCH_ACCURACY)!!.level)
    }

    // --- shift accuracy: its own wider scale, on the interval, only with extras ---

    @Test
    fun `shift accuracy grades the interval on its own scale, separate from pitch`() {
        // Landings 30¢ off (pitch = DEVELOPING) but the interval travelled was clean (4¢).
        val attempts = List(6) {
            attempt(cents = 30f, extrasJson = ShiftAttemptExtras(startCents = 26f, shiftCents = 4f).encode())
        }
        val g = buildGauges(round(attempts, EXERCISE_TYPE_SHIFT))
        assertEquals(GaugeLevel.DEVELOPING, g.of(GaugeKind.PITCH_ACCURACY)!!.level)
        assertEquals(GaugeLevel.GOOD, g.of(GaugeKind.SHIFT_ACCURACY)!!.level) // ≤20¢ interval
    }

    @Test
    fun `legacy shift rounds without extras show pitch only, no shift-accuracy gauge`() {
        val g = buildGauges(round(List(5) { attempt(cents = 8f) }, EXERCISE_TYPE_SHIFT))
        assertNotNull(g.of(GaugeKind.PITCH_ACCURACY))
        assertNull(g.of(GaugeKind.SHIFT_ACCURACY))
    }

    // --- sustain: pitch + steadiness (arco only) + hold ---

    @Test
    fun `sustain shows pitch, steadiness and hold on arco`() {
        val holds = List(4) { attempt(cents = 6f, steadiness = 5f, heldMs = 5000) }
        val g = buildGauges(round(holds, EXERCISE_TYPE_SUSTAIN, mode = "arco"))
        assertEquals(listOf(GaugeKind.PITCH_ACCURACY, GaugeKind.STEADINESS, GaugeKind.HOLD), g.map { it.kind })
        assertEquals(GaugeLevel.GOOD, g.of(GaugeKind.STEADINESS)!!.level) // ≤8¢ wobble
    }

    @Test
    fun `sustain omits steadiness on pizz (wobble is a decay artifact there)`() {
        val holds = List(4) { attempt(cents = 6f, steadiness = 5f, heldMs = 5000) }
        val g = buildGauges(round(holds, EXERCISE_TYPE_SUSTAIN, mode = "pizz"))
        assertNull(g.of(GaugeKind.STEADINESS))
        assertEquals(listOf(GaugeKind.PITCH_ACCURACY, GaugeKind.HOLD), g.map { it.kind })
    }

    @Test
    fun `hold gauge grades the share of holds that reached the goal`() {
        fun holds(success: Int, fail: Int) =
            List(success) { attempt(cents = 6f, heldMs = 5000) } +
                List(fail) { attempt(cents = null, timedOut = true, heldMs = 1500) }
        assertEquals(GaugeLevel.GOOD, holdGauge(
            (List(4) { HoldSample(5000, true) })).level)
        assertEquals(GaugeLevel.OK, buildGauges(round(holds(2, 2), EXERCISE_TYPE_SUSTAIN)).of(GaugeKind.HOLD)!!.level)
        assertEquals(GaugeLevel.DEVELOPING, buildGauges(round(holds(1, 4), EXERCISE_TYPE_SUSTAIN)).of(GaugeKind.HOLD)!!.level)
    }

    // --- chords: pitch accuracy over the fingered tones ---

    @Test
    fun `chords show a single pitch-accuracy gauge`() {
        val tones = List(9) { attempt(cents = 12f) }
        val g = buildGauges(round(tones, EXERCISE_TYPE_CHORDS))
        assertEquals(listOf(GaugeKind.PITCH_ACCURACY), g.map { it.kind })
    }

    // --- the round that started it all: session 41 (Shifts · pizz, no extras) ---

    @Test
    fun `session 41 replay - pitch accuracy is Solid, no shift gauge (legacy)`() {
        val landings = listOf(
            -16.6f, 23.2f, null, 17.2f, 28.2f, -6.8f, -22.4f, null, 15.6f, 10.2f,
            18.4f, 16.9f, 19.9f, 22.6f, -10.3f, 58.5f, -6.7f, -0.6f, -4.2f, -18.2f,
        )
        val attempts = landings.map { c ->
            if (c == null) attempt(cents = null, timedOut = true) else attempt(cents = c)
        }
        val gauges = buildGauges(round(attempts, EXERCISE_TYPE_SHIFT, mode = "pizz"))
        val pitch = gauges.of(GaugeKind.PITCH_ACCURACY)!!

        assertEquals(GaugeLevel.OK, pitch.level)              // "Solid": 17.6¢ avg, 90% capped
        assertEquals(17.58f, pitch.value!!, 0.1f)
        assertEquals(20, pitch.points.size)
        assertEquals(2, pitch.points.count { it.zone == GaugeZone.MISS })   // the two timeouts
        assertNull(gauges.of(GaugeKind.SHIFT_ACCURACY))       // no extras persisted on this round
        assertTrue(pitch.points[15].zone == GaugeZone.DEVELOPING) // the 58.5¢ landing
    }
}
