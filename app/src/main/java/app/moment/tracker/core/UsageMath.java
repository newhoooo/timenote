package app.moment.tracker.core;

public final class UsageMath {
    private UsageMath() {}

    /** Split observed duration across local-day boundaries without counting unobserved gaps. */
    public static long overlapMs(long startedAt, long endedAt, long observedMs, long dayStart, long dayEnd) {
        if (endedAt <= startedAt || observedMs <= 0 || dayEnd <= dayStart) return 0;
        long overlap = Math.max(0, Math.min(endedAt, dayEnd) - Math.max(startedAt, dayStart));
        double fraction = Math.max(0, Math.min(1, (double) overlap / (endedAt - startedAt)));
        return (long) (observedMs * fraction);
    }
}
