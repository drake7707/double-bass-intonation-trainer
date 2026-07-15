package be.drakarah.intonation.data

import be.drakarah.intonation.metrics.practiceStreak
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** The streak algorithm now lives in the pure metrics domain and works on local epoch-days. */
class StreakTest {

    private val today = LocalDate.of(2026, 7, 10).toEpochDay().toInt()
    private fun days(vararg offsets: Int) = offsets.map { today - it }.toSet()

    @Test fun noPracticeMeansNoStreak() = assertEquals(0, practiceStreak(emptySet(), today))

    @Test fun practicedTodayOnly() = assertEquals(1, practiceStreak(days(0), today))

    @Test fun threeConsecutiveDaysEndingToday() =
        assertEquals(3, practiceStreak(days(0, 1, 2), today))

    @Test fun streakSurvivesUntilTomorrow() =
        // practiced yesterday and before, not yet today — streak still alive
        assertEquals(2, practiceStreak(days(1, 2), today))

    @Test fun gapBreaksStreak() = assertEquals(1, practiceStreak(days(0, 2, 3), today))

    @Test fun lapsedStreakIsZero() = assertEquals(0, practiceStreak(days(2, 3, 4), today))
}
