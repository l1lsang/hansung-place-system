package com.hansung.hsp.space;

import com.hansung.hsp.common.*;
import com.hansung.hsp.policy.*;
import com.hansung.hsp.reservation.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class SeatAvailabilityService {
    private final SpaceService spaces;
    private final SeatRepository seats;
    private final ReservationRepository reservations;
    private final SpaceBlockRepository blocks;
    private final OperatingPolicyService policies;
    private final BookingRulesService rules;
    private final Clock clock;
    public SeatAvailabilityService(SpaceService spaces, SeatRepository seats, ReservationRepository reservations,
            SpaceBlockRepository blocks, OperatingPolicyService policies, BookingRulesService rules, Clock clock) {
        this.spaces = spaces; this.seats = seats; this.reservations = reservations;
        this.blocks = blocks; this.policies = policies; this.rules = rules; this.clock = clock;
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SeatAvailabilityResponse get(Long spaceId, Instant start, Instant end, Long userId, int page, int size) {
        var window = new TimeRange(start, end);
        if (Duration.between(start, end).compareTo(Duration.ofDays(31)) > 0) {
            throw ApiException.badRequest("AVAILABILITY_RANGE_TOO_LARGE", "한 번에 최대 31일을 조회할 수 있습니다.");
        }
        return snapshot(spaceId, window, userId, false, page, size);
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SeatAvailabilityResponse now(Long spaceId, Long userId, int page, int size) {
        var start = clock.instant();
        return snapshot(spaceId, new TimeRange(start, start.plusSeconds(rules.find(spaceId).instantUseMinutes() * 60L)),
                userId, true, page, size);
    }
    private SeatAvailabilityResponse snapshot(Long spaceId, TimeRange window, Long userId,
            boolean instant, int page, int size) {
        var space = spaces.require(spaceId);
        var list = seats.findBySpaceId(spaceId, PageResponse.request(page, size, Sort.by("id")));
        var bookings = reservations.findOverlapping(spaceId, null, window.startTime(), window.endTime());
        var blocked = blocks.findOverlapping(spaceId, window.startTime(), window.endTime()).stream()
                .map(b -> new TimeRange(b.getStartTime(), b.getEndTime())).toList();
        var policy = policies.find(spaceId);
        var open = OperatingCalendar.openWindows(policy, window);
        var now = clock.instant();
        var result = list.map(seat -> {
            var seatBookings = bookings.stream().filter(r -> r.getSeatId() == null || r.getSeatId().equals(seat.getId())).toList();
            var occupied = new ArrayList<>(blocked);
            seatBookings.forEach(r -> occupied.add(new TimeRange(r.getStartTime(), r.effectiveEnd())));
            var free = new ArrayList<TimeRange>();
            boolean enabled = space.isBookingEnabled() && "AVAILABLE".equals(seat.getStatus());
            if (enabled) for (var hours : open) free.addAll(TimeRanges.available(hours, occupied));
            var until = continuousEnd(window.startTime(), free);
            var current = seatBookings.stream().filter(r -> !r.getStartTime().isAfter(window.startTime())
                    && r.effectiveEnd().isAfter(window.startTime())).findFirst().orElse(null);
            boolean mine = current != null && current.getUserId().equals(userId);
            return new SeatAvailabilityResponse.Item(seat.getId(), spaceId, seat.getSeatNumber(), seat.getStatus(),
                    until != null && (instant || (!until.isBefore(window.endTime()) && window.startTime().isAfter(now))),
                    until, current == null ? null : current.effectiveEnd(), mine, mine ? current.getId() : null);
        });
        return new SeatAvailabilityResponse(spaceId, window.startTime(), window.endTime(), space.isBookingEnabled(),
                policy != null && policy.isEnabled(), instant, rules.find(spaceId).instantUseMinutes(),
                userId != null && reservations.hasSeatUse(userId, window.startTime(), window.endTime()), PageResponse.from(result));
    }
    public static Instant continuousEnd(Instant start, List<TimeRange> ranges) {
        Instant cursor = start;
        for (var range : ranges) {
            if (range.startTime().isAfter(cursor)) break;
            if (range.endTime().isAfter(cursor)) cursor = range.endTime();
        }
        return cursor.isAfter(start) ? cursor : null;
    }
}
