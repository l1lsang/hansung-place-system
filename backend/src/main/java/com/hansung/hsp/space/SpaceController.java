package com.hansung.hsp.space;

import com.hansung.hsp.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.*;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/spaces")
@Tag(name = "Spaces")
public class SpaceController {
    private final SpaceService spaces;
    private final SeatService seats;
    private final AvailabilityService availability;
    public SpaceController(SpaceService spaces, SeatService seats, AvailabilityService availability) {
        this.spaces = spaces;
        this.seats = seats;
        this.availability = availability;
    }

    @GetMapping
    @Operation(summary = "공간 목록", description = "bookingEnabled 기본값 true. minCapacity는 필요한 최소 수용 능력(max_capacity 기준), q는 이름/위치 검색입니다.")
    public PageResponse<SpaceResponse> list(
            @RequestParam(required = false) @Size(max = 30) String venue,
            @RequestParam(required = false) @Size(max = 30) String type,
            @RequestParam(defaultValue = "true") Boolean bookingEnabled,
            @RequestParam(required = false) @Positive Integer minCapacity,
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return spaces.list(venue, type, bookingEnabled, minCapacity, q, page, size);
    }

    @GetMapping("/{spaceId}")
    public SpaceResponse get(@PathVariable @Positive Long spaceId) { return spaces.get(spaceId); }

    @GetMapping("/{spaceId}/seats")
    @Operation(summary = "공간의 좌석 목록", description = "status는 좌석 운영 상태입니다. 시간별 점유 여부는 availability로 조회합니다.")
    public PageResponse<SeatResponse> seats(@PathVariable @Positive Long spaceId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return seats.list(spaceId, page, size);
    }

    @GetMapping("/{spaceId}/availability")
    @Operation(summary = "요청 구간의 예약 가능 시간", description = "좌석 공간에는 seatId가 필요합니다. "
            + "시간은 ISO-8601 offset 포함, 한 번에 최대 31일. 활성화된 운영시간/시험기간 정책과 예약/차단을 반영합니다. policyScope로 적용 범위를 확인합니다.")
    public AvailabilityResponse availability(@PathVariable @Positive Long spaceId,
            @RequestParam(required = false) @Positive Long seatId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime) {
        return availability.get(spaceId, seatId, startTime, endTime);
    }
}
