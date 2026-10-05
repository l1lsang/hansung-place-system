package com.hansung.hsp.reservation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    String OVERLAP = """
            r.spaceId = :spaceId
            and r.status <> com.hansung.hsp.reservation.ReservationStatus.CANCELLED
            and r.startTime < :endTime and coalesce(r.endedAt, r.endTime) > :startTime
            and coalesce(r.endedAt, r.endTime) > r.startTime
            and (:seatId is null or r.seatId is null or r.seatId = :seatId)
            """;

    @Query("select (count(r) > 0) from Reservation r where " + OVERLAP)
    boolean existsOverlapping(@Param("spaceId") Long spaceId, @Param("seatId") Long seatId,
            @Param("startTime") Instant startTime, @Param("endTime") Instant endTime);

    @Query("select r from Reservation r where " + OVERLAP + " order by r.startTime")
    List<Reservation> findOverlapping(@Param("spaceId") Long spaceId, @Param("seatId") Long seatId,
            @Param("startTime") Instant startTime, @Param("endTime") Instant endTime);

    Page<Reservation> findByUserId(Long userId, Pageable pageable);

    @Query("""
            select r from Reservation r join Space s on s.id = r.spaceId
            where r.userId = :userId and r.status <> com.hansung.hsp.reservation.ReservationStatus.CANCELLED
            and r.startTime < :endTime and coalesce(r.endedAt, r.endTime) > :startTime
            and ((:venueScope = true and s.venue = :venue) or (:venueScope = false and r.spaceId = :spaceId))
            """)
    List<Reservation> findUserUsage(@Param("userId") Long userId, @Param("spaceId") Long spaceId,
            @Param("venue") String venue, @Param("venueScope") boolean venueScope,
            @Param("startTime") Instant startTime, @Param("endTime") Instant endTime);

    @Query("""
            select (count(r) > 0) from Reservation r where r.userId = :userId
            and r.kind = com.hansung.hsp.reservation.ReservationKind.SEAT_USE
            and r.status <> com.hansung.hsp.reservation.ReservationStatus.CANCELLED
            and r.startTime < :endTime and coalesce(r.endedAt, r.endTime) > :startTime
            """)
    boolean hasSeatUse(@Param("userId") Long userId, @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime);

    @Query("""
            select r from Reservation r where r.status <> com.hansung.hsp.reservation.ReservationStatus.CANCELLED
            and r.startTime < :endTime and coalesce(r.endedAt, r.endTime) > :startTime
            and exists (select p.id from AdminSpacePermission p where p.userId = :adminId and p.spaceId = r.spaceId)
            """)
    List<Reservation> findManagedDuring(@Param("adminId") Long adminId,
            @Param("startTime") Instant startTime, @Param("endTime") Instant endTime);

    @Query("select r.spaceId from Reservation r where r.id = :id")
    Optional<Long> findSpaceIdById(@Param("id") Long id);

    @Query("""
            select r from Reservation r where exists (
                select p.id from AdminSpacePermission p where p.userId = :adminId and p.spaceId = r.spaceId
            ) and (:spaceId is null or r.spaceId = :spaceId)
            """)
    Page<Reservation> findManagedReservations(@Param("adminId") Long adminId,
            @Param("spaceId") Long spaceId, Pageable pageable);
}
