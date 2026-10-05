import { query, request } from './client'
import type { Page, Reservation, ReservationInput } from '../types/api'
export const reservationsApi = {
  list: (page = 0, signal?: AbortSignal) =>
    request<Page<Reservation>>('/api/reservations' + query({ page, size: 20 }), { signal }),
  get: (id: number, signal?: AbortSignal) =>
    request<Reservation>(`/api/reservations/${id}`, { signal }),
  create: (body: ReservationInput) =>
    request<Reservation>('/api/reservations', { method: 'POST', body }),
  cancel: (id: number) => request<void>(`/api/reservations/${id}`, { method: 'DELETE' }),
}
