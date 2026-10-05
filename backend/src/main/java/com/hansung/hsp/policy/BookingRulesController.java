package com.hansung.hsp.policy;

import com.hansung.hsp.auth.HspPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class BookingRulesController {
    private final BookingRulesService service;
    public BookingRulesController(BookingRulesService service) { this.service = service; }
    @GetMapping("/api/spaces/{spaceId}/booking-rules")
    @Operation(summary = "공간별 예약 제한 및 즉시 이용시간 조회")
    public BookingRulesResponse get(@PathVariable @Positive Long spaceId) { return service.get(spaceId); }
    @PutMapping("/api/admin/spaces/{spaceId}/booking-rules")
    @Operation(summary = "담당 공간 예약 규칙 저장", description = "enabled=false이면 기존 예약 제한을 유지합니다. instantUseMinutes는 즉시 이용에 항상 적용됩니다.")
    public BookingRulesResponse replace(@AuthenticationPrincipal HspPrincipal principal,
            @PathVariable @Positive Long spaceId, @Valid @RequestBody BookingRulesInput input) {
        return service.replace(principal.getUserId(), spaceId, input);
    }
}
