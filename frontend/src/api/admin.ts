import { query, request } from './client'
import type {
  OperatingPolicy,
  Page,
  PolicyInput,
  Reservation,
  Space,
  SpaceBlock,
  SpaceInput,
  TimeRange,
  BookingRules,
  BookingRulesInput,
  AdminSummary,
} from '../types/api'
export const adminApi = {
  summary: (date: string, signal?: AbortSignal) =>
    request<AdminSummary>('/api/admin/summary' + query({ date }), { signal }),
  cancelReservation: (id: number, reason: string) =>
    request<Reservation>(`/api/admin/reservations/${id}/cancel`, {
      method: 'POST',
      body: { reason },
    }),
  saveBookingRules: (id: number, body: BookingRulesInput) =>
    request<BookingRules>(`/api/admin/spaces/${id}/booking-rules`, { method: 'PUT', body }),
  spaces: (page = 0, signal?: AbortSignal) =>
    request<Page<Space>>('/api/admin/spaces' + query({ page, size: 20 }), { signal }),
  createSpace: (body: SpaceInput) => request<Space>('/api/admin/spaces', { method: 'POST', body }),
  updateSpace: (id: number, body: Partial<SpaceInput>) =>
    request<Space>(`/api/admin/spaces/${id}`, { method: 'PATCH', body }),
  reservations: (page = 0, spaceId?: number, signal?: AbortSignal) =>
    request<Page<Reservation>>('/api/admin/reservations' + query({ page, size: 20, spaceId }), {
      signal,
    }),
  reservation: (id: number, signal?: AbortSignal) =>
    request<Reservation>(`/api/admin/reservations/${id}`, { signal }),
  blocks: (id: number, page = 0, signal?: AbortSignal) =>
    request<Page<SpaceBlock>>(`/api/admin/spaces/${id}/blocks` + query({ page, size: 20 }), {
      signal,
    }),
  createBlock: (id: number, body: TimeRange & { reason: string | null }) =>
    request<SpaceBlock>(`/api/admin/spaces/${id}/blocks`, { method: 'POST', body }),
  releaseBlock: (id: number) =>
    request<void>(`/api/admin/space-blocks/${id}`, { method: 'DELETE' }),
  savePolicy: (id: number, body: PolicyInput) =>
    request<OperatingPolicy>(`/api/admin/spaces/${id}/policy`, { method: 'PUT', body }),
}
