package com.hansung.hsp.admin;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminSpacePermissionRepository extends JpaRepository<AdminSpacePermission, Long> {
    boolean existsByUserIdAndSpaceId(Long userId, Long spaceId);
}

