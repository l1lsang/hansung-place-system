package com.hansung.hsp.space;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SpaceBlockRepository extends JpaRepository<SpaceBlock, Long> {
    String OVERLAP = """
            b.spaceId = :spaceId and b.cancelledAt is null
            and b.startTime < :endTime and b.endTime > :startTime
            """;
    @Query("select (count(b) > 0) from SpaceBlock b where " + OVERLAP)
    boolean existsOverlapping(@Param("spaceId") Long spaceId, @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime);

    @Query("select b from SpaceBlock b where " + OVERLAP + " order by b.startTime")
    List<SpaceBlock> findOverlapping(@Param("spaceId") Long spaceId, @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime);

    Page<SpaceBlock> findBySpaceIdAndCancelledAtIsNull(Long spaceId, Pageable pageable);

    @Query("select b.spaceId from SpaceBlock b where b.id = :id")
    Optional<Long> findSpaceIdById(@Param("id") Long id);
}

