package com.hansung.hsp.reservation;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "reservations")
public class Reservation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "space_id", nullable = false)
    private Long spaceId;
    @Column(name = "seat_id")
    private Long seatId;
    @Column(name = "start_time", nullable = false)
    private Instant startTime;
    @Column(name = "end_time", nullable = false)
    private Instant endTime;
    @Column(length = 100)
    private String purpose;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private ReservationKind kind;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private ReservationStatus status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "cancelled_at")
    private Instant cancelledAt;
    @Column(name = "ended_at")
    private Instant endedAt;

    protected Reservation() {}
    public Reservation(Long userId, Long spaceId, Long seatId, Instant startTime, Instant endTime,
            String purpose, ReservationKind kind, Instant createdAt) {
        this.userId = userId;
        this.spaceId = spaceId;
        this.seatId = seatId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.purpose = purpose;
        this.kind = kind;
        this.status = ReservationStatus.UPCOMING;
        this.createdAt = createdAt;
    }
    public void cancel(Instant now) {
        status = ReservationStatus.CANCELLED;
        cancelledAt = now;
    }
    public void endSeatUse(Instant now) {
        status = ReservationStatus.COMPLETED;
        endedAt = now;
    }
    public Instant effectiveEnd() { return endedAt == null ? endTime : endedAt; }
    public Instant getEndedAt() { return endedAt; }
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getSpaceId() { return spaceId; }
    public Long getSeatId() { return seatId; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public String getPurpose() { return purpose; }
    public ReservationKind getKind() { return kind; }
    public ReservationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCancelledAt() { return cancelledAt; }
}
