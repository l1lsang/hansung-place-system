package com.hansung.hsp.space;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "space_blocks")
public class SpaceBlock {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "space_id", nullable = false)
    private Long spaceId;
    @Column(name = "created_by", nullable = false)
    private Long createdBy;
    @Column(name = "start_time", nullable = false)
    private Instant startTime;
    @Column(name = "end_time", nullable = false)
    private Instant endTime;
    @Column(length = 100)
    private String reason;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    protected SpaceBlock() {}
    public SpaceBlock(Long spaceId, Long createdBy, Instant startTime, Instant endTime, String reason, Instant createdAt) {
        this.spaceId = spaceId;
        this.createdBy = createdBy;
        this.startTime = startTime;
        this.endTime = endTime;
        this.reason = reason;
        this.createdAt = createdAt;
    }
    public void cancel(Instant now) { cancelledAt = now; }
    public Long getId() { return id; }
    public Long getSpaceId() { return spaceId; }
    public Long getCreatedBy() { return createdBy; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCancelledAt() { return cancelledAt; }
}

