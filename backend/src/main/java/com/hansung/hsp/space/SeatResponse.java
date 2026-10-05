package com.hansung.hsp.space;

public record SeatResponse(Long id, Long spaceId, String seatNumber, String status) {
    public static SeatResponse from(Seat seat) {
        return new SeatResponse(seat.getId(), seat.getSpaceId(), seat.getSeatNumber(), seat.getStatus());
    }
}

