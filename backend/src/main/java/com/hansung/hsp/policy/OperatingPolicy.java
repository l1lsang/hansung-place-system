package com.hansung.hsp.policy;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity @Table(name = "space_operating_policies")
public class OperatingPolicy {
    @Id @Column(name = "space_id") private Long spaceId;
    @Column(nullable = false) private boolean enabled;
    @Enumerated(EnumType.STRING) @Column(name = "academic_period", nullable = false, length = 20)
    private OperatingPeriod academicPeriod;
    @Column(name = "exam_start_date") private LocalDate examStartDate;
    @Column(name = "exam_end_date") private LocalDate examEndDate;
    @Column(name = "updated_by", nullable = false) private Long updatedBy;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @ElementCollection
    @CollectionTable(name = "space_operating_hours", joinColumns = @JoinColumn(name = "space_id"))
    private List<OperatingHours> hours = new ArrayList<>();

    protected OperatingPolicy() {}
    OperatingPolicy(Long spaceId) { this.spaceId = spaceId; }
    void replace(OperatingPolicyInput input, Long adminId, Instant now) {
        enabled = input.enabled(); academicPeriod = input.academicPeriod();
        examStartDate = input.examStartDate(); examEndDate = input.examEndDate();
        updatedBy = adminId; updatedAt = now;
        hours.clear(); input.hours().forEach(h -> hours.add(new OperatingHours(h)));
    }
    public boolean isEnabled() { return enabled; }
    public OperatingPeriod getAcademicPeriod() { return academicPeriod; }
    public LocalDate getExamStartDate() { return examStartDate; }
    public LocalDate getExamEndDate() { return examEndDate; }
    public List<OperatingHours> getHours() { return hours; }
    OperatingPolicyResponse response() {
        return new OperatingPolicyResponse(spaceId, true, enabled, "Asia/Seoul", academicPeriod,
                examStartDate, examEndDate, updatedBy, updatedAt,
                hours.stream().sorted(Comparator.comparing(OperatingHours::getPeriod)
                        .thenComparingInt(OperatingHours::getDayOfWeek)).map(OperatingHours::response).toList());
    }
}
