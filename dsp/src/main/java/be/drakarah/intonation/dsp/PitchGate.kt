// The gating logic and energy-level transform are adapted from FrequencyEvaluatorSimple in
// Tuner (https://codeberg.org/thetwom/Tuner), Copyright Michael Moessner, GPL-3.0-or-later.
// Modifications: exposes the gating diagnostics (noise, harmonic energy, level) alongside the
// smoothed frequency instead of collapsing rejected samples to a bare 0f.
package be.drakarah.intonation.dsp

import be.drakarah.intonation.dsp.detection.FrequencyDetectionCollectedResults
import be.drakarah.intonation.dsp.detection.OutlierRemovingSmoother
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
    frequencyMin: Float = DspDefaults.FREQUENCY_MIN,
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

    fun evaluate(results: FrequencyDetectionCollectedResults): PitchSample {
        // minus a very small number, so that sensitivity 100 always accepts a level of 0
        val requiredEnergyLevel = 100f - sensitivity - 0.0001f
        val energyLevel = transformEnergyToLevelFrom0To100(results.harmonicEnergyAbsolute)
        val accepted = results.noise < maxNoise
                && results.harmonicEnergyContentRelative >= minHarmonicEnergyContent
                && energyLevel >= requiredEnergyLevel
        val smoothed = if (accepted) smoother(results.frequency) else 0f

        val windowEndFrame = results.timeSeries.framePosition + results.sizeOfTimeSeries
        return PitchSample(
            timestampMs = windowEndFrame * 1000L / results.sampleRate,
            framePosition = results.timeSeries.framePosition,
            frequencyHz = results.frequency,
            smoothedHz = smoothed,
            accepted = accepted,
            noise = results.noise,
            harmonicEnergyRelative = results.harmonicEnergyContentRelative,
            energyLevel = energyLevel,
        )
    }

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
}
