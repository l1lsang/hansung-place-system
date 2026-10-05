import { useQuery } from '@tanstack/react-query'
import { spacesApi } from '../api/spaces'
import type { Seat } from '../types/api'
import { ErrorNotice, Loading } from './ui'
import { ReadingRoomSeatMap } from './ReadingRoomSeatMap'
import { seatStatusLabels, type SeatStatus } from '../utils/seatStatus'
import { useNow } from '../hooks/useNow'
import { useAuth } from '../hooks/authContext'

export function SeatPicker({
  spaceId,
  start,
  end,
  selectedId,
  onSelect,
  imageMap = false,
  bookingEnabled = true,
}: {
  spaceId: number
  start: string
  end: string
  selectedId: number | undefined
  onSelect: (seat: Seat) => void
  imageMap?: boolean
  bookingEnabled?: boolean
}) {
  const now = useNow()
  const auth = useAuth()
  const past = Date.parse(start) <= now
  const blocked = start >= end || past || !bookingEnabled
  const result = useQuery({
    queryKey: ['seat-availability', spaceId, 'scheduled', start, end, auth.user?.id],
    queryFn: ({ signal }) => spacesApi.seatAvailability(spaceId, start, end, signal),
    enabled: !blocked,
    staleTime: 15_000,
    placeholderData: (previous, query) =>
      query?.queryKey[1] === spaceId && query.queryKey[5] === auth.user?.id ? previous : undefined,
  })
  const statuses = Object.fromEntries(
    (result.data?.seats.content ?? []).map((seat) => [
      seat.id,
      (blocked
        ? 'unavailable'
        : seat.status !== 'AVAILABLE'
          ? 'disabled'
          : seat.available
            ? 'free'
            : 'unavailable') as SeatStatus,
    ]),
  )
  return (
    <section className={`panel seat-picker ${imageMap ? 'image-seat-picker' : ''}`}>
      <div className="section-heading">
        <div>
          <p className="eyebrow">SEAT SELECTION</p>
          <h2>
            좌석 선택 <span className="count">{result.data?.seats.totalElements ?? '…'}</span>
          </h2>
        </div>
        <button
          className="text-link"
          onClick={() => void result.refetch()}
          disabled={result.isFetching || blocked}
        >
          새로고침
        </button>
      </div>
      <p className="muted small mb-5">
        선택한 시간 전체를 이용할 수 있는 좌석입니다. 날짜나 시간을 바꾸면 예약 가능 여부를 다시
        확인합니다.
      </p>
      <div className="legend">
        <span>
          <i className="free" />
          예약 가능
        </span>
        <span>
          <i className="selected" />
          선택됨
        </span>
        <span>
          <i className="unavailable" />
          예약 불가
        </span>
        <span>
          <i className="disabled" />
          운영 중지
        </span>
      </div>
      {blocked ? (
        <p className="notice">
          {!bookingEnabled
            ? '현재 이 공간의 예약 운영이 중지되어 있습니다.'
            : past
              ? '현재 시간 이후로 예약 시간을 선택해주세요.'
              : '종료 시간을 시작 시간 이후로 선택해주세요.'}
        </p>
      ) : result.isPending ? (
        <Loading message="전체 좌석의 예약 가능 여부를 확인하고 있습니다." />
      ) : result.isError ? (
        <ErrorNotice error={result.error} retry={() => void result.refetch()} />
      ) : (
        <>
          {imageMap ? (
            <ReadingRoomSeatMap
              seats={result.data.seats.content}
              statuses={statuses}
              selectedId={selectedId}
              checking={result.isFetching}
              onSelect={onSelect}
            />
          ) : (
            <div className="seat-grid" aria-label="좌석 목록" aria-busy={result.isFetching}>
              {result.data.seats.content.map((seat) => {
                const state = result.isFetching ? 'loading' : statuses[seat.id]
                const chosen = selectedId === seat.id
                return (
                  <button
                    key={seat.id}
                    className={`seat ${state} ${chosen ? 'selected' : ''}`}
                    disabled={state !== 'free'}
                    aria-pressed={chosen}
                    aria-label={`${seat.seatNumber}번 좌석, ${chosen ? '선택됨, ' : ''}${seatStatusLabels[state]}`}
                    onClick={() => onSelect(seat)}
                  >
                    <span>{seat.seatNumber}</span>
                    <small>{chosen ? '선택됨' : seatStatusLabels[state]}</small>
                  </button>
                )
              })}
            </div>
          )}
          {result.isFetching && (
            <p role="status" className="small muted mt-3">
              최신 좌석 현황을 확인하고 있습니다.
            </p>
          )}
        </>
      )}
    </section>
  )
}
