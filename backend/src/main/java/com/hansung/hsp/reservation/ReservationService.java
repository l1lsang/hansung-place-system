package com.hansung.hsp.reservation;

import com.hansung.hsp.admin.AdminAccessService;
import com.hansung.hsp.common.*;
import com.hansung.hsp.space.*;
import com.hansung.hsp.user.UserRepository;
import java.time.Clock;
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

    public ReservationService(ReservationRepository reservations, ReservationMemberRepository members,
            SpaceService spaces, SeatService seats, SpaceBlockRepository blocks, UserRepository users,
            ReservationPolicy policy, AdminAccessService access, Clock clock) {
        this.reservations = reservations;
        this.members = members;
        this.spaces = spaces;
        this.seats = seats;
        this.blocks = blocks;
        this.users = users;
        this.policy = policy;
        this.access = access;
        this.clock = clock;
    }

    @Transactional(timeout = 10)
    public ReservationResponse create(Long userId, ReservationCreateRequest input) {
        // All reservation/block writes take this same PostgreSQL row lock FIRST.
        // READ COMMITTED re-checks occupancy after the lock is acquired, including
        // reservations committed by another backend instance while we waited.
        var space = spaces.lock(input.spaceId());
        var user = users.findById(userId).orElseThrow(ForbiddenException::new);
        policy.validate(space, user, input);
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
        var reservation = reservations.save(new Reservation(userId, space.getId(), input.seatId(),
                input.startTime(), input.endTime(), input.purpose(), input.kind(), clock.instant()));
        var savedMembers = members.saveAll(input.members().stream()
                .map(m -> new ReservationMember(reservation.getId(), m.studentId(), m.name())).toList());
        return ReservationResponse.from(reservation, savedMembers);
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
        reservation.cancel(clock.instant());
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
        return ReservationResponse.from(reservation, members.findByReservationIdOrderById(reservation.getId()));
    }
    private PageResponse<ReservationResponse> responses(Page<Reservation> page) {
        var ids = page.getContent().stream().map(Reservation::getId).toList();
        var grouped = ids.isEmpty() ? java.util.Map.<Long, List<ReservationMember>>of()
                : members.findByReservationIdInOrderById(ids).stream()
                        .collect(Collectors.groupingBy(ReservationMember::getReservationId));
        return PageResponse.from(page.map(r -> ReservationResponse.from(r, grouped.getOrDefault(r.getId(), List.of()))));
    }
}

