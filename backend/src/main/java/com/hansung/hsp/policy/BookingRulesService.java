package com.hansung.hsp.policy;

import com.hansung.hsp.admin.AdminAccessService;
import com.hansung.hsp.common.*;
import com.hansung.hsp.reservation.*;
import com.hansung.hsp.space.*;
import java.time.*;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly = true)
public class BookingRulesService {
    private final BookingRulesRepository rules;
    private final SpaceService spaces;
    private final AdminAccessService access;
    private final ReservationRepository reservations;
    private final Clock clock;
    public BookingRulesService(BookingRulesRepository rules, SpaceService spaces, AdminAccessService access,
            ReservationRepository reservations, Clock clock) {
        this.rules = rules; this.spaces = spaces; this.access = access;
        this.reservations = reservations; this.clock = clock;
    }
    public BookingRulesResponse get(Long spaceId) { spaces.require(spaceId); return find(spaceId); }
    public BookingRulesResponse find(Long spaceId) {
        return rules.findById(spaceId).map(BookingRules::response).orElseGet(() -> BookingRulesResponse.unconfigured(spaceId));
    }
    @Transactional(timeout = 10)
    public BookingRulesResponse replace(Long adminId, Long spaceId, BookingRulesInput input) {
        spaces.lock(spaceId); access.requireSpace(adminId, spaceId);
        if (!List.of(15, 30, 60).contains(input.slotMinutes())
                || input.minDurationMinutes() > input.maxDurationMinutes()
                || input.minDurationMinutes() % input.slotMinutes() != 0
                || input.maxDurationMinutes() % input.slotMinutes() != 0
                || (input.dailyMaxMinutes() != null && input.dailyMaxMinutes() < input.minDurationMinutes())) {
            throw ApiException.badRequest("INVALID_BOOKING_RULES", "이용시간은 예약 단위의 배수이며 최소 시간은 최대·일일 한도 이하여야 합니다.");
        }
        var entity = rules.findById(spaceId).orElseGet(() -> new BookingRules(spaceId));
        entity.replace(input, adminId, clock.instant());
        return rules.save(entity).response();
    }
    // Caller holds the owner's row lock followed by the space lock. A user cannot
    // race their daily quota by submitting reservations for different rooms.
    public void validate(Long userId, Space space, ReservationCreateRequest input) {
        var rule = find(space.getId());
        if (!rule.enabled()) return;
        var start = input.startTime().atZone(OperatingCalendar.ZONE);
        var end = input.endTime().atZone(OperatingCalendar.ZONE);
        long duration = Duration.between(input.startTime(), input.endTime()).getSeconds();
        if (duration < rule.minDurationMinutes() * 60L || duration > rule.maxDurationMinutes() * 60L
                || start.getSecond() != 0 || start.getNano() != 0 || end.getSecond() != 0 || end.getNano() != 0
                || start.getMinute() % rule.slotMinutes() != 0 || end.getMinute() % rule.slotMinutes() != 0) {
            throw ApiException.badRequest("BOOKING_DURATION_LIMIT", "예약 단위 또는 최소·최대 이용시간 조건에 맞지 않습니다.");
        }
        var date = start.toLocalDate();
        if (!date.equals(input.endTime().minusNanos(1).atZone(OperatingCalendar.ZONE).toLocalDate())) {
            throw ApiException.badRequest("BOOKING_SINGLE_DAY_REQUIRED", "예약은 같은 날짜 안에서만 가능합니다.");
        }
        if (date.isAfter(LocalDate.now(clock.withZone(OperatingCalendar.ZONE)).plusDays(rule.advanceDays()))) {
            throw ApiException.badRequest("BOOKING_ADVANCE_LIMIT", "예약 가능한 날짜 범위를 초과했습니다.");
        }
        if (rule.purposeRequired() && (input.purpose() == null || input.purpose().isBlank())) {
            throw ApiException.badRequest("PURPOSE_REQUIRED", "이용 목적을 입력해주세요.");
        }
        var midnight = date.atStartOfDay(OperatingCalendar.ZONE).toInstant();
        var next = date.plusDays(1).atStartOfDay(OperatingCalendar.ZONE).toInstant();
        var existing = reservations.findUserUsage(userId, space.getId(), space.getVenue(),
                rule.usageScope() == BookingRulesInput.UsageScope.VENUE, midnight, next);
        long seconds = existing.stream().mapToLong(r -> Duration.between(
                r.getStartTime().isBefore(midnight) ? midnight : r.getStartTime(),
                r.effectiveEnd().isAfter(next) ? next : r.effectiveEnd()).getSeconds()).sum();
        if (rule.dailyMaxMinutes() != null && seconds + duration > rule.dailyMaxMinutes() * 60L) {
            throw ApiException.conflict("DAILY_USAGE_LIMIT", "해당 공간·시설의 하루 이용시간 한도를 초과했습니다.");
        }
        if (rule.preventAdjacent() && existing.stream().anyMatch(r -> !input.startTime().isAfter(r.effectiveEnd())
                && !input.endTime().isBefore(r.getStartTime()))) {
            throw ApiException.conflict("ADJACENT_BOOKING_NOT_ALLOWED", "동일 신청자의 중복 또는 연속 예약은 허용되지 않습니다.");
        }
    }
}
