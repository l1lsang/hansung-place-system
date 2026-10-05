package com.hansung.hsp.space;

import com.hansung.hsp.common.*;
import com.hansung.hsp.reservation.ReservationRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AvailabilityService {
    private final SpaceService spaces;
    private final SeatService seats;
    private final SpaceBlockRepository blocks;
    private final ReservationRepository reservations;

    public AvailabilityService(SpaceService spaces, SeatService seats, SpaceBlockRepository blocks,
            ReservationRepository reservations) {
        this.spaces = spaces;
        this.seats = seats;
        this.blocks = blocks;
        this.reservations = reservations;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AvailabilityResponse get(Long spaceId, Long seatId, Instant start, Instant end) {
        var window = new TimeRange(start, end);
        // Query-size bound, not a rule about how far in advance reservations are allowed.
        if (Duration.between(start, end).compareTo(Duration.ofDays(31)) > 0) {
            throw ApiException.badRequest("AVAILABILITY_RANGE_TOO_LARGE", "한 번에 최대 31일을 조회할 수 있습니다.");
        }
        var space = spaces.require(spaceId);
        boolean enabled = space.isBookingEnabled();
        if (seatId == null && seats.hasSeats(spaceId)) {
            throw ApiException.badRequest("SEAT_REQUIRED", "좌석이 있는 공간은 seatId를 지정해주세요.");
        }
        if (seatId != null) enabled &= "AVAILABLE".equals(seats.require(spaceId, seatId).getStatus());
        var occupied = new ArrayList<TimeRange>();
        reservations.findOverlapping(spaceId, seatId, start, end)
                .forEach(r -> occupied.add(new TimeRange(r.getStartTime(), r.getEndTime())));
        blocks.findOverlapping(spaceId, start, end)
                .forEach(b -> occupied.add(new TimeRange(b.getStartTime(), b.getEndTime())));
        // This is an occupancy snapshot, never a guarantee that a later POST will succeed.
        // TODO: intersect with approved university opening-hours/calendar policy when provided.
        return new AvailabilityResponse(spaceId, seatId, start, end, enabled, "OCCUPANCY_ONLY",
                enabled ? TimeRanges.available(window, occupied) : List.of());
    }
}

