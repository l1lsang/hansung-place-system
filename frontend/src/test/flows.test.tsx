import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthValue } from '../hooks/authContext'
import { RouteGuard } from '../components/RouteGuard'
import { BookingForm } from '../components/BookingForm'
import { PolicyEditor } from '../components/PolicyEditor'
import { BookingRulesEditor } from '../components/BookingRulesEditor'
import { InstantSeatUse } from '../components/InstantSeatUse'
import ReservationDetailPage from '../pages/ReservationDetailPage'
import LoginPage from '../pages/LoginPage'
import { reservationsApi } from '../api/reservations'
import { spacesApi } from '../api/spaces'
import { adminApi } from '../api/admin'
import type { Reservation, Space, BookingRules, SeatAvailability } from '../types/api'
import { ApiError } from '../api/client'
import type { ReactNode } from 'react'

const user = {
  id: 1,
  studentId: '20300001',
  name: '테스트 학생',
  email: 'student@example.test',
  role: 'STUDENT',
}
const auth: AuthValue = {
  user,
  loading: false,
  error: null,
  refresh: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}
const room: Space = {
  id: 2,
  name: '테스트 스터디룸',
  spaceCode: 'TEST_ROOM',
  type: 'STUDY_ROOM',
  venue: 'LIBRARY',
  location: '테스트 위치',
  minCapacity: 2,
  maxCapacity: 6,
  facilities: null,
  bookingEnabled: true,
}
const start = '2030-06-01T01:00:00.000Z',
  end = '2030-06-01T02:00:00.000Z'
