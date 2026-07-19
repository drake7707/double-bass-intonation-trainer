package be.drakarah.intonation.dsp

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.ln
import kotlin.math.roundToInt

/** Guards the 2026-07-19 fix: the odd-harmonic octave-DOWN proof is ARCO-ONLY (see DETECTION.md §12).
 *
 * Her feedback (embedded in the shift game trace): "mi/sol on the re string often said wrong note,
 * though it was right, mi2 especially". The clip is a 28 s slice of that pizz shift trace holding two
 * landings: a correctly fingered Mi2 (E2, 82 Hz, ~6 s in) and Sol2 (G2, 98 Hz, ~25.5 s in). On pizz,
 * plucking these sympathetically drives the OPEN E/A string, whose 3rd harmonic lands at 1.5x the
 * played note and satisfied PitchGate's odd-harmonic proof — falsely halving E2->E1 and G2->G1
 * (a "wrong note" that was right).
 *
 * The fix disables that stateless proof for pizz ([PitchEngineConfig.oddHarmonicOctaveDown]=false),
 * leaving the decay-continuation rule (which handles every genuine pizz octave-up in the corpus —
 * see [PizzOctaveDownTest] and the calibration takes). This asserts: with the proof ON (the old,
 * arco-style behaviour on pizz) both notes are halved an octave; with it OFF (shipped for pizz) both
 * read their true octave. */
class PizzOctaveDownFalsePositiveTest {

    private val clip = "shift-pizz-octavedown-20260719"
    private fun midi(hz: Float) = (69 + 12 * ln(hz / 440.0) / ln(2.0)).roundToInt()

    /** Her exact rig config from the trace header, so the test reproduces the real device. */
    private fun headerConfig(): PitchEngineConfig {
        val url = javaClass.classLoader!!.getResource("wav/$clip.jsonl")!!
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

    /** Median detected midi in a clip-relative time window. */
    private fun medianMidi(cfg: PitchEngineConfig, loMs: Long, hiMs: Long): Int = runBlocking {
        val url = javaClass.classLoader!!.getResource("wav/$clip.wav")!!
        val s = PitchEngine(cfg).wavSamples(WavFile.read(File(url.toURI())).samples).toList()
            .filter { it.accepted && it.smoothedHz > 0f && it.timestampMs in loMs..hiMs }
            .map { midi(it.smoothedHz) }.sorted()
        assertTrue("no accepted samples in $loMs..$hiMs ms", s.size >= 5)
        s[s.size / 2]
    }

    // Landing windows within the 28 s clip (trace 74/94 s minus the 68 s slice start), measured
    // from the replay: Mi2 lands ~6-7.5 s, Sol2 ~26-27 s.
    private val mi2Window = 6_000L to 7_500L    // played Mi2 = E2 = midi 40 (halves to E1=28)
    private val sol2Window = 26_000L to 27_000L // played Sol2 = G2 = midi 43 (halves to G1=31)

    @Test
    fun oddHarmonicProofOnPizzFalselyHalvesFingeredMiAndSol() {
        // Old behaviour (proof ON, as arco would run it) reproduces the bug.
        val on = headerConfig().copy(oddHarmonicOctaveDown = true)
        assertEquals("Mi2 should reproduce the octave-down bug with the proof on", 28, medianMidi(on, mi2Window.first, mi2Window.second))
        assertEquals("Sol2 should reproduce the octave-down bug with the proof on", 31, medianMidi(on, sol2Window.first, sol2Window.second))
    }

    @Test
    fun pizzReadsTheTrueOctaveWithTheProofOff() {
        // Shipped for pizz: proof off -> the notes keep their played octave.
        val off = headerConfig().copy(oddHarmonicOctaveDown = false)
        assertEquals("Mi2 (E2, 82 Hz) must read its true octave", 40, medianMidi(off, mi2Window.first, mi2Window.second))
        assertEquals("Sol2 (G2, 98 Hz) must read its true octave", 43, medianMidi(off, sol2Window.first, sol2Window.second))
    }
}
