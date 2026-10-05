package com.hansung.hsp.admin;

import com.hansung.hsp.auth.HspPrincipal;
import com.hansung.hsp.common.PageResponse;
import com.hansung.hsp.reservation.*;
import com.hansung.hsp.space.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "ADMIN 역할과 담당 공간 권한을 모두 확인합니다.")
@SecurityRequirement(name = "session")
public class AdminController {
    private final AdminSpaceService spaces;
    private final ReservationService reservations;
    private final SpaceBlockService blocks;
    private final AdminSummaryService summary;

    public AdminController(AdminSpaceService spaces, ReservationService reservations, SpaceBlockService blocks,
            AdminSummaryService summary) {
        this.spaces = spaces;
        this.reservations = reservations;
        this.blocks = blocks;
        this.summary = summary;
    }

    @GetMapping("/summary")
    @Operation(summary = "담당 공간의 일별 예약 통계", description = "KST 기준. 취소 제외, 자정 경계와 실제 좌석 반납 시각을 반영한 예약 분 합계입니다.")
    public AdminSummaryService.Summary summary(@AuthenticationPrincipal HspPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return summary.get(principal.getUserId(), date);
    }

    @GetMapping("/spaces")
    public PageResponse<SpaceResponse> spaces(@AuthenticationPrincipal HspPrincipal principal,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return spaces.list(principal.getUserId(), page, size);
    }

    @PostMapping("/spaces")
    @Operation(summary = "공간 등록", description = "등록한 관리자는 새 공간의 담당자로 연결됩니다.")
    public ResponseEntity<SpaceResponse> createSpace(@AuthenticationPrincipal HspPrincipal principal,
            @Valid @RequestBody SpaceCreateRequest input) {
        var result = spaces.create(principal.getUserId(), input);
        return ResponseEntity.created(URI.create("/api/spaces/" + result.id())).body(result);
    }

    @PatchMapping("/spaces/{spaceId}")
    @Operation(summary = "담당 공간 수정", description = "생략하거나 null인 필드는 유지합니다.")
    public SpaceResponse updateSpace(@AuthenticationPrincipal HspPrincipal principal,
            @PathVariable @Positive Long spaceId, @Valid @RequestBody SpacePatchRequest input) {
        return spaces.update(principal.getUserId(), spaceId, input);
    }

    @GetMapping("/reservations")
    public PageResponse<ReservationResponse> reservations(@AuthenticationPrincipal HspPrincipal principal,
            @RequestParam(required = false) @Positive Long spaceId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return reservations.listManaged(principal.getUserId(), spaceId, page, size);
    }

    @GetMapping("/reservations/{reservationId}")
    public ReservationResponse reservation(@AuthenticationPrincipal HspPrincipal principal,
            @PathVariable @Positive Long reservationId) {
        return reservations.getManaged(principal.getUserId(), reservationId);
    }

    @PostMapping("/reservations/{reservationId}/cancel")
    @Operation(summary = "담당 공간 예약 강제 취소·좌석 종료", description = "사유 필수. 예약·참여자·원래 시간과 처리자 이력을 보존합니다.")
    public ReservationResponse cancelReservation(@AuthenticationPrincipal HspPrincipal principal,
            @PathVariable @Positive Long reservationId, @Valid @RequestBody ReservationCancelRequest input) {
        return reservations.cancelManaged(principal.getUserId(), reservationId, input.reason());
    }

    @GetMapping("/spaces/{spaceId}/blocks")
    public PageResponse<SpaceBlockResponse> blocks(@AuthenticationPrincipal HspPrincipal principal,
            @PathVariable @Positive Long spaceId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return blocks.list(principal.getUserId(), spaceId, page, size);
    }

    @PostMapping("/spaces/{spaceId}/blocks")
    public ResponseEntity<SpaceBlockResponse> createBlock(@AuthenticationPrincipal HspPrincipal principal,
            @PathVariable @Positive Long spaceId, @Valid @RequestBody SpaceBlockCreateRequest input) {
        var result = blocks.create(principal.getUserId(), spaceId, input);
        return ResponseEntity.created(URI.create("/api/admin/spaces/" + spaceId + "/blocks")).body(result);
    }

    @DeleteMapping("/space-blocks/{blockId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "담당 공간의 차단 해제", description = "행을 삭제하지 않고 cancelled_at을 기록합니다.")
    public void cancelBlock(@AuthenticationPrincipal HspPrincipal principal, @PathVariable @Positive Long blockId) {
        blocks.cancel(principal.getUserId(), blockId);
    }
}
