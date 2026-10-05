package com.hansung.hsp.space;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SpaceRepository extends JpaRepository<Space, Long>, JpaSpecificationExecutor<Space> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select s from Space s where s.id = :id")
    Optional<Space> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select s from Space s where exists (
                select p.id from AdminSpacePermission p where p.userId = :adminId and p.spaceId = s.id
            )
            """)
    Page<Space> findManagedSpaces(@Param("adminId") Long adminId, Pageable pageable);
}

