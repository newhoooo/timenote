package app.moment.tracker.core

import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class SessionClockTest {
    @Test fun leavingAndReturningOnlyCountsForegroundTime() {
        val firstVisit = SessionClock.elapsed(0, 100_000L, 400_000)
        assertEquals(300_000L, firstVisit)
        assertEquals(firstVisit, SessionClock.elapsed(firstVisit, null, 1_600_000))
        assertEquals(480_000L, SessionClock.elapsed(firstVisit, 1_600_000L, 1_780_000))
    }
    @Test fun expiryIsOnceAndKeepsOvertime() {
        assertTrue(SessionClock.shouldAlert(TimerMode.COUNT_DOWN, 60_000, 60_000, false))
        assertFalse(SessionClock.shouldAlert(TimerMode.COUNT_DOWN, 60_000, 90_000, true))
        assertEquals(-30_000L, SessionClock.remaining(60_000, 90_000))
    }
    @Test fun FridayEveningReminderResumesOnMonday() {
        val friday = ZonedDateTime.parse("2026-11-06T21:30:00+08:00")
        assertEquals(ZonedDateTime.parse("2026-11-09T09:00:00+08:00"), ReminderWindow.next(friday, 60, 9, 22, true, null))
    }
    @Test fun midnightSplitDoesNotDoubleCount() {
        val left = UsageMath.overlapMs(0, 60_000, 60_000, 0, 30_000)
        val right = UsageMath.overlapMs(0, 60_000, 60_000, 30_000, 60_000)
        assertEquals(60_000L, left + right)
    }
}
