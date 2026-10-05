package com.hansung.hsp.admin;
import jakarta.validation.constraints.*;
public record ReservationCancelRequest(@NotBlank @Size(max = 200) String reason) {}
