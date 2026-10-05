package com.hansung.hsp.admin;

import com.hansung.hsp.policy.OperatingCalendar;
import com.hansung.hsp.reservation.ReservationRepository;
import com.hansung.hsp.space.SpaceRepository;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class AdminSummaryService {
    private final AdminAccessService access;
    private final ReservationRepository reservations;
    private final SpaceRepository spaces;
    private final Clock clock;
    public AdminSummaryService(AdminAccessService access, ReservationRepository reservations,
            SpaceRepository spaces, Clock clock) {
        this.access = access; this.reservations = reservations; this.spaces = spaces; this.clock = clock;
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Summary get(Long adminId, LocalDate selectedDate) {
        access.requireAdmin(adminId);
        var date = selectedDate == null ? LocalDate.now(clock.withZone(OperatingCalendar.ZONE)) : selectedDate;
        var start = date.atStartOfDay(OperatingCalendar.ZONE).toInstant();
        var end = date.plusDays(1).atStartOfDay(OperatingCalendar.ZONE).toInstant();
        var now = clock.instant();
        var list = reservations.findManagedDuring(adminId, start, end);
        long seconds = list.stream().mapToLong(r -> Math.max(0, Duration.between(
                r.getStartTime().isBefore(start) ? start : r.getStartTime(),
                r.effectiveEnd().isAfter(end) ? end : r.effectiveEnd()).getSeconds())).sum();
        long active = list.stream().filter(r -> !r.getStartTime().isAfter(now) && r.effectiveEnd().isAfter(now)).count();
        return new Summary(date, "Asia/Seoul", now, spaces.countManaged(adminId, false),
                spaces.countManaged(adminId, true), list.size(), active, seconds / 60.0);
    }
    public record Summary(LocalDate date, String timeZone, Instant observedAt, long managedSpaces,
            long disabledSpaces, long reservationCount, long activeCount, double reservedMinutes) {}
}
