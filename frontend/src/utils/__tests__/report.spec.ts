import { describe, expect, it } from 'vitest'
import { formatReportTime, recentReportRange, validateReportRange } from '../report'

describe('report dates', () => {
  it('uses the Shanghai calendar date at the UTC day boundary', () => {
    expect(recentReportRange(new Date('2026-11-14T17:00:00Z'))).toEqual({
      rangeStart: '2026-11-09',
      rangeEnd: '2026-11-15',
    })
    expect(formatReportTime('2026-11-14T17:00:00Z')).toContain('01:00:00')
    expect(formatReportTime(null)).toBe('—')
  })

  it('allows 366 inclusive days but rejects 367, reversed and invalid dates', () => {
    expect(validateReportRange({ rangeStart: '2024-01-01', rangeEnd: '2024-12-31' })).toEqual({})
    expect(
      validateReportRange({ rangeStart: '2024-01-01', rangeEnd: '2025-01-01' }).rangeEnd,
    ).toBeTruthy()
    expect(
      validateReportRange({ rangeStart: '2026-11-15', rangeEnd: '2026-11-14' }).rangeEnd,
    ).toBeTruthy()
    expect(
      validateReportRange({ rangeStart: '2026-02-30', rangeEnd: '2026-03-01' }).rangeStart,
    ).toBeTruthy()
    expect(validateReportRange({ rangeStart: '', rangeEnd: '' }).rangeStart).toBeTruthy()
    expect(validateReportRange({ rangeStart: '2099-01-01', rangeEnd: '2099-01-01' })).toEqual({})
  })
})
