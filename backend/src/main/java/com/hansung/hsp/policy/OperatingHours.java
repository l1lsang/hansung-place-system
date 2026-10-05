package com.hansung.hsp.policy;

import jakarta.persistence.*;
import java.time.DayOfWeek;

@Embeddable
public class OperatingHours {
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private OperatingPeriod period;
    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;
    @Column(nullable = false) private boolean closed;
    @Column(name = "open_minute") private Integer openMinute;
    @Column(name = "close_minute") private Integer closeMinute;
    protected OperatingHours() {}
    OperatingHours(OperatingHoursInput input) {
        period = input.period(); dayOfWeek = input.dayOfWeek().getValue(); closed = input.closed();
        openMinute = minute(input.openTime()); closeMinute = minute(input.closeTime());
    }
    static Integer minute(String time) {
        if (time == null) return null;
        var parts = time.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }
    static String time(Integer minute) {
        return minute == null ? null : "%02d:%02d".formatted(minute / 60, minute % 60);
    }
    public OperatingPeriod getPeriod() { return period; }
    public int getDayOfWeek() { return dayOfWeek; }
    public boolean isClosed() { return closed; }
    public Integer getOpenMinute() { return openMinute; }
    public Integer getCloseMinute() { return closeMinute; }
    OperatingHoursInput response() {
        return new OperatingHoursInput(period, DayOfWeek.of(dayOfWeek), closed, time(openMinute), time(closeMinute));
    }
}
