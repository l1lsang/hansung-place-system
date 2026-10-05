package com.hansung.hsp.common;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class TimeRanges {
    private TimeRanges() {}

    // Subtract the union of occupied intervals using half-open [start, end) boundaries.
    public static List<TimeRange> available(TimeRange window, List<TimeRange> occupied) {
        var ordered = occupied.stream()
                .filter(r -> r.startTime().isBefore(window.endTime()) && r.endTime().isAfter(window.startTime()))
                .sorted(Comparator.comparing(TimeRange::startTime)).toList();
        var result = new ArrayList<TimeRange>();
        Instant cursor = window.startTime();
        for (var range : ordered) {
            Instant start = range.startTime().isAfter(window.startTime()) ? range.startTime() : window.startTime();
            Instant end = range.endTime().isBefore(window.endTime()) ? range.endTime() : window.endTime();
            if (cursor.isBefore(start)) result.add(new TimeRange(cursor, start));
            if (end.isAfter(cursor)) cursor = end;
        }
        if (cursor.isBefore(window.endTime())) result.add(new TimeRange(cursor, window.endTime()));
        return List.copyOf(result);
    }
}

