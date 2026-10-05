package com.hansung.hsp.policy;

import com.hansung.hsp.admin.AdminAccessService;
import com.hansung.hsp.common.*;
import com.hansung.hsp.space.SpaceService;
import java.time.Clock;
import java.util.HashSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly = true)
public class OperatingPolicyService {
    private final OperatingPolicyRepository policies;
    private final SpaceService spaces;
    private final AdminAccessService access;
    private final Clock clock;
    public OperatingPolicyService(OperatingPolicyRepository policies, SpaceService spaces,
            AdminAccessService access, Clock clock) {
        this.policies = policies; this.spaces = spaces; this.access = access; this.clock = clock;
    }
    public OperatingPolicyResponse get(Long spaceId) {
        spaces.require(spaceId);
        return policies.findById(spaceId).map(OperatingPolicy::response)
                .orElseGet(() -> OperatingPolicyResponse.unconfigured(spaceId));
    }
    public OperatingPolicy find(Long spaceId) { return policies.findById(spaceId).orElse(null); }

    @Transactional(timeout = 10)
    public OperatingPolicyResponse replace(Long adminId, Long spaceId, OperatingPolicyInput input) {
        // Same lock as reservation creation: policy changes and booking checks are serialized.
        spaces.lock(spaceId);
        access.requireSpace(adminId, spaceId);
        validate(input);
        var policy = policies.findById(spaceId).orElseGet(() -> new OperatingPolicy(spaceId));
        policy.replace(input, adminId, clock.instant());
        return policies.save(policy).response();
    }
    static void validate(OperatingPolicyInput input) {
        if (input.academicPeriod() == OperatingPeriod.EXAM) fail("기본 운영 기준은 학기 또는 방학이어야 합니다.");
        if ((input.examStartDate() == null) != (input.examEndDate() == null)) fail("시험기간 시작일과 종료일을 함께 입력해주세요.");
        if (input.examStartDate() != null && (input.examStartDate().isAfter(input.examEndDate())
                || input.examStartDate().getYear() < 2000 || input.examEndDate().getYear() > 9998)) {
            fail("시험기간은 2000~9998년 사이이며 시작일이 종료일보다 늦을 수 없습니다.");
        }
        var keys = new HashSet<String>();
        for (var h : input.hours()) {
            if (!keys.add(h.period() + ":" + h.dayOfWeek())) fail("운영 기준별 요일은 한 번씩 입력해주세요.");
            if (h.closed()) {
                if (h.openTime() != null || h.closeTime() != null) fail("휴무일에는 운영시간을 비워주세요.");
            } else if (h.openTime() == null || h.closeTime() == null
                    || OperatingHours.minute(h.openTime()) >= OperatingHours.minute(h.closeTime())) {
                fail("운영 시작 시간은 종료 시간보다 빨라야 합니다. 자정 종료는 24:00으로 입력해주세요.");
            }
        }
        if (keys.size() != 21) fail("학기·방학·시험기간 각각 월~일 7일의 운영시간을 입력해주세요.");
    }
    private static void fail(String message) { throw ApiException.badRequest("INVALID_OPERATING_POLICY", message); }
    public void validateReservation(Long spaceId, TimeRange range) {
        if (!OperatingCalendar.allows(find(spaceId), range)) {
            throw ApiException.conflict("OUTSIDE_OPERATING_HOURS", "선택한 시간이 공간 운영시간에 포함되지 않습니다.");
        }
    }
}
