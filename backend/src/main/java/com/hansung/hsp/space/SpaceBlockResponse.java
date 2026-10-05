package com.hansung.hsp.space;

import java.time.Instant;

public record SpaceBlockResponse(Long id, Long spaceId, Long createdBy, Instant startTime,
        Instant endTime, String reason, Instant createdAt, Instant cancelledAt) {
    public static SpaceBlockResponse from(SpaceBlock b) {
        return new SpaceBlockResponse(b.getId(), b.getSpaceId(), b.getCreatedBy(), b.getStartTime(),
                b.getEndTime(), b.getReason(), b.getCreatedAt(), b.getCancelledAt());
    }
}

