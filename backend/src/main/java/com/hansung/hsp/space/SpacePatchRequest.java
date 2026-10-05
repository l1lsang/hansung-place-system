package com.hansung.hsp.space;

import jakarta.validation.constraints.*;

// Null/omitted fields are unchanged. Nullable text can be cleared with an empty string.
public record SpacePatchRequest(
        @Pattern(regexp = "(?s).*\\S.*") @Size(max = 50) String spaceCode,
        @Pattern(regexp = "(?s).*\\S.*") @Size(max = 100) String name,
        @Pattern(regexp = "(?s).*\\S.*") @Size(max = 30) String type,
        @Size(max = 100) String location,
        @Positive Integer minCapacity,
        @Positive Integer maxCapacity,
        @Pattern(regexp = "(?s).*\\S.*") @Size(max = 30) String venue,
        @Size(max = 255) String facilities,
        Boolean bookingEnabled) {}

