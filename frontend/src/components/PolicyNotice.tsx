import type { OperatingPolicy } from '../types/api'
import { weekdays } from '../types/api'
const days = ['월', '화', '수', '목', '금', '토', '일']
export function PolicyNotice({ policy, date }: { policy: OperatingPolicy; date: string }) {
  if (!policy.configured || !policy.enabled)
    return (
      <p className="notice small">
        운영시간 정책이 아직 적용되지 않은 공간입니다. 방문 전 시설 이용 안내를 확인해주세요.
      </p>
    )
  const exam =
    policy.examStartDate &&
    policy.examEndDate &&
    date >= policy.examStartDate &&
    date <= policy.examEndDate
  const period = exam ? 'EXAM' : policy.academicPeriod
  return (
    <details className="policy-notice">
      <summary>
        {exam ? '시험기간 특별 운영' : period === 'VACATION' ? '방학 운영시간' : '학기 운영시간'}{' '}
        확인
      </summary>
      <div className="hours-list mt-3">
        {weekdays.map((day, i) => {
          const h = policy.hours.find((h) => h.period === period && h.dayOfWeek === day)
          return (
            <div key={day}>
              <span>{days[i]}요일</span>
              <strong>{!h || h.closed ? '휴무' : `${h.openTime} ~ ${h.closeTime}`}</strong>
            </div>
          )
        })}
      </div>
      {policy.examStartDate && (
        <p className="small muted mt-3">
          시험기간: {policy.examStartDate} ~ {policy.examEndDate}
        </p>
      )}
    </details>
  )
}
