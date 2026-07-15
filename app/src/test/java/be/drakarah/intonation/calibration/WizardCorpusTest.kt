package be.drakarah.intonation.calibration

import be.drakarah.intonation.dsp.PitchEngine
import be.drakarah.intonation.dsp.PitchEngineConfig
import be.drakarah.intonation.dsp.PitchSample
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Grounds the wizard's offline decision steps in the real recorded corpus: the same
 * replay-and-score moves the wizard performs on-device must, on the reference phone's
 * recordings, reproduce the hand-tuned defaults. */
class WizardCorpusTest {

    private fun readFloatWav(resource: String): FloatArray {
        val url = javaClass.classLoader!!.getResource("wav/$resource") ?: error("missing $resource")
        val bytes = File(url.toURI()).readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val chunkId = String(bytes, pos, 4)
            val chunkSize = buf.getInt(pos + 4)
            if (chunkId == "data") {
                return FloatArray(chunkSize / 4) { buf.getFloat(pos + 8 + it * 4) }
            }
            pos += 8 + chunkSize + (chunkSize and 1)
        }
        error("no data chunk in $resource")
    }

    private fun replay(pcm: FloatArray, config: PitchEngineConfig): List<PitchSample> =
        runBlocking { PitchEngine(config).wavSamples(pcm).toList() }

    private fun List<PitchSample>.segment(fromS: Double, toS: Double) =
        filter { it.timestampMs in (fromS * 1000).toLong()..(toS * 1000).toLong() }

    @Test
    fun rolloffProbeReproducesTheHandTunedKnee() {
        val pcm = readFloatWav("bass-arco-open-strings.wav")
        // the wizard's probe: octave correction disabled, everything else stock
        val probed = replay(pcm, PitchEngineConfig(missingFundamentalMaxHz = 0f))
        val aRate = CalibrationAnalysis.score(probed.segment(0.2, 2.2), 55f).octaveUpRate
        val eRate = CalibrationAnalysis.score(probed.segment(2.5, 7.9), 41.2f).octaveUpRate

        // on the reference phone the A string's fundamental is missing, the E string's is not
        assertTrue("A-string probe should show octave-up ($aRate)", aRate >= 0.3f)
        assertTrue("E-string probe should be clean ($eRate)", eRate < 0.3f)

        val knee = CalibrationAnalysis.rolloffKneeHz(mapOf(41.2f to eRate, 55f to aRate))
        assertEquals("knee should land near the hand-tuned 63 Hz", 63f, knee, 3f)
    }

    @Test
    fun wrongNoteFloorFromCorpusSitsAboveTheGateInASanePlayingBand() {
        // measured the way the wizard does: gate wide open, energy of quiet vs playing
        val noise = replay(readFloatWav("bass-noise-floor-desk.wav"), PitchEngineConfig(sensitivity = 100f))
            .map { it.energyLevel }
        val playing = replay(readFloatWav("bass-arco-open-strings.wav"), PitchEngineConfig(sensitivity = 100f))
            .map { it.energyLevel }
        val noiseCeil = CalibrationAnalysis.percentile(noise, 95)
        val playingFloor = CalibrationAnalysis.percentile(playing, 70)

        val gate = CalibrationAnalysis.gateFor(noiseCeil, playingFloor).second!!
        val floor = CalibrationAnalysis.wrongNoteFloor(noiseCeil, playingFloor)
        assertTrue("wrong-note floor ($floor) must be stricter than the gate ($gate)", floor > gate)
        assertTrue("wrong-note floor ($floor) should land in a sane playing band", floor in 45f..70f)
    }

    @Test
    fun lowestPlayableHzIsJustBelowTheOpenLowString() {
        // open E1 at A4=440 is 41.2 Hz; the floor should sit ~a semitone under it
        assertEquals(38.9f, CalibrationAnalysis.lowestPlayableHz(41.2f), 0.5f)
        // and track a different tuning rather than assume 440
        assertTrue(CalibrationAnalysis.lowestPlayableHz(41.4f) > CalibrationAnalysis.lowestPlayableHz(41.2f))
    }

    @Test
    fun usableTakeAcceptsARealOpenStringAndRejectsTheWrongNote() {
        val eString = replay(readFloatWav("bass-arco-open-strings.wav"), PitchEngineConfig())
            .segment(2.5, 7.9)
        // scored against the note actually played (E ~41.2) -> usable
        assertTrue(CalibrationAnalysis.isUsableTake(CalibrationAnalysis.score(eString, 41.2f)))
        // scored as if we'd asked for a note she did NOT play (C#2 ~69) -> rejected
        assertTrue(!CalibrationAnalysis.isUsableTake(CalibrationAnalysis.score(eString, 69.3f)))
    }

    @Test
    fun defaultThresholdCandidateWinsOnTheReferencePhone() {
        // the wizard tries the corpus-measured defaults first: they must make the high
        // note read at its true octave AND keep the A string corrected — i.e. on the
        // reference phone the wizard keeps the defaults rather than adjusting.
        val defaults = PitchEngineConfig()

        val doScore = CalibrationAnalysis.score(
            replay(readFloatWav("snippet-20260711-131856.wav"), defaults).segment(0.6, 5.4),
            130.81f,
        )
        assertTrue("Do3 must stay Do3 (rate ${doScore.correctRate})", doScore.correctRate >= 0.85f)

        val aScore = CalibrationAnalysis.score(
            replay(readFloatWav("bass-arco-open-strings.wav"), defaults).segment(0.2, 2.2),
            55f,
        )
        assertTrue("open A must stay corrected (rate ${aScore.correctRate})", aScore.correctRate >= 0.7f)
    }

    @Test
    fun pizzOctaveFitPicksLoosestSafeThatClearsTheOctave() {
        // one TakeScore per pizz take, per candidate (aligned with PIZZ_OCTAVE_CANDIDATES:
        // 2.0/.02, 1.8/.01, 1.5/.01, 1.2/.015, 1.2/.01). Mimics the reference rig: the octave-high
        // reads only clear at ratio 1.2, and only the very loosest starts halving a genuine note.
        fun ts(up: Float, down: Float) = TakeScore(200, 200, 1f - up - down, up, down, 100)
        val scores = listOf(
            listOf(ts(0.11f, 0f), ts(0f, 0f)),   // 2.0/.02  — octave not cleared
            listOf(ts(0.06f, 0f), ts(0f, 0f)),   // 1.8/.01  — better, still not clear
            listOf(ts(0.07f, 0f), ts(0f, 0f)),   // 1.5/.01
            listOf(ts(0.00f, 0.005f), ts(0f, 0f)), // 1.2/.015 — cleared, safe  <-- expected
            listOf(ts(0.00f, 0.27f), ts(0f, 0f)),  // 1.2/.01  — cleared but HALVES a real note
        )
        val fit = CalibrationAnalysis.choosePizzOctaveFit(scores)
        assertEquals(1.2f, fit.minRatio)
        assertEquals(0.015f, fit.minRelative) // the safe one, not the halving 0.01
    }

    @Test
    fun settledPitchTracksTheSustainNotTheAttack() {
        // synthetic pluck: 200 ms of a sharp attack transient (+30c) then a flat settled sustain
        // (+3c). settledPitchHz must report the sustain, not the attack it opens with.
        val ref = 82.41f // Mi2
        fun hzAt(cents: Float) = ref * Math.pow(2.0, (cents / 1200f).toDouble()).toFloat()
        val samples = ArrayList<PitchSample>()
        var t = 0L
        repeat(9) { // attack: +30c, 9 windows over ~200 ms
            samples.add(PitchSample(t, 0, hzAt(30f), hzAt(30f), true, 0f, 1f, 90f)); t += 23
        }
        repeat(40) { // settled sustain: +3c
            samples.add(PitchSample(t, 0, hzAt(3f), hzAt(3f), true, 0f, 1f, 80f)); t += 23
        }
        val settled = CalibrationAnalysis.settledPitchHz(samples, ref)!!
        val settledCents = 1200.0 * Math.log(settled / ref.toDouble()) / Math.log(2.0)
        assertEquals("settled should be the sustain (+3c), not the attack (+30c)", 3.0, settledCents, 4.0)
    }

    @Test
    fun choosePizzTimingDoesNotWorsenTheFreezeErrorOnTheReferenceRig() {
        // Replay the reference rig's plucked open-string calibration takes through the pizz config,
        // then let the chooser pick the capture timing. It must return a candidate whose worst
        // freeze-vs-settled error is no worse than the shipped 60/150 preset — i.e. the per-rig
        // measurement never regresses accuracy, and picks up any early-trigger sharpness it finds.
        val pizzCfg = PitchEngineConfig(oddHarmonicMinRatio = 1.2f, oddHarmonicMinRelative = 0.01f)
        val takes = mapOf(
            41.2f to "calibration-pizz-28-20260713-073213.wav",
            55.0f to "calibration-pizz-33-20260713-073213.wav",
            73.4f to "calibration-pizz-38-20260713-073213.wav",
            98.0f to "calibration-pizz-43-20260713-073213.wav",
        ).mapValues { (_, wav) -> replay(readFloatWav(wav), pizzCfg) }

        val profile = CalibrationAnalysis.choosePizzTiming(takes, settleMs = 300, lowestPlayableHz = 38.9f)
        // returns one of the defined candidates
        assertTrue(
            "chosen timing must be a defined candidate",
            CalibrationAnalysis.PIZZ_TIMING_CANDIDATES.any {
                it.attackSkipMs == profile.attackSkipMs && it.stabilityWindowMs == profile.stabilityWindowMs
            }
        )
        // never worse than the shipped preset
        assertTrue(
            "chosen skip (${profile.attackSkipMs}) should be >= the shipped 60 ms",
            profile.attackSkipMs >= 60L
        )
    }

    @Test
    fun pizzOctaveFitFallsBackToStrictWhenNothingClears() {
        // a rig with no octave artifact: every candidate is clean and safe -> keep the strictest.
        fun ts() = TakeScore(200, 200, 1f, 0f, 0f, 100)
        val scores = CalibrationAnalysis.PIZZ_OCTAVE_CANDIDATES.map { listOf(ts()) }
        val fit = CalibrationAnalysis.choosePizzOctaveFit(scores)
        assertEquals(2.0f, fit.minRatio)
        assertEquals(0.02f, fit.minRelative)
    }
}
