import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ArrowUpRight, CalendarDays } from 'lucide-react'
import type { Reservation } from '../types/api'
import { spacesApi } from '../api/spaces'
import { formatRange, reservationState } from '../utils/time'
import { Empty } from './ui'
import { useNow } from '../hooks/useNow'
function ReservationRow({ item, admin }: { item: Reservation; admin: boolean }) {
  const space = useQuery({
    queryKey: ['space', item.spaceId],
    queryFn: ({ signal }) => spacesApi.get(item.spaceId, signal),
  })
  const now = useNow()
  const state = reservationState(item, now)
  return (
    <Link className="reservation-row" to={`${admin ? '/admin' : ''}/reservations/${item.id}`}>
      <span className="feature-icon">
        <CalendarDays size={21} />
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex items-center flex-wrap gap-3">
          <h3>{space.data?.name ?? `공간 #${item.spaceId}`}</h3>
          <span className={`badge ${state === '예정' ? 'blue' : ''}`}>{state}</span>
        </div>
        <p className="muted small mt-2">{formatRange(item)}</p>
        <p className="muted small mt-1">
          예약 #{item.id} ·{' '}
          {item.kind === 'SEAT_USE'
            ? '좌석 이용 · 1명'
            : `공간 예약 · ${item.members.length + 1}명`}
        </p>
        {admin && item.owner && (
          <p className="small mt-1">
            {item.owner.name} · {item.owner.studentId} · {item.owner.email}
          </p>
        )}
        {item.endedAt && <p className="small muted mt-1">좌석 반납 완료</p>}
      </div>
      <ArrowUpRight size={19} className="shrink-0" />
    </Link>
  )
}
export function ReservationList({
  items,
  admin = false,
}: {
  items: Reservation[]
  admin?: boolean
}) {
  return items.length ? (
    <div className="panel p-0 overflow-hidden">
      {items.map((item) => (
        <ReservationRow key={item.id} item={item} admin={admin} />
      ))}
    </div>
  ) : (
    <Empty title="예약 내역이 없습니다.">
      {admin
        ? '담당 공간의 예약이 등록되면 여기에 표시됩니다.'
        : '공간을 찾아 첫 예약을 만들어보세요.'}
    </Empty>
  )
}
