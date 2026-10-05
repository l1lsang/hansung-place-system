package com.hansung.hsp.policy;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public record OperatingPolicyInput(
        @NotNull Boolean enabled,
        @NotNull OperatingPeriod academicPeriod,
        LocalDate examStartDate,
        LocalDate examEndDate,
        @NotNull @Size(min = 21, max = 21) List<@NotNull @Valid OperatingHoursInput> hours) {}
