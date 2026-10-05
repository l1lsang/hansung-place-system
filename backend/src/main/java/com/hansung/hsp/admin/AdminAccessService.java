package com.hansung.hsp.admin;

import com.hansung.hsp.common.ForbiddenException;
import com.hansung.hsp.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminAccessService {
    private final UserRepository users;
    private final AdminSpacePermissionRepository permissions;
    public AdminAccessService(UserRepository users, AdminSpacePermissionRepository permissions) {
        this.users = users;
        this.permissions = permissions;
    }

    public void requireAdmin(Long userId) {
        // Check the current DB role as well as the HTTP security rule, even for an existing session.
        if (!users.findById(userId).map(user -> "ADMIN".equals(user.getRole())).orElse(false)) {
            throw new ForbiddenException();
        }
    }

    public void requireSpace(Long userId, Long spaceId) {
        requireAdmin(userId);
        if (!permissions.existsByUserIdAndSpaceId(userId, spaceId)) {
            throw new ForbiddenException();
        }
    }
}

