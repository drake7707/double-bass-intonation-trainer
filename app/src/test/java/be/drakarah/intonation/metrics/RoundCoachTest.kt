package be.drakarah.intonation.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoundCoachTest {

    private fun input(
        cents: List<Float>,
        attempts: Int = 10,
        timeouts: Int = 0,
        wrong: Int = 0,
        lastWeek: Float? = null,
    ) = RoundCoachInput(cents, attempts, timeouts, wrong, MasteryThresholds.NOTE, lastWeek)

    @Test
    fun `empty round says nothing`() {
        assertNull(roundCoachVerdict(input(emptyList(), attempts = 0)))
    }

    @Test
    fun `all misses steadies instead of grading`() {
        assertEquals(
            RoundCoachVerdict.NOTHING_SCORED,
            roundCoachVerdict(input(emptyList(), attempts = 10, timeouts = 10)),
        )
    }

    @Test
    fun `too few scored notes stays silent`() {
        assertNull(roundCoachVerdict(input(listOf(3f, -4f), attempts = 10, timeouts = 8)))
    }

    @Test
    fun `sharp lean is named`() {
        assertEquals(
            RoundCoachVerdict.LEAN_SHARP,
            roundCoachVerdict(input(listOf(15f, 20f, 14f, 18f))),
        )
    }

    @Test
    fun `flat lean is named`() {
        assertEquals(
            RoundCoachVerdict.LEAN_FLAT,
            roundCoachVerdict(input(listOf(-15f, -20f, -14f, -18f))),
        )
    }

    @Test
    fun `bias uses the median so one outlier does not flip the tip`() {
        // Median of (2, 3, 4, 40) sits at 3.5 — centered, no lean tip.
        assertEquals(
            RoundCoachVerdict.SOLID,
            roundCoachVerdict(input(listOf(2f, 3f, 4f, 40f))),
        )
    }

    @Test
    fun `many timeouts get the breathing tip when intonation was fine`() {
        assertEquals(
            RoundCoachVerdict.TIME_PRESSURE,
            roundCoachVerdict(input(listOf(3f, -2f, 4f), attempts = 10, timeouts = 4)),
        )
    }

    @Test
    fun `locked-in round is celebrated by name`() {
        assertEquals(
            RoundCoachVerdict.LOCKED,
            roundCoachVerdict(input(listOf(3f, -4f, 2f, -5f, 4f))),
        )
    }

    @Test
    fun `improvement over last week is celebrated when not locked`() {
        assertEquals(
            RoundCoachVerdict.IMPROVED,
            roundCoachVerdict(input(listOf(12f, -14f, 13f, -11f), lastWeek = 25f)),
        )
    }

    @Test
    fun `developing round encourages without fake praise`() {
        assertEquals(
            RoundCoachVerdict.DEVELOPING,
            roundCoachVerdict(input(listOf(30f, -35f, 28f, -32f))),
        )
    }

    @Test
    fun `sustain verdicts scale with hold success`() {
        assertEquals(SustainCoachVerdict.ALL_HELD, sustainRoundCoachVerdict(5, 5))
        assertEquals(SustainCoachVerdict.MOST_HELD, sustainRoundCoachVerdict(3, 5))
        assertEquals(SustainCoachVerdict.FEW_HELD, sustainRoundCoachVerdict(1, 5))
        assertNull(sustainRoundCoachVerdict(0, 0))
    }
}
