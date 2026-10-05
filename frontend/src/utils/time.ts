import type { Reservation, TimeRange } from '../types/api'
const zone = 'Asia/Seoul'
export function today() {
  return new Intl.DateTimeFormat('sv-SE', { timeZone: zone }).format(new Date())
}
export function addDays(date: string, days: number) {
  const value = new Date(`${date}T12:00:00Z`)
  value.setUTCDate(value.getUTCDate() + days)
  return value.toISOString().slice(0, 10)
}
export function toInstant(date: string, time: string) {
  if (time === '24:00') return new Date(`${addDays(date, 1)}T00:00:00+09:00`).toISOString()
  return new Date(`${date}T${time}:00+09:00`).toISOString()
}
export function validDate(date: string) {
  return (
    /^\d{4}-\d{2}-\d{2}$/.test(date) &&
    !Number.isNaN(Date.parse(date)) &&
    new Date(date).toISOString().slice(0, 10) === date
  )
}
export function validTime(time: string) {
  return /^([01]\d|2[0-3]):[0-5]\d$/.test(time)
}
export function formatDate(value: string) {
  return new Intl.DateTimeFormat('ko-KR', {
    timeZone: zone,
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'short',
  }).format(new Date(value.length === 10 ? `${value}T12:00:00+09:00` : value))
}
export function formatTime(value: string) {
  return new Intl.DateTimeFormat('ko-KR', {
    timeZone: zone,
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).format(new Date(value))
}
export function formatRange(range: TimeRange) {
  return `${formatDate(range.startTime)} ${formatTime(range.startTime)} ~ ${formatDate(range.endTime)} ${formatTime(range.endTime)}`
}
export function covers(ranges: TimeRange[], start: string, end: string) {
  let cursor = Date.parse(start)
  const target = Date.parse(end)
  if (!Number.isFinite(cursor) || !(cursor < target)) return false
  for (const range of [...ranges].sort(
    (a, b) => Date.parse(a.startTime) - Date.parse(b.startTime),
  )) {
    if (Date.parse(range.startTime) > cursor) return false
    if (Date.parse(range.endTime) > cursor) cursor = Date.parse(range.endTime)
    if (cursor >= target) return true
  }
  return false
}
export function reservationState(r: Reservation, now = Date.now()) {
  return r.status === 'CANCELLED'
    ? '취소'
    : r.status === 'COMPLETED' || Date.parse(r.endedAt ?? r.endTime) <= now
      ? '종료'
      : Date.parse(r.startTime) <= now
        ? '이용 중'
        : '예정'
}
export function defaultSelection() {
  const now = new Date(Date.now() + 60 * 60_000)
  const date = new Intl.DateTimeFormat('sv-SE', { timeZone: zone }).format(now)
  const hour = Number(formatTime(now.toISOString()).slice(0, 2))
  return {
    date,
    start: `${String(hour).padStart(2, '0')}:00`,
    end: `${String(hour + 1).padStart(2, '0')}:00`,
  }
}
