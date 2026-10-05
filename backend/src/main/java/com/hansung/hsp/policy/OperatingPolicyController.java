package com.hansung.hsp.policy;

import com.hansung.hsp.auth.HspPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @Tag(name = "Operating policy")
public class OperatingPolicyController {
    private final OperatingPolicyService policies;
    public OperatingPolicyController(OperatingPolicyService policies) { this.policies = policies; }
    @GetMapping("/api/spaces/{spaceId}/policy")
    @Operation(summary = "공간 운영시간 및 시험기간 정책 조회", description = "미설정 또는 비활성 정책은 기존 예약 동작을 유지합니다. 시간대는 Asia/Seoul입니다.")
    public OperatingPolicyResponse get(@PathVariable @Positive Long spaceId) { return policies.get(spaceId); }

    @PutMapping("/api/admin/spaces/{spaceId}/policy") @SecurityRequirement(name = "session")
    @Operation(summary = "담당 공간 정책 전체 저장", description = "학기·방학·시험기간 각 7일, 총 21개 요일 규칙을 전달합니다. 시험기간 양 끝 날짜를 포함하며 기본 주간 운영시간을 대체합니다. 기존 예약은 변경하지 않고 새 예약에 적용합니다. 00:00~24:00은 종일, closed=true는 휴무입니다.")
    public OperatingPolicyResponse replace(@AuthenticationPrincipal HspPrincipal principal,
            @PathVariable @Positive Long spaceId, @Valid @RequestBody OperatingPolicyInput input) {
        return policies.replace(principal.getUserId(), spaceId, input);
    }
}
