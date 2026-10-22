import { request } from './http'

export interface Venue {
  id: string
  name: string
  location: string
  capacity: number
  openTime: string
  closeTime: string
  status: 'OPEN' | 'DISABLED'
}

interface VenuePage {
  items: Venue[]
  total: number
  page: number
  pageSize: number
}

export function getVenues(page = 1, pageSize = 10) {
  return request<VenuePage>(
    `/venues?page=${page}&pageSize=${pageSize}`,
  )
}
export function getVenue(id: string) {
  return request<Venue>(
    `/venues/${encodeURIComponent(id)}`,
  )
}

export interface CreateVenueRequest {
  name: string
  location: string
  capacity: number
  openTime: string
  closeTime: string
  status: 'OPEN' | 'DISABLED'
}

export function createVenue(data: CreateVenueRequest) {
  return request<Venue>('/venues', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}