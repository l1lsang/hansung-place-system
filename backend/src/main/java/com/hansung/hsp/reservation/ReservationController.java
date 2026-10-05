package com.hansung.hsp.reservation;

import com.hansung.hsp.auth.HspPrincipal;
import com.hansung.hsp.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservations")
@Tag(name = "Reservations")
@SecurityRequirement(name = "session")
public class ReservationController {
    private final ReservationService service;
    public ReservationController(ReservationService service) { this.service = service; }

    @PostMapping
    @Operation(summary = "예약 생성", description = "members는 예약자 본인을 제외한 참여자입니다. 좌석 공간은 kind=SEAT_USE, seatId 필수입니다.")
    public ResponseEntity<ReservationResponse> create(@AuthenticationPrincipal HspPrincipal principal,
            @Valid @RequestBody ReservationCreateRequest input) {
        var result = service.create(principal.getUserId(), input);
        return ResponseEntity.created(URI.create("/api/reservations/" + result.id())).body(result);
    }

    @GetMapping
    public PageResponse<ReservationResponse> list(@AuthenticationPrincipal HspPrincipal principal,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.listMine(principal.getUserId(), page, size);
    }

    @GetMapping("/{reservationId}")
    public ReservationResponse get(@AuthenticationPrincipal HspPrincipal principal,
            @PathVariable @Positive Long reservationId) {
        return service.getMine(principal.getUserId(), reservationId);
    }

    @DeleteMapping("/{reservationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "본인 예약 취소", description = "행과 참여자는 보존합니다. 진행 중인 좌석은 반납(COMPLETED), 나머지 예약은 CANCELLED로 처리합니다.")
    public void cancel(@AuthenticationPrincipal HspPrincipal principal,
            @PathVariable @Positive Long reservationId) {
        service.cancel(principal.getUserId(), reservationId);
    }

    @PostMapping("/{reservationId}/return")
    @Operation(summary = "본인 좌석 반납", description = "실제 종료 시각을 기록하고 남은 시간을 해제합니다. 원래 예약 시간과 이력을 보존합니다.")
    public ReservationResponse returnSeat(@AuthenticationPrincipal HspPrincipal principal,
            @PathVariable @Positive Long reservationId) {
        return service.returnSeat(principal.getUserId(), reservationId);
    }
}
