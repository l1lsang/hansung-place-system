import type { BookingRules } from '../types/api'
import { addDays, today } from './time'

export function bookingRuleMessage(rule: BookingRules | undefined, start: string, end: string) {
  if (!rule?.enabled) return ''
  const duration = (Date.parse(end) - Date.parse(start)) / 60_000
  if (
    duration < rule.minDurationMinutes ||
    duration > rule.maxDurationMinutes ||
    Date.parse(start) % (rule.slotMinutes * 60_000) ||
    Date.parse(end) % (rule.slotMinutes * 60_000)
  ) {
    return `${rule.slotMinutes}분 단위로 ${rule.minDurationMinutes}~${rule.maxDurationMinutes}분을 선택해주세요.`
  }
  const formatter = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Seoul' })
  const date = formatter.format(new Date(start))
  if (date !== formatter.format(new Date(Date.parse(end) - 1)))
    return '같은 날짜 안에서 이용시간을 선택해주세요.'
  if (date > addDays(today(), rule.advanceDays))
    return `오늘부터 ${rule.advanceDays}일 뒤까지 예약할 수 있습니다.`
  return ''
}
