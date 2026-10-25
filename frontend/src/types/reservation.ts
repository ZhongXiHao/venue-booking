export type ReservationStatus = 'ACTIVE' | 'CANCELLED'

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
  status: ReservationStatus
}

export interface ReservationQuery {
  venueId?: string
  reservationDate?: string
  status?: ReservationStatus
  page?: number
  pageSize?: number
}
