package com.hansung.hsp.space;

import java.time.Instant;
import jakarta.validation.constraints.*;

public record SpaceBlockCreateRequest(@NotNull Instant startTime, @NotNull Instant endTime,
                                      @Size(max = 100) String reason) {}

