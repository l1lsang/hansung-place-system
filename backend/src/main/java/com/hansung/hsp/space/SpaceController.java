package com.hansung.hsp.space;

import com.hansung.hsp.common.PageResponse;
import com.hansung.hsp.auth.HspPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
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
    private final SeatAvailabilityService seatAvailability;
    public SpaceController(SpaceService spaces, SeatService seats, AvailabilityService availability,
            SeatAvailabilityService seatAvailability) {
        this.spaces = spaces;
        this.seats = seats;
        this.availability = availability;
        this.seatAvailability = seatAvailability;
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

    @GetMapping("/{spaceId}/seats/availability")
    @Operation(summary = "좌석별 예약 가능 여부 일괄 조회", description = "페이지당 최대 100개. 본인 예약 ID만 반환합니다.")
    public ResponseEntity<SeatAvailabilityResponse> seatAvailability(@PathVariable @Positive Long spaceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size,
            @AuthenticationPrincipal HspPrincipal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(seatAvailability.get(spaceId,
                startTime, endTime, principal == null ? null : principal.getUserId(), page, size));
    }

    @GetMapping("/{spaceId}/seat-status")
    @Operation(summary = "지금 이용 가능한 좌석과 종료 예정 시각", description = "서버 현재 시각 기준. 운영 종료·차단·다음 예약 직전까지만 즉시 이용할 수 있습니다.")
    public ResponseEntity<SeatAvailabilityResponse> seatStatus(@PathVariable @Positive Long spaceId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size,
            @AuthenticationPrincipal HspPrincipal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(seatAvailability.now(spaceId,
                principal == null ? null : principal.getUserId(), page, size));
    }
}
