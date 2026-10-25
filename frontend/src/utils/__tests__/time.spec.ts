import { describe, it, expect } from 'vitest'

import { isHalfHourSharp } from '../time'

describe('isHalfHourSharp', () => {
  it('accepts :00 and :30', () => {
    expect(isHalfHourSharp('14:00')).toBe(true)
    expect(isHalfHourSharp('14:30')).toBe(true)
    expect(isHalfHourSharp('08:30:00')).toBe(true)
  })

  it('rejects other minutes and non-zero seconds', () => {
    expect(isHalfHourSharp('14:15')).toBe(false)
    expect(isHalfHourSharp('14:01')).toBe(false)
    expect(isHalfHourSharp('14:00:30')).toBe(false)
  })

  it('rejects malformed values', () => {
    expect(isHalfHourSharp('')).toBe(false)
    expect(isHalfHourSharp('14')).toBe(false)
    expect(isHalfHourSharp('abc')).toBe(false)
  })
})
