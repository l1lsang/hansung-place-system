import { query, request } from './client'
import type {
  Availability,
  OperatingPolicy,
  Page,
  Seat,
  Space,
  SeatAvailability,
  BookingRules,
} from '../types/api'
export interface SpaceFilters {
  q?: string
  venue?: string
  type?: string
  minCapacity?: number
  bookingEnabled?: boolean
  page?: number
  size?: number
}
export const spacesApi = {
  async seatAvailability(
    id: number,
    start: string | null,
    end: string | null,
    signal?: AbortSignal,
  ): Promise<SeatAvailability> {
    const path =
      start && end ? `/api/spaces/${id}/seats/availability` : `/api/spaces/${id}/seat-status`
    const parameters = start && end ? { startTime: start, endTime: end } : {}
    const first = await request<SeatAvailability>(
      path + query({ ...parameters, page: 0, size: 100 }),
      { signal },
    )
    const content = [...first.seats.content]
    for (let page = 1; page < first.seats.totalPages; page++) {
      signal?.throwIfAborted()
      const next = await request<SeatAvailability>(
        path + query({ ...parameters, page, size: 100 }),
        { signal },
      )
      content.push(...next.seats.content)
    }
    return { ...first, seats: { ...first.seats, content } }
  },
  async allSeats(id: number, signal?: AbortSignal): Promise<Page<Seat>> {
    const first = await spacesApi.seats(id, 0, 100, signal)
    const content = [...first.content]
    for (let page = 1; page < first.totalPages; page++) {
      signal?.throwIfAborted()
      content.push(...(await spacesApi.seats(id, page, 100, signal)).content)
    }
    return { ...first, content }
  },
  async catalog(bookingEnabled: boolean, signal?: AbortSignal): Promise<Space[]> {
    const first = await spacesApi.list({ bookingEnabled, size: 100 }, signal)
    const items = [...first.content]
    for (let page = 1; page < first.totalPages; page++) {
      signal?.throwIfAborted()
      items.push(...(await spacesApi.list({ bookingEnabled, size: 100, page }, signal)).content)
    }
    return items
  },
  list: (filters: SpaceFilters, signal?: AbortSignal) =>
    request<Page<Space>>('/api/spaces' + query({ ...filters }), { signal }),
  get: (id: number, signal?: AbortSignal) => request<Space>(`/api/spaces/${id}`, { signal }),
  seats: (id: number, page = 0, size = 36, signal?: AbortSignal) =>
    request<Page<Seat>>(`/api/spaces/${id}/seats` + query({ page, size }), { signal }),
  availability: (
    id: number,
    startTime: string,
    endTime: string,
    seatId?: number,
    signal?: AbortSignal,
  ) =>
    request<Availability>(
      `/api/spaces/${id}/availability` + query({ startTime, endTime, seatId }),
      { signal },
    ),
  policy: (id: number, signal?: AbortSignal) =>
    request<OperatingPolicy>(`/api/spaces/${id}/policy`, { signal }),
  bookingRules: (id: number, signal?: AbortSignal) =>
    request<BookingRules>(`/api/spaces/${id}/booking-rules`, { signal }),
}
