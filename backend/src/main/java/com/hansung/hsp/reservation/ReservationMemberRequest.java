package com.hansung.hsp.reservation;

import jakarta.validation.constraints.*;

public record ReservationMemberRequest(@NotBlank @Size(max = 20) String studentId,
                                       @NotBlank @Size(max = 50) String name) {}

