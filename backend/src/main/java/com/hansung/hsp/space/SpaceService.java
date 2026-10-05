package com.hansung.hsp.space;

import com.hansung.hsp.common.*;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
@Transactional(readOnly = true)
public class SpaceService {
    private final SpaceRepository spaces;
    public SpaceService(SpaceRepository spaces) { this.spaces = spaces; }

    public PageResponse<SpaceResponse> list(String venue, String type, Boolean bookingEnabled,
            Integer minCapacity, String query, int page, int size) {
        if (minCapacity != null && minCapacity < 1) {
            throw ApiException.badRequest("INVALID_CAPACITY", "인원은 1 이상이어야 합니다.");
        }
        var results = spaces.findAll(SpaceSpecifications.filter(venue, type, bookingEnabled, minCapacity, query),
                PageResponse.request(page, size, Sort.by("id")));
        return PageResponse.from(results.map(SpaceResponse::from));
    }

    public SpaceResponse get(Long id) { return SpaceResponse.from(require(id)); }

    public Space require(Long id) {
        return spaces.findById(id).orElseThrow(() -> new ResourceNotFoundException(
                "SPACE_NOT_FOUND", "존재하지 않는 공간입니다."));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Space lock(Long id) {
        return spaces.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException(
                "SPACE_NOT_FOUND", "존재하지 않는 공간입니다."));
    }
}
