package com.hansung.hsp.space;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    Page<Seat> findBySpaceId(Long spaceId, Pageable pageable);
    boolean existsBySpaceId(Long spaceId);
    long countBySpaceId(Long spaceId);
}
