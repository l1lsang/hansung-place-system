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
import type { Availability, Seat, Space, SeatAvailability } from '../types/api'
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
function batch(content: Seat[], available = true, from = start): SeatAvailability {
  return {
    spaceId: 1,
    startTime: from,
    endTime: end,
    bookingEnabled: true,
    policyConfigured: false,
    instant: false,
    instantUseMinutes: 180,
    userHasSeatUse: false,
    seats: {
      page: 0,
      size: 100,
      totalElements: content.length,
      totalPages: 1,
      content: content.map((seat) => ({
        ...seat,
        available: available && seat.id !== 9010 && seat.id !== 9101 && seat.status === 'AVAILABLE',
        availableUntil: available ? end : null,
        occupiedUntil: null,
        mine: false,
        reservationId: null,
      })),
    },
  }
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
  return (
    <QueryClientProvider client={client}>
      <AuthContext.Provider
        value={{
          user: null,
          loading: false,
          error: null,
          refresh: vi.fn(),
          login: vi.fn(),
          logout: vi.fn(),
        }}
      >
        {children}
      </AuthContext.Provider>
    </QueryClientProvider>
  )
}
function queryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } })
}
afterEach(() => vi.restoreAllMocks())

describe('interactive reading room map', () => {
  it('loads every batch page, maps seat numbers to real IDs, and blocks unavailable seats', async () => {
    const fetcher = vi.fn(async (input: string | URL | Request) => {
      const url = new URL(String(input))
      const number = Number(url.searchParams.get('page'))
      const response = batch(number === 0 ? seats.slice(0, 3) : seats.slice(3))
      response.seats.totalPages = 2
      response.seats.page = number
      response.seats.totalElements = 5
      return new Response(JSON.stringify(response), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    })
    vi.stubGlobal('fetch', fetcher)
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
    expect(fetcher).toHaveBeenCalledTimes(2)
    expect(String(fetcher.mock.calls[1][0])).toContain('/seats/availability?')
    expect(String(fetcher.mock.calls[1][0])).toContain('page=1')
    expect(screen.queryByRole('button', { name: /002번 좌석/ })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '010번 좌석, 예약 불가' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '075번 좌석, 운영 중지' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '101번 좌석, 예약 불가' })).toBeDisabled()
    await userEvent.click(last)
    expect(select).toHaveBeenCalledWith(expect.objectContaining(seats[4]))
    expect(parseFloat(last.style.left)).toBeGreaterThan(85)
    expect(parseFloat(last.style.top)).toBeGreaterThan(90)
  })

  it('prevents clicking cached availability while a changed time is being checked', async () => {
    const check = vi.spyOn(spacesApi, 'seatAvailability').mockResolvedValue(batch([seats[0]]))
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
    let finish!: (result: SeatAvailability) => void
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
    await act(async () => finish(batch([seats[0]], false, next)))
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
    vi.spyOn(spacesApi, 'seatAvailability').mockResolvedValue(batch([seats[4]]))
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
