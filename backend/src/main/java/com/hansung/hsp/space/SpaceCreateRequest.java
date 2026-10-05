package com.hansung.hsp.space;

import jakarta.validation.constraints.*;

public record SpaceCreateRequest(
        @NotBlank @Size(max = 50) String spaceCode,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 30) String type,
        @Size(max = 100) String location,
        @Positive Integer minCapacity,
        @Positive Integer maxCapacity,
        @NotBlank @Size(max = 30) String venue,
        @Size(max = 255) String facilities,
        Boolean bookingEnabled) {}

