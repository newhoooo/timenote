package app.moment.tracker.core;

import java.util.Locale;

/** Pure duration arithmetic. Callers supply monotonic time, never the wall clock. */
public final class SessionClock {
    private SessionClock() {}

    public static long elapsed(long baseMs, Long startedAtMs, long nowMs) {
        long delta = startedAtMs == null ? 0 : Math.max(0, nowMs - startedAtMs);
        return Math.max(0, baseMs) + delta;
    }

    public static long remaining(long targetMs, long elapsedMs) {
        return Math.max(0, targetMs) - Math.max(0, elapsedMs);
    }

    public static boolean shouldAlert(TimerMode mode, long targetMs, long elapsedMs, boolean alreadyAlerted) {
        return mode == TimerMode.COUNT_DOWN && targetMs > 0 && elapsedMs >= targetMs && !alreadyAlerted;
    }

    public static String format(long ms) {
        long seconds = Math.abs(ms / 1000);
        long hours = seconds / 3600;
        return hours > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, seconds / 60 % 60, seconds % 60)
                : String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }
}
