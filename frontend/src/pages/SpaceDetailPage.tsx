import { Link, useParams, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, Clock3, MapPin, Users } from 'lucide-react'
import { spacesApi } from '../api/spaces'
import { Empty, ErrorNotice, Loading, PageHeading } from '../components/ui'
import { MonthCalendar } from '../components/MonthCalendar'
import { SeatPicker } from '../components/SeatPicker'
import { BookingForm } from '../components/BookingForm'
import { PolicyNotice } from '../components/PolicyNotice'
import {
  addDays,
  covers,
  defaultSelection,
  formatDate,
  toInstant,
  validDate,
  validTime,
} from '../utils/time'
import { capacity, label } from '../utils/labels'
import { useState } from 'react'
import { useNow } from '../hooks/useNow'
import { FloorPlan } from '../components/FloorPlan'
import { facilityFor } from '../assets/spaceMedia'
import { InstantSeatUse } from '../components/InstantSeatUse'
import { bookingRuleMessage } from '../utils/bookingRules'
import { today } from '../utils/time'

export default function SpaceDetailPage() {
  const { spaceId } = useParams()
  const id = Number(spaceId)
  const [params, setParams] = useSearchParams()
  const requestedInstant = params.get('mode') === 'instant'
  const [defaults] = useState(defaultSelection)
  const now = useNow()
  const date = validDate(params.get('date') ?? '') ? params.get('date')! : defaults.date
  const startTime = validTime(params.get('start') ?? '') ? params.get('start')! : defaults.start
  const endTime =
    validTime(params.get('end') ?? '') || params.get('end') === '24:00'
      ? params.get('end')!
      : defaults.end
  const seatId = Number(params.get('seat')) > 0 ? Number(params.get('seat')) : undefined
  const start = toInstant(date, startTime),
    end = toInstant(date, endTime)
  const validId = Number.isSafeInteger(id) && id > 0
  const space = useQuery({
    queryKey: ['space', id],
    queryFn: ({ signal }) => spacesApi.get(id, signal),
    enabled: validId,
  })
  const seats = useQuery({
    queryKey: ['seat-count', id],
    queryFn: ({ signal }) => spacesApi.seats(id, 0, 1, signal),
    enabled: validId,
  })
  const policy = useQuery({
    queryKey: ['policy', id],
    queryFn: ({ signal }) => spacesApi.policy(id, signal),
    enabled: validId,
  })
  const hasSeats = Boolean(seats.data?.totalElements)
  const instantMode = Boolean(
    requestedInstant && hasSeats && space.data && facilityFor(space.data)?.id === 'reading',
  )
  const rules = useQuery({
    queryKey: ['booking-rules', id],
    queryFn: ({ signal }) => spacesApi.bookingRules(id, signal),
    enabled: validId,
  })
  const step = rules.data?.enabled ? rules.data.slotMinutes : 30
  const times = Array.from(
    { length: 1440 / step + 1 },
    (_, i) =>
      `${String(Math.floor((i * step) / 60)).padStart(2, '0')}:${String((i * step) % 60).padStart(2, '0')}`,
  )
  const availability = useQuery({
    queryKey: ['availability', id, date, seatId],
    enabled: Boolean(!instantMode && seats.data && (!hasSeats || seatId)),
    staleTime: 15_000,
    queryFn: ({ signal }) =>
      spacesApi.availability(
        id,
        toInstant(date, '00:00'),
        toInstant(addDays(date, 1), '00:00'),
        hasSeats ? seatId : undefined,
        signal,
      ),
  })
  function selection(update: Record<string, string>) {
    const next = new URLSearchParams(params)
    Object.entries({ date, start: startTime, end: endTime, ...update }).forEach(([k, v]) =>
      next.set(k, v),
    )
    setParams(next, { replace: true })
  }
  if (!validId) return <Empty title="올바르지 않은 공간 주소입니다." />
  if (space.isPending || seats.isPending) return <Loading />
  if (space.isError || seats.isError)
    return (
      <ErrorNotice
        error={space.error ?? seats.error}
        retry={() => {
          void space.refetch()
          void seats.refetch()
        }}
      />
    )
  const ruleError = bookingRuleMessage(rules.data, start, end)
  const free = Boolean(
    !ruleError &&
    availability.data &&
    covers(availability.data.available, start, end) &&
    Date.parse(start) > now,
  )
  const readingMap = hasSeats && facilityFor(space.data)?.id === 'reading'
  return (
    <>
      <Link to="/spaces" className="back-link">
        <ArrowLeft size={16} />
        공간 목록
      </Link>
      <PageHeading
        eyebrow={label(space.data.venue)}
        title={space.data.name}
        action={
          <span className={`badge ${space.data.bookingEnabled ? 'green' : ''}`}>
            {space.data.bookingEnabled ? '예약 운영 중' : '예약 중지'}
          </span>
        }
      >
        <span className="inline-flex items-center gap-2">
          <MapPin size={16} />
          {space.data.location || '위치 미등록'}
        </span>
      </PageHeading>
      <div className="detail-meta">
        <span>
          <Users size={17} />
          {hasSeats ? `좌석 ${seats.data.totalElements}개 · 1인 이용` : capacity(space.data)}
        </span>
        <span>{label(space.data.type)}</span>
        {space.data.facilities && <span>{space.data.facilities}</span>}
      </div>
      {readingMap && (
        <div className="tabs" aria-label="좌석 이용 방식">
          <button
            className={!instantMode ? 'active' : ''}
            aria-pressed={!instantMode}
            onClick={() => selection({ mode: 'scheduled' })}
          >
            날짜·시간 예약
          </button>
          <button
            className={instantMode ? 'active' : ''}
            aria-pressed={instantMode}
            onClick={() => selection({ mode: 'instant' })}
          >
            지금 바로 이용
          </button>
        </div>
      )}
      {readingMap && instantMode ? (
        <InstantSeatUse spaceId={id} />
      ) : (
        <div className={`booking-layout ${readingMap ? 'reading-layout' : ''}`}>
          <div className={readingMap ? 'booking-sections' : 'stack gap-6 booking-sections'}>
            <div className="panel schedule-grid">
              <MonthCalendar
                value={date}
                onChange={(d) => selection({ date: d })}
                maximum={rules.data?.enabled ? addDays(today(), rules.data.advanceDays) : undefined}
              />
              <section className="time-section">
                <h3 className="flex items-center gap-2">
                  <Clock3 size={18} />
                  시간 선택
                </h3>
                <p className="small muted mt-2 mb-5">{formatDate(date)}</p>
                <div className="grid grid-cols-2 gap-3">
                  <label>
                    시작
                    <select
                      value={startTime}
                      onChange={(e) => selection({ start: e.target.value })}
                    >
                      {[...new Set([...times.slice(0, -1), startTime])].sort().map((t) => (
                        <option key={t}>{t}</option>
                      ))}
                    </select>
                  </label>
                  <label>
                    종료
                    <select value={endTime} onChange={(e) => selection({ end: e.target.value })}>
                      {[...new Set([...times.slice(1), endTime])].sort().map((t) => (
                        <option key={t}>{t}</option>
                      ))}
                    </select>
                  </label>
                </div>
                <p className="small muted my-4">{step}분 단위로 시간을 선택할 수 있습니다.</p>
                {rules.isError && (
                  <ErrorNotice error={rules.error} retry={() => void rules.refetch()} />
                )}
                {rules.data?.enabled && (
                  <p className="notice mb-4">
                    오늘부터 {rules.data.advanceDays}일 · 1회 {rules.data.minDurationMinutes}~
                    {rules.data.maxDurationMinutes}분
                    {rules.data.dailyMaxMinutes
                      ? ` · 하루 ${rules.data.dailyMaxMinutes}분 (${rules.data.usageScope === 'VENUE' ? '동일 시설 합산' : '이 공간'})`
                      : ''}
                    {rules.data.preventAdjacent ? ' · 연속 예약 제한' : ''}
                  </p>
                )}
                {ruleError && (
                  <p role="alert" className="notice error mb-4">
                    {ruleError}
                  </p>
                )}
                {policy.isPending ? (
                  <Loading message="운영시간 확인 중" />
                ) : policy.isError ? (
                  <ErrorNotice error={policy.error} retry={() => void policy.refetch()} />
                ) : (
                  <PolicyNotice policy={policy.data} date={date} />
                )}
              </section>
            </div>
            {!readingMap && <FloorPlan space={space.data} />}
            {hasSeats && (
              <SeatPicker
                spaceId={id}
                imageMap={readingMap}
                bookingEnabled={space.data.bookingEnabled}
                start={start}
                end={end}
                selectedId={seatId}
                onSelect={(s) => selection({ seat: String(s.id), seatLabel: s.seatNumber })}
              />
            )}
            <section className="panel daily-availability">
              <div className="section-heading">
                <h2>시간별 이용 가능 여부</h2>
                <button
                  className="text-link"
                  disabled={availability.isFetching || (hasSeats && !seatId)}
                  onClick={() => void availability.refetch()}
                >
                  새로고침
                </button>
              </div>
              {hasSeats && !seatId ? (
                <p className="notice">좌석을 선택하면 하루 시간표를 확인할 수 있습니다.</p>
              ) : availability.isPending ? (
                <Loading />
              ) : availability.isError ? (
                <ErrorNotice error={availability.error} retry={() => void availability.refetch()} />
              ) : (
                <>
                  <div className="legend">
                    <span>
                      <i className="free" />
                      예약 가능
                    </span>
                    <span>
                      <i className="unavailable" />
                      예약·차단·운영시간 외
                    </span>
                  </div>
                  <div className="time-grid">
                    {times.slice(0, -1).map((t, i) => {
                      const a = toInstant(date, t),
                        b = toInstant(date, times[i + 1])
                      const possible =
                        Date.parse(a) > now && covers(availability.data.available, a, b)
                      const chosen = start <= a && b <= end
                      return (
                        <button
                          key={t}
                          disabled={!possible}
                          aria-pressed={chosen}
                          aria-label={`${t}부터 ${step}분 ${possible ? '예약 가능' : '예약 불가'}`}
                          className={chosen && possible ? 'selected' : ''}
                          onClick={() => selection({ start: t, end: times[i + 1] })}
                        >
                          {t}
                        </button>
                      )
                    })}
                  </div>
                  <p className={`notice mt-4 ${free ? 'success' : ''}`} role="status">
                    {free
                      ? '선택한 시간 전체를 예약할 수 있습니다.'
                      : '선택한 시간은 예약할 수 없습니다. 시간 또는 좌석을 변경해주세요.'}
                  </p>
                </>
              )}
            </section>
          </div>
          <BookingForm
            space={space.data}
            hasSeats={hasSeats}
            seatId={seatId}
            seatLabel={params.get('seatLabel') ?? undefined}
            start={start}
            end={end}
            available={free && !policy.isError && !rules.isError}
            checking={availability.isFetching || policy.isPending || rules.isPending}
            purposeRequired={Boolean(rules.data?.enabled && rules.data.purposeRequired)}
          />
        </div>
      )}
    </>
  )
}
