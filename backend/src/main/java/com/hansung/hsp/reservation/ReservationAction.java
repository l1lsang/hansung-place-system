package com.hansung.hsp.reservation;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "reservation_actions")
public class ReservationAction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "reservation_id", nullable = false) private Long reservationId;
    @Column(name = "actor_id", nullable = false) private Long actorId;
    @Column(nullable = false, length = 30) private String action;
    @Column(length = 200) private String reason;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected ReservationAction() {}
    public ReservationAction(Long reservationId, Long actorId, String action, String reason, Instant now) {
        this.reservationId = reservationId; this.actorId = actorId; this.action = action;
        this.reason = reason; this.createdAt = now;
    }
    public Long getReservationId() { return reservationId; }
    public Response response() { return new Response(id, actorId, action, reason, createdAt); }
    public record Response(Long id, Long actorId, String action, String reason, Instant createdAt) {}
}
