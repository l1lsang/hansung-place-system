package com.hansung.hsp.policy;

import java.time.Instant;

public record BookingRulesResponse(Long spaceId, boolean configured, boolean enabled, int slotMinutes,
        int minDurationMinutes, int maxDurationMinutes, int advanceDays, Integer dailyMaxMinutes,
        BookingRulesInput.UsageScope usageScope, boolean preventAdjacent, boolean purposeRequired,
        int instantUseMinutes, Long updatedBy, Instant updatedAt) {
    public static BookingRulesResponse unconfigured(Long spaceId) {
        return new BookingRulesResponse(spaceId, false, false, 30, 30, 180, 7, null,
                BookingRulesInput.UsageScope.SPACE, false, false, 180, null, null);
    }
}
