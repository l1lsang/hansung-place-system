package com.hansung.hsp.reservation;

import java.time.Instant;
import java.util.List;
import com.hansung.hsp.user.User;

public record ReservationResponse(Long id, Long userId, Long spaceId, Long seatId,
        Instant startTime, Instant endTime, String purpose, ReservationKind kind, ReservationStatus status,
        Instant createdAt, Instant cancelledAt, List<ReservationMemberResponse> members,
        Instant endedAt, Owner owner, List<ReservationAction.Response> actions) {
    public record Owner(String name, String studentId, String email) {}
    public static ReservationResponse from(Reservation r, List<ReservationMember> members,
            User owner, List<ReservationAction> actions) {
        return new ReservationResponse(r.getId(), r.getUserId(), r.getSpaceId(), r.getSeatId(),
                r.getStartTime(), r.getEndTime(), r.getPurpose(), r.getKind(), r.getStatus(),
                r.getCreatedAt(), r.getCancelledAt(), members.stream().map(ReservationMemberResponse::from).toList(),
                r.getEndedAt(), new Owner(owner.getName(), owner.getStudentId(), owner.getEmail()),
                actions.stream().map(ReservationAction::response).toList());
    }
}
