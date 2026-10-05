package com.hansung.hsp.policy;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "space_booking_rules")
public class BookingRules {
    @Id @Column(name = "space_id") private Long spaceId;
    @Column(nullable = false) private boolean enabled;
    @Column(name = "slot_minutes", nullable = false) private int slotMinutes;
    @Column(name = "min_duration_minutes", nullable = false) private int minDurationMinutes;
    @Column(name = "max_duration_minutes", nullable = false) private int maxDurationMinutes;
    @Column(name = "advance_days", nullable = false) private int advanceDays;
    @Column(name = "daily_max_minutes") private Integer dailyMaxMinutes;
    @Enumerated(EnumType.STRING) @Column(name = "usage_scope", nullable = false, length = 10)
    private BookingRulesInput.UsageScope usageScope;
    @Column(name = "prevent_adjacent", nullable = false) private boolean preventAdjacent;
    @Column(name = "purpose_required", nullable = false) private boolean purposeRequired;
    @Column(name = "instant_use_minutes", nullable = false) private int instantUseMinutes;
    @Column(name = "updated_by", nullable = false) private Long updatedBy;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected BookingRules() {}
    public BookingRules(Long spaceId) { this.spaceId = spaceId; }
    public void replace(BookingRulesInput input, Long adminId, Instant now) {
        enabled = input.enabled(); slotMinutes = input.slotMinutes();
        minDurationMinutes = input.minDurationMinutes(); maxDurationMinutes = input.maxDurationMinutes();
        advanceDays = input.advanceDays(); dailyMaxMinutes = input.dailyMaxMinutes();
        usageScope = input.usageScope(); preventAdjacent = input.preventAdjacent();
        purposeRequired = input.purposeRequired(); instantUseMinutes = input.instantUseMinutes();
        updatedBy = adminId; updatedAt = now;
    }
    public BookingRulesResponse response() {
        return new BookingRulesResponse(spaceId, true, enabled, slotMinutes, minDurationMinutes,
                maxDurationMinutes, advanceDays, dailyMaxMinutes, usageScope, preventAdjacent,
                purposeRequired, instantUseMinutes, updatedBy, updatedAt);
    }
}
