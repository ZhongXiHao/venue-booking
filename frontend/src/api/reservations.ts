import { request } from './http'

export interface CreateReservationRequest {
  venueId: string
  bookerName: string
  reservationDate: string
  startTime: string
  endTime: string
  purpose: string
}

export interface Reservation extends CreateReservationRequest {
  id: string
  venueName: string
  status: 'ACTIVE' | 'CANCELLED'
}

export function createReservation(
  data: CreateReservationRequest,
) {
  return request<Reservation>('/reservations', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export interface ReservationPage {
  items: Reservation[]
  total: number
  page: number
  pageSize: number
}

export interface ReservationQuery {
  venueId?: string
  date?: string
  status?: Reservation['status']
  page?: number
  pageSize?: number
}

export function getReservations(
  params: ReservationQuery = {},
) {
  const query = new URLSearchParams({
    page: String(params.page ?? 1),
    pageSize: String(params.pageSize ?? 10),
  })

  if (params.venueId?.trim()) {
    query.set('venueId', params.venueId.trim())
  }

  if (params.date) {
    query.set('date', params.date)
  }

  if (params.status) {
    query.set('status', params.status)
  }

  return request<ReservationPage>(
    `/reservations?${query.toString()}`,
  )
}

export function cancelReservation(id: string) {
  return request<Reservation>(
    `/reservations/${encodeURIComponent(id)}/cancel`,
    {
      method: 'POST',
    },
  )
}

export function getReservation(id: string) {
  return request<Reservation>(
    `/reservations/${encodeURIComponent(id)}`,
  )
}