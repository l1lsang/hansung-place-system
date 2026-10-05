import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { spacesApi } from '../api/spaces'
import { reservationsApi } from '../api/reservations'
import { useAuth } from '../hooks/authContext'
import { useAction } from '../hooks/useAction'
import { ReadingRoomSeatMap } from './ReadingRoomSeatMap'
import { Empty, ErrorNotice, Loading, Modal } from './ui'
import { formatTime } from '../utils/time'
import type { SeatAvailabilityItem } from '../types/api'
import type { SeatStatus } from '../utils/seatStatus'

export function InstantSeatUse({ spaceId }: { spaceId: number }) {
  const auth = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const cache = useQueryClient()
  const action = useAction()
  const [selected, setSelected] = useState<SeatAvailabilityItem | null>(null)
  const result = useQuery({
    queryKey: ['seat-availability', spaceId, 'instant', auth.user?.id],
    queryFn: ({ signal }) => spacesApi.seatAvailability(spaceId, null, null, signal),
    staleTime: 10_000,
    refetchInterval: 30_000,
  })
  const seats = result.data?.seats.content ?? []
  const mine = seats.find((seat) => seat.mine && seat.reservationId)
  const latest = selected ? seats.find((seat) => seat.id === selected.id) : undefined
  const canStart = Boolean(
    latest?.available && !result.data?.userHasSeatUse && !result.isFetching && !result.isError,
  )
  const statuses = Object.fromEntries(
    seats.map((seat) => [
      seat.id,
      (seat.status !== 'AVAILABLE'
        ? 'disabled'
        : seat.available
          ? 'free'
          : 'unavailable') as SeatStatus,
    ]),
  )
  async function start() {
    if (!selected) return
    await action.run(
      () => reservationsApi.startSeatUse(spaceId, selected.id),
      async (reservation) => {
        await Promise.all(
          ['seat-availability', 'reservations', 'availability', 'admin'].map((key) =>
            cache.invalidateQueries({ queryKey: [key] }),
          ),
        )
        navigate(`/reservations/${reservation.id}`, { state: { created: true } })
      },
    )
    void result.refetch()
  }
  return (
    <section className="panel image-seat-picker">
      <div className="section-heading">
        <div>
          <p className="eyebrow">START USING NOW</p>
          <h2>집중열람실 즉시 이용</h2>
        </div>
        <button
          className="btn secondary small"
          disabled={result.isFetching}
          onClick={() => void result.refetch()}
        >
          현황 새로고침
        </button>
      </div>
      <p className="muted mb-4">
        빈 좌석을 선택하면 지금부터 이용을 시작할 수 있습니다. 기본 이용시간은{' '}
        {result.data?.instantUseMinutes ?? '…'}분이며 운영 종료·차단·다음 예약에 맞춰 단축됩니다.
      </p>
      {result.isPending ? (
        <Loading />
      ) : result.isError ? (
        <ErrorNotice error={result.error} retry={() => void result.refetch()} />
      ) : seats.length === 0 ? (
        <Empty title="등록된 좌석이 없습니다." />
      ) : (
        <>
          {!result.data.policyConfigured && (
            <p className="notice mb-4">
              운영시간 정책이 아직 등록되지 않았습니다. 방문 전 시설 이용 안내를 확인해주세요.
            </p>
          )}
          {result.data.userHasSeatUse && (
            <p className="notice mb-4">
              같은 시간에 이용 중이거나 예약한 좌석이 있습니다.{' '}
              <Link
                className="text-link"
                to={mine ? `/reservations/${mine.reservationId}` : '/reservations'}
              >
                {mine ? `${mine.seatNumber}번 이용 내역·반납` : '내 예약 확인'} →
              </Link>
            </p>
          )}
          <div className="legend">
            <span>
              <i className="free" />
              이용 가능
            </span>
            <span>
              <i className="selected" />
              선택
            </span>
            <span>
              <i className="unavailable" />
              이용 불가
            </span>
            <span>
              <i className="disabled" />
              운영 중지
            </span>
          </div>
          <ReadingRoomSeatMap
            seats={seats}
            statuses={statuses}
            selectedId={selected?.id}
            checking={result.isFetching}
            selectionLink={false}
            onSelect={(seat) => {
              action.clearError()
              setSelected(seats.find((item) => item.id === seat.id) ?? null)
            }}
          />
          {seats.some((seat) => seat.occupiedUntil) && (
            <details className="mt-4">
              <summary>이용 중인 좌석의 종료 예정 시간</summary>
              <div className="occupancy-times">
                {seats
                  .filter((seat) => seat.occupiedUntil)
                  .map((seat) => (
                    <span key={seat.id}>
                      {seat.seatNumber}번 · {formatTime(seat.occupiedUntil!)}까지{' '}
                      {seat.mine ? '(내 좌석)' : ''}
                    </span>
                  ))}
              </div>
            </details>
          )}
          <p className="small muted mt-4">
            30초마다 현황이 갱신됩니다. 최종 이용 시간과 가능 여부는 이용 시작 시 다시 확인합니다.
          </p>
        </>
      )}
      {selected && (
        <Modal
          title={`${selected.seatNumber}번 좌석을 지금 이용할까요?`}
          busy={action.pending}
          onClose={() => {
            setSelected(null)
            action.clearError()
          }}
        >
          <p className="notice">
            {latest?.availableUntil
              ? `지금부터 ${formatTime(latest.availableUntil)}까지 이용할 수 있습니다.`
              : '현재 이용 가능 여부를 다시 확인해주세요.'}
          </p>
          {result.data?.userHasSeatUse && (
            <p className="notice mt-3">기존 좌석 이용·예약을 먼저 확인해주세요.</p>
          )}
          <ErrorNotice error={action.error ?? result.error} />
          {auth.user ? (
            <div className="modal-actions">
              <button
                className="btn secondary"
                disabled={action.pending}
                onClick={() => setSelected(null)}
              >
                돌아가기
              </button>
              <button
                className="btn primary"
                disabled={!canStart || action.pending}
                onClick={() => void start()}
              >
                {action.pending ? '이용 시작 중…' : '지금 이용 시작'}
              </button>
            </div>
          ) : (
            <Link
              className="btn primary w-full mt-4"
              to={`/login?next=${encodeURIComponent(location.pathname + location.search)}`}
            >
              로그인하고 이용하기
            </Link>
          )}
        </Modal>
      )}
    </section>
  )
}
