package com.hansung.hsp.common;

import java.time.Instant;

public record TimeRange(Instant startTime, Instant endTime) {
    public TimeRange {
        if (startTime == null || endTime == null || !startTime.isBefore(endTime)) {
            throw new InvalidReservationTimeException("시작 시간은 종료 시간보다 빨라야 합니다.");
        }
    }
}

