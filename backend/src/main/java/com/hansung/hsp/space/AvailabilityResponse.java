package com.hansung.hsp.space;

import com.hansung.hsp.common.TimeRange;
import java.time.Instant;
import java.util.List;

public record AvailabilityResponse(Long spaceId, Long seatId, Instant startTime, Instant endTime,
        boolean bookingEnabled, String policyScope, List<TimeRange> available) {}

