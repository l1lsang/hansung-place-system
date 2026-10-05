package com.hansung.hsp.reservation;

import java.time.Instant;
import java.util.List;

public record ReservationResponse(Long id, Long userId, Long spaceId, Long seatId,
        Instant startTime, Instant endTime, String purpose, ReservationKind kind, ReservationStatus status,
        Instant createdAt, Instant cancelledAt, List<ReservationMemberResponse> members) {
    public static ReservationResponse from(Reservation r, List<ReservationMember> members) {
        return new ReservationResponse(r.getId(), r.getUserId(), r.getSpaceId(), r.getSeatId(),
                r.getStartTime(), r.getEndTime(), r.getPurpose(), r.getKind(), r.getStatus(),
                r.getCreatedAt(), r.getCancelledAt(), members.stream().map(ReservationMemberResponse::from).toList());
    }
}

