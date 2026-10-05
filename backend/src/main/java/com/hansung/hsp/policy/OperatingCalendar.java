package com.hansung.hsp.policy;

import com.hansung.hsp.common.TimeRange;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** All boundaries are campus local time; end is exclusive, exam end DATE is inclusive. */
public final class OperatingCalendar {
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private OperatingCalendar() {}

    public static List<TimeRange> openWindows(OperatingPolicy policy, TimeRange window) {
        if (policy == null || !policy.isEnabled()) return List.of(window);
        var result = new ArrayList<TimeRange>();
        LocalDate last = window.endTime().minusNanos(1).atZone(ZONE).toLocalDate();
        for (var day = window.startTime().atZone(ZONE).toLocalDate(); !day.isAfter(last); day = day.plusDays(1)) {
            var hours = hours(policy, day, period(policy, day));
            if (hours.isClosed()) continue;
            Instant start = day.atStartOfDay(ZONE).plusMinutes(hours.getOpenMinute()).toInstant();
            Instant end = day.atStartOfDay(ZONE).plusMinutes(hours.getCloseMinute()).toInstant();
            if (start.isBefore(window.startTime())) start = window.startTime();
            if (end.isAfter(window.endTime())) end = window.endTime();
            if (start.isBefore(end)) result.add(new TimeRange(start, end));
        }
        return List.copyOf(result);
    }

    public static boolean allows(OperatingPolicy policy, TimeRange window) {
        if (policy == null || !policy.isEnabled()) return true;
        // Split at exam transitions. Each segment has a repeating weekly schedule;
        // inspect at most a week of interior days even for very long input ranges.
        var boundaries = new TreeSet<Instant>();
        boundaries.add(window.startTime()); boundaries.add(window.endTime());
        if (policy.getExamStartDate() != null) {
            for (var date : List.of(policy.getExamStartDate(), policy.getExamEndDate().plusDays(1))) {
                var boundary = date.atStartOfDay(ZONE).toInstant();
                if (boundary.isAfter(window.startTime()) && boundary.isBefore(window.endTime())) boundaries.add(boundary);
            }
        }
        var points = new ArrayList<>(boundaries);
        for (int i = 1; i < points.size(); i++) {
            Instant start = points.get(i - 1), end = points.get(i);
            LocalDate first = start.atZone(ZONE).toLocalDate(), last = end.minusNanos(1).atZone(ZONE).toLocalDate();
            var period = period(policy, first);
            if (!dayAllows(policy, first, period, start, end) || !dayAllows(policy, last, period, start, end)) return false;
            long interiors = ChronoUnit.DAYS.between(first, last) - 1;
            for (int day = 1; day <= Math.min(interiors, 7); day++) {
                if (!dayAllows(policy, first.plusDays(day), period, start, end)) return false;
            }
        }
        return true;
    }

    private static boolean dayAllows(OperatingPolicy policy, LocalDate day, OperatingPeriod period,
            Instant start, Instant end) {
        var hours = hours(policy, day, period);
        if (hours.isClosed()) return false;
        var midnight = day.atStartOfDay(ZONE);
        Instant clippedStart = start.isAfter(midnight.toInstant()) ? start : midnight.toInstant();
        Instant next = midnight.plusDays(1).toInstant();
        Instant clippedEnd = end.isBefore(next) ? end : next;
        return !clippedStart.isBefore(midnight.plusMinutes(hours.getOpenMinute()).toInstant())
                && !clippedEnd.isAfter(midnight.plusMinutes(hours.getCloseMinute()).toInstant());
    }
    private static OperatingPeriod period(OperatingPolicy policy, LocalDate day) {
        return policy.getExamStartDate() != null && !day.isBefore(policy.getExamStartDate())
                && !day.isAfter(policy.getExamEndDate()) ? OperatingPeriod.EXAM : policy.getAcademicPeriod();
    }
    private static OperatingHours hours(OperatingPolicy policy, LocalDate day, OperatingPeriod period) {
        return policy.getHours().stream().filter(h -> h.getPeriod() == period
                && h.getDayOfWeek() == day.getDayOfWeek().getValue()).findFirst().orElseThrow();
    }
}
