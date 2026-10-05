import { describe, expect, it } from 'vitest'
import { addDays, covers, toInstant, validDate } from './time'
describe('campus time and availability', () => {
  it('converts KST independently of the computer time zone and supports midnight', () => {
    expect(toInstant('2030-06-01', '09:00')).toBe('2030-06-01T00:00:00.000Z')
    expect(toInstant('2030-06-01', '24:00')).toBe('2030-06-01T15:00:00.000Z')
    expect(addDays('2030-12-31', 1)).toBe('2031-01-01')
  })
  it('requires full coverage including adjacent days and rejects a gap', () => {
    const a = toInstant('2030-06-01', '09:00'),
      b = toInstant('2030-06-01', '10:00'),
      c = toInstant('2030-06-01', '11:00')
    expect(
      covers(
        [
          { startTime: a, endTime: b },
          { startTime: b, endTime: c },
        ],
        a,
        c,
      ),
    ).toBe(true)
    expect(covers([{ startTime: a, endTime: b }], a, c)).toBe(false)
    expect(covers([], a, a)).toBe(false)
  })
  it('rejects nonexistent calendar dates in URLs', () => {
    expect(validDate('2030-02-30')).toBe(false)
    expect(validDate('2030-06-01')).toBe(true)
  })
})
