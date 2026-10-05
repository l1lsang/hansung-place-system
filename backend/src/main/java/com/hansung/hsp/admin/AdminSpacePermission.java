package com.hansung.hsp.admin;

import jakarta.persistence.*;

@Entity
@Table(name = "admin_space_permissions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "space_id"}))
public class AdminSpacePermission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "space_id", nullable = false)
    private Long spaceId;

    protected AdminSpacePermission() {}
    public AdminSpacePermission(Long userId, Long spaceId) {
        this.userId = userId;
        this.spaceId = spaceId;
    }
}

