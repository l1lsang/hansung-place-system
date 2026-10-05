package com.hansung.hsp.reservation;

import com.hansung.hsp.admin.AdminAccessService;
import com.hansung.hsp.common.*;
import com.hansung.hsp.space.*;
import com.hansung.hsp.policy.OperatingPolicyService;
import com.hansung.hsp.policy.OperatingCalendar;
import com.hansung.hsp.policy.BookingRulesService;
import com.hansung.hsp.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReservationService {
    private final ReservationRepository reservations;
    private final ReservationMemberRepository members;
    private final SpaceService spaces;
    private final SeatService seats;
    private final SpaceBlockRepository blocks;
    private final UserRepository users;
    private final ReservationPolicy policy;
    private final AdminAccessService access;
    private final Clock clock;
    private final OperatingPolicyService operatingPolicies;
    private final BookingRulesService bookingRules;
    private final ReservationActionRepository actions;

    public ReservationService(ReservationRepository reservations, ReservationMemberRepository members,
            SpaceService spaces, SeatService seats, SpaceBlockRepository blocks, UserRepository users,
            ReservationPolicy policy, AdminAccessService access, Clock clock, OperatingPolicyService operatingPolicies,
            BookingRulesService bookingRules, ReservationActionRepository actions) {
        this.reservations = reservations;
        this.members = members;
        this.spaces = spaces;
        this.seats = seats;
        this.blocks = blocks;
        this.users = users;
        this.policy = policy;
        this.access = access;
        this.clock = clock;
        this.operatingPolicies = operatingPolicies;
        this.bookingRules = bookingRules;
        this.actions = actions;
    }

    @Transactional(timeout = 10)
    public ReservationResponse create(Long userId, ReservationCreateRequest input) {
        // Creation locks the owner FIRST, then the space. This also serializes
        // the same owner's quota and seat use across multiple spaces.
        var user = users.findByIdForUpdate(userId).orElseThrow(ForbiddenException::new);
        // All reservation/block writes take this same PostgreSQL space row lock.
        // READ COMMITTED re-checks occupancy after the lock is acquired, including
        // reservations committed by another backend instance while we waited.
        var space = spaces.lock(input.spaceId());
        policy.validate(space, user, input);
        operatingPolicies.validateReservation(space.getId(), new TimeRange(input.startTime(), input.endTime()));
        bookingRules.validate(userId, space, input);
        if (input.kind() == ReservationKind.BOOKING && seats.hasSeats(space.getId())) {
            throw ApiException.badRequest("SEAT_REQUIRED", "좌석이 있는 공간은 SEAT_USE와 좌석 ID를 지정해주세요.");
        }
        if (input.seatId() != null) {
            var seat = seats.require(space.getId(), input.seatId());
            if (!"AVAILABLE".equals(seat.getStatus())) {
                throw ApiException.conflict("SEAT_DISABLED", "사용할 수 없는 좌석입니다.");
            }
        }
        if (reservations.existsOverlapping(space.getId(), input.seatId(), input.startTime(), input.endTime())) {
            throw new DuplicateReservationException();
        }
        if (blocks.existsOverlapping(space.getId(), input.startTime(), input.endTime())) {
            throw ApiException.conflict("SPACE_BLOCKED", "관리자가 차단한 시간입니다.");
        }
        if (input.kind() == ReservationKind.SEAT_USE
                && reservations.hasSeatUse(userId, input.startTime(), input.endTime())) {
            throw ApiException.conflict("ALREADY_USING_SEAT", "같은 시간에 이용 중이거나 예약한 좌석이 있습니다.");
        }
        var reservation = reservations.save(new Reservation(userId, space.getId(), input.seatId(),
                input.startTime(), input.endTime(), input.purpose(), input.kind(), clock.instant()));
        var savedMembers = members.saveAll(input.members().stream()
                .map(m -> new ReservationMember(reservation.getId(), m.studentId(), m.name())).toList());
        return ReservationResponse.from(reservation, savedMembers, user, List.of());
    }

    @Transactional(timeout = 10)
    public ReservationResponse startSeatUse(Long userId, Long spaceId, Long seatId) {
        var user = users.findByIdForUpdate(userId).orElseThrow(ForbiddenException::new);
        var space = spaces.lock(spaceId);
        var seat = seats.require(spaceId, seatId);
        if (!space.isBookingEnabled()) throw ApiException.conflict("BOOKING_DISABLED", "예약이 비활성화된 공간입니다.");
        if (!"AVAILABLE".equals(seat.getStatus())) throw ApiException.conflict("SEAT_DISABLED", "사용할 수 없는 좌석입니다.");
        var now = clock.instant();
        var window = new TimeRange(now, now.plusSeconds(bookingRules.find(spaceId).instantUseMinutes() * 60L));
        var occupied = new ArrayList<TimeRange>();
        reservations.findOverlapping(spaceId, seatId, now, window.endTime())
                .forEach(r -> occupied.add(new TimeRange(r.getStartTime(), r.effectiveEnd())));
        blocks.findOverlapping(spaceId, now, window.endTime())
                .forEach(b -> occupied.add(new TimeRange(b.getStartTime(), b.getEndTime())));
        var available = new ArrayList<TimeRange>();
        for (var open : OperatingCalendar.openWindows(operatingPolicies.find(spaceId), window)) {
            available.addAll(TimeRanges.available(open, occupied));
        }
        var end = SeatAvailabilityService.continuousEnd(now, available);
        if (end == null) throw ApiException.conflict("SEAT_NOT_AVAILABLE_NOW", "현재 이용할 수 없는 좌석입니다. 운영시간과 좌석 상태를 확인해주세요.");
        if (reservations.hasSeatUse(userId, now, end)) {
            throw ApiException.conflict("ALREADY_USING_SEAT", "같은 시간에 이용 중이거나 예약한 좌석이 있습니다.");
        }
        var reservation = reservations.save(new Reservation(userId, spaceId, seatId, now, end,
                "좌석 즉시 이용", ReservationKind.SEAT_USE, now));
        return ReservationResponse.from(reservation, List.of(), user, List.of());
    }

    public PageResponse<ReservationResponse> listMine(Long userId, int page, int size) {
        return responses(reservations.findByUserId(userId,
                PageResponse.request(page, size, Sort.by(Sort.Direction.DESC, "startTime", "id"))));
    }

    public ReservationResponse getMine(Long userId, Long reservationId) {
        var reservation = require(reservationId);
        requireOwner(userId, reservation);
        return response(reservation);
    }

    @Transactional(timeout = 10)
    public void cancel(Long userId, Long reservationId) {
        Long spaceId = reservations.findSpaceIdById(reservationId).orElseThrow(this::notFound);
        spaces.lock(spaceId);
        var reservation = require(reservationId);
        requireOwner(userId, reservation);
        if (reservation.getStatus() == ReservationStatus.CANCELLED) return;
        if (reservation.getStatus() == ReservationStatus.COMPLETED
                || !reservation.getEndTime().isAfter(clock.instant())) {
            throw ApiException.conflict("RESERVATION_COMPLETED", "이미 종료된 예약은 취소할 수 없습니다.");
        }
        var now = clock.instant();
        if (reservation.getKind() == ReservationKind.SEAT_USE && !reservation.getStartTime().isAfter(now)) {
            finishSeat(reservation, userId, "RETURN_SEAT", null, now);
        } else {
            reservation.cancel(now);
            actions.save(new ReservationAction(reservationId, userId, "CANCEL", null, now));
        }
    }

    @Transactional(timeout = 10)
    public ReservationResponse returnSeat(Long userId, Long reservationId) {
        spaces.lock(reservations.findSpaceIdById(reservationId).orElseThrow(this::notFound));
        var reservation = require(reservationId);
        requireOwner(userId, reservation);
        if (reservation.getKind() != ReservationKind.SEAT_USE) {
            throw ApiException.badRequest("NOT_SEAT_USE", "좌석 이용만 반납할 수 있습니다.");
        }
        if (reservation.getEndedAt() != null) return response(reservation);
        var now = clock.instant();
        if (reservation.getStatus() != ReservationStatus.UPCOMING || reservation.getStartTime().isAfter(now)
                || !reservation.getEndTime().isAfter(now)) {
            throw ApiException.conflict("SEAT_USE_NOT_ACTIVE", "현재 이용 중인 좌석만 반납할 수 있습니다.");
        }
        finishSeat(reservation, userId, "RETURN_SEAT", null, now);
        return response(reservation);
    }

    @Transactional(timeout = 10)
    public ReservationResponse cancelManaged(Long adminId, Long reservationId, String reason) {
        Long spaceId = reservations.findSpaceIdById(reservationId).orElseThrow(this::notFound);
        spaces.lock(spaceId);
        access.requireSpace(adminId, spaceId);
        var reservation = require(reservationId);
        if (reservation.getStatus() == ReservationStatus.CANCELLED || reservation.getEndedAt() != null) return response(reservation);
        var now = clock.instant();
        if (reservation.getStatus() == ReservationStatus.COMPLETED || !reservation.getEndTime().isAfter(now)) {
            throw ApiException.conflict("RESERVATION_COMPLETED", "이미 종료된 예약은 취소할 수 없습니다.");
        }
        if (reservation.getKind() == ReservationKind.SEAT_USE && !reservation.getStartTime().isAfter(now)) {
            finishSeat(reservation, adminId, "ADMIN_END", reason.trim(), now);
        } else {
            reservation.cancel(now);
            actions.save(new ReservationAction(reservationId, adminId, "ADMIN_CANCEL", reason.trim(), now));
        }
        return response(reservation);
    }

    private void finishSeat(Reservation reservation, Long actorId, String action, String reason, Instant now) {
        reservation.endSeatUse(now);
        actions.save(new ReservationAction(reservation.getId(), actorId, action, reason, now));
    }

    public PageResponse<ReservationResponse> listManaged(Long adminId, Long spaceId, int page, int size) {
        access.requireAdmin(adminId);
        if (spaceId != null) access.requireSpace(adminId, spaceId);
        return responses(reservations.findManagedReservations(adminId, spaceId,
                PageResponse.request(page, size, Sort.by(Sort.Direction.DESC, "startTime", "id"))));
    }

    public ReservationResponse getManaged(Long adminId, Long reservationId) {
        var reservation = require(reservationId);
        access.requireSpace(adminId, reservation.getSpaceId());
        return response(reservation);
    }

    private Reservation require(Long id) {
        return reservations.findById(id).orElseThrow(this::notFound);
    }
    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("RESERVATION_NOT_FOUND", "존재하지 않는 예약입니다.");
    }
    private void requireOwner(Long userId, Reservation reservation) {
        if (!reservation.getUserId().equals(userId)) throw new ForbiddenException();
    }
    private ReservationResponse response(Reservation reservation) {
        return ReservationResponse.from(reservation, members.findByReservationIdOrderById(reservation.getId()),
                users.findById(reservation.getUserId()).orElseThrow(ForbiddenException::new),
                actions.findByReservationIdOrderById(reservation.getId()));
    }
    private PageResponse<ReservationResponse> responses(Page<Reservation> page) {
        var ids = page.getContent().stream().map(Reservation::getId).toList();
        var grouped = ids.isEmpty() ? java.util.Map.<Long, List<ReservationMember>>of()
                : members.findByReservationIdInOrderById(ids).stream()
                        .collect(Collectors.groupingBy(ReservationMember::getReservationId));
        var owners = users.findAllById(page.getContent().stream().map(Reservation::getUserId).distinct().toList()).stream()
                .collect(Collectors.toMap(com.hansung.hsp.user.User::getId, u -> u));
        var audit = ids.isEmpty() ? Map.<Long, List<ReservationAction>>of() : actions.findByReservationIdInOrderById(ids).stream()
                .collect(Collectors.groupingBy(ReservationAction::getReservationId));
        return PageResponse.from(page.map(r -> ReservationResponse.from(r, grouped.getOrDefault(r.getId(), List.of()),
                owners.get(r.getUserId()), audit.getOrDefault(r.getId(), List.of()))));
    }
}
