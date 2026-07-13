package be.drakarah.intonation.dsp

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

/** Guards the pizz octave-DOWN correction, driven by her 2026-07-13 Mi-string snippet where
 * sympathetic resonance pushes low Mi to read Mi2 (82 Hz, an octave up) — one run stuck for ~11 s.
 * Replays the recording under HER exact config (from the header, so it reproduces her rig) with
 * the ARCO octave knobs vs the looser PIZZ knobs, and asserts the pizz knobs collapse the octave
 * read. This is why pizz gets its own, looser odd-harmonic fit (see DETECTION.md §5). */
class PizzOctaveDownTest {

    private val snippet = "snippet-20260713-071450-mi-resonance-pizz"
    private fun midi(hz: Float) = (69 + 12 * ln(hz / 440.0) / ln(2.0)).roundToInt()

    private fun headerConfig(): PitchEngineConfig {
        val url = javaClass.classLoader!!.getResource("wav/$snippet.jsonl")!!
        val header = File(url.toURI()).bufferedReader().readLine()
        fun f(k: String) = Regex("\"$k\":([-+0-9.eE]+)").find(header)!!.groupValues[1].toFloat()
        fun i(k: String) = f(k).toInt()
        return PitchEngineConfig(
            sampleRate = i("sampleRate"), windowSize = i("windowSize"), overlap = f("overlap"),
            audioSource = i("audioSource"), maxNoise = f("maxNoise"),
            minHarmonicEnergyContent = f("minHarmonicEnergyContent"), sensitivity = f("sensitivity"),
            numMovingAverage = i("numMovingAverage"), maxNumFaultyValues = i("maxNumFaultyValues"),
            frequencyMin = f("frequencyMin"), frequencyMax = f("frequencyMax"),
            missingFundamentalMaxHz = f("missingFundamentalMaxHz"),
            oddHarmonicMinRatio = f("oddHarmonicMinRatio"), oddHarmonicMinRelative = f("oddHarmonicMinRelative"),
        )
    }

    /** Fraction of accepted windows reading Mi2 (82 Hz), i.e. an octave above the true Mi1. */
    private fun octaveHighRate(config: PitchEngineConfig): Double = runBlocking {
        val url = javaClass.classLoader!!.getResource("wav/$snippet.wav")!!
        val s = PitchEngine(config).wavSamples(WavFile.read(File(url.toURI())).samples).toList()
            .filter { it.accepted && it.smoothedHz > 0f }
        val mi1 = s.count { midi(it.smoothedHz) == 28 }
        val mi2 = s.count { midi(it.smoothedHz) == 40 }
        if (mi1 + mi2 == 0) 0.0 else 100.0 * mi2 / (mi1 + mi2)
    }

    @Test
    fun pizzKnobsCollapseTheResonanceOctaveTheArcoKnobsLeave() {
        val base = headerConfig()
        // arco (strict, as she calibrated): the octave read persists
        val arco = octaveHighRate(base)
        // pizz (looser fit): the octave-down correction recovers Mi1
        val pizz = octaveHighRate(base.copy(oddHarmonicMinRatio = 1.2f, oddHarmonicMinRelative = 0.01f))
        assertTrue("arco knobs should still show the octave read (was ~28%), got $arco%", arco > 10.0)
        assertTrue("pizz knobs should collapse it to near zero, got $pizz%", pizz < 2.0)
    }
}
