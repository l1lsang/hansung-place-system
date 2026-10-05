package com.hansung.hsp.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(@NotBlank @Size(max = 20) String studentId,
                           @NotBlank @Size(max = 72) String password) {
    @Override public String toString() { return "LoginRequest[credentials redacted]"; }
}
