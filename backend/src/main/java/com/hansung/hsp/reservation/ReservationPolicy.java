package com.hansung.hsp.reservation;

import com.hansung.hsp.common.*;
import com.hansung.hsp.space.Space;
import com.hansung.hsp.user.User;
import java.time.Clock;
import java.util.HashSet;
import org.springframework.stereotype.Component;

@Component
public class ReservationPolicy {
    private final Clock clock;
    public ReservationPolicy(Clock clock) { this.clock = clock; }

    public void validate(Space space, User user, ReservationCreateRequest input) {
        new TimeRange(input.startTime(), input.endTime());
        if (!input.startTime().isAfter(clock.instant())) {
            throw new InvalidReservationTimeException("예약 시작 시간은 현재 시간 이후여야 합니다.");
        }
        if (!space.isBookingEnabled()) {
            throw ApiException.conflict("BOOKING_DISABLED", "예약이 비활성화된 공간입니다.");
        }
        var studentIds = new HashSet<String>();
        studentIds.add(user.getStudentId());
        for (var member : input.members()) {
            if (!studentIds.add(member.studentId())) {
                throw ApiException.badRequest("DUPLICATE_MEMBER", "예약자 또는 참여자 학번이 중복됩니다.");
            }
        }
        if (input.kind() == ReservationKind.SEAT_USE) {
            if (input.seatId() == null || !input.members().isEmpty()) {
                throw ApiException.badRequest("INVALID_SEAT_RESERVATION", "좌석 이용은 좌석 ID와 예약자 1명만 허용합니다.");
            }
        } else {
            if (input.seatId() != null) {
                throw ApiException.badRequest("INVALID_RESERVATION_KIND", "좌석 예약에는 SEAT_USE를 사용해주세요.");
            }
            int headcount = 1 + input.members().size();
            if ((space.getMinCapacity() != null && headcount < space.getMinCapacity())
                    || (space.getMaxCapacity() != null && headcount > space.getMaxCapacity())) {
                throw ApiException.badRequest("CAPACITY_VIOLATION", "예약자를 포함한 인원이 공간의 수용 인원 조건에 맞지 않습니다.");
            }
        }
        // Operating hours/exam dates are checked by OperatingPolicyService under the space lock.
        // Optional duration/horizon/daily rules are checked by BookingRulesService.
    }
}
