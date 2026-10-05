package com.hansung.hsp.policy;

import com.hansung.hsp.common.TimeRange;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class OperatingCalendarTest {
    private OperatingPolicy policy() {
        var hours = new ArrayList<OperatingHoursInput>();
        for (var period : OperatingPeriod.values()) for (var day : DayOfWeek.values()) {
            boolean closed = period != OperatingPeriod.EXAM && day == DayOfWeek.SUNDAY;
            hours.add(new OperatingHoursInput(period, day, closed,
                    closed ? null : period == OperatingPeriod.EXAM ? "00:00" : "09:00",
                    closed ? null : period == OperatingPeriod.EXAM ? "24:00" : "18:00"));
        }
        var p = new OperatingPolicy(1L);
        p.replace(new OperatingPolicyInput(true, OperatingPeriod.SEMESTER,
                LocalDate.parse("2030-06-01"), LocalDate.parse("2030-06-03"), hours), 1L, Instant.EPOCH);
        return p;
    }
    private TimeRange range(String start, String end) {
        return new TimeRange(OffsetDateTime.parse(start + "+09:00").toInstant(),
                OffsetDateTime.parse(end + "+09:00").toInstant());
    }
    @Test void honorsHoursExactClosingAndSunday() {
        var p = policy();
        assertThat(OperatingCalendar.allows(p, range("2030-05-27T09:00:00", "2030-05-27T18:00:00"))).isTrue();
        assertThat(OperatingCalendar.allows(p, range("2030-05-27T08:59:59", "2030-05-27T10:00:00"))).isFalse();
        assertThat(OperatingCalendar.allows(p, range("2030-05-26T10:00:00", "2030-05-26T11:00:00"))).isFalse();
    }
    @Test void examIncludesBothDatesAndSupportsMidnight() {
        var p = policy();
        assertThat(OperatingCalendar.allows(p, range("2030-06-01T00:00:00", "2030-06-04T00:00:00"))).isTrue();
        assertThat(OperatingCalendar.allows(p, range("2030-06-03T23:00:00", "2030-06-04T00:00:01"))).isFalse();
        assertThat(OperatingCalendar.openWindows(p, range("2030-06-02T00:00:00", "2030-06-03T00:00:00")))
                .containsExactly(range("2030-06-02T00:00:00", "2030-06-03T00:00:00"));
    }
    @Test void clipsAvailabilityAndPreservesUnconfiguredBehavior() {
        var day = range("2030-05-27T00:00:00", "2030-05-28T00:00:00");
        assertThat(OperatingCalendar.openWindows(policy(), day)).containsExactly(range("2030-05-27T09:00:00", "2030-05-27T18:00:00"));
        assertThat(OperatingCalendar.openWindows(null, day)).containsExactly(day);
        assertThat(OperatingCalendar.allows(null, day)).isTrue();
    }
    @Test void rejectsLongSpansWithClosedInteriorDays() {
        assertThat(OperatingCalendar.allows(policy(), range("2030-01-01T09:00:00", "2035-01-01T18:00:00"))).isFalse();
    }
    @Test void rejectsDuplicateDaysAndReversedExamDates() {
        var p = policy().response();
        var duplicate = new ArrayList<>(p.hours()); duplicate.set(1, duplicate.getFirst());
        assertThatThrownBy(() -> OperatingPolicyService.validate(new OperatingPolicyInput(true, OperatingPeriod.SEMESTER,
                null, null, duplicate))).hasMessageContaining("한 번씩");
        assertThatThrownBy(() -> OperatingPolicyService.validate(new OperatingPolicyInput(true, OperatingPeriod.SEMESTER,
                LocalDate.parse("2030-06-03"), LocalDate.parse("2030-06-01"), p.hours()))).hasMessageContaining("시험기간");
    }
}
