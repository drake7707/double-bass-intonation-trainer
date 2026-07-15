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

    // --- 2026-07-15 regression: confusing drift banners diagnosed from her real game traces.
    // She reported (and the .trace-incoming replays confirmed) the banner popping up flat right
    // after she scored a sharp note. Two causes, guarded below. See docs/DETECTION.md.

    /** She just landed +20c (sharp); the trailing window was flat. Warning "flat" against the
     * sharp note she's looking at is confusing — the banner must agree with the triggering note.
     * Real scored-cents sequence from pizz-verify/...-180739 up to that moment (the −136/−375
     * values are wrong-octave detector artifacts that WRONG_NOTE_CENTS=450 lets through). */
    @Test
    fun sharpNoteNeverTriggersAFlatBanner_realTrace180739() {
        val d = DriftDetector()
        val seq = listOf(-2.2f, 22.5f, 21.6f, -4.3f, -13.7f, -135.7f, -375.3f, -29.2f, -6.4f, -41.4f)
        seq.forEach { d.onAttempt(it) }
        assertNull("scoring +20c must not raise a flat drift banner", d.onAttempt(20.1f))
    }

    /** She just landed +6c — essentially in tune. Regardless of the trend, warning against a
     * near-in-tune note is confusing. Real sequence from pizz-verify/...-180245 up to that note. */
    @Test
    fun nearInTuneNoteDoesNotTrigger_realTrace180245() {
        val d = DriftDetector()
        val seq = listOf(13.0f, -11.4f, -2.5f, -31.7f, 31.3f, -12.4f, 5.9f, -12.1f, -13.6f, 8.9f,
            10.8f, 17.7f, -30.3f, -13.0f, -7.1f, -14.0f, -177.9f)
        seq.forEach { d.onAttempt(it) }
        assertNull("a +6c (in-tune) note must not raise a banner", d.onAttempt(6.4f))
    }

    /** Gross mis-detections (wrong octave / adjacent note) are not intonation: two −300c artifacts
     * amid otherwise in-tune playing must never be counted into a flat trend. */
    @Test
    fun grossMisdetectionsDoNotFabricateDrift() {
        val d = DriftDetector()
        // in-tune-ish playing peppered with two artifacts; nothing here is a systematic bias
        listOf(3f, -300f, 2f, -4f, 5f, -320f, -3f, 4f).forEach {
            assertNull("artifact-polluted window must not fire, note=$it", d.onAttempt(it))
        }
    }

    /** The genuine article must still fire: she was consistently ~+15–40c sharp and the note she
     * just played was also sharp. Real sequence from ...-201133. Regression against over-suppression. */
    @Test
    fun genuineSharpDriftStillFires_realTrace201133() {
        val d = DriftDetector()
        listOf(-27.1f, 44.3f, 4.5f, 19.4f, 43.4f, 14.2f).forEach { d.onAttempt(it) }
        val drift = d.onAttempt(15.0f)
        assertNotNull("consistent sharp playing must still warn", drift)
        assertTrue("expected sharp drift, got $drift", drift!! > 8f)
    }
}
