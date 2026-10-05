import { useState } from 'react'
import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { spacesApi } from '../api/spaces'
import { reservationsApi } from '../api/reservations'
import { SeatPicker } from '../components/SeatPicker'
import { BookingForm } from '../components/BookingForm'
import { ReadingRoomSeatMap } from '../components/ReadingRoomSeatMap'
import { AuthContext, type AuthValue } from '../hooks/authContext'
import type { Availability, Page, Seat, Space } from '../types/api'
import type { ReactNode } from 'react'

const start = '2035-06-01T01:00:00.000Z'
const end = '2035-06-01T02:00:00.000Z'
const seats: Seat[] = [
  { id: 9001, spaceId: 1, seatNumber: '001', status: 'AVAILABLE' },
  { id: 9010, spaceId: 1, seatNumber: '010', status: 'AVAILABLE' },
  { id: 9075, spaceId: 1, seatNumber: '075', status: 'DISABLED' },
  { id: 9101, spaceId: 1, seatNumber: '101', status: 'AVAILABLE' },
  { id: 9162, spaceId: 1, seatNumber: '162', status: 'AVAILABLE' },
]
function page(content: Seat[], number = 0, totalPages = 1): Page<Seat> {
  return { content, page: number, size: 100, totalElements: seats.length, totalPages }
}
function availability(seatId: number, available = true, from = start): Availability {
  return {
    spaceId: 1,
    seatId,
    startTime: from,
    endTime: end,
    bookingEnabled: true,
    policyScope: 'OCCUPANCY_ONLY',
    available: available ? [{ startTime: from, endTime: end }] : [],
  }
}
function provider(children: ReactNode, client: QueryClient) {
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>
}
function queryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } })
}
afterEach(() => vi.restoreAllMocks())

