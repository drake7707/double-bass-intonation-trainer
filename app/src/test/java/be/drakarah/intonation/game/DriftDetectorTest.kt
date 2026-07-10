package be.drakarah.intonation.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriftDetectorTest {

    @Test
    fun consistentSharpnessTriggersAfterFullWindow() {
        val d = DriftDetector()
        val inputs = listOf(10f, 12f, 9f, 15f, 11f)
        inputs.forEach { assertNull(d.onAttempt(it)) } // window not full yet
        val drift = d.onAttempt(13f)
        assertNotNull(drift)
        assertTrue("expected sharp drift, got $drift", drift!! > 8f)
    }

    @Test
    fun mixedSignsDoNotTrigger() {
        val d = DriftDetector()
        listOf(10f, -12f, 9f, -15f, 11f, -13f, 10f, -9f).forEach {
            assertNull(d.onAttempt(it))
        }
    }

    @Test
    fun consistentButSmallOffsetsDoNotTrigger() {
        val d = DriftDetector()
        listOf(3f, 4f, 2f, 5f, 3f, 4f).forEach { assertNull(d.onAttempt(it)) }
    }

    @Test
    fun oneOutlierDoesNotBreakTheSignal() {
        val d = DriftDetector()
        listOf(-10f, -12f, 4f, -15f, -11f).forEach { d.onAttempt(it) }
        val drift = d.onAttempt(-13f)
        assertNotNull(drift)
        assertTrue("expected flat drift, got $drift", drift!! < -8f)
    }

    @Test
    fun timeoutsAreIgnored() {
        val d = DriftDetector()
        listOf(10f, 11f, 12f, 9f, 13f).forEach { d.onAttempt(it) }
        assertNull(d.onAttempt(null))          // a timeout says nothing
        assertNotNull(d.onAttempt(10f))        // the next scored attempt completes the window
    }

    @Test
    fun resetClearsHistory() {
        val d = DriftDetector()
        listOf(10f, 11f, 12f, 9f, 13f).forEach { d.onAttempt(it) }
        d.reset()
        assertNull(d.onAttempt(10f))
    }
}
