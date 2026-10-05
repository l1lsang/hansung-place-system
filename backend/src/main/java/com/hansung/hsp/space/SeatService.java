package com.hansung.hsp.space;

import com.hansung.hsp.common.*;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SeatService {
    private final SeatRepository seats;
    private final SpaceService spaces;
    public SeatService(SeatRepository seats, SpaceService spaces) {
        this.seats = seats;
        this.spaces = spaces;
    }
    public PageResponse<SeatResponse> list(Long spaceId, int page, int size) {
        spaces.require(spaceId);
        return PageResponse.from(seats.findBySpaceId(spaceId,
                PageResponse.request(page, size, Sort.by("id"))).map(SeatResponse::from));
    }
    public Seat require(Long spaceId, Long seatId) {
        var seat = seats.findById(seatId).orElseThrow(() -> new ResourceNotFoundException(
                "SEAT_NOT_FOUND", "존재하지 않는 좌석입니다."));
        if (!seat.getSpaceId().equals(spaceId)) {
            throw ApiException.badRequest("SEAT_SPACE_MISMATCH", "해당 공간의 좌석이 아닙니다.");
        }
        return seat;
    }
    public boolean hasSeats(Long spaceId) { return seats.existsBySpaceId(spaceId); }
}

