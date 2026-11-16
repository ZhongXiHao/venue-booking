import type { ReportRange, ReportStatus } from '../types/report'

export const reportStatusText: Record<ReportStatus, string> = {
  PENDING: '处理中',
  RUNNING: '处理中',
  SUCCESS: '成功',
  FAILED: '失败',
}

export function isReportProcessing(status: ReportStatus) {
  return status === 'PENDING' || status === 'RUNNING'
}

const shanghaiTime = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
})

export function formatReportTime(value: string | null) {
  return value ? shanghaiTime.format(new Date(value)) : '—'
}

export function recentReportRange(now = new Date()): ReportRange {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(now)
  const part = (type: string) => parts.find((p) => p.type === type)!.value
  const rangeEnd = `${part('year')}-${part('month')}-${part('day')}`
  const start = new Date(`${rangeEnd}T00:00:00Z`)
  start.setUTCDate(start.getUTCDate() - 6)
  return { rangeStart: start.toISOString().slice(0, 10), rangeEnd }
}

export function validateReportRange(range: ReportRange): Record<string, string> {
  const errors: Record<string, string> = {}
  for (const key of ['rangeStart', 'rangeEnd'] as const) {
    const value = range[key]
    const parsed = new Date(`${value}T00:00:00Z`)
    if (
      !/^\d{4}-\d{2}-\d{2}$/.test(value) ||
      !Number.isFinite(parsed.getTime()) ||
      parsed.toISOString().slice(0, 10) !== value
    ) {
      errors[key] = '请选择有效日期'
    }
  }
  if (Object.keys(errors).length) return errors
  const days = (Date.parse(range.rangeEnd) - Date.parse(range.rangeStart)) / 86_400_000
  if (days < 0) errors.rangeEnd = '结束日期不能早于开始日期'
  // 与当前 ReportServiceImpl 一致：包含首尾，最多 366 天。
  else if (days > 365) errors.rangeEnd = '统计范围含首尾最多 366 天'
  return errors
}
