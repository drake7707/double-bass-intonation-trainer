package be.drakarah.intonation.dsp

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.ln

/** Regression corpus: real double-bass recordings from the target device (Pixel 6a, stock
 * MIC source), captured via the debug screen's snippet button. Each segment asserts the
 * dominant detected note — these recordings are what exposed the octave-up errors that
 * PitchGate's correction now fixes, so they must never regress. */
class RealBassRegressionTest {

    private data class Segment(
        val fromS: Double,
        val toS: Double,
        val expectedHz: Double,
        val label: String,
        /** Fraction of accepted samples that must sit within +-60 cents of expected. */
        val minFraction: Double = 0.90,
    )

    private fun centsOff(hz: Float, expected: Double) =
        1200.0 * ln(hz / expected) / ln(2.0)

    /** [oddHarmonicOctaveDown] mirrors how the mode actually ships: arco keeps the odd-harmonic
     * octave-up proof (true), pizz relies on decay-continuation instead (false — DETECTION.md §12).
     * Pizz open strings must survive with it OFF, or the arco-only decision regressed pizz. */
    private fun check(
        resource: String,
        segments: List<Segment>,
        oddHarmonicOctaveDown: Boolean = true,
    ) = runBlocking {
        val url = javaClass.classLoader!!.getResource("wav/$resource") ?: error("missing $resource")
        val wav = WavFile.read(File(url.toURI()))
        val samples = PitchEngine(PitchEngineConfig(oddHarmonicOctaveDown = oddHarmonicOctaveDown))
            .wavSamples(wav.samples).toList()

        for (seg in segments) {
            val inSegment = samples.filter {
                it.timestampMs in (seg.fromS * 1000).toLong()..(seg.toS * 1000).toLong() &&
                        it.accepted && it.smoothedHz > 0f
            }
            assertTrue("${seg.label}: no accepted samples", inSegment.size >= 10)
            val correct = inSegment.count { abs(centsOff(it.smoothedHz, seg.expectedHz)) <= 60.0 }
            val octaveUp = inSegment.count { abs(centsOff(it.smoothedHz, 2 * seg.expectedHz)) <= 60.0 }
            val fraction = correct.toDouble() / inSegment.size
            assertTrue(
                "${seg.label}: only ${"%.0f".format(fraction * 100)}% on ${seg.expectedHz} Hz " +
                        "($correct/${inSegment.size}, $octaveUp octave-up)",
                fraction >= seg.minFraction
            )
        }
    }

    @Test
    fun arcoOpenStrings() = check(
        "bass-arco-open-strings.wav",
        listOf(
            Segment(0.2, 2.2, 55.0, "arco open A (mic loses the 55 Hz fundamental)"),
            Segment(2.5, 7.9, 41.2, "arco open E", minFraction = 0.97),
        )
    )

    // Pizz runs with the odd-harmonic octave-up proof OFF (§12): the decay-continuation rule alone
    // must keep the open strings on their true octave. Guards that the arco-only decision didn't
    // regress pizz — on this corpus both strings stay 100% correct without the proof.
    @Test
    fun pizzOpenStrings() = check(
        "bass-pizz-open-strings.wav",
        listOf(
            Segment(0.2, 2.8, 55.0, "pizz open A", minFraction = 0.95),
            Segment(3.1, 7.9, 41.2, "pizz open E through full decay", minFraction = 0.95),
        ),
        oddHarmonicOctaveDown = false,
    )

    // 2026-07-11 note-sweep snippets (user report): genuinely played notes from Do3 up were
    // halved by the octave correction — sympathetic open-string harmonics sit exactly at
    // 1.5x these notes and satisfied the old odd-harmonic proof. The notes must keep their
    // played octave.

    @Test
    fun highC3KeepsItsOctave() = check(
        "snippet-20260711-131856.wav",
        listOf(Segment(0.6, 5.4, 130.81, "sustained Do3 (was read Do2)")),
    )

    // Segment starts after the attack (raw detector picks the Ré2 subharmonic there) and
    // ends at the bow lift (the open Ré string genuinely rings an octave below after it).
    @Test
    fun highD3KeepsItsOctave() = check(
        "snippet-20260711-131358.wav",
        listOf(Segment(3.7, 6.0, 146.83, "sustained Ré3 (was read Ré2)")),
    )

    @Test
    fun highDs3KeepsItsOctave() = check(
        "snippet-20260711-131522.wav",
        listOf(
            Segment(1.0, 3.7, 155.56, "sustained Ré#3 (was read Ré#2)"),
            Segment(4.9, 7.9, 155.56, "second Ré#3 bow"),
        ),
    )

    @Test
    fun highE3KeepsItsOctave() = check(
        "snippet-20260711-132008.wav",
        listOf(Segment(0.2, 5.3, 164.81, "sustained Mi3")),
    )

    @Test
    fun highF3KeepsItsOctave() = check(
        "snippet-20260711-132031.wav",
        listOf(Segment(0.3, 3.2, 174.61, "sustained Fa3 (second bow was read Fa2)")),
    )
}
