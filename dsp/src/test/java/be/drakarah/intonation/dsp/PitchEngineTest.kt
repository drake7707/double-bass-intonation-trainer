package be.drakarah.intonation.dsp

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sin

class PitchEngineTest {

    private val config = PitchEngineConfig()

    /** A bass-like tone: fundamental plus decaying harmonic stack, slight fade-in/out. */
    private fun synthesize(
        fundamentalHz: Double,
        seconds: Double = 2.0,
        numHarmonics: Int = 10,
        sampleRate: Int = config.sampleRate,
    ): ShortArray {
        val n = (seconds * sampleRate).toInt()
        val amplitudes = DoubleArray(numHarmonics) { h -> 1.0 / (h + 1) }
        val norm = 0.5 / amplitudes.sum()
        return ShortArray(n) { i ->
            val t = i.toDouble() / sampleRate
            var v = 0.0
            for (h in 0 until numHarmonics) {
                v += amplitudes[h] * sin(2.0 * PI * fundamentalHz * (h + 1) * t)
            }
            val envelope = minOf(1.0, i / (0.05 * sampleRate), (n - i) / (0.05 * sampleRate))
            (Short.MAX_VALUE * norm * envelope * v).toInt().toShort()
        }
    }

    private fun centsOff(measured: Double, expected: Double): Double =
        1200.0 * ln(measured / expected) / ln(2.0)

    private fun assertDetects(fundamentalHz: Double) = runBlocking {
        val samples = PitchEngine(config).wavSamples(synthesize(fundamentalHz)).toList()
        val locked = samples.filter { it.accepted && it.smoothedHz > 0f }
        assertTrue(
            "expected accepted samples for $fundamentalHz Hz, got ${samples.size} total, 0 locked",
            locked.isNotEmpty()
        )
        // majority of the locked samples must sit within 2 cents — and none an octave off
        val errors = locked.map { centsOff(it.smoothedHz.toDouble(), fundamentalHz) }
        val within2c = errors.count { abs(it) <= 2.0 }
        assertTrue(
            "only $within2c/${errors.size} within 2 cents of $fundamentalHz Hz",
            within2c >= errors.size / 2
        )
        val octaveErrors = errors.count { abs(it) > 300.0 }
        assertEquals("octave/harmonic errors at $fundamentalHz Hz", 0, octaveErrors)
    }

    @Test fun detectsOpenE1() = assertDetects(41.203)
    @Test fun detectsOpenA1() = assertDetects(55.0)
    @Test fun detectsOpenD2() = assertDetects(73.416)
    @Test fun detectsOpenG2() = assertDetects(97.999)
    @Test fun detectsHighD4() = assertDetects(293.665)

    @Test
    fun detectsSharpE1AsSharp() = runBlocking {
        // 20 cents sharp of E1 — the detector must report the played pitch, not snap to E1
        val played = 41.203 * Math.pow(2.0, 20.0 / 1200.0)
        val samples = PitchEngine(config).wavSamples(synthesize(played)).toList()
        val locked = samples.filter { it.accepted && it.smoothedHz > 0f }
        assertTrue(locked.isNotEmpty())
        val medianHz = locked.map { it.smoothedHz }.sorted()[locked.size / 2].toDouble()
        assertTrue(
            "median ${centsOff(medianHz, played)} cents off played pitch",
            abs(centsOff(medianHz, played)) <= 2.0
        )
    }

    @Test
    fun silenceProducesNoAcceptedSamples() = runBlocking {
        val silence = ShortArray(config.sampleRate) // 1 s of zeros
        val samples = PitchEngine(config).wavSamples(silence).toList()
        assertTrue(samples.none { it.accepted })
        assertTrue(samples.none { it.smoothedHz > 0f })
    }

    @Test
    fun shortInputYieldsEmptyFlow() = runBlocking {
        val tooShort = ShortArray(config.windowSize - 1)
        assertEquals(0, PitchEngine(config).wavSamples(tooShort).toList().size)
    }
}
