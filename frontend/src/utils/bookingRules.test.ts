import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { bookingRuleMessage } from './bookingRules'
import type { BookingRules } from '../types/api'

const rules: BookingRules = {
  spaceId: 1,
  configured: true,
  enabled: true,
  slotMinutes: 30,
  minDurationMinutes: 30,
  maxDurationMinutes: 180,
  advanceDays: 7,
  dailyMaxMinutes: 180,
  usageScope: 'VENUE',
  preventAdjacent: true,
  purposeRequired: true,
  instantUseMinutes: 180,
  updatedBy: 1,
  updatedAt: null,
}
beforeEach(() => {
  vi.useFakeTimers()
  vi.setSystemTime(new Date('2030-05-31T16:00:00Z'))
}) // June 1 KST
afterEach(() => vi.useRealTimers())
it('uses the KST day for the inclusive advance limit', () => {
  expect(bookingRuleMessage(rules, '2030-06-08T09:00:00+09:00', '2030-06-08T10:00:00+09:00')).toBe(
    '',
  )
  expect(
    bookingRuleMessage(rules, '2030-06-09T09:00:00+09:00', '2030-06-09T10:00:00+09:00'),
  ).toContain('7일')
})
it('accepts closing at midnight but rejects crossing into another day', () => {
  expect(bookingRuleMessage(rules, '2030-06-01T23:00:00+09:00', '2030-06-02T00:00:00+09:00')).toBe(
    '',
  )
  expect(
    bookingRuleMessage(rules, '2030-06-01T23:00:00+09:00', '2030-06-02T00:30:00+09:00'),
  ).toContain('같은 날짜')
})
it('rejects misaligned time and keeps unconfigured legacy behavior', () => {
  expect(
    bookingRuleMessage(rules, '2030-06-01T09:15:00+09:00', '2030-06-01T10:15:00+09:00'),
  ).toContain('30분 단위')
  expect(
    bookingRuleMessage(
      { ...rules, enabled: false },
      '2030-07-01T09:15:00+09:00',
      '2030-07-01T14:15:00+09:00',
    ),
  ).toBe('')
})
