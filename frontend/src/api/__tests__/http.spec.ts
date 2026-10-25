import { describe, it, expect } from 'vitest'

import { ApiError, getFieldErrors, parseApiError } from '../http'

function validationError(fieldErrors: unknown) {
  return new ApiError(400, 'VALIDATION_ERROR', '请求参数校验失败', {
    fieldErrors,
  })
}

describe('getFieldErrors', () => {
  it('maps fieldErrors by field name', () => {
    const error = validationError([
      { field: 'startTime', message: '开始时间必须为 30 分钟整点' },
    ])

    expect(getFieldErrors(error)).toEqual({
      startTime: '开始时间必须为 30 分钟整点',
    })
  })

  it('joins several messages for the same field', () => {
    const error = validationError([
      { field: 'name', message: 'a' },
      { field: 'name', message: 'b' },
    ])

    expect(getFieldErrors(error)).toEqual({ name: 'a；b' })
  })

  it('returns an empty object when there is nothing usable', () => {
    expect(getFieldErrors(new Error('boom'))).toEqual({})
    expect(getFieldErrors(new ApiError(409, 'RESERVATION_CONFLICT', 'x'))).toEqual({})
    expect(getFieldErrors(validationError('oops'))).toEqual({})
    expect(getFieldErrors(validationError([null, { field: 1 }]))).toEqual({})
  })
})

describe('parseApiError', () => {
  it('keeps known fields separate and folds unknown ones into the message', () => {
    const error = validationError([
      { field: 'startTime', message: '时间不合法' },
      { field: 'venueId', message: '场地不能为空' },
    ])

    expect(parseApiError(error, '失败', ['startTime'])).toEqual({
      message: '请求参数校验失败（venueId：场地不能为空）',
      fieldErrors: { startTime: '时间不合法' },
    })
  })

  it('falls back for non-Error values', () => {
    expect(parseApiError('x', '预约提交失败', [])).toEqual({
      message: '预约提交失败',
      fieldErrors: {},
    })
  })
})
