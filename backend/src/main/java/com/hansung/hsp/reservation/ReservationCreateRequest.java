package com.hansung.hsp.reservation;

import java.time.Instant;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record ReservationCreateRequest(
        @NotNull @Positive Long spaceId,
        @Positive Long seatId,
        @NotNull Instant startTime,
        @NotNull Instant endTime,
        @Size(max = 100) String purpose,
        @NotNull ReservationKind kind,
        @Size(max = 100) List<@NotNull @Valid ReservationMemberRequest> members) {
    public ReservationCreateRequest {
        if (kind == null) kind = ReservationKind.BOOKING;
        if (members == null) members = List.of();
    }
}

