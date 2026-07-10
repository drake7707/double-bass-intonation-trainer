// The gating logic and energy-level transform are adapted from FrequencyEvaluatorSimple in
// Tuner (https://codeberg.org/thetwom/Tuner), Copyright Michael Moessner, GPL-3.0-or-later.
// Modifications: exposes the gating diagnostics (noise, harmonic energy, level) alongside the
// smoothed frequency instead of collapsing rejected samples to a bare 0f.
package be.drakarah.intonation.dsp

import be.drakarah.intonation.dsp.detection.FrequencyDetectionCollectedResults
import be.drakarah.intonation.dsp.detection.OutlierRemovingSmoother
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.log10

/** One pitch measurement, emitted per analysis window (every ~23 ms at default settings).
 *
 * @param timestampMs End time of the analysis window relative to the start of the recording,
 *   derived from the audio frame position (monotonic, unaffected by processing latency).
 * @param framePosition First audio frame of the analysis window.
 * @param frequencyHz Raw detector output for this window, 0 if nothing was detected.
 * @param smoothedHz Outlier-removed moving average, 0 while rejected or still accumulating.
 * @param accepted Whether this window passed the noise/harmonic-energy/sensitivity gates.
 * @param noise Lag-1 decorrelation of the signal (0 = clean periodic signal, 1 = noise).
 * @param harmonicEnergyRelative Fraction of spectral energy contained in the found harmonics.
 * @param energyLevel Absolute harmonic energy mapped logarithmically to 0..100.
 */
data class PitchSample(
    val timestampMs: Long,
    val framePosition: Int,
    val frequencyHz: Float,
    val smoothedHz: Float,
    val accepted: Boolean,
    val noise: Float,
    val harmonicEnergyRelative: Float,
    val energyLevel: Float,
    /** True when the octave-up correction halved the detector's frequency (see [PitchGate]). */
    val octaveCorrected: Boolean = false,
)

/** Filters raw detection results into [PitchSample]s.
 *
 * A window is accepted when the signal is periodic enough (low noise), enough of its energy
 * sits in the detected harmonic series, and its absolute level clears the sensitivity
 * threshold. Only accepted windows feed the smoother.
 */
