package com.hansung.hsp.policy;

import jakarta.validation.constraints.*;

public record BookingRulesInput(
        @NotNull Boolean enabled,
        @NotNull @Min(15) @Max(60) Integer slotMinutes,
        @NotNull @Min(1) @Max(1440) Integer minDurationMinutes,
        @NotNull @Min(1) @Max(1440) Integer maxDurationMinutes,
        @NotNull @Min(0) @Max(365) Integer advanceDays,
        @Min(1) @Max(1440) Integer dailyMaxMinutes,
        @NotNull UsageScope usageScope,
        @NotNull Boolean preventAdjacent,
        @NotNull Boolean purposeRequired,
        @NotNull @Min(1) @Max(1440) Integer instantUseMinutes) {
    public enum UsageScope { SPACE, VENUE }
}
