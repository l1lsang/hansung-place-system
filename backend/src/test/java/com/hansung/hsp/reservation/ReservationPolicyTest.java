package com.hansung.hsp.reservation;

import com.hansung.hsp.common.ApiException;
import com.hansung.hsp.space.Space;
import com.hansung.hsp.user.User;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ReservationPolicyTest {
    private final Instant now = Instant.parse("2030-01-01T00:00:00Z");
    private final ReservationPolicy policy = new ReservationPolicy(Clock.fixed(now, ZoneOffset.UTC));
    private final User user = new User("owner", "owner@example.test", "예약자", "STUDENT");

    private Space space(boolean enabled, Integer min, Integer max) {
        return new Space("test", "공간", "ROOM", null, min, max, "TEST", null, enabled);
    }
    private ReservationCreateRequest request(Instant start, Instant end, List<ReservationMemberRequest> members) {
        return new ReservationCreateRequest(1L, null, start, end, "공부", ReservationKind.BOOKING, members);
    }
    @Test void acceptsValidRoomReservationAndCountsOwner() {
        policy.validate(space(true, 2, 2), user, request(now.plusSeconds(60), now.plusSeconds(120),
                List.of(new ReservationMemberRequest("guest", "참여자"))));
    }
    @Test void rejectsInvalidAndPastTime() {
        assertThatThrownBy(() -> policy.validate(space(true, null, null), user,
                request(now.plusSeconds(120), now.plusSeconds(60), List.of())))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("INVALID_RESERVATION_TIME");
        assertThatThrownBy(() -> policy.validate(space(true, null, null), user,
                request(now, now.plusSeconds(60), List.of()))).isInstanceOf(ApiException.class);
    }
    @Test void rejectsDisabledSpace() {
        assertThatThrownBy(() -> policy.validate(space(false, null, null), user,
                request(now.plusSeconds(60), now.plusSeconds(120), List.of())))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("BOOKING_DISABLED");
    }
    @Test void rejectsDuplicateOwnerOrMembers() {
        assertThatThrownBy(() -> policy.validate(space(true, null, null), user,
                request(now.plusSeconds(60), now.plusSeconds(120),
                        List.of(new ReservationMemberRequest("owner", "다른 이름")))))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("DUPLICATE_MEMBER");
    }
    @Test void rejectsCapacityViolation() {
        assertThatThrownBy(() -> policy.validate(space(true, 2, 4), user,
                request(now.plusSeconds(60), now.plusSeconds(120), List.of())))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("CAPACITY_VIOLATION");
    }
    @Test void rejectsMembersOnSeatReservation() {
        assertThatThrownBy(() -> policy.validate(space(true, null, null), user,
                new ReservationCreateRequest(1L, 1L, now.plusSeconds(60), now.plusSeconds(120), null,
                        ReservationKind.SEAT_USE, List.of(new ReservationMemberRequest("guest", "참여자")))))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("INVALID_SEAT_RESERVATION");
    }
}

