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
} from '../types/api'
export const adminApi = {
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
