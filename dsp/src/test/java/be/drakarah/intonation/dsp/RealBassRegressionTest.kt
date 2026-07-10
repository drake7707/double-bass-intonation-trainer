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

    private fun check(resource: String, segments: List<Segment>) = runBlocking {
        val url = javaClass.classLoader!!.getResource("wav/$resource") ?: error("missing $resource")
        val wav = WavFile.read(File(url.toURI()))
        val samples = PitchEngine(PitchEngineConfig()).wavSamples(wav.samples).toList()

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

    @Test
    fun pizzOpenStrings() = check(
        "bass-pizz-open-strings.wav",
        listOf(
            Segment(0.2, 2.8, 55.0, "pizz open A", minFraction = 0.95),
            Segment(3.1, 7.9, 41.2, "pizz open E through full decay", minFraction = 0.95),
        )
    )
}
