package be.drakarah.intonation.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakTest {

    private val today = LocalDate.of(2026, 7, 10)
    private fun days(vararg offsets: Long) = offsets.map { today.minusDays(it) }.toSet()

    @Test fun noPracticeMeansNoStreak() = assertEquals(0, computeStreak(emptySet(), today))

    @Test fun practicedTodayOnly() = assertEquals(1, computeStreak(days(0), today))

    @Test fun threeConsecutiveDaysEndingToday() =
        assertEquals(3, computeStreak(days(0, 1, 2), today))

    @Test fun streakSurvivesUntilTomorrow() =
        // practiced yesterday and before, not yet today — streak still alive
        assertEquals(2, computeStreak(days(1, 2), today))

    @Test fun gapBreaksStreak() = assertEquals(1, computeStreak(days(0, 2, 3), today))

    @Test fun lapsedStreakIsZero() = assertEquals(0, computeStreak(days(2, 3, 4), today))
}
