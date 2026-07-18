package be.drakarah.intonation.game

/** How a captured note was physically produced, inferred from its attack shape (see
 * [CapturedPitch.attackMaxStep] / [CapturedPitch.attackRiseSamples] and docs/DETECTION.md §10).
 * Used to spot a style mismatch — pizz played in an arco exercise or vice versa. */
enum class PlayStyle {
    /** Bowed: a gradual energy crescendo into the note. */
    ARCO,
    /** Plucked: a near-instant energy step into the note. */
    PIZZ,
    /** No calibrated threshold yet, or the attack was too weak to judge — don't decide. */
    UNKNOWN,
}

/** The per-rig attack-shape thresholds that separate a bowed onset from a plucked one, measured by
 * the calibration wizard from labeled arco/pizz takes (see
 * `CalibrationAnalysis.playStyleSeparation`). [attackMaxStep] is the primary cut: a steepest attack
 * step at or above it is plucked. [maxRiseSamples] is the secondary catch for plucks that land
 * already saturated (their step happened before onset), whose rise is that short. */
data class PlayStyleThreshold(
    val attackMaxStep: Float,
    val maxRiseSamples: Int = 1,
) {
    /** Armed only when the wizard found a real gap between the two styles on this rig; a
     * non-positive step threshold means "styles overlap here, don't classify". */
    val armed: Boolean get() = attackMaxStep > 0f
}

/** Pure attack-shape → playing-style decision. The single home for this call so the game trace and
 * the wizard's separation check agree exactly (no per-caller drift). */
object PlayStyleClassifier {
    fun classify(attackMaxStep: Float, attackRiseSamples: Int, threshold: PlayStyleThreshold?): PlayStyle {
        if (threshold == null || !threshold.armed) return PlayStyle.UNKNOWN
        val plucked = attackMaxStep >= threshold.attackMaxStep ||
            attackRiseSamples <= threshold.maxRiseSamples
        return if (plucked) PlayStyle.PIZZ else PlayStyle.ARCO
    }
}
