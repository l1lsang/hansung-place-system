package com.hansung.hsp.reservation;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationMemberRepository extends JpaRepository<ReservationMember, Long> {
    List<ReservationMember> findByReservationIdOrderById(Long reservationId);
    List<ReservationMember> findByReservationIdInOrderById(Collection<Long> reservationIds);
}

