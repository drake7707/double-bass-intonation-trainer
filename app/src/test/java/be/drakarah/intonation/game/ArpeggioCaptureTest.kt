package be.drakarah.intonation.game

import be.drakarah.intonation.dsp.PitchSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ln

class ArpeggioCaptureTest {

    private val hop = 23L
    private val rootHz = 110.0     // A2
    private val thirdHz = 138.59   // C#3 (major third)
    private val fifthHz = 164.81   // E3

    private fun sample(tMs: Long, hz: Float, accepted: Boolean = true, level: Float = 70f) =
        PitchSample(
            timestampMs = tMs, framePosition = 0, frequencyHz = hz,
            smoothedHz = if (accepted) hz else 0f, accepted = accepted,
            noise = 0.02f, harmonicEnergyRelative = 0.7f, energyLevel = level,
        )

    private fun silence(fromMs: Long, toMs: Long) =
        generateSequence(fromMs) { it + hop }.takeWhile { it < toMs }
            .map { sample(it, 0f, accepted = false, level = 5f) }.toList()

    private fun note(fromMs: Long, toMs: Long, hz: Double) =
        generateSequence(fromMs) { it + hop }.takeWhile { it < toMs }
            .map { sample(it, hz.toFloat()) }.toList()

    // minReadMs = 0: the "too soon" root guard is inherited from Round and would otherwise
    // discard these synthetic captures that freeze in well under a second. It's exercised
    // separately below.
    private fun capture(minReadMs: Long = 0L) =
        ArpeggioCapture(listOf(rootHz, thirdHz, fifthHz), CaptureParams.arco(), minReadMs = minReadMs)

    private fun run(capture: ArpeggioCapture, script: List<PitchSample>): ArpeggioState {
        var s: ArpeggioState = capture.state
        for (x in script) s = capture.process(x)
        return s
    }

    private fun cents(hz: Float, refHz: Double) = 1200.0 * ln(hz / refHz) / ln(2.0)

    @Test
    fun cleanArpeggioFreezesThreeTonesInOrder() {
        val script = silence(0, 150) + note(150, 950, rootHz) +
                silence(950, 1150) + note(1150, 1950, thirdHz) +
                silence(1950, 2150) + note(2150, 2950, fifthHz)
        val state = run(capture(), script)
        assertTrue("expected Finished, got $state", state is ArpeggioState.Finished)
        val tones = (state as ArpeggioState.Finished).tones
        assertEquals(3, tones.size)
        assertTrue(tones.none { it.timedOut })
        assertTrue(abs(cents(tones[0].frequencyHz!!, rootHz)) < 5)
        assertTrue(abs(cents(tones[1].frequencyHz!!, thirdHz)) < 5)
        assertTrue(abs(cents(tones[2].frequencyHz!!, fifthHz)) < 5)
    }

    @Test
    fun octaveOffToneFoldsAndKeepsIntonationWhenIgnoringOctave() {
        // Play root + third clean, then the fifth an octave below (E2 = E3/2) but 6¢ sharp. With
        // ignoreWrongOctave on (default) the tone folds onto the target octave: not a wrong note /
        // wrong octave, cents keep the 6¢ error (NOT zeroed) — same as every other game.
        val fifthOctaveDownSharp = (fifthHz / 2) * Math.pow(2.0, 6.0 / 1200)
        val script = silence(0, 150) + note(150, 950, rootHz) +
                silence(950, 1150) + note(1150, 1950, thirdHz) +
                silence(1950, 2150) + note(2150, 2950, fifthOctaveDownSharp)
        val tones = (run(capture(), script) as ArpeggioState.Finished).tones
        assertFalse("octave-off-but-in-tune is not a wrong note", tones[2].wrongNote)
        assertFalse("...and not flagged wrong octave when folded", tones[2].wrongOctave)
        assertEquals("fold keeps the within-octave error (+6¢), not 0", 6f, tones[2].cents!!, 3f)
    }

