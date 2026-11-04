package app.moment.tracker.core;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;

/** Local-time reminder windows are recalculated on time and timezone changes. */
public final class ReminderWindow {
    private ReminderWindow() {}

    public static ZonedDateTime next(ZonedDateTime now, int intervalMinutes, int startHour,
                                     int endHour, boolean weekdaysOnly, Integer snoozeMinutes) {
        if (startHour < 0 || startHour > 23 || endHour <= startHour || endHour > 24) {
            throw new IllegalArgumentException("Reminder hours must satisfy 0 <= start < end <= 24");
        }
        ZonedDateTime candidate = now.plusMinutes(Math.max(1, snoozeMinutes == null ? intervalMinutes : snoozeMinutes));
        for (int i = 0; i < 10; i++) {
            boolean weekend = candidate.getDayOfWeek() == DayOfWeek.SATURDAY || candidate.getDayOfWeek() == DayOfWeek.SUNDAY;
            if (weekdaysOnly && weekend) candidate = atStart(candidate.plusDays(1), startHour);
            else if (candidate.getHour() < startHour) candidate = atStart(candidate, startHour);
            else if (endHour < 24 && candidate.getHour() >= endHour) candidate = atStart(candidate.plusDays(1), startHour);
            else return candidate;
        }
        throw new IllegalStateException("Unable to find an eligible reminder time");
    }

    private static ZonedDateTime atStart(ZonedDateTime day, int hour) {
        return day.toLocalDate().atTime(hour, 0).atZone(day.getZone());
    }
}
