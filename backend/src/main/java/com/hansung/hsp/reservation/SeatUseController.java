package com.hansung.hsp.reservation;

import com.hansung.hsp.auth.HspPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class SeatUseController {
    private final ReservationService service;
    public SeatUseController(ReservationService service) { this.service = service; }
    @PostMapping("/api/spaces/{spaceId}/seats/{seatId}/use")
    @Operation(summary = "좌석 즉시 이용 시작", description = "시작·종료 시각은 서버가 계산합니다. 기본 180분이며 운영 종료·차단·다음 예약에 맞춰 단축합니다.")
    public ResponseEntity<ReservationResponse> start(@AuthenticationPrincipal HspPrincipal principal,
            @PathVariable @Positive Long spaceId, @PathVariable @Positive Long seatId) {
        var result = service.startSeatUse(principal.getUserId(), spaceId, seatId);
        return ResponseEntity.created(URI.create("/api/reservations/" + result.id())).body(result);
    }
}
