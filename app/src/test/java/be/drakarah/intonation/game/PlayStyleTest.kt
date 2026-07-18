package be.drakarah.intonation.game

import be.drakarah.intonation.calibration.CalibrationAnalysis
import be.drakarah.intonation.calibration.CalibrationAnalysis.AttackShape
import be.drakarah.intonation.calibration.SeparationVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayStyleTest {

    // --- the pure classifier decision ---------------------------------------------------

    private val threshold = PlayStyleThreshold(attackMaxStep = 33f, maxRiseSamples = 1)

    @Test
    fun steepAttackClassifiesAsPizz() {
        assertEquals(PlayStyle.PIZZ, PlayStyleClassifier.classify(48f, 4, threshold))
    }

    @Test
    fun gradualAttackClassifiesAsArco() {
        assertEquals(PlayStyle.ARCO, PlayStyleClassifier.classify(15f, 6, threshold))
    }

    @Test
    fun alreadySaturatedPluckCaughtByTheRiseRule() {
        // a pluck that landed before onset confirmed: small step but no ramp
        assertEquals(PlayStyle.PIZZ, PlayStyleClassifier.classify(10f, 0, threshold))
    }

    @Test
    fun noThresholdMeansUnknown() {
        assertEquals(PlayStyle.UNKNOWN, PlayStyleClassifier.classify(90f, 0, null))
        // a disarmed threshold (styles overlap on this rig) also stays out of it
        assertEquals(PlayStyle.UNKNOWN, PlayStyleClassifier.classify(90f, 0, PlayStyleThreshold(0f)))
    }

    // --- the pure separation decision ---------------------------------------------------

    @Test
    fun cleanSeparationIsGoodAndSetsAZeroFalsePositiveThreshold() {
        val arco = mapOf(55f to AttackShape(14f, 6), 73f to AttackShape(18f, 5), 98f to AttackShape(12f, 7))
        val pizz = mapOf(55f to AttackShape(48f, 1), 73f to AttackShape(52f, 0), 98f to AttackShape(40f, 1))
        val s = CalibrationAnalysis.playStyleSeparation(arco, pizz)
        assertEquals(SeparationVerdict.GOOD, s.verdict)
        val t = s.threshold
        assertNotNull(t)
        // threshold sits above the worst bowed step (18) — no bowed take is misread
        assertTrue("threshold ${t!!.attackMaxStep}", t.attackMaxStep > 18f)
        assertTrue(s.arcoChecks.all { it.second })
        assertTrue(s.pizzChecks.all { it.second })
        assertEquals(1f, s.pizzRecall)
    }

    @Test
    fun overlappingStylesLeaveTheClassifierOff() {
        // plucked attacks no sharper than the bowed ones, and no faster to the plateau — nothing to
        // arm on this rig (both the step and the rise rules come up empty)
        val arco = mapOf(55f to AttackShape(30f, 4), 73f to AttackShape(28f, 5))
        val pizz = mapOf(55f to AttackShape(20f, 4), 73f to AttackShape(25f, 5))
        val s = CalibrationAnalysis.playStyleSeparation(arco, pizz)
        assertEquals(SeparationVerdict.OVERLAP, s.verdict)
        assertNull(s.threshold)
    }

    @Test
    fun emptyTakesDoNotArm() {
        val s = CalibrationAnalysis.playStyleSeparation(emptyMap(), mapOf(55f to AttackShape(50f, 0)))
        assertEquals(SeparationVerdict.OVERLAP, s.verdict)
        assertNull(s.threshold)
        assertFalse(PlayStyleThreshold(0f).armed)
    }
}
