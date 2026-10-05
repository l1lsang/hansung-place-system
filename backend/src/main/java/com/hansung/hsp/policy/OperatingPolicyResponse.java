package com.hansung.hsp.policy;

import java.time.*;
import java.util.List;

public record OperatingPolicyResponse(Long spaceId, boolean configured, boolean enabled, String timeZone,
        OperatingPeriod academicPeriod, LocalDate examStartDate, LocalDate examEndDate,
        Long updatedBy, Instant updatedAt, List<OperatingHoursInput> hours) {
    static OperatingPolicyResponse unconfigured(Long spaceId) {
        return new OperatingPolicyResponse(spaceId, false, false, "Asia/Seoul", null, null, null,
                null, null, List.of());
    }
}
