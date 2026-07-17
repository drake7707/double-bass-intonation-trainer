package be.drakarah.intonation.metrics

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class TrendWindowTest {

    private val zone = ZoneId.of("Europe/Brussels")

    @Test
    fun `previous block is the seven-day span ending a week before the round`() {
        val startedAt = 1_700_000_000_000L
        val day = epochDayOf(startedAt, zone)
        val (from, until) = previousBlockWindow(startedAt, zone)
        // [day-14, day-7): a full week, entirely in the past, never touching the current week.
        assertEquals(day - 14, from)
        assertEquals(day - 7, until)
        assertEquals(7, until - from)
    }

    @Test
    fun `window is anchored to the round's own day so history replays match`() {
        // Two rounds a fortnight apart get non-overlapping previous blocks.
        val early = 1_700_000_000_000L
        val late = early + 14L * 24 * 60 * 60 * 1000
        assertEquals(
            previousBlockWindow(early, zone).let { it.first + 14 to it.second + 14 },
            previousBlockWindow(late, zone),
        )
    }
}
