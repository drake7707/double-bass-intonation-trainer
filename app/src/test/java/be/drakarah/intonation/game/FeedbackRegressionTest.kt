package be.drakarah.intonation.game

import be.drakarah.intonation.dsp.PitchEngine
import be.drakarah.intonation.dsp.PitchEngineConfig
import be.drakarah.intonation.dsp.PitchSample
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

/** Guards the fixes for the 2026-07-11 afternoon feedback, driven by her real snippets:
 *  - Fa2/Fa#2 arco "no note": mid-round prompts now arm without waiting for silence, so
 *    continuous bowing is captured (it was stuck in AwaitQuiet).
 *  - Sol#1 pizz false "wrong note": every confidently-played wrong capture on the Sol#1
 *    snippets is one the game filters out (faint, shaky, or an integer harmonic of the
 *    target), so it never reaches the player as "wrong note?".
 * These are provisional thresholds mirroring NoteAccuracyViewModel; retune from a full-game trace. */
class FeedbackRegressionTest {

    private val wrongNoteMinLevel = 55f
    private val nonOctaveHarmonics = intArrayOf(3, 5, 6, 7, 9, 10)

    private fun readFloatWav(resource: String): FloatArray {
        val url = javaClass.classLoader!!.getResource("wav/$resource") ?: error("missing $resource")
        val bytes = File(url.toURI()).readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val chunkId = String(bytes, pos, 4)
            val chunkSize = buf.getInt(pos + 4)
            if (chunkId == "data") return FloatArray(chunkSize / 4) { buf.getFloat(pos + 8 + it * 4) }
            pos += 8 + chunkSize + (chunkSize and 1)
        }
        error("no data chunk in $resource")
    }

    private fun samples(resource: String): List<PitchSample> = runBlocking {
        val out = ArrayList<PitchSample>()
        PitchEngine(PitchEngineConfig()).wavSamples(readFloatWav(resource)).collect { out.add(it) }
        out
    }

    /** Freezes produced by re-arming exactly the way a mid-round prompt now does. */
    private fun freezes(samples: List<PitchSample>, params: CaptureParams): List<CapturedPitch> {
        val out = ArrayList<CapturedPitch>()
        var capture = AttemptCapture(params, skipQuietGate = true)
        for (s in samples) {
            when (val st = capture.process(s)) {
                is CaptureState.Frozen -> { out.add(st.result); capture = AttemptCapture(params, skipQuietGate = true) }
                CaptureState.TimedOut -> capture = AttemptCapture(params, skipQuietGate = true)
                else -> {}
            }
        }
        return out
    }

    private fun midiHz(midi: Int) = 440.0 * 2.0.pow((midi - 69) / 12.0)

    private fun cents(hz: Double, ref: Double) = 1200.0 * ln(hz / ref) / ln(2.0)

    private fun isIntegerHarmonic(playedHz: Double, targetHz: Double): Boolean {
        val ratioCents = 1200.0 * ln(maxOf(playedHz, targetHz) / minOf(playedHz, targetHz)) / ln(2.0)
        return nonOctaveHarmonics.any { abs(ratioCents - 1200.0 * ln(it.toDouble()) / ln(2.0)) < 50.0 }
    }

    @Test
    fun fa2ArcoIsCapturedWhenArmedLikeAMidRoundPrompt() {
        // She bowed continuously; the engine detected F2/F#2 solidly. The capture used to sit
        // in AwaitQuiet forever (no silence between prompts). Armed the new way, it captures.
        val f = freezes(samples("snippet-20260711-163722.wav"), CaptureParams.arco())
        assertTrue("continuous arco must now produce captures, got none", f.isNotEmpty())
        // and what it captures is a real, strong note (not a stray transient)
        assertTrue(f.all { it.energyLevel > wrongNoteMinLevel })
    }

    @Test
    fun solSharp1PizzNeverSurfacesAConfidentWrongNote() {
        val target = midiHz(32) // G#1 / Sol#1
        for (res in listOf("snippet-20260711-170932.wav", "snippet-20260711-170944.wav")) {
            for (frozen in freezes(samples(res), CaptureParams.pizz())) {
                val off = abs(cents(frozen.frequencyHz.toDouble(), target))
                if (off <= WRONG_NOTE_CENTS) continue // near the target — fine
                // A wrong capture must be one the game discards: faint, shaky, or a harmonic.
                val filtered = frozen.quality == CaptureQuality.SHAKY ||
                        frozen.energyLevel < wrongNoteMinLevel ||
                        frozen.frequencyHz < 40f || // below open E1 — unplayable artifact
                        isIntegerHarmonic(frozen.frequencyHz.toDouble(), target)
                assertTrue(
                    "$res: unfiltered wrong note ${frozen.frequencyHz}Hz " +
                        "(${off.toInt()}c off, ${frozen.quality}, level ${frozen.energyLevel})",
                    filtered,
                )
            }
        }
    }
}
