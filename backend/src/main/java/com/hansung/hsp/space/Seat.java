package com.hansung.hsp.space;

import jakarta.persistence.*;

@Entity
@Table(name = "seats")
public class Seat {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "space_id", nullable = false)
    private Long spaceId;
    @Column(name = "seat_number", nullable = false, length = 20)
    private String seatNumber;
    @Column(nullable = false, length = 20)
    private String status;

    protected Seat() {}
    public Seat(Long spaceId, String seatNumber, String status) {
        this.spaceId = spaceId;
        this.seatNumber = seatNumber;
        this.status = status;
    }
    public Long getId() { return id; }
    public Long getSpaceId() { return spaceId; }
    public String getSeatNumber() { return seatNumber; }
    public String getStatus() { return status; }
}

