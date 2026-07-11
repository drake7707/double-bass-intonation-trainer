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
}