class PitchGate(
    numMovingAverage: Int,
    maxNumFaultyValues: Int,
    private val maxNoise: Float,
    private val minHarmonicEnergyContent: Float,
    private val sensitivity: Float,
    private val frequencyMin: Float = DspDefaults.FREQUENCY_MIN,
    frequencyMax: Float = DspDefaults.FREQUENCY_MAX,
) {
    private val smoother = OutlierRemovingSmoother(
        numMovingAverage,
        frequencyMin,
        frequencyMax,
        relativeDeviationToBeAnOutlier = 0.1f,
        maxNumSuccessiveOutliers = maxNumFaultyValues,
        minNumValuesForValidMean = 2,
        numBuffers = 3
    )

    private var lastValidHz = 0f
    private var lastValidMs = 0L
    private var lastValidLevel = 0f

    fun evaluate(results: FrequencyDetectionCollectedResults): PitchSample {
        // minus a very small number, so that sensitivity 100 always accepts a level of 0
        val requiredEnergyLevel = 100f - sensitivity - 0.0001f
        val energyLevel = transformEnergyToLevelFrom0To100(results.harmonicEnergyAbsolute)
        val accepted = results.noise < maxNoise
                && results.harmonicEnergyContentRelative >= minHarmonicEnergyContent
                && energyLevel >= requiredEnergyLevel

        val windowEndFrame = results.timeSeries.framePosition + results.sizeOfTimeSeries
        val timestampMs = windowEndFrame * 1000L / results.sampleRate

        var frequency = results.frequency
        var octaveCorrected = false
        if (accepted && frequency > 0f) {
            val corrected = correctOctaveUp(results, frequency, energyLevel, timestampMs)
            octaveCorrected = corrected != frequency
            frequency = corrected
        }

        val smoothed = if (accepted) smoother(frequency) else 0f
        if (accepted && smoothed > 0f) {
            lastValidHz = smoothed
            lastValidMs = timestampMs
            lastValidLevel = energyLevel
        }

        return PitchSample(
            timestampMs = timestampMs,
            framePosition = results.timeSeries.framePosition,
            frequencyHz = frequency,
            smoothedHz = smoothed,
            accepted = accepted,
            noise = results.noise,
            harmonicEnergyRelative = results.harmonicEnergyContentRelative,
            energyLevel = energyLevel,
            octaveCorrected = octaveCorrected,
        )
    }

    /** Detects and undoes octave-up errors, the double bass's number-one detection problem.
     *
     * Two independent pieces of evidence, each targeting a failure mode observed in real
     * Pixel-6a recordings (see dsp test `OctaveDiagnosis`):
     *
     * 1. Odd-harmonic proof (stateless): if the spectrum holds a clear peak at 1.5x the
     *    detected frequency, that peak is the 3rd harmonic of f/2 — a true f-note cannot
     *    produce it. Happens on the bowed A string, where the phone mic loses the 55 Hz
     *    fundamental and the detector picks 110 Hz although 165 Hz is present.
     * 2. Decay continuation (stateful): a note was tracked at ~f/2 until just now, the
     *    energy is not rising (no new attack), and the detection jumped to exactly one
     *    octave up. Happens on plucked low strings when the fundamental decays away first.
     *    The energy condition keeps deliberately played octave jumps (new attack) intact.
     */
    private fun correctOctaveUp(
        results: FrequencyDetectionCollectedResults,
        frequency: Float,
        energyLevel: Float,
        timestampMs: Long,
    ): Float {
        val half = frequency / 2f
        if (half < frequencyMin) return frequency

        if (spectralPeakRatio(results, 1.5f * frequency) > ODD_HARMONIC_MIN_RATIO) return half

        val decayContinuation = lastValidHz > 0f
                && timestampMs - lastValidMs <= DECAY_MEMORY_MS
                && abs(centsBetween(half, lastValidHz)) <= DECAY_MATCH_CENTS
                && energyLevel <= lastValidLevel + DECAY_LEVEL_MARGIN
        if (decayContinuation) return half

        return frequency
    }

    /** Peak height around [hz] relative to the local spectral mean (peak-detection style). */
    private fun spectralPeakRatio(results: FrequencyDetectionCollectedResults, hz: Float): Float {
        val spec = results.frequencySpectrum.amplitudeSpectrumSquared
        val df = results.frequencySpectrum.df
        val center = (hz / df).toInt()
        val half = maxOf(2, (0.05f * hz / df).toInt())
        if (center - 4 * half < 0 || center + 4 * half >= spec.size) return 0f
        val peak = (center - half..center + half).maxOf { spec[it] }
        var localMean = 0f
        for (i in center - 4 * half..center + 4 * half) localMean += spec[i]
        localMean /= (8 * half + 1)
        return if (localMean > 0f) peak / localMean else 0f
    }

    private fun centsBetween(hz: Float, referenceHz: Float): Float =
        (1200.0 * ln(hz.toDouble() / referenceHz) / ln(2.0)).toFloat()

    private fun transformEnergyToLevelFrom0To100(energy: Float): Float {
        // sine waves of maximum amplitude (1f) would have a level of log10(1f)
        // but normal levels are normally much below 1, 1e-3 seems good enough
        val minValue = 1e-7f
        val maxValue = 1e-2f
        val minLevel = log10(minValue)
        val maxLevel = log10(maxValue)
        val energyLevel = log10(energy.coerceAtLeast(minValue))
        return (100 * (energyLevel - minLevel) / (maxLevel - minLevel)).coerceIn(0f, 100f)
    }

    private companion object {
        /** Correct segments in the recorded corpus stay <= 1.1; wrong ones average 2.3. */
        const val ODD_HARMONIC_MIN_RATIO = 1.4f
        const val DECAY_MEMORY_MS = 800L
        const val DECAY_MATCH_CENTS = 60f
        const val DECAY_LEVEL_MARGIN = 3f
    }
}
