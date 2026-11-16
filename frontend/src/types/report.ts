export type ReportStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED'
export type TriggerType = 'MANUAL' | 'SCHEDULED'

export interface ReportRun {
  id: string
  triggerType: TriggerType
  rangeStart: string
  rangeEnd: string
  status: ReportStatus
  jobName: string | null
  attempt: number
  errorMessage: string | null
  createdAt: string
  startedAt: string | null
  finishedAt: string | null
}

export interface ReportResult {
  venueId: string
  venueName: string
  reservationCount: number
  totalMinutes: number
}

export interface ReportDetail extends ReportRun {
  results: ReportResult[]
}

export interface ReportRange {
  rangeStart: string
  rangeEnd: string
}
