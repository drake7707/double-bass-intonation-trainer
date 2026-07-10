package be.drakarah.intonation.dsp

/** Detection range defaults for a 4-string double bass in orchestral tuning.
 *
 * The lowest target is open E1 at ~41.2 Hz; 35 Hz leaves headroom for flat playing and
 * A4 != 440 while cutting the subharmonic candidate space the detector has to consider.
 * The upper bound covers neck-position targets (~D4) plus harmonics headroom.
 */
object DspDefaults {
    const val FREQUENCY_MIN = 35f
    const val FREQUENCY_MAX = 1800f
}
