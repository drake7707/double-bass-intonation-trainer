package be.drakarah.intonation.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        assertNull(roundCoachLine(input(emptyList(), attempts = 0)))
    }

    @Test
    fun `all misses steadies instead of grading`() {
        val line = roundCoachLine(input(emptyList(), attempts = 10, timeouts = 10))!!
        assertTrue(line.contains("Slow down"))
    }

    @Test
    fun `too few scored notes stays silent`() {
        assertNull(roundCoachLine(input(listOf(3f, -4f), attempts = 10, timeouts = 8)))
    }

    @Test
    fun `sharp lean names the fix in pitch terms`() {
        val line = roundCoachLine(input(listOf(15f, 20f, 14f, 18f)))!!
        assertTrue(line.contains("leaned sharp"))
        assertTrue(line.contains("lower"))
    }

    @Test
    fun `flat lean aims higher`() {
        val line = roundCoachLine(input(listOf(-15f, -20f, -14f, -18f)))!!
        assertTrue(line.contains("leaned flat"))
        assertTrue(line.contains("higher"))
    }

    @Test
    fun `bias uses the median so one outlier does not flip the tip`() {
        // Median of (2, 3, 4, 40) sits at 3.5 — centered, no lean tip.
        val line = roundCoachLine(input(listOf(2f, 3f, 4f, 40f)))!!
        assertTrue(!line.contains("leaned"))
    }

    @Test
    fun `many timeouts get the breathing tip when intonation was fine`() {
        val line = roundCoachLine(input(listOf(3f, -2f, 4f), attempts = 10, timeouts = 4))!!
        assertTrue(line.contains("ran out of time"))
    }

    @Test
    fun `locked-in round is celebrated by name`() {
        val line = roundCoachLine(input(listOf(3f, -4f, 2f, -5f, 4f)))!!
        assertTrue(line.contains("Locked in"))
    }

    @Test
    fun `improvement over last week is celebrated when not locked`() {
        val line = roundCoachLine(input(listOf(12f, -14f, 13f, -11f), lastWeek = 25f))!!
        assertTrue(line.contains("last week"))
    }

    @Test
    fun `developing round encourages without fake praise`() {
        val line = roundCoachLine(input(listOf(30f, -35f, 28f, -32f)))!!
        assertEquals("Every round trains your ear a little — keep landing them.", line)
    }

    @Test
    fun `sustain lines scale with hold success`() {
        assertTrue(sustainRoundCoachLine(5, 5)!!.contains("Every hold"))
        assertTrue(sustainRoundCoachLine(3, 5)!!.contains("Good holding"))
        assertTrue(sustainRoundCoachLine(1, 5)!!.contains("slower"))
        assertNull(sustainRoundCoachLine(0, 0))
    }
}
