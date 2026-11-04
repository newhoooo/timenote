import app.moment.tracker.core.*;
import java.time.*;
import java.util.Random;

/** Executes the actual production classes; no Android SDK or test library required. */
public final class CoreBehaviorTest {
    private static int assertions = 0;
    private static void eq(Object expected, Object actual, String context) {
        assertions++;
        if (!expected.equals(actual)) throw new AssertionError(context + ": expected " + expected + ", got " + actual);
    }
    private static void yes(boolean condition, String context) { eq(true, condition, context); }
    private static ZonedDateTime time(String value) { return ZonedDateTime.parse(value); }
    public static void main(String[] args) {
        // A 5-minute foreground interval, 20 minutes in background, and a 3-minute return = 8 minutes.
        long first = SessionClock.elapsed(0, 100_000L, 400_000);
        eq(300_000L, first, "first visit");
        eq(first, SessionClock.elapsed(first, null, 1_600_000), "background time is not usage");
        long resumed = SessionClock.elapsed(first, 1_600_000L, 1_780_000);
        eq(480_000L, resumed, "resumed session retains previous time");
        eq(1_020_000L, SessionClock.remaining(1_500_000, resumed), "mode change preserves elapsed time");
        eq(-60_000L, SessionClock.remaining(1_500_000, 1_560_000), "overtime is represented");
        yes(SessionClock.shouldAlert(TimerMode.COUNT_DOWN, 1_500_000, 1_500_000, false), "alert at zero");
        eq(false, SessionClock.shouldAlert(TimerMode.COUNT_DOWN, 1_500_000, 1_560_000, true), "no repeated alert");
        eq(false, SessionClock.shouldAlert(TimerMode.COUNT_UP, 1_500_000, 1_560_000, false), "count up has no expiry alert");
        eq(200L, SessionClock.elapsed(200, 1000L, 900), "invalid negative monotonic delta does not subtract usage");
        eq("01:05", SessionClock.format(65_000), "minute formatting");
        eq("1:01:01", SessionClock.format(3_661_000), "hour formatting");
        eq("02:10", SessionClock.format(-130_000), "overtime formatting");

        eq(time("2026-11-03T11:00:00+08:00"), ReminderWindow.next(time("2026-11-03T10:00:00+08:00"), 60, 9, 22, false, null), "hour interval");
        eq(time("2026-11-04T09:00:00+08:00"), ReminderWindow.next(time("2026-11-03T21:30:00+08:00"), 60, 9, 22, false, null), "quiet hours");
        eq(time("2026-11-09T09:00:00+08:00"), ReminderWindow.next(time("2026-11-06T21:30:00+08:00"), 60, 9, 22, true, null), "Friday evening skips weekend");
        eq(time("2026-11-03T10:10:00+08:00"), ReminderWindow.next(time("2026-11-03T10:00:00+08:00"), 60, 9, 22, false, 10), "snooze");
        eq(time("2026-11-03T00:30:00Z"), ReminderWindow.next(time("2026-11-02T23:30:00Z"), 60, 0, 24, false, null), "24-hour window");
        ZonedDateTime dst = ReminderWindow.next(time("2026-03-08T01:30:00-05:00[America/New_York]"), 60, 0, 24, false, null);
        eq(3600L, Duration.between(time("2026-03-08T01:30:00-05:00[America/New_York]"), dst).getSeconds(), "DST advances by elapsed hour");

        eq(30_000L, UsageMath.overlapMs(0, 60_000, 60_000, 0, 30_000), "split at day boundary");
        eq(30_000L, UsageMath.overlapMs(0, 60_000, 60_000, 30_000, 90_000), "remaining half");
        eq(0L, UsageMath.overlapMs(0, 60_000, 60_000, 60_000, 90_000), "half-open intervals do not double count");
        eq(15_000L, UsageMath.overlapMs(0, 60_000, 30_000, 0, 30_000), "partial duration is apportioned");
        eq(0L, UsageMath.overlapMs(60_000, 0, 30_000, 0, 60_000), "invalid wall-clock interval");

        Random random = new Random(42);
        String[] zones = { "Asia/Shanghai", "America/New_York", "Europe/Berlin", "UTC" };
        for (int i = 0; i < 2000; i++) {
            ZonedDateTime now = Instant.ofEpochSecond(1_700_000_000L + random.nextInt(100_000_000)).atZone(ZoneId.of(zones[i % zones.length]));
            int start = random.nextInt(23), end = start + 1 + random.nextInt(24 - start);
            boolean weekdays = random.nextBoolean();
            ZonedDateTime next = ReminderWindow.next(now, 5 + random.nextInt(236), start, end, weekdays, null);
            yes(next.isAfter(now), "next reminder must be in the future");
            yes(next.getHour() >= start && next.getHour() < end, "next reminder inside configured hours");
            yes(!weekdays || next.getDayOfWeek().getValue() <= 5, "next reminder respects workdays");
            long base = random.nextInt(1_000_000), duration = random.nextInt(100_000);
            eq(base + duration, SessionClock.elapsed(base, 5_000L, 5_000L + duration), "monotonic elapsed invariant");
        }
        System.out.println("PASS: " + assertions + " behavioral assertions against production timer, reminder, and daily-overlap code.");
    }
}