    @Test
    fun octaveOffToneIsReportedAsWrongOctaveWhenNotIgnoring() {
        val cap = ArpeggioCapture(
            listOf(rootHz, thirdHz, fifthHz), CaptureParams.arco(),
            minReadMs = 0L, ignoreWrongOctave = false,
        )
        val script = silence(0, 150) + note(150, 950, rootHz) +
                silence(950, 1150) + note(1150, 1950, thirdHz) +
                silence(1950, 2150) + note(2150, 2950, fifthHz / 2) // fifth an octave down
        val tones = (run(cap, script) as ArpeggioState.Finished).tones
        assertTrue("an octave-off tone is its own dimension, not a flat wrong note", tones[2].wrongOctave)
    }

    @Test
    fun ringOverOfPreviousToneIsDiscarded() {
        // After the root, the root sounds again (its ring re-attacked) while the third tone is
        // armed. That freeze matches the previous tone, not the third — it must be discarded and
        // listening continue, so tone[1] ends up the actual third, not the ringing root.
        val script = silence(0, 150) + note(150, 950, rootHz) +
                silence(950, 1150) + note(1150, 1950, rootHz) +   // ring-over of the root
                silence(1950, 2150) + note(2150, 2950, thirdHz) + // the real third
                silence(2950, 3150) + note(3150, 3950, fifthHz)
        val state = run(capture(), script)
        val tones = (state as ArpeggioState.Finished).tones
        assertEquals(3, tones.size)
        assertTrue("tone 2 should be the third, not the ringing root",
            abs(cents(tones[1].frequencyHz!!, thirdHz)) < 5)
    }

    @Test
    fun wrongRootReArmsAndAsksAgain() {
        val wrongRootHz = 146.83 // D3 — a clearly different note, loud (not an artifact)
        val cap = capture()
        run(cap, silence(0, 150) + note(150, 950, wrongRootHz))
        val s = cap.state
        assertTrue("expected Capturing(0, wrongRoot), got $s",
            s is ArpeggioState.Capturing && s.toneIndex == 0 && s.wrongRoot)

        // now play the real arpeggio: it proceeds and finishes on the right tones
        val state = run(cap, silence(950, 1150) + note(1150, 1950, rootHz) +
                silence(1950, 2150) + note(2150, 2950, thirdHz) +
                silence(2950, 3150) + note(3150, 3950, fifthHz))
        val tones = (state as ArpeggioState.Finished).tones
        assertTrue(abs(cents(tones[0].frequencyHz!!, rootHz)) < 5)
        assertTrue(abs(cents(tones[2].frequencyHz!!, fifthHz)) < 5)
    }

    @Test
    fun wrongThirdIsScoredAndAdvances() {
        // A confident, non-artifact wrong third must not get stuck — it's captured (a scored
        // miss) and the arpeggio advances to the fifth.
        val wrongThirdHz = 196.0 // G3 — far from the third, loud
        val script = silence(0, 150) + note(150, 950, rootHz) +
                silence(950, 1150) + note(1150, 1950, wrongThirdHz) +
                silence(1950, 2150) + note(2150, 2950, fifthHz)
        val state = run(capture(), script)
        val tones = (state as ArpeggioState.Finished).tones
        assertEquals(3, tones.size)
        assertTrue(tones.none { it.timedOut })
        assertTrue("tone 2 is the wrong note played", abs(cents(tones[1].frequencyHz!!, thirdHz)) > 450)
        assertTrue(abs(cents(tones[2].frequencyHz!!, fifthHz)) < 5)
    }

    @Test
    fun neverPlayingTimesOut() {
        val state = run(capture(), silence(0, 9000))
        val tones = (state as ArpeggioState.Finished).tones
        assertEquals(3, tones.size)
        assertTrue(tones.all { it.timedOut })
    }

    @Test
    fun tooSoonRootCaptureIsDiscarded() {
        // With a real read floor, a root that freezes almost instantly is leftover sound, not
        // her attempt: it's discarded and the machine keeps waiting on the root.
        val cap = capture(minReadMs = 2000L)
        run(cap, silence(0, 150) + note(150, 900, rootHz))
        val s = cap.state
        assertTrue("still waiting on the root, got $s",
            s is ArpeggioState.Capturing && s.toneIndex == 0)
    }
}
