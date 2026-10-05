package com.hansung.hsp.reservation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ReservationActionRepository extends JpaRepository<ReservationAction, Long> {
    List<ReservationAction> findByReservationIdOrderById(Long reservationId);
    List<ReservationAction> findByReservationIdInOrderById(List<Long> reservationIds);
}
