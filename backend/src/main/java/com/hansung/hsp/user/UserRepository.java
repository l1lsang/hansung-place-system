package com.hansung.hsp.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByStudentId(String studentId);
    // Serialize per-user booking checks without blocking FK key-share locks
    // taken by cancellation audits and blocks while they hold a space lock.
    @Query(value = "select * from users where id = :id for no key update", nativeQuery = true)
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