const reservation: Reservation = {
  id: 10,
  userId: 1,
  spaceId: 2,
  seatId: null,
  kind: 'BOOKING',
  members: [],
  purpose: null,
  startTime: start,
  endTime: end,
  status: 'UPCOMING',
  createdAt: start,
  cancelledAt: null,
}
function Location() {
  const location = useLocation()
  return <p>{location.pathname + location.search}</p>
}
function mount(node: ReactNode, value = auth, entry = '/') {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={client}>
      <AuthContext.Provider value={value}>
        <MemoryRouter initialEntries={[entry]}>{node}</MemoryRouter>
      </AuthContext.Provider>
    </QueryClientProvider>,
  )
}
afterEach(() => vi.restoreAllMocks())
describe('authentication and route access', () => {
  it('redirects anonymous requests while keeping the destination', async () => {
    mount(
      <Routes>
        <Route element={<RouteGuard />}>
          <Route path="/reservations" element={<p>보호된 예약</p>} />
        </Route>
        <Route path="/login" element={<Location />} />
      </Routes>,
      { ...auth, user: null },
      '/reservations',
    )
    expect(await screen.findByText('/login?next=%2Freservations')).toBeInTheDocument()
    expect(screen.queryByText('보호된 예약')).not.toBeInTheDocument()
  })
  it('blocks a student opening an administrator URL directly', () => {
    mount(
      <Routes>
        <Route element={<RouteGuard admin />}>
          <Route path="/admin" element={<p>관리 폼</p>} />
        </Route>
      </Routes>,
      auth,
      '/admin',
    )
    expect(screen.getByText('관리자 권한이 필요합니다.')).toBeInTheDocument()
    expect(screen.queryByText('관리 폼')).not.toBeInTheDocument()
  })
  it('shows login errors and prevents an external redirect', async () => {
    const login = vi
      .fn()
      .mockRejectedValue(new ApiError(401, 'UNAUTHENTICATED', '인증에 실패했습니다.'))
    mount(<LoginPage />, { ...auth, user: null, login }, '/login?next=https://example.test')
    await userEvent.type(screen.getByLabelText('학번'), '20300001')
    await userEvent.type(screen.getByLabelText('비밀번호'), 'test-password')
    await userEvent.click(screen.getByRole('button', { name: '로그인' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('인증에 실패했습니다.')
    expect(login).toHaveBeenCalledWith('20300001', 'test-password')
  })
})
describe('booking and cancellation', () => {
  it('excludes the owner from members and locks duplicate booking clicks', async () => {
    vi.spyOn(spacesApi, 'availability').mockResolvedValue({
      spaceId: 2,
      seatId: null,
      startTime: start,
      endTime: end,
      bookingEnabled: true,
      policyScope: 'OCCUPANCY_ONLY',
      available: [{ startTime: start, endTime: end }],
    })
    let finish!: (value: Reservation) => void
    const create = vi.spyOn(reservationsApi, 'create').mockImplementation(
      () =>
        new Promise((resolve) => {
          finish = resolve
        }),
    )
    mount(
      <Routes>
        <Route
          path="/"
          element={
            <BookingForm
              space={room}
              hasSeats={false}
              start={start}
              end={end}
              available
              checking={false}
            />
          }
        />
        <Route path="/reservations/:id" element={<p>예약 상세 도착</p>} />
      </Routes>,
    )
    await userEvent.click(screen.getByText('참여자 추가'))
    await userEvent.type(screen.getByLabelText('참여자 1 학번'), '20300002')
    await userEvent.type(screen.getByLabelText('참여자 1 이름'), '동반자')
    await userEvent.click(screen.getByRole('button', { name: '예약 내용 확인' }))
    const confirm = screen.getByRole('button', { name: '예약 확정' })
    fireEvent.click(confirm)
    fireEvent.click(confirm)
    await waitFor(() => expect(create).toHaveBeenCalledTimes(1))
    expect(create).toHaveBeenCalledWith(
      expect.objectContaining({
        members: [{ studentId: '20300002', name: '동반자' }],
        kind: 'BOOKING',
        seatId: null,
      }),
    )
    await act(async () => finish(reservation))
    expect(await screen.findByText('예약 상세 도착')).toBeInTheDocument()
  })
  it('rejects the owner entered as an additional member before submitting', async () => {
    const create = vi.spyOn(reservationsApi, 'create')
    mount(
      <BookingForm
        space={room}
        hasSeats={false}
        start={start}
        end={end}
        available
        checking={false}
      />,
    )
    await userEvent.click(screen.getByText('참여자 추가'))
    await userEvent.type(screen.getByLabelText('참여자 1 학번'), user.studentId)
    await userEvent.type(screen.getByLabelText('참여자 1 이름'), user.name)
    await userEvent.click(screen.getByRole('button', { name: '예약 내용 확인' }))
    expect(screen.getByRole('alert')).toHaveTextContent('학번이 중복')
    expect(create).not.toHaveBeenCalled()
  })
  it('shows a 409 conflict without falsely reporting success', async () => {
    vi.spyOn(spacesApi, 'availability').mockResolvedValue({
      spaceId: 2,
      seatId: 7,
      startTime: start,
      endTime: end,
      bookingEnabled: true,
      policyScope: 'OCCUPANCY_ONLY',
      available: [{ startTime: start, endTime: end }],
    })
    const create = vi
      .spyOn(reservationsApi, 'create')
      .mockRejectedValue(new ApiError(409, 'RESERVATION_CONFLICT', '이미 예약된 시간입니다.'))
    mount(
      <BookingForm
        space={room}
        hasSeats
        seatId={7}
        seatLabel="007"
        start={start}
        end={end}
        available
        checking={false}
      />,
    )
    await userEvent.click(screen.getByRole('button', { name: '예약 내용 확인' }))
    await userEvent.click(screen.getByRole('button', { name: '예약 확정' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('이미 예약된 시간')
    expect(create).toHaveBeenCalledWith(
      expect.objectContaining({ kind: 'SEAT_USE', seatId: 7, members: [] }),
    )
  })
  it('confirms cancellation then refreshes the saved status', async () => {
    vi.spyOn(spacesApi, 'get').mockResolvedValue(room)
    const get = vi.spyOn(reservationsApi, 'get').mockResolvedValue(reservation)
    const cancel = vi.spyOn(reservationsApi, 'cancel').mockImplementation(async () => {
      get.mockResolvedValue({ ...reservation, status: 'CANCELLED' })
    })
    mount(
      <Routes>
        <Route path="/reservations/:reservationId" element={<ReservationDetailPage />} />
      </Routes>,
      auth,
      '/reservations/10',
    )
    await userEvent.click(await screen.findByRole('button', { name: '예약 취소' }))
    expect(cancel).not.toHaveBeenCalled()
    await userEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: '취소 확정' }),
    )
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(cancel).toHaveBeenCalledWith(10)
    expect(screen.getByText('취소', { selector: '.badge' })).toBeInTheDocument()
  })
})
describe('operating policy editor', () => {
  it('submits all 21 rules and inclusive exam dates through the actual API contract', async () => {
    const policy = {
      spaceId: 2,
      configured: false,
      enabled: false,
      timeZone: 'Asia/Seoul',
      academicPeriod: null,
      examStartDate: null,
      examEndDate: null,
      hours: [],
      updatedBy: null,
      updatedAt: null,
    }
    const save = vi.spyOn(adminApi, 'savePolicy').mockResolvedValue(policy)
    mount(<PolicyEditor policy={policy} />)
    await userEvent.click(screen.getByLabelText('새 예약에 운영시간 정책 적용'))
    fireEvent.change(screen.getByLabelText('시험기간 시작일'), { target: { value: '2030-06-01' } })
    fireEvent.change(screen.getByLabelText('시험기간 종료일'), { target: { value: '2030-06-03' } })
    await userEvent.click(screen.getAllByLabelText('휴무')[0])
    await userEvent.click(screen.getByRole('button', { name: '정책 전체 저장' }))
    expect(await screen.findByText('운영 정책을 저장했습니다.')).toBeInTheDocument()
    expect(save).toHaveBeenCalledWith(
      2,
      expect.objectContaining({
        enabled: true,
        examStartDate: '2030-06-01',
        examEndDate: '2030-06-03',
        hours: expect.arrayContaining([
          expect.objectContaining({
            dayOfWeek: 'MONDAY',
            period: 'SEMESTER',
            closed: false,
            openTime: '09:00',
            closeTime: '18:00',
          }),
        ]),
      }),
    )
    expect(save.mock.calls[0][1].hours).toHaveLength(21)
  })
})

describe('reference booking and operations features', () => {
  const batch: SeatAvailability = {
    spaceId: 2,
    startTime: start,
    endTime: end,
    bookingEnabled: true,
    policyConfigured: true,
    instant: true,
    instantUseMinutes: 180,
    userHasSeatUse: false,
    seats: {
      content: [
        {
          id: 8162,
          spaceId: 2,
          seatNumber: '162',
          status: 'AVAILABLE',
          available: true,
          availableUntil: end,
          occupiedUntil: null,
          mine: false,
          reservationId: null,
        },
      ],
      page: 0,
      size: 100,
      totalPages: 1,
      totalElements: 1,
    },
  }
  it('starts immediate use only after confirmation and sends no client-controlled times', async () => {
    vi.spyOn(spacesApi, 'seatAvailability').mockResolvedValue(batch)
    const startUse = vi
      .spyOn(reservationsApi, 'startSeatUse')
      .mockResolvedValue({ ...reservation, seatId: 8162, kind: 'SEAT_USE' })
    mount(
      <Routes>
        <Route path="/" element={<InstantSeatUse spaceId={2} />} />
        <Route path="/reservations/:id" element={<p>즉시 이용 내역 도착</p>} />
      </Routes>,
    )
    await userEvent.click(await screen.findByRole('button', { name: '162번 좌석, 예약 가능' }))
    expect(startUse).not.toHaveBeenCalled()
    await userEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: '지금 이용 시작' }),
    )
    expect(await screen.findByText('즉시 이용 내역 도착')).toBeInTheDocument()
    expect(startUse).toHaveBeenCalledExactlyOnceWith(2, 8162)
  })
  it('blocks immediate use when the user already has a seat and links their own reservation', async () => {
    vi.spyOn(spacesApi, 'seatAvailability').mockResolvedValue({
      ...batch,
      userHasSeatUse: true,
      seats: {
        ...batch.seats,
        content: [
          ...batch.seats.content,
          {
            ...batch.seats.content[0],
            id: 1,
            seatNumber: '001',
            available: false,
            mine: true,
            reservationId: 10,
            occupiedUntil: end,
          },
        ],
      },
    })
    const startUse = vi.spyOn(reservationsApi, 'startSeatUse')
    mount(<InstantSeatUse spaceId={2} />)
    expect(await screen.findByRole('link', { name: /001번 이용 내역/ })).toHaveAttribute(
      'href',
      '/reservations/10',
    )
    await userEvent.click(screen.getByRole('button', { name: '162번 좌석, 예약 가능' }))
    expect(
      within(screen.getByRole('dialog')).getByRole('button', { name: '지금 이용 시작' }),
    ).toBeDisabled()
    expect(startUse).not.toHaveBeenCalled()
  })
  it('returns an active seat and refreshes its completed record', async () => {
    const current = {
      ...reservation,
      kind: 'SEAT_USE' as const,
      seatId: 7,
      startTime: new Date(Date.now() - 60000).toISOString(),
      endTime: new Date(Date.now() + 3600000).toISOString(),
    }
    vi.spyOn(spacesApi, 'get').mockResolvedValue(room)
    const get = vi.spyOn(reservationsApi, 'get').mockResolvedValue(current)
    const returnSeat = vi.spyOn(reservationsApi, 'returnSeat').mockImplementation(async () => {
      const result = { ...current, status: 'COMPLETED' as const, endedAt: new Date().toISOString() }
      get.mockResolvedValue(result)
      return result
    })
    const cancel = vi.spyOn(reservationsApi, 'cancel')
    mount(
      <Routes>
        <Route path="/reservations/:reservationId" element={<ReservationDetailPage />} />
      </Routes>,
      auth,
      '/reservations/10',
    )
    await userEvent.click(await screen.findByRole('button', { name: '좌석 반납' }))
    await userEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: '반납·종료 확정' }),
    )
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(returnSeat).toHaveBeenCalledWith(10)
    expect(cancel).not.toHaveBeenCalled()
    expect(screen.getByText('종료', { selector: '.badge' })).toBeInTheDocument()
  })
  it('requires an administrator reason and displays the saved cancellation audit', async () => {
    vi.spyOn(spacesApi, 'get').mockResolvedValue(room)
    const get = vi.spyOn(adminApi, 'reservation').mockResolvedValue({
      ...reservation,
      owner: { name: user.name, studentId: user.studentId, email: user.email },
    })
    const cancel = vi.spyOn(adminApi, 'cancelReservation').mockImplementation(async () => {
      const result = {
        ...reservation,
        status: 'CANCELLED' as const,
        actions: [
          {
            id: 1,
            actorId: 8,
            action: 'ADMIN_CANCEL' as const,
            reason: '시설 점검',
            createdAt: start,
          },
        ],
      }
      get.mockResolvedValue(result)
      return result
    })
    mount(
      <Routes>
        <Route
          path="/admin/reservations/:reservationId"
          element={<ReservationDetailPage admin />}
        />
      </Routes>,
      { ...auth, user: { ...user, role: 'ADMIN' } },
      '/admin/reservations/10',
    )
    await userEvent.click(await screen.findByRole('button', { name: '예약 강제 취소' }))
    const confirm = within(screen.getByRole('dialog')).getByRole('button', { name: '취소 확정' })
    expect(confirm).toBeDisabled()
    await userEvent.type(screen.getByLabelText('처리 사유'), '시설 점검')
    await userEvent.click(confirm)
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(cancel).toHaveBeenCalledWith(10, '시설 점검')
    expect(screen.getByText('사유: 시설 점검')).toBeInTheDocument()
  })
  it('loads reference limits as an editable preset and persists only on save', async () => {
    const rules: BookingRules = {
      spaceId: 2,
      configured: false,
      enabled: false,
      slotMinutes: 30,
      minDurationMinutes: 30,
      maxDurationMinutes: 180,
      advanceDays: 7,
      dailyMaxMinutes: null,
      usageScope: 'SPACE',
      preventAdjacent: false,
      purposeRequired: false,
      instantUseMinutes: 180,
      updatedBy: null,
      updatedAt: null,
    }
    const save = vi.spyOn(adminApi, 'saveBookingRules').mockResolvedValue(rules)
    mount(<BookingRulesEditor rules={rules} />)
    await userEvent.click(
      screen.getByRole('button', { name: '참고 프로젝트 그룹스터디실 기준 불러오기' }),
    )
    expect(save).not.toHaveBeenCalled()
    await userEvent.click(screen.getByRole('button', { name: '예약 규칙 저장' }))
    expect(await screen.findByText('예약 규칙을 저장했습니다.')).toBeInTheDocument()
    expect(save).toHaveBeenCalledWith(
      2,
      expect.objectContaining({
        enabled: true,
        dailyMaxMinutes: 180,
        usageScope: 'VENUE',
        advanceDays: 7,
        preventAdjacent: true,
        purposeRequired: true,
      }),
    )
  })
})
