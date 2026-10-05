package com.hansung.hsp.admin;

import com.hansung.hsp.common.*;
import com.hansung.hsp.space.*;
import com.hansung.hsp.reservation.ReservationRepository;
import java.time.Clock;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SpaceBlockService {
    private final AdminAccessService access;
    private final SpaceService spaces;
    private final SpaceBlockRepository blocks;
    private final ReservationRepository reservations;
    private final Clock clock;

    public SpaceBlockService(AdminAccessService access, SpaceService spaces, SpaceBlockRepository blocks,
            ReservationRepository reservations, Clock clock) {
        this.access = access;
        this.spaces = spaces;
        this.blocks = blocks;
        this.reservations = reservations;
        this.clock = clock;
    }

    public PageResponse<SpaceBlockResponse> list(Long adminId, Long spaceId, int page, int size) {
        access.requireSpace(adminId, spaceId);
        spaces.require(spaceId);
        return PageResponse.from(blocks.findBySpaceIdAndCancelledAtIsNull(spaceId,
                PageResponse.request(page, size, Sort.by("startTime", "id"))).map(SpaceBlockResponse::from));
    }

    @Transactional(timeout = 10)
    public SpaceBlockResponse create(Long adminId, Long spaceId, SpaceBlockCreateRequest input) {
        access.requireSpace(adminId, spaceId);
        spaces.lock(spaceId);
        new TimeRange(input.startTime(), input.endTime());
        if (!input.endTime().isAfter(clock.instant())) {
            throw new InvalidReservationTimeException("차단 종료 시간은 현재 시간 이후여야 합니다.");
        }
        // The shared space lock also serializes block creation against reservation creation.
        // Do not silently invalidate any existing reservation, including a single occupied seat.
        if (reservations.existsOverlapping(spaceId, null, input.startTime(), input.endTime())) {
            throw ApiException.conflict("RESERVATION_CONFLICT", "기존 예약이 있는 시간은 차단할 수 없습니다.");
        }
        if (blocks.existsOverlapping(spaceId, input.startTime(), input.endTime())) {
            throw ApiException.conflict("BLOCK_CONFLICT", "이미 차단된 시간입니다.");
        }
        return SpaceBlockResponse.from(blocks.save(new SpaceBlock(spaceId, adminId,
                input.startTime(), input.endTime(), input.reason(), clock.instant())));
    }

    @Transactional(timeout = 10)
    public void cancel(Long adminId, Long blockId) {
        Long spaceId = blocks.findSpaceIdById(blockId).orElseThrow(this::notFound);
        access.requireSpace(adminId, spaceId);
        spaces.lock(spaceId);
        var block = blocks.findById(blockId).orElseThrow(this::notFound);
        if (block.getCancelledAt() == null) block.cancel(clock.instant());
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("BLOCK_NOT_FOUND", "존재하지 않는 차단입니다.");
    }
}

