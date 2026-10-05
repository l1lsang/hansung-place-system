package com.hansung.hsp.reservation;

import jakarta.persistence.*;

@Entity
@Table(name = "reservation_members")
public class ReservationMember {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "reservation_id", nullable = false)
    private Long reservationId;
    @Column(name = "student_id", nullable = false, length = 20)
    private String studentId;
    @Column(nullable = false, length = 50)
    private String name;

    protected ReservationMember() {}
    public ReservationMember(Long reservationId, String studentId, String name) {
        this.reservationId = reservationId;
        this.studentId = studentId;
        this.name = name;
    }
    public Long getReservationId() { return reservationId; }
    public String getStudentId() { return studentId; }
    public String getName() { return name; }
}

