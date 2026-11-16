import { request } from './http'
import type { PageResult } from '../types/api'
import type {
  ReportDetail,
  ReportRange,
  ReportRun,
  ReportStatus,
  TriggerType,
} from '../types/report'

export function createReport(range: ReportRange) {
  return request<ReportRun>('/reports', { method: 'POST', body: JSON.stringify(range) })
}

export function getReports(
  params: {
    page?: number
    pageSize?: number
    status?: ReportStatus | ''
    triggerType?: TriggerType | ''
  } = {},
) {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') query.set(key, String(value))
  }
  return request<PageResult<ReportRun>>(`/reports?${query}`)
}

export function getReport(id: string, signal?: AbortSignal) {
  return request<ReportDetail>(`/reports/${encodeURIComponent(id)}`, { signal })
}
