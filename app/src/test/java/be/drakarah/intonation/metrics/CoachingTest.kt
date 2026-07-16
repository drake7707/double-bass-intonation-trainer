package be.drakarah.intonation.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachingTest {

    private val note = MasteryThresholds.NOTE

    // --- Mastery bands ---------------------------------------------------------------------

    @Test fun bandBoundariesAreInclusive() {
        assertEquals(MasteryBand.LOCKED, MasteryBand.of(0f, note))
        assertEquals(MasteryBand.LOCKED, MasteryBand.of(10f, note))       // ≤10 = locked
        assertEquals(MasteryBand.SOLID, MasteryBand.of(10.1f, note))
        assertEquals(MasteryBand.SOLID, MasteryBand.of(25f, note))        // ≤25 = solid
        assertEquals(MasteryBand.DEVELOPING, MasteryBand.of(25.1f, note))
        assertEquals(MasteryBand.DEVELOPING, MasteryBand.of(200f, note))
    }

    @Test fun shiftThresholdsAreMoreLenientThanNotes() {
        // A 40¢ landing is Developing as a static note but Solid as a shift.
        assertEquals(MasteryBand.DEVELOPING, MasteryBand.of(40f, MasteryThresholds.NOTE))
        assertEquals(MasteryBand.SOLID, MasteryBand.of(40f, MasteryThresholds.SHIFT))
    }

    // --- Bar fraction ----------------------------------------------------------------------

    @Test fun masteryFractionAnchors() {
        assertEquals(1.0f, masteryFraction(0f, note), 1e-4f)
        assertEquals(1.0f, masteryFraction(5f, note), 1e-4f)
        assertEquals(0.8f, masteryFraction(10f, note), 1e-4f)
        assertEquals(0.5f, masteryFraction(25f, note), 1e-4f)
        assertEquals(0.05f, masteryFraction(45f, note), 1e-4f)
        assertEquals(0.05f, masteryFraction(500f, note), 1e-4f) // floor, never 0
    }

    @Test fun masteryFractionScalesToThresholds() {
        // Shift's "solid" boundary (45¢) should fill about half, just like a note's 25¢ does.
        assertEquals(0.5f, masteryFraction(MasteryThresholds.SHIFT.solidMax, MasteryThresholds.SHIFT), 1e-4f)
        assertEquals(0.8f, masteryFraction(MasteryThresholds.SHIFT.lockedMax, MasteryThresholds.SHIFT), 1e-4f)
    }

    @Test fun masteryFractionIsMonotonicallyDecreasing() {
        var prev = masteryFraction(0f, note)
        var c = 1f
        while (c <= 60f) {
            val cur = masteryFraction(c, note)
            assertTrue("fraction should not increase as cents grow (at $c)", cur <= prev + 1e-6f)
            prev = cur
            c += 1f
        }
    }

    @Test fun the25centResultIsNotDemotivating() {
        // Regression against the old linear scale: 25¢ used to read "49%" with a near-empty bar.
        assertEquals(MasteryBand.SOLID, MasteryBand.of(25f, note))
        assertTrue("25¢ should fill about half the bar, not look empty", masteryFraction(25f, note) >= 0.5f)
    }

    // --- Bias ------------------------------------------------------------------------------

    @Test fun biasCenteredWithinDeadband() {
        assertEquals(BiasDirection.CENTERED, biasOf(0f).direction)
        assertEquals(BiasDirection.CENTERED, biasOf(5.9f).direction)
        assertEquals(BiasDirection.CENTERED, biasOf(-5.9f).direction)
    }

    @Test fun biasSignConvention() {
        // + sharp, − flat.
        assertEquals(BiasDirection.FLAT, biasOf(-22f).direction)
        assertEquals(BiasDirection.SHARP, biasOf(22f).direction)
        assertEquals("a bit flat", biasOf(-22f).label)
        assertEquals("a bit sharp", biasOf(22f).label)
        assertEquals("runs 22¢ flat", biasOf(-22f).detailedLabel)
        assertEquals("runs 22¢ sharp", biasOf(22f).detailedLabel)
        assertEquals("centered", biasOf(1f).label)
    }

    // --- Week trend ------------------------------------------------------------------------

    @Test fun trendNullWhenNoDataThisWeek() {
        assertNull(weekTrend(null, 30f))
    }

    @Test fun trendHasNoComparisonWithoutPriorWeek() {
        val t = weekTrend(20f, null)!!
        assertEquals(TrendDirection.STEADY, t.direction)
        assertTrue(!t.hasComparison)
    }

    @Test fun trendClassification() {
        // fewer cents this week than last = tighter (improvement).
        assertEquals(TrendDirection.TIGHTER, weekTrend(20f, 30f)!!.direction)
        assertEquals(TrendDirection.LOOSER, weekTrend(30f, 20f)!!.direction)
        assertEquals(TrendDirection.STEADY, weekTrend(21f, 20f)!!.direction) // within ±2¢ band
        assertEquals(10f, weekTrend(20f, 30f)!!.deltaCents, 1e-4f)
    }

    // --- Insight selection -----------------------------------------------------------------

    private fun pos(id: String, cents: Float, signed: Float, n: Int = 20, mode: String = "arco") =
        PositionMastery(id, id, mode, cents, signed, n, note)

    @Test fun insightPrefersBiggestActionableBias() {
        val positions = listOf(
            pos("1st", 24f, -22f),   // strong flat bias — the flagship case
            pos("2nd", 18f, -8f),
        )
        val insight = selectInsight(positions, weekTrend(20f, 21f))!!
        assertTrue(insight.contains("1st"))
        assertTrue(insight.contains("flat"))
        assertTrue("flat → aim higher (pitch terms, not hand geometry)", insight.contains("higher"))
        assertTrue("insight names the mode", insight.contains("arco"))
    }

    @Test fun insightCelebratesImprovementWhenNoBias() {
        val positions = listOf(pos("1st", 18f, -2f))
        val insight = selectInsight(positions, weekTrend(18f, 25f))!! // 7¢ tighter
        assertTrue(insight.contains("more in tune"))
    }

    @Test fun insightNamesAnchorWhenSteadyAndCentered() {
        val positions = listOf(pos("1st", 8f, -1f), pos("2nd", 20f, 2f))
        val insight = selectInsight(positions, weekTrend(15f, 15f))!!
        assertTrue(insight.contains("anchor"))
        assertTrue(insight.contains("1st")) // the most secure (lowest cents) position
    }

    @Test fun insightNullWhenNothingConfident() {
        val positions = listOf(pos("1st", 40f, -3f, n = 2))
        assertNull(selectInsight(positions, null))
    }

    @Test fun insightIgnoresBiasOnTooFewSamples() {
        val positions = listOf(pos("1st", 24f, -30f, n = 3)) // big bias but only 3 samples
        assertNull(selectInsight(positions, null))
    }

    @Test fun sharpBiasSuggestsAimingLower() {
        val positions = listOf(pos("2nd", 22f, 20f))
        val insight = selectInsight(positions, null)!!
        assertTrue(insight.contains("sharp"))
        assertTrue("sharp → aim lower", insight.contains("lower"))
    }

    @Test fun positionMasteryDerivations() {
        val p = pos("1st", 25f, -22f)
        assertEquals(MasteryBand.SOLID, p.band)
        assertEquals(BiasDirection.FLAT, p.bias.direction)
    }

    @Test fun hasEnoughDataGuardsAgainstOneGameConclusions() {
        // Below the threshold there is data but no verdict; at/above it a verdict is trustworthy.
        assertTrue(!pos("1st", 20f, 0f, n = MIN_SCORED_FOR_VERDICT - 1).hasEnoughData)
        assertTrue(pos("1st", 20f, 0f, n = MIN_SCORED_FOR_VERDICT).hasEnoughData)
        assertTrue("threshold must exceed one default round (10 prompts)", MIN_SCORED_FOR_VERDICT > 10)
    }

    @Test fun bandUsesRoundedCentsSoWordAndNumberAgree() {
        // 25.4¢ displays as "25¢"; the band must not read Developing (which would contradict).
        val p = pos("1st", 25.4f, 0f)
        assertEquals(MasteryBand.SOLID, p.band)
    }
}
