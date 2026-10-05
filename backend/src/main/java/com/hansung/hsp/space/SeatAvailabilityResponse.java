package com.hansung.hsp.space;

import com.hansung.hsp.common.PageResponse;
import java.time.Instant;

public record SeatAvailabilityResponse(Long spaceId, Instant startTime, Instant endTime,
        boolean bookingEnabled, boolean policyConfigured, boolean instant,
        int instantUseMinutes, boolean userHasSeatUse, PageResponse<Item> seats) {
    public record Item(Long id, Long spaceId, String seatNumber, String status,
            boolean available, Instant availableUntil, Instant occupiedUntil,
            boolean mine, Long reservationId) {}
}
