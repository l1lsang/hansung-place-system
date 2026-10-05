package com.hansung.hsp.policy;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.DayOfWeek;

public record OperatingHoursInput(
        @NotNull OperatingPeriod period,
        @NotNull DayOfWeek dayOfWeek,
        @NotNull Boolean closed,
        @Pattern(regexp = "([01][0-9]|2[0-3]):[0-5][0-9]") String openTime,
        @Pattern(regexp = "([01][0-9]|2[0-3]):[0-5][0-9]|24:00") String closeTime) {}
