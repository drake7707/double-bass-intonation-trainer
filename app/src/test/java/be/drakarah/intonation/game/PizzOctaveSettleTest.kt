package be.drakarah.intonation.game

import be.drakarah.intonation.calibration.CalibrationAnalysis
import be.drakarah.intonation.dsp.PitchSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

/** Guards the pizz octave-settle fix, driven by her 2026-07-12 Fa#1 pizz snippet.
 *
 * On the reference rig every pizz pluck's attack transient reads a full octave high (the detector
 * latching the 2nd harmonic) for the first ~100-530 ms, then settles onto the true fundamental
 * Fa#1. Without the guard, the capture machine freezes that steady octave window and scores a
 * confident "right note, wrong octave". With the octave-settle guard the fundamental is taken.
 *
 * The test feeds her ACTUAL recorded detection stream (the snippet's JSONL — the engine's output
 * on her calibrated rig) straight into the capture machine, rather than re-running the engine on
 * the WAV under a different config. Octave-up correction is config-dependent, so replaying the
 * WAV under stock settings would mask the artifact her calibrated rig actually produced; the
 * JSONL is the faithful ground truth. */
class PizzOctaveSettleTest {

    private val fasharp1Hz = 440.0 * Math.pow(2.0, (30 - 69) / 12.0) // midi 30 ≈ 46.25 Hz
    private val snippet = "snippet-20260712-131607-fasharp1-pizz.jsonl"

    /** Parses a recorded snippet/game-trace JSONL into the detection stream, skipping the header
     * (config) line. Only the fields the capture consumes need to be exact. */
    private fun recordedSamples(resource: String): List<PitchSample> {
        val url = javaClass.classLoader!!.getResource("wav/$resource") ?: error("missing $resource")
        fun Regex.num(line: String): Float =
            find(line)?.groupValues?.get(1)?.toFloat() ?: 0f
        val tMs = Regex(""""tMs":([-+0-9.eE]+)""")
        val frame = Regex(""""frame":([-+0-9.eE]+)""")
        val hz = Regex(""""hz":([-+0-9.eE]+)""")
        val smoothed = Regex(""""smoothedHz":([-+0-9.eE]+)""")
        val accepted = Regex(""""accepted":(true|false)""")
        val noise = Regex(""""noise":([-+0-9.eE]+)""")
        val harm = Regex(""""harmRel":([-+0-9.eE]+)""")
        val level = Regex(""""level":([-+0-9.eE]+)""")
        return File(url.toURI()).readLines()
            .filter { it.contains("\"tMs\"") }
            .map { line ->
                PitchSample(
                    timestampMs = tMs.num(line).toLong(),
                    framePosition = frame.num(line).toInt(),
                    frequencyHz = hz.num(line),
                    smoothedHz = smoothed.num(line),
                    accepted = accepted.find(line)?.groupValues?.get(1) == "true",
                    noise = noise.num(line),
                    harmonicEnergyRelative = harm.num(line),
                    energyLevel = level.num(line),
                )
            }
    }

    /** Re-arms exactly as a Note Accuracy round does (immediate, attack-required). */
    private fun freezes(samples: List<PitchSample>, params: CaptureParams): List<CapturedPitch> {
        fun fresh() = AttemptCapture(params, skipQuietGate = true, requireOnsetRise = true)
        val out = ArrayList<CapturedPitch>()
        var cap = fresh()
        for (s in samples) when (val st = cap.process(s)) {
            is CaptureState.Frozen -> { out.add(st.result); cap = fresh() }
            CaptureState.TimedOut -> cap = fresh()
            else -> {}
        }
        return out
    }

    private fun cents(hz: Double, ref: Double) = 1200.0 * ln(hz / ref) / ln(2.0)

    private fun octaveHighCount(freezes: List<CapturedPitch>): Int = freezes.count { f ->
        val c = cents(f.frequencyHz.toDouble(), fasharp1Hz)
        val octaves = (c / 1200.0).roundToInt()
        octaves >= 1 && abs(c - octaves * 1200.0) <= 70.0
    }

    @Test
    fun octaveSettleEliminatesTheWrongOctaveFreezes() {
        val f = freezes(recordedSamples(snippet),
            CaptureParams.pizz().copy(octaveSettleMs = 300L, octaveFoldMinHz = 40f))
        assertEquals("no pluck should freeze an octave high with the guard on", 0, octaveHighCount(f))
        assertTrue("the fundamental Fa#1 must still be captured",
            f.any { abs(cents(it.frequencyHz.toDouble(), fasharp1Hz)) <= 60.0 })
    }

    @Test
    fun withoutTheGuardTheArtifactIsPresent() {
        // The reported bug: same stream, guard off (octaveSettleMs = null) freezes the octave.
        val f = freezes(recordedSamples(snippet), CaptureParams.pizz().copy(octaveSettleMs = null))
        assertTrue("guard off should leave octave-high freezes (the reported bug)",
            octaveHighCount(f) > 0)
    }

    @Test
    fun pizzPhaseChoosesASettleWindowThatResolvesThisRig() {
        // The wizard's per-rig decision on this exact stream: window 0 (off) leaves the artifact,
        // so a non-zero window is chosen and it resolves — measured, not assumed.
        val profile = CalibrationAnalysis.choosePizzSettle(
            mapOf(fasharp1Hz.toFloat() to recordedSamples(snippet)), lowestPlayableHz = 40f)
        assertTrue("a non-zero settle window should be chosen (got ${profile.settleMs})", profile.settleMs > 0)
        assertTrue("the chosen window must resolve the artifact", profile.resolved)
        assertTrue("Fa#1 pizz must verify at the correct octave", profile.checks.all { it.second })
    }
}
