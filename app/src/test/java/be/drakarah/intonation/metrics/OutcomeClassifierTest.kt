package be.drakarah.intonation.metrics

import org.junit.Assert.assertEquals
import org.junit.Test

class OutcomeClassifierTest {

    @Test fun cleanNearTargetIsScored() =
        assertEquals(AttemptOutcome.SCORED, classifyOutcome(AttemptQuality.CLEAN, false, false, false))

    @Test fun shakyButRightNoteStillScored() =
        assertEquals(AttemptOutcome.SCORED, classifyOutcome(AttemptQuality.SHAKY, false, false, false))

    @Test fun wrongNoteIsWrongNote() =
        assertEquals(AttemptOutcome.WRONG_NOTE, classifyOutcome(AttemptQuality.CLEAN, true, false, false))

    @Test fun octaveErrorReportedAsOctaveNotWrongNote() =
        // wrongNote is also set on an octave miss; octave classification must win.
        assertEquals(AttemptOutcome.WRONG_OCTAVE, classifyOutcome(AttemptQuality.CLEAN, true, true, false))

    @Test fun timeoutBeatsEverything() =
        assertEquals(AttemptOutcome.TIMEOUT, classifyOutcome(AttemptQuality.CLEAN, true, true, true))

    @Test fun timeoutQualityIsTimeout() =
        assertEquals(AttemptOutcome.TIMEOUT, classifyOutcome(AttemptQuality.TIMEOUT, false, false, false))

    @Test fun scoredRecordNeedsCents() {
        val withCents = AttemptRecord(0, 40, 82f, score = 90, stars = 3, quality = AttemptQuality.CLEAN, centsError = 5f)
        val noCents = withCents.copy(centsError = null)
        assertEquals(true, withCents.isScored)
        assertEquals(false, noCents.isScored)
    }
}
