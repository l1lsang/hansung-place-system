package com.hansung.hsp.admin;

import com.hansung.hsp.common.*;
import com.hansung.hsp.space.*;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminSpaceService {
    private final AdminAccessService access;
    private final SpaceRepository spaces;
    private final SpaceService spaceService;
    private final AdminSpacePermissionRepository permissions;

    public AdminSpaceService(AdminAccessService access, SpaceRepository spaces, SpaceService spaceService,
            AdminSpacePermissionRepository permissions) {
        this.access = access;
        this.spaces = spaces;
        this.spaceService = spaceService;
        this.permissions = permissions;
    }

    public PageResponse<SpaceResponse> list(Long adminId, int page, int size) {
        access.requireAdmin(adminId);
        return PageResponse.from(spaces.findManagedSpaces(adminId,
                PageResponse.request(page, size, Sort.by("id"))).map(SpaceResponse::from));
    }

    @Transactional
    public SpaceResponse create(Long adminId, SpaceCreateRequest input) {
        access.requireAdmin(adminId);
        validateCapacity(input.minCapacity(), input.maxCapacity());
        var space = spaces.save(new Space(input.spaceCode(), input.name(), input.type(), input.location(),
                input.minCapacity(), input.maxCapacity(), input.venue(), input.facilities(),
                input.bookingEnabled() == null || input.bookingEnabled()));
        // A creator manages this newly created space only. Existing spaces need explicit grants.
        permissions.save(new AdminSpacePermission(adminId, space.getId()));
        return SpaceResponse.from(space);
    }

    @Transactional(timeout = 10)
    public SpaceResponse update(Long adminId, Long spaceId, SpacePatchRequest input) {
        access.requireSpace(adminId, spaceId);
        var space = spaceService.lock(spaceId);
        validateCapacity(input.minCapacity() == null ? space.getMinCapacity() : input.minCapacity(),
                input.maxCapacity() == null ? space.getMaxCapacity() : input.maxCapacity());
        space.update(input);
        return SpaceResponse.from(space);
    }

    private void validateCapacity(Integer min, Integer max) {
        if ((min != null && min < 1) || (max != null && max < 1)
                || (min != null && max != null && min > max)) {
            throw ApiException.badRequest("INVALID_CAPACITY", "수용 인원은 양수이며 최소 인원은 최대 인원 이하여야 합니다.");
        }
    }
}