describe('interactive reading room map', () => {
  it('loads every seat page, uses seatNumber for position and real IDs for selection, and blocks unsafe states', async () => {
    const list = vi
      .spyOn(spacesApi, 'seats')
      .mockImplementation(async (_, number) =>
        page(number === 0 ? seats.slice(0, 3) : seats.slice(3), number, 2),
      )
    const check = vi
      .spyOn(spacesApi, 'availability')
      .mockImplementation(async (_, from, __, id) => {
        if (id === 9101) throw new Error('temporary network error')
        return availability(id!, id !== 9010, from)
      })
    const select = vi.fn()
    render(
      provider(
        <SeatPicker
          spaceId={1}
          start={start}
          end={end}
          selectedId={undefined}
          onSelect={select}
          imageMap
        />,
        queryClient(),
      ),
    )
    const last = await screen.findByRole('button', { name: '162번 좌석, 예약 가능' })
    expect(list).toHaveBeenCalledWith(1, 0, 100, expect.any(AbortSignal))
    expect(list).toHaveBeenCalledWith(1, 1, 100, expect.any(AbortSignal))
    expect(screen.queryByRole('button', { name: /002번 좌석/ })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '010번 좌석, 예약 불가' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '075번 좌석, 운영 중지' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '101번 좌석, 확인 실패' })).toBeDisabled()
    expect(check.mock.calls.some((call) => call[3] === 9075)).toBe(false)
    await userEvent.click(last)
    expect(select).toHaveBeenCalledWith(seats[4])
    expect(parseFloat(last.style.left)).toBeGreaterThan(85)
    expect(parseFloat(last.style.top)).toBeGreaterThan(90)
  })

  it('prevents clicking cached availability while a changed time is being checked', async () => {
    vi.spyOn(spacesApi, 'seats').mockResolvedValue(page([seats[0]]))
    const check = vi.spyOn(spacesApi, 'availability').mockResolvedValue(availability(9001))
    const client = queryClient()
    const select = vi.fn()
    const picker = (from: string) =>
      provider(
        <SeatPicker
          spaceId={1}
          start={from}
          end={end}
          selectedId={9001}
          onSelect={select}
          imageMap
        />,
        client,
      )
    const view = render(picker(start))
    expect(
      await screen.findByRole('button', { name: '001번 좌석, 선택됨, 예약 가능' }),
    ).toBeEnabled()
    let finish!: (result: Availability) => void
    check.mockImplementation(
      () =>
        new Promise((resolve) => {
          finish = resolve
        }),
    )
    const next = '2035-06-01T01:30:00.000Z'
    view.rerender(picker(next))
    const pending = await screen.findByRole('button', { name: '001번 좌석, 선택됨, 확인 중' })
    expect(pending).toBeDisabled()
    await userEvent.click(pending)
    expect(select).not.toHaveBeenCalled()
    await act(async () => finish(availability(9001, false, next)))
    expect(
      await screen.findByRole('button', { name: '001번 좌석, 선택됨, 예약 불가' }),
    ).toBeDisabled()
  })

  it('supports unmapped API seats and selection inside the enlarged dialog', async () => {
    const extra: Seat = { id: 2000, spaceId: 1, seatNumber: 'A-01', status: 'AVAILABLE' }
    const select = vi.fn()
    render(
      <ReadingRoomSeatMap
        seats={[seats[0], extra]}
        statuses={{ 9001: 'free', 2000: 'free' }}
        selectedId={undefined}
        checking={false}
        onSelect={select}
      />,
    )
    await userEvent.click(screen.getByRole('button', { name: 'A-01번 좌석, 예약 가능' }))
    expect(select).toHaveBeenLastCalledWith(extra)
    await userEvent.click(screen.getByRole('button', { name: '크게 열기' }))
    await userEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: '001번 좌석, 예약 가능' }),
    )
    expect(select).toHaveBeenLastCalledWith(seats[0])
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('carries an image selection into the confirmed reservation payload', async () => {
    vi.spyOn(spacesApi, 'seats').mockResolvedValue(page([seats[4]]))
    vi.spyOn(spacesApi, 'availability').mockResolvedValue(availability(9162))
    const create = vi.spyOn(reservationsApi, 'create').mockResolvedValue({
      id: 44,
      userId: 3,
      spaceId: 1,
      seatId: 9162,
      kind: 'SEAT_USE',
      startTime: start,
      endTime: end,
      status: 'UPCOMING',
      members: [],
      purpose: null,
      createdAt: start,
      cancelledAt: null,
    })
    const space: Space = {
      id: 1,
      spaceCode: 'READING_ROOM',
      name: '집중열람실',
      type: 'READING_ROOM',
      venue: 'READING_ROOM',
      location: '학술정보관',
      facilities: null,
      bookingEnabled: true,
      minCapacity: null,
      maxCapacity: null,
    }
    const auth: AuthValue = {
      user: {
        id: 3,
        studentId: '20300003',
        name: '학생',
        email: 'student@example.test',
        role: 'STUDENT',
      },
      loading: false,
      error: null,
      refresh: vi.fn(),
      login: vi.fn(),
      logout: vi.fn(),
    }
    function Flow() {
      const [selected, setSelected] = useState<Seat>()
      return (
        <>
          <SeatPicker
            spaceId={1}
            start={start}
            end={end}
            selectedId={selected?.id}
            onSelect={setSelected}
            imageMap
          />
          <BookingForm
            space={space}
            hasSeats
            seatId={selected?.id}
            seatLabel={selected?.seatNumber}
            start={start}
            end={end}
            available={Boolean(selected)}
            checking={false}
          />
        </>
      )
    }
    render(
      provider(
        <AuthContext.Provider value={auth}>
          <MemoryRouter>
            <Routes>
              <Route path="/" element={<Flow />} />
              <Route path="/reservations/44" element={<p>좌석 예약 완료</p>} />
            </Routes>
          </MemoryRouter>
        </AuthContext.Provider>,
        queryClient(),
      ),
    )
    await userEvent.click(await screen.findByRole('button', { name: '162번 좌석, 예약 가능' }))
    expect(screen.getByText('162번', { selector: 'dd' })).toBeInTheDocument()
    expect(create).not.toHaveBeenCalled()
    await userEvent.click(screen.getByRole('button', { name: '예약 내용 확인' }))
    await userEvent.click(screen.getByRole('button', { name: '예약 확정' }))
    await waitFor(() =>
      expect(create).toHaveBeenCalledWith(
        expect.objectContaining({ spaceId: 1, seatId: 9162, kind: 'SEAT_USE', members: [] }),
      ),
    )
    expect(await screen.findByText('좌석 예약 완료')).toBeInTheDocument()
  })
})
