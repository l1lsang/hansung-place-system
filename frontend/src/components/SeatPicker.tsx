import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { spacesApi } from '../api/spaces'
import type { Seat } from '../types/api'
import { covers } from '../utils/time'
import { ErrorNotice, Loading, Pagination } from './ui'
import { ReadingRoomSeatMap } from './ReadingRoomSeatMap'
import { seatStatusLabels, type SeatStatus } from '../utils/seatStatus'
import { useNow } from '../hooks/useNow'

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
  const [page, setPage] = useState(0)
  const now = useNow()
  const past = Date.parse(start) <= now
  const blocked = start >= end || past || !bookingEnabled
  const seats = useQuery({
    queryKey: ['seats', spaceId, imageMap ? 'map' : page],
    queryFn: ({ signal }) =>
      imageMap ? spacesApi.allSeats(spaceId, signal) : spacesApi.seats(spaceId, page, 36, signal),
  })
  const statuses = useQuery({
    queryKey: [
      'seat-availability',
      spaceId,
      imageMap ? 'map' : page,
      start,
      end,
      seats.data?.content.map((s) => s.id),
    ],
    enabled: Boolean(seats.data && !blocked),
    staleTime: 15_000,
    queryFn: async ({ signal }) => {
      const list = seats.data!.content
      const result: Record<number, SeatStatus> = {}
      let index = 0
      // There is no batch endpoint. Bound requests and cancel obsolete selections.
      await Promise.all(
        Array.from({ length: Math.min(6, list.length) }, async () => {
          while (index < list.length) {
            signal.throwIfAborted()
            const seat = list[index++]
            if (seat.status !== 'AVAILABLE') {
              result[seat.id] = 'disabled'
              continue
            }
            try {
              const availability = await spacesApi.availability(
                spaceId,
                start,
                end,
                seat.id,
                signal,
              )
              result[seat.id] =
                availability.bookingEnabled && covers(availability.available, start, end)
                  ? 'free'
                  : 'unavailable'
            } catch (error) {
              if (signal.aborted) throw error
              result[seat.id] = 'error'
            }
          }
        }),
      )
      return result
    },
  })
  const labels = seatStatusLabels
  return (
    <section className={`panel seat-picker ${imageMap ? 'image-seat-picker' : ''}`}>
      <div className="section-heading">
        <div>
          <p className="eyebrow">SEAT SELECTION</p>
          <h2>
            좌석 선택 <span className="count">{seats.data?.totalElements ?? '…'}</span>
          </h2>
        </div>
        <button
          className="text-link"
          onClick={() => void statuses.refetch()}
          disabled={statuses.isFetching || !seats.data || blocked}
        >
          새로고침
        </button>
      </div>
      <p className="muted small mb-5">
        선택한 시간 전체를 이용할 수 있는 좌석입니다.{' '}
        {imageMap
          ? '날짜나 시간을 바꾸면 예약 가능 여부를 다시 확인합니다.'
          : '번호순 배치이며 실제 좌석 위치와 다를 수 있습니다.'}
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
      {seats.isPending ? (
        <Loading />
      ) : seats.isError ? (
        <ErrorNotice error={seats.error} retry={() => void seats.refetch()} />
      ) : (
        <>
          {start >= end && <p className="notice">종료 시간을 시작 시간 이후로 선택해주세요.</p>}
          {past && <p className="notice">현재 시간 이후로 예약 시간을 선택해주세요.</p>}
          {!bookingEnabled && (
            <p className="notice">현재 이 공간의 예약 운영이 중지되어 있습니다.</p>
          )}
          {imageMap ? (
            <ReadingRoomSeatMap
              seats={seats.data.content}
              statuses={
                blocked
                  ? Object.fromEntries(
                      seats.data.content.map((seat) => [seat.id, 'unavailable' as const]),
                    )
                  : statuses.data
              }
              selectedId={selectedId}
              checking={statuses.isFetching}
              onSelect={onSelect}
            />
          ) : (
            <div className="seat-grid" aria-label="좌석 목록" aria-busy={statuses.isFetching}>
              {seats.data.content.map((seat) => {
                const state = statuses.data?.[seat.id] ?? 'loading'
                const chosen = selectedId === seat.id
                return (
                  <button
                    key={seat.id}
                    className={`seat ${state} ${chosen ? 'selected' : ''}`}
                    disabled={state !== 'free' || statuses.isFetching || blocked}
                    aria-pressed={chosen}
                    aria-label={`${seat.seatNumber}번 좌석, ${chosen ? '선택됨, ' : ''}${labels[state]}`}
                    onClick={() => onSelect(seat)}
                  >
                    <span>{seat.seatNumber}</span>
                    <small>{chosen ? '선택됨' : labels[state]}</small>
                  </button>
                )
              })}
            </div>
          )}
          {statuses.isFetching && (
            <p role="status" className="small muted mt-3">
              좌석별 예약 가능 시간을 확인하고 있습니다.
            </p>
          )}
          {statuses.data && Object.values(statuses.data).includes('error') && (
            <p className="notice error mt-3" role="alert">
              일부 좌석을 확인하지 못했습니다. 새로고침해주세요.
            </p>
          )}
          {!imageMap && (
            <Pagination page={page} totalPages={seats.data.totalPages} onChange={setPage} />
          )}
        </>
      )}
      <ErrorNotice error={statuses.error} retry={() => void statuses.refetch()} />
    </section>
  )
}
